package com.bcoworks.codeanalyzer.tui;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.AbstractInteractableComponent;
import com.googlecode.lanterna.gui2.InteractableRenderer;
import com.googlecode.lanterna.gui2.TextGUIGraphics;
import com.googlecode.lanterna.input.KeyStroke;

import java.util.ArrayList;
import java.util.List;

/**
 * Web'deki textarea mantığında çok satırlı giriş alanı.
 * Kelime bazlı sarma yapar, yükseklik MIN_ROWS ile MAX_ROWS arasında içeriğe göre büyür,
 * sonrasında kendi içinde kayar. Sarma, sabit '\n' satır sonlarından bağımsızdır.
 */
final class PromptArea extends AbstractInteractableComponent<PromptArea> {

    static final int MIN_ROWS = 2;
    static final int MAX_ROWS = 8;
    private static final int DEFAULT_WIDTH = 40;

    /**
     * Ekranda görünen bir satır: metindeki [start, end) aralığı.
     */
    private record Line(int start, int end, boolean paragraphEnd) {
        int length() {
            return end - start;
        }

        /**
         * İmlecin bu satırda durabileceği son sütun.
         */
        int maxColumn() {
            return paragraphEnd ? length() : Math.max(0, length() - 1);
        }
    }

    private final StringBuilder text = new StringBuilder();
    private int caret;
    private int goalColumn = -1; // yukarı/aşağı giderken korunan sütun
    private int topLine;
    private int rows = MIN_ROWS;
    private int lastWidth = DEFAULT_WIDTH;

    // ---------------------------------------------------------------- public API

    String getText() {
        return text.toString();
    }

    void setText(String value) {
        text.setLength(0);
        text.append(value == null ? "" : value);
        caret = text.length();
        topLine = 0;
        afterEdit();
    }

    /**
     * İmleç ilk satırda değilse bir satır yukarı çıkar ve true döner.
     */
    boolean moveCaretUp() {
        return moveVertically(-1);
    }

    /**
     * İmleç son satırda değilse bir satır aşağı iner ve true döner.
     */
    boolean moveCaretDown() {
        return moveVertically(1);
    }

    /**
     * Panodan gelen metni imlecin olduğu yere ekler. Satır sonlarını \n'e çevirir, kontrol karakterlerini atar.
     */
    void insertText(String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n').replace("\t", "    ");
        StringBuilder clean = new StringBuilder(normalized.length());
        normalized.chars()
                .filter(ch -> ch == '\n' || !Character.isISOControl(ch))
                .forEach(ch -> clean.append((char) ch));
        insert(clean.toString());
    }

    /**
     * İmlecin hemen solunda "\" varsa onu satır sonuna çevirir (kısayol tuşu olmayan terminaller için).
     */
    boolean continueLine() {
        if (caret > 0 && text.charAt(caret - 1) == '\\') {
            text.setCharAt(caret - 1, '\n');
            afterEdit();
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- input

    @Override
    protected Result handleKeyStroke(KeyStroke key) {
        switch (key.getKeyType()) {
            case Character -> {
                Character c = key.getCharacter();
                // Türkçe klavyede AltGr (@ \ { [ ] }) Ctrl+Alt olarak gelebilir, bu yüzden yalnızca "salt Ctrl" elenir.
                boolean ctrlOnly = key.isCtrlDown() && !key.isAltDown();
                if (c == null || ctrlOnly || Character.isISOControl(c)) {
                    return Result.UNHANDLED;
                }
                insert(String.valueOf(c));
            }
            case Enter -> insert("\n");
            case Backspace -> backspace();
            case Delete -> deleteForward();
            case ArrowLeft -> moveHorizontally(-1);
            case ArrowRight -> moveHorizontally(1);
            case ArrowUp -> moveCaretUp();
            case ArrowDown -> moveCaretDown();
            case Home -> jumpTo(false);
            case End -> jumpTo(true);
            default -> {
                return Result.UNHANDLED;
            }
        }
        return Result.HANDLED;
    }

    private void insert(String value) {
        text.insert(caret, value);
        caret += value.length();
        afterEdit();
    }

    private void backspace() {
        if (caret > 0) {
            text.deleteCharAt(caret - 1);
            caret--;
            afterEdit();
        }
    }

    private void deleteForward() {
        if (caret < text.length()) {
            text.deleteCharAt(caret);
            afterEdit();
        }
    }

    private void moveHorizontally(int delta) {
        caret = Math.max(0, Math.min(text.length(), caret + delta));
        goalColumn = -1;
        invalidate();
    }

    private void jumpTo(boolean end) {
        List<Line> lines = layout();
        Line line = lines.get(locate(lines)[0]);
        caret = end ? line.start() + line.maxColumn() : line.start();
        goalColumn = -1;
        invalidate();
    }

    private boolean moveVertically(int delta) {
        List<Line> lines = layout();
        int[] position = locate(lines);
        int targetRow = position[0] + delta;
        if (targetRow < 0 || targetRow >= lines.size()) {
            return false;
        }
        if (goalColumn < 0) {
            goalColumn = position[1];
        }
        Line target = lines.get(targetRow);
        caret = target.start() + Math.min(goalColumn, target.maxColumn());
        invalidate();
        return true;
    }

    private void afterEdit() {
        goalColumn = -1;
        updateRows(currentWidth());
        invalidate();
    }

    // ---------------------------------------------------------------- layout

    private int currentWidth() {
        int width = getSize().getColumns();
        return width > 1 ? width : lastWidth;
    }

    /**
     * Son sütun imleç için ayrılır, böylece satır sonundaki imleç taşmaz.
     */
    private static int wrapWidth(int width) {
        return Math.max(1, width - 1);
    }

    private static int clampRows(int lineCount) {
        return Math.clamp(lineCount, MIN_ROWS, MAX_ROWS);
    }

    /**
     * İçerik ya da genişlik değişince yüksekliği günceller.
     */
    private void updateRows(int width) {
        int needed = clampRows(layout(wrapWidth(width)).size());
        if (needed != rows) {
            rows = needed;
            setPreferredSize(new TerminalSize(DEFAULT_WIDTH, rows));
            invalidate();
        }
    }

    private List<Line> layout() {
        return layout(wrapWidth(currentWidth()));
    }

    private List<Line> layout(int width) {
        List<Line> lines = new ArrayList<>();
        int from = 0;
        while (true) {
            int newline = text.indexOf("\n", from);
            int to = newline < 0 ? text.length() : newline;
            wrapParagraph(from, to, width, lines);
            if (newline < 0) {
                return lines;
            }
            from = newline + 1;
        }
    }

    /**
     * Mümkünse son boşlukta, yoksa tam genişlikte keser.
     */
    private void wrapParagraph(int from, int to, int width, List<Line> out) {
        int start = from;
        while (to - start > width) {
            int limit = start + width;
            int space = text.lastIndexOf(" ", limit - 1);
            int end = space > start ? space + 1 : limit;
            out.add(new Line(start, end, false));
            start = end;
        }
        out.add(new Line(start, to, true));
    }

    /**
     * İmlecin {satır, sütun} konumu.
     */
    private int[] locate(List<Line> lines) {
        for (int row = 0; row < lines.size(); row++) {
            Line line = lines.get(row);
            boolean inside = caret >= line.start()
                    && (caret < line.end() || (caret == line.end() && line.paragraphEnd()));
            if (inside) {
                return new int[]{row, caret - line.start()};
            }
        }
        Line last = lines.getLast();
        return new int[]{lines.size() - 1, last.length()};
    }

    private void keepCaretVisible(List<Line> lines, int visibleRows) {
        int row = locate(lines)[0];
        if (row < topLine) {
            topLine = row;
        } else if (row >= topLine + visibleRows) {
            topLine = row - visibleRows + 1;
        }
        topLine = Math.clamp(topLine, 0, Math.max(0, lines.size() - visibleRows));
    }

    // ---------------------------------------------------------------- rendering

    @Override
    protected InteractableRenderer<PromptArea> createDefaultRenderer() {
        return new Renderer();
    }

    private final class Renderer implements InteractableRenderer<PromptArea> {

        @Override
        public TerminalSize getPreferredSize(PromptArea component) {
            return new TerminalSize(DEFAULT_WIDTH, rows);
        }

        @Override
        public TerminalPosition getCursorLocation(PromptArea component) {
            List<Line> lines = layout();
            int visible = getSize().getRows() > 0 ? getSize().getRows() : rows;
            keepCaretVisible(lines, visible);
            int[] position = locate(lines);
            return new TerminalPosition(position[1], position[0] - topLine);
        }

        @Override
        public void drawComponent(TextGUIGraphics graphics, PromptArea component) {
            TerminalSize size = graphics.getSize();
            lastWidth = size.getColumns();
            List<Line> lines = layout(wrapWidth(lastWidth));
            keepCaretVisible(lines, size.getRows());

            graphics.setForegroundColor(OctopusTheme.TEXT);
            graphics.setBackgroundColor(OctopusTheme.BASE);
            graphics.fill(' ');
            for (int row = 0; row < size.getRows() && topLine + row < lines.size(); row++) {
                Line line = lines.get(topLine + row);
                graphics.putString(0, row, text.substring(line.start(), line.end()));
            }

            // Genişlik değişti ve satır sayısı artık uymuyorsa, çizim bittikten sonra yeniden ölçülür.
            if (clampRows(lines.size()) != rows && getTextGUI() != null) {
                int width = lastWidth;
                getTextGUI().getGUIThread().invokeLater(() -> updateRows(width));
            }
        }
    }
}