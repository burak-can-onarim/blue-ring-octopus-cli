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
 * Oturum boyunca birikmiş diyalog: her istek bir "Sen" başlığıyla, yanıtı bir "Octopus" başlığıyla gelir. Salt
 * okunurdur, odak almaz. Satırlar çizim anında genişliğe göre sarılır (pencere büyüyünce yeniden akar). Görünüm en
 * altta ise yeni mesajı takip eder; kullanıcı yukarı kaydırınca durur, en alta dönünce yeniden takip eder.
 * Geçmiş yalnızca bellekte tutulur, uygulama kapanınca sıfırlanır.
 */
final class DialogView extends AbstractComponent<DialogView> {

    enum Kind {
        USER_HEADER, USER, ANSWER_HEADER, ANSWER, HEADING, WARNING, INFO
    }

    /**
     * Ekrandaki bir satır. Başlıklarda text yalnızca etikettir, çizgi çizimde genişliğe doldurulur.
     */
    record VisualLine(String text, Kind kind) {
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

    // ---------------------------------------------------------------- content

    /**
     * Yeni bir tur başlatır: "Sen" başlığı ve kullanıcının metni. Sonraki {@link #addAnswer} yeni bir yanıt açar.
     */
    void startTurn(String label, String userText) {
        add(Kind.USER_HEADER, label);
        for (String line : lines(userText)) {
            add(Kind.USER, line);
        }
        answerOpen = false;
    }

    /**
     * Geçerli turun yanıtına metin ekler; tur için ilk çıktıysa "Octopus" başlığını açar.
     */
    void addAnswer(String text) {
        if (!answerOpen) {
            add(Kind.ANSWER_HEADER, "Octopus");
            answerOpen = true;
        }
        for (String line : lines(text)) {
            add(classify(line), line);
        }
    }

    /**
     * Tura bağlı olmayan sistem notu (karşılama, iptal bildirimi gibi).
     */
    void addNote(String text) {
        for (String line : lines(text)) {
            add(Kind.INFO, line);
        }
    }

    /**
     * Yanıt satırının türü: uyarı/hata sarı, "--- dosya ---" başlığı vurgulu, iptal notu soluk.
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
        }
        cacheDirty = true;
        invalidate();
    }

    int entryCount() {
        return entries.size();
    }

    // ---------------------------------------------------------------- scrolling

    /**
     * delta satır kaydırır (negatif yukarı). En alta varılırsa yeni mesajları yeniden takip eder.
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
     * Çizimde kullanılacak üst satır. Takipteyse hep en alt.
     */
    int viewTop(int rows, int width) {
        visibleRows = Math.max(1, rows);
        int max = Math.max(0, layout(width).size() - visibleRows);
        top = following ? max : Math.min(top, max);
        return top;
    }

    private int maxTop() {
        return Math.max(0, layout(lastWidth).size() - visibleRows);
    }

    // ---------------------------------------------------------------- layout

    /**
     * Genişliğe göre sarılmış satırlar. Her başlıktan önce (ilki hariç) bir boş satır bırakılır.
     */
    List<VisualLine> layout(int width) {
        int wrapWidth = Math.max(10, width);
        if (!cacheDirty && cacheWidth == wrapWidth) {
            return cache;
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
                for (String part : wrap(entry.text(), wrapWidth)) {
                    result.add(new VisualLine(part, entry.kind()));
                }
            }
        }
        cache = result;
        cacheWidth = wrapWidth;
        cacheDirty = false;
        return cache;
    }

    /**
     * Kelime sınırında böler, boşluk yoksa sert keser. Satır başı girintisi korunmaz, boş satır boş kalır.
     */
    static List<String> wrap(String line, int width) {
        if (line.length() <= width) {
            return List.of(line);
        }
        List<String> parts = new ArrayList<>();
        String rest = line;
        while (rest.length() > width) {
            int lead = rest.length() - rest.stripLeading().length();
            int cut = rest.lastIndexOf(' ', width);
            if (cut <= lead) {
                cut = width;
            }
            parts.add(rest.substring(0, cut).stripTrailing());
            rest = rest.substring(cut).stripLeading();
        }
        parts.add(rest);
        return parts;
    }

    /**
     * "── etiket ────────" biçiminde genişliğe dolan başlık çizgisi.
     */
    static String header(String label, int width) {
        String prefix = RULE + RULE + " " + label + " ";
        return prefix + RULE.repeat(Math.max(0, width - prefix.length()));
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
            int textWidth = Math.max(1, size.getColumns() - 1); // son sütun kaydırma çubuğu
            lastWidth = textWidth;
            List<VisualLine> lines = layout(textWidth);
            int viewTop = viewTop(size.getRows(), textWidth);

            graphics.setBackgroundColor(OctopusTheme.BASE);
            graphics.setForegroundColor(OctopusTheme.TEXT);
            graphics.fill(' ');
            for (int row = 0; row < size.getRows() && viewTop + row < lines.size(); row++) {
                VisualLine line = lines.get(viewTop + row);
                graphics.setForegroundColor(colorOf(line.kind()));
                boolean header = line.kind() == Kind.USER_HEADER || line.kind() == Kind.ANSWER_HEADER;
                graphics.putString(0, row, header ? header(line.text(), textWidth) : line.text());
            }
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
