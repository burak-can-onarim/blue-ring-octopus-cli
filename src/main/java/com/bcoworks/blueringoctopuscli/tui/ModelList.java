package com.bcoworks.blueringoctopuscli.tui;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.AbstractInteractableComponent;
import com.googlecode.lanterna.gui2.InteractableRenderer;
import com.googlecode.lanterna.gui2.TextGUIGraphics;

import java.util.List;

/**
 * Model paneli listesi. Satır başına renk verebilmek için ActionListBox yerine kendi çizimi var: seçili model yeşil,
 * kurulu modeller mavi, kurulu olmayanlar soluk. Tuşları MainWindow yönetir (↑↓ imleç, Enter seç); bu bileşen yalnızca
 * satırları, imleci ve kaydırmayı tutar. Odaktayken imlecin olduğu satır vurgulanır.
 */
final class ModelList extends AbstractInteractableComponent<ModelList> {

    enum Installed {
        YES, NO, UNKNOWN
    }

    /**
     * @param selected geçerli moddaki seçili model mi
     */
    record Row(String name, boolean selected, Installed installed) {
    }

    private List<Row> rows = List.of();
    private int cursor;
    private int top;
    private int visibleRows = 8;

    // ---------------------------------------------------------------- state

    void setRows(List<Row> rows) {
        this.rows = List.copyOf(rows);
        this.cursor = clampCursor(cursor);
        invalidate();
    }

    int rowCount() {
        return rows.size();
    }

    int getCursor() {
        return cursor;
    }

    /**
     * The row shown y rows below the top of this list, or -1 if there is none there.
     */
    int rowAt(int y) {
        int index = top + y;
        return y >= 0 && index < rows.size() ? index : -1;
    }

    void setCursor(int index) {
        cursor = clampCursor(index);
        invalidate();
    }

    void moveCursor(int delta) {
        setCursor(cursor + delta);
    }

    Row cursorRow() {
        return rows.isEmpty() ? null : rows.get(cursor);
    }

    private int clampCursor(int index) {
        return rows.isEmpty() ? 0 : Math.clamp(index, 0, rows.size() - 1);
    }

    /**
     * İmleci görünür tutan üst satır.
     */
    static int viewTop(int top, int cursor, int visible, int total) {
        int result = top;
        if (cursor < result) {
            result = cursor;
        } else if (cursor >= result + visible) {
            result = cursor - visible + 1;
        }
        return Math.clamp(result, 0, Math.max(0, total - visible));
    }

    /**
     * Satırın rengi: seçili yeşil, kurulu mavi, kurulu değil soluk, bilinmiyorsa normal.
     */
    static TextColor colorOf(Row row) {
        if (row.selected()) {
            return OctopusTheme.GREEN;
        }
        return switch (row.installed()) {
            case YES -> OctopusTheme.BLUE;
            case NO -> OctopusTheme.MUTED;
            case UNKNOWN -> OctopusTheme.TEXT;
        };
    }

    /**
     * Sağdaki durum işareti ve rengi: + kurulu (mavi), - yok (soluk), bilinmiyorsa boş.
     */
    static String stateMark(Row row) {
        return switch (row.installed()) {
            case YES -> "+";
            case NO -> "-";
            case UNKNOWN -> " ";
        };
    }

    static TextColor stateColor(Row row) {
        return row.installed() == Installed.YES ? OctopusTheme.BLUE : OctopusTheme.MUTED;
    }

    // ---------------------------------------------------------------- rendering

    @Override
    protected InteractableRenderer<ModelList> createDefaultRenderer() {
        return new Renderer();
    }

    private final class Renderer implements InteractableRenderer<ModelList> {

        @Override
        public TerminalSize getPreferredSize(ModelList component) {
            return new TerminalSize(28, 8);
        }

        @Override
        public TerminalPosition getCursorLocation(ModelList component) {
            return null; // satır vurgusu kullanılır, terminal imleci gösterilmez
        }

        @Override
        public void drawComponent(TextGUIGraphics graphics, ModelList component) {
            TerminalSize size = graphics.getSize();
            visibleRows = Math.max(1, size.getRows());
            top = viewTop(top, cursor, visibleRows, rows.size());

            graphics.setBackgroundColor(OctopusTheme.BASE);
            graphics.fill(' ');
            int nameWidth = Math.max(1, size.getColumns() - 5); // " ● " + ad + " " + durum
            for (int y = 0; y < visibleRows && top + y < rows.size(); y++) {
                Row row = rows.get(top + y);
                boolean highlighted = isFocused() && top + y == cursor;
                TextColor background = highlighted ? OctopusTheme.SURFACE : OctopusTheme.BASE;
                graphics.setBackgroundColor(background);
                graphics.drawLine(0, y, size.getColumns() - 1, y, ' ');

                TextColor color = colorOf(row);
                graphics.setForegroundColor(color);
                if (row.selected()) {
                    graphics.enableModifiers(SGR.BOLD);
                }
                graphics.putString(1, y, row.selected() ? "●" : "○");
                graphics.putString(3, y, fit(row.name(), nameWidth));
                graphics.disableModifiers(SGR.BOLD);

                graphics.setForegroundColor(stateColor(row));
                graphics.putString(size.getColumns() - 2, y, stateMark(row));
            }
        }
    }

    private static String fit(String text, int width) {
        return text.length() > width ? text.substring(0, width - 1) + "…" : text;
    }
}
