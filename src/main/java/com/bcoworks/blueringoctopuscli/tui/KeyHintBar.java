package com.bcoworks.blueringoctopuscli.tui;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.AbstractComponent;
import com.googlecode.lanterna.gui2.ComponentRenderer;
import com.googlecode.lanterna.gui2.TextGUIGraphics;

import java.util.List;

/**
 * Kısayol çubuğu: her satırın başında grup adı, ardından "TUŞ açıklama" çiftleri. Çiftler satırlar arasında sütunlara
 * hizalanır (n. çift her satırda aynı sütundadır) ve " · " ile ayrılır, böylece ayraçlar alt alta gelir. Tuşlar vurgulu,
 * açıklamalar soluk çizilir. Dar pencerede metin ortadan kesilmez, en sağdaki sütunlar atlanır.
 */
final class KeyHintBar extends AbstractComponent<KeyHintBar> {

    record Hint(String key, String label) {

        int width() {
            return key.length() + 1 + label.length();
        }
    }

    record Row(String group, List<Hint> hints) {
    }

    static final int GROUP_WIDTH = 10;
    static final String SEPARATOR = " · ";

    private final List<Row> rows;
    private final int[] columnWidths;

    KeyHintBar(List<Row> rows) {
        this.rows = List.copyOf(rows);
        this.columnWidths = columnWidths(this.rows);
    }

    /**
     * Her sütunun genişliği: o sütundaki en geniş çift. Satırlarda çift sayısı farklı olabilir.
     */
    static int[] columnWidths(List<Row> rows) {
        int columns = rows.stream().mapToInt(row -> row.hints().size()).max().orElse(0);
        int[] widths = new int[columns];
        for (Row row : rows) {
            for (int i = 0; i < row.hints().size(); i++) {
                widths[i] = Math.max(widths[i], row.hints().get(i).width());
            }
        }
        return widths;
    }

    /**
     * Verilen genişliğe sığan sütun sayısı (soldan). Sığmayan ilk sütundan sonrası atlanır.
     */
    static int visibleColumns(int[] widths, int available) {
        int used = 0;
        for (int i = 0; i < widths.length; i++) {
            int needed = (i == 0 ? 0 : SEPARATOR.length()) + widths[i];
            if (used + needed > available) {
                return i;
            }
            used += needed;
        }
        return widths.length;
    }

    @Override
    protected ComponentRenderer<KeyHintBar> createDefaultRenderer() {
        return new Renderer();
    }

    private final class Renderer implements ComponentRenderer<KeyHintBar> {

        @Override
        public TerminalSize getPreferredSize(KeyHintBar component) {
            return new TerminalSize(40, rows.size());
        }

        @Override
        public void drawComponent(TextGUIGraphics graphics, KeyHintBar component) {
            graphics.setBackgroundColor(OctopusTheme.BASE);
            graphics.fill(' ');

            int start = 1 + GROUP_WIDTH;
            int visible = visibleColumns(columnWidths, graphics.getSize().getColumns() - start);
            for (int y = 0; y < rows.size(); y++) {
                Row row = rows.get(y);
                graphics.setForegroundColor(OctopusTheme.MAUVE);
                graphics.enableModifiers(SGR.BOLD);
                graphics.putString(1, y, row.group());
                graphics.disableModifiers(SGR.BOLD);

                int x = start;
                for (int column = 0; column < visible; column++) {
                    if (column > 0) {
                        graphics.setForegroundColor(OctopusTheme.MUTED);
                        graphics.putString(x, y, SEPARATOR);
                        x += SEPARATOR.length();
                    }
                    if (column < row.hints().size()) {
                        Hint hint = row.hints().get(column);
                        graphics.setForegroundColor(OctopusTheme.BLUE);
                        graphics.enableModifiers(SGR.BOLD);
                        graphics.putString(x, y, hint.key());
                        graphics.disableModifiers(SGR.BOLD);
                        graphics.setForegroundColor(OctopusTheme.MUTED);
                        graphics.putString(x + hint.key().length() + 1, y, hint.label());
                    }
                    x += columnWidths[column];
                }
            }
        }
    }
}
