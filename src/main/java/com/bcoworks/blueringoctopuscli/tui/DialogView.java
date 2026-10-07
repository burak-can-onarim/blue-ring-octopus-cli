package com.bcoworks.blueringoctopuscli.tui;

import com.bcoworks.blueringoctopuscli.i18n.Messages;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.AbstractComponent;
import com.googlecode.lanterna.gui2.ComponentRenderer;
import com.googlecode.lanterna.gui2.TextGUIGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * The conversation of the session (the "Output" box): every request comes under a "You" heading, the answer under an
 * "Octopus" heading. It is read-only and never takes the keyboard focus. Lines are wrapped when drawn, so they reflow
 * when the window grows. A new message scrolls the view to the bottom; scrolling up pauses that until the next
 * message or until the bottom is reached again. Text can be selected with the mouse (drag, double click for a word,
 * triple click for a line) and copied. The history lives in memory only and is gone when the application quits.
 */
final class DialogView extends AbstractComponent<DialogView> {

    enum Kind {
        USER_HEADER, USER, ANSWER_HEADER, ANSWER, HEADING, WARNING, INFO
    }

    /**
     * A line on screen. For headings, text is only the label; the rule is filled in to the width when drawn.
     *
     * @param continuation  the line continues the previous one (a wrapped line), not a new line of text
     * @param spaceBefore   the wrap happened at a space, so joining the two lines needs one
     */
    record VisualLine(String text, Kind kind, boolean continuation, boolean spaceBefore) {

        VisualLine(String text, Kind kind) {
            this(text, kind, false, false);
        }

        boolean isHeader() {
            return kind == Kind.USER_HEADER || kind == Kind.ANSWER_HEADER;
        }
    }

    /**
     * A wrapped piece of a line.
     */
    record Part(String text, boolean spaceBefore) {
    }

    /**
     * A character cell in the wrapped text: line index and column.
     */
    record Pos(int line, int col) implements Comparable<Pos> {

        @Override
        public int compareTo(Pos other) {
            return line != other.line ? Integer.compare(line, other.line) : Integer.compare(col, other.col);
        }
    }

    private record Entry(Kind kind, String text) {
    }

    static final int MAX_ENTRIES = 5_000;
    private static final int FALLBACK_WIDTH = 80;
    private static final int FALLBACK_ROWS = 10;
    private static final String RULE = "─";

    private final List<Entry> entries = new ArrayList<>();
    private boolean answerOpen;
    private int top;
    private boolean following = true;
    private int visibleRows = FALLBACK_ROWS;
    private int lastWidth = FALLBACK_WIDTH;

    private List<VisualLine> cache = List.of();
    private int cacheWidth = -1;
    private boolean cacheDirty = true;

    private Pos anchor;
    private Pos caret;
    private boolean selectionActive;

    // ---------------------------------------------------------------- content

    /**
     * Starts a new turn: the "You" heading and the user's text. The next {@link #addAnswer} opens a new answer.
     */
    void startTurn(String label, String userText) {
        add(Kind.USER_HEADER, label);
        for (String line : lines(userText)) {
            add(Kind.USER, line);
        }
        answerOpen = false;
        following = true;
    }

    /**
     * Adds text to the answer of the current turn; the first output of a turn opens the "Octopus" heading.
     */
    void addAnswer(String text) {
        if (!answerOpen) {
            add(Kind.ANSWER_HEADER, "Octopus");
            answerOpen = true;
        }
        for (String line : lines(text)) {
            add(classify(line), line);
        }
        following = true;
    }

    /**
     * A note that does not belong to a turn (welcome, cancellation, hints).
     */
    void addNote(String text) {
        for (String line : lines(text)) {
            add(Kind.INFO, line);
        }
        following = true;
    }

    /**
     * Kind of an answer line: warnings and errors yellow, the "--- file ---" heading highlighted, the cancellation
     * note dim.
     */
    static Kind classify(String line) {
        String stripped = line.stripLeading();
        if (Messages.isProblemLine(stripped)) {
            return Kind.WARNING;
        }
        if (stripped.startsWith("--- ")) {
            return Kind.HEADING;
        }
        if (stripped.startsWith("■")) {
            return Kind.INFO;
        }
        return Kind.ANSWER;
    }

    private static List<String> lines(String text) {
        return List.of(text.replace("\t", "    ").split("\\R", -1));
    }

    private void add(Kind kind, String text) {
        entries.add(new Entry(kind, text));
        if (entries.size() > MAX_ENTRIES) {
            entries.subList(0, entries.size() - MAX_ENTRIES).clear();
            clearSelection(); // the line numbers shifted
        }
        cacheDirty = true;
        invalidate();
    }

    int entryCount() {
        return entries.size();
    }

    // ---------------------------------------------------------------- scrolling

    /**
     * Scrolls by delta lines (negative is up). Reaching the bottom makes the view follow new messages again.
     */
    void scroll(int delta) {
        int max = maxTop();
        if (following) {
            top = max;
        }
        top = Math.clamp(top + delta, 0, max);
        following = top >= max;
        invalidate();
    }

    void pageUp() {
        scroll(-Math.max(1, visibleRows - 1));
    }

    void pageDown() {
        scroll(Math.max(1, visibleRows - 1));
    }

    boolean isFollowing() {
        return following;
    }

    /**
     * Top line to draw. When following it is always the bottom.
     */
    int viewTop(int rows, int width) {
        visibleRows = Math.max(1, rows);
        int max = Math.max(0, layout(width).size() - visibleRows);
        top = following ? max : Math.min(top, max);
        return top;
    }

    /**
     * Tells the view how big the text area is (done on every draw; tests call it directly).
     */
    void viewport(int textWidth, int rows) {
        lastWidth = Math.max(1, textWidth);
        visibleRows = Math.max(1, rows);
    }

    private int maxTop() {
        return Math.max(0, layout(lastWidth).size() - visibleRows);
    }

    // ---------------------------------------------------------------- layout

    /**
     * The lines wrapped to the width. A blank line is left before every heading (except the first).
     */
    List<VisualLine> layout(int width) {
        int wrapWidth = Math.max(10, width);
        if (!cacheDirty && cacheWidth == wrapWidth) {
            return cache;
        }
        if (cacheWidth != -1 && cacheWidth != wrapWidth) {
            clearSelection(); // the wrapping changed, so the selected cells moved
        }
        List<VisualLine> result = new ArrayList<>();
        for (Entry entry : entries) {
            boolean header = entry.kind() == Kind.USER_HEADER || entry.kind() == Kind.ANSWER_HEADER;
            if (header && !result.isEmpty() && !result.getLast().text().isEmpty()) {
                result.add(new VisualLine("", Kind.INFO));
            }
            if (header) {
                result.add(new VisualLine(entry.text(), entry.kind()));
            } else {
                boolean first = true;
                for (Part part : wrapParts(entry.text(), wrapWidth)) {
                    result.add(new VisualLine(part.text(), entry.kind(), !first, part.spaceBefore()));
                    first = false;
                }
            }
        }
        cache = result;
        cacheWidth = wrapWidth;
        cacheDirty = false;
        return cache;
    }

    /**
     * Breaks at a word boundary, or hard when there is no space. Leading indentation is not kept; a blank line stays
     * blank.
     */
    static List<String> wrap(String line, int width) {
        return wrapParts(line, width).stream().map(Part::text).toList();
    }

    static List<Part> wrapParts(String line, int width) {
        if (line.length() <= width) {
            return List.of(new Part(line, false));
        }
        List<Part> parts = new ArrayList<>();
        String rest = line;
        boolean space = false;
        while (rest.length() > width) {
            int lead = rest.length() - rest.stripLeading().length();
            int cut = rest.lastIndexOf(' ', width);
            boolean atSpace = true;
            if (cut <= lead) {
                cut = width;
                atSpace = false;
            }
            parts.add(new Part(rest.substring(0, cut).stripTrailing(), space));
            rest = rest.substring(cut).stripLeading();
            space = atSpace;
        }
        parts.add(new Part(rest, space));
        return parts;
    }

    /**
     * "── label ────────" filled to the width.
     */
    static String header(String label, int width) {
        String prefix = RULE + RULE + " " + label + " ";
        return prefix + RULE.repeat(Math.max(0, width - prefix.length()));
    }

    private String shown(VisualLine line) {
        return line.isHeader() ? header(line.text(), lastWidth) : line.text();
    }

    // ---------------------------------------------------------------- selection

    /**
     * The cell under a screen position (row and column relative to the text area).
     */
    Pos positionAt(int row, int column) {
        List<VisualLine> lines = layout(lastWidth);
        if (lines.isEmpty()) {
            return new Pos(0, 0);
        }
        int line = Math.clamp((long) top + row, 0, lines.size() - 1);
        int length = shown(lines.get(line)).length();
        return new Pos(line, Math.clamp(column, 0, Math.max(0, length - 1)));
    }

    /**
     * A mouse button went down: the selection starts here (a plain click only clears the old one).
     */
    void startSelection(int row, int column) {
        anchor = positionAt(row, column);
        caret = anchor;
        selectionActive = false;
        invalidate();
    }

    /**
     * The mouse moved with the button down: the selection reaches the cell under it. Rows above or below the text
     * area (negative or too large) are clamped to the first or last visible line.
     */
    void extendSelection(int row, int column) {
        if (anchor == null) {
            return;
        }
        caret = positionAt(Math.clamp(row, 0, visibleRows - 1), column);
        selectionActive = !caret.equals(anchor);
        invalidate();
    }

    void clearSelection() {
        anchor = null;
        caret = null;
        selectionActive = false;
        invalidate();
    }

    boolean hasSelection() {
        return anchor != null && selectionActive;
    }

    /**
     * Double click: the word (a run of non-blank characters) under the position.
     */
    void selectWordAt(int row, int column) {
        Pos at = positionAt(row, column);
        List<VisualLine> lines = layout(lastWidth);
        if (lines.isEmpty()) {
            return;
        }
        String text = shown(lines.get(at.line()));
        if (text.isEmpty() || Character.isWhitespace(text.charAt(at.col()))) {
            clearSelection();
            return;
        }
        int from = at.col();
        int to = at.col();
        while (from > 0 && !Character.isWhitespace(text.charAt(from - 1))) {
            from--;
        }
        while (to < text.length() - 1 && !Character.isWhitespace(text.charAt(to + 1))) {
            to++;
        }
        anchor = new Pos(at.line(), from);
        caret = new Pos(at.line(), to);
        selectionActive = true;
        invalidate();
    }

    /**
     * Triple click: the whole line of text, including the rows it wrapped onto.
     */
    void selectLineAt(int row) {
        Pos at = positionAt(row, 0);
        List<VisualLine> lines = layout(lastWidth);
        if (lines.isEmpty()) {
            return;
        }
        int first = at.line();
        while (first > 0 && lines.get(first).continuation()) {
            first--;
        }
        int last = at.line();
        while (last + 1 < lines.size() && lines.get(last + 1).continuation()) {
            last++;
        }
        anchor = new Pos(first, 0);
        caret = new Pos(last, Math.max(0, shown(lines.get(last)).length() - 1));
        selectionActive = true;
        invalidate();
    }

    /**
     * The selected text. Rows a line was wrapped onto are joined again; headings give their label only.
     */
    String selectedText() {
        if (!hasSelection()) {
            return "";
        }
        Pos start = anchor.compareTo(caret) <= 0 ? anchor : caret;
        Pos end = anchor.compareTo(caret) <= 0 ? caret : anchor;
        List<VisualLine> lines = layout(lastWidth);
        StringBuilder text = new StringBuilder();
        for (int i = start.line(); i <= end.line() && i < lines.size(); i++) {
            VisualLine line = lines.get(i);
            if (i > start.line()) {
                text.append(line.continuation() ? (line.spaceBefore() ? " " : "") : "\n");
            }
            if (line.isHeader()) {
                text.append(line.text());
                continue;
            }
            String content = line.text();
            int from = i == start.line() ? start.col() : 0;
            int to = i == end.line() ? Math.min(end.col() + 1, content.length()) : content.length();
            if (from < to) {
                text.append(content, from, to);
            }
        }
        return text.toString();
    }

    /**
     * The columns [from, to) of a line that are selected, or null.
     */
    private int[] selectionSpan(int lineIndex, int length) {
        if (!hasSelection()) {
            return null;
        }
        Pos start = anchor.compareTo(caret) <= 0 ? anchor : caret;
        Pos end = anchor.compareTo(caret) <= 0 ? caret : anchor;
        if (lineIndex < start.line() || lineIndex > end.line()) {
            return null;
        }
        int from = lineIndex == start.line() ? start.col() : 0;
        int to = lineIndex == end.line() ? Math.min(end.col() + 1, length) : length;
        if (length == 0) {
            return lineIndex < end.line() || lineIndex == start.line() ? new int[]{0, 1} : null;
        }
        return from < to ? new int[]{from, to} : null;
    }

    // ---------------------------------------------------------------- rendering

    @Override
    protected ComponentRenderer<DialogView> createDefaultRenderer() {
        return new Renderer();
    }

    private static TextColor colorOf(Kind kind) {
        return switch (kind) {
            case USER_HEADER, USER -> OctopusTheme.MAUVE;
            case ANSWER_HEADER, HEADING -> OctopusTheme.TEAL;
            case WARNING -> OctopusTheme.YELLOW;
            case INFO -> OctopusTheme.MUTED;
            case ANSWER -> OctopusTheme.TEXT;
        };
    }

    private final class Renderer implements ComponentRenderer<DialogView> {

        @Override
        public TerminalSize getPreferredSize(DialogView component) {
            return new TerminalSize(40, 6);
        }

        @Override
        public void drawComponent(TextGUIGraphics graphics, DialogView component) {
            TerminalSize size = graphics.getSize();
            int textWidth = Math.max(1, size.getColumns() - 1); // the last column is the scrollbar
            viewport(textWidth, size.getRows());
            List<VisualLine> lines = layout(textWidth);
            int viewTop = viewTop(size.getRows(), textWidth);

            graphics.setBackgroundColor(OctopusTheme.BASE);
            graphics.setForegroundColor(OctopusTheme.TEXT);
            graphics.fill(' ');
            for (int row = 0; row < size.getRows() && viewTop + row < lines.size(); row++) {
                VisualLine line = lines.get(viewTop + row);
                String text = shown(line);
                graphics.setBackgroundColor(OctopusTheme.BASE);
                graphics.setForegroundColor(colorOf(line.kind()));
                graphics.putString(0, row, text);

                int[] span = selectionSpan(viewTop + row, text.length());
                if (span != null) {
                    graphics.setBackgroundColor(OctopusTheme.BLUE);
                    graphics.setForegroundColor(OctopusTheme.BASE);
                    graphics.putString(span[0], row, text.isEmpty() ? " " : text.substring(span[0], span[1]));
                }
            }
            graphics.setBackgroundColor(OctopusTheme.BASE);
            drawScrollbar(graphics, size, lines.size(), viewTop);
        }

        private void drawScrollbar(TextGUIGraphics graphics, TerminalSize size, int total, int viewTop) {
            int rows = size.getRows();
            if (total <= rows || rows < 3) {
                return;
            }
            int column = size.getColumns() - 1;
            int thumb = Math.max(1, rows * rows / total);
            int travel = rows - thumb;
            int maxTop = total - rows;
            int thumbAt = maxTop == 0 ? 0 : viewTop * travel / maxTop;
            for (int row = 0; row < rows; row++) {
                boolean inThumb = row >= thumbAt && row < thumbAt + thumb;
                graphics.setForegroundColor(inThumb ? OctopusTheme.BLUE : OctopusTheme.MUTED);
                graphics.putString(column, row, inThumb ? "█" : "│");
            }
        }
    }
}
