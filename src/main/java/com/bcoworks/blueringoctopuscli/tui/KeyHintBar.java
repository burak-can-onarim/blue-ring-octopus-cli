package com.bcoworks.blueringoctopuscli.tui;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.AbstractComponent;
import com.googlecode.lanterna.gui2.ComponentRenderer;
import com.googlecode.lanterna.gui2.TextGUIGraphics;

import java.util.List;

/**
 * Kısayol / gösterge çubuğu: isteğe bağlı grup adı, ardından "TUŞ açıklama" çiftleri. Çiftler satırlar arasında
 * sütunlara hizalanır (n. çift her satırda aynı sütundadır) ve " · " ile ayrılır, böylece ayraçlar alt alta gelir.
 * Tuşlar vurgulu, açıklamalar soluk çizilir (çift başına renk verilebilir). Dar alanda metin ortadan kesilmez,
 * en sağdaki sütunlar atlanır. Satırlar çalışma anında değiştirilebilir.
 */
final class KeyHintBar extends AbstractComponent<KeyHintBar> {

    /**
     * @param key        tuş ya da işaret; boşsa yalnızca açıklama çizilir (düz mesaj)
     * @param keyColor   tuşun rengi
     * @param labelColor açıklamanın rengi
     */
    record Hint(String key, String label, TextColor keyColor, TextColor labelColor) {

        Hint(String key, String label) {
            this(key, label, OctopusTheme.BLUE, OctopusTheme.MUTED);
        }

        int width() {
            return key.isEmpty() ? label.length() : key.length() + 1 + label.length();
        }
    }

    record Row(List<Hint> hints) {
    }

    static final String SEPARATOR = " · ";
    private static final int MARGIN = 1;

    private List<Row> rows;
    private int[] columnWidths;
    private int[] keyWidths;

    KeyHintBar(List<Row> rows) {
        setRows(rows);
    }

    /**
     * Satırları değiştirir (örn. durum mesajı). Herhangi bir thread yerine GUI thread'inden çağrılmalıdır.
     */
    void setRows(List<Row> rows) {
        this.rows = List.copyOf(rows);
        this.columnWidths = columnWidths(this.rows);
        this.keyWidths = keyWidths(this.rows);
        invalidate();
    }

    /**
     * Her sütunun tuş kısmının genişliği: o sütundaki en uzun tuş. Açıklamalar bu genişlikten sonra başlar, böylece
     * satırlar arasında yalnızca ayraçlar değil açıklamalar da alt alta gelir.
     */
    static int[] keyWidths(List<Row> rows) {
        int columns = rows.stream().mapToInt(row -> row.hints().size()).max().orElse(0);
        int[] widths = new int[columns];
        for (Row row : rows) {
            for (int i = 0; i < row.hints().size(); i++) {
                widths[i] = Math.max(widths[i], row.hints().get(i).key().length());
            }
        }
        return widths;
    }

    /**
     * Her sütunun genişliği: tuş sütunu + boşluk (tuş varsa) + o sütundaki en uzun açıklama. Satırlarda çift sayısı
     * farklı olabilir.
     */
    static int[] columnWidths(List<Row> rows) {
        int[] keys = keyWidths(rows);
        int[] widths = new int[keys.length];
        for (Row row : rows) {
            for (int i = 0; i < row.hints().size(); i++) {
                int label = row.hints().get(i).label().length();
                widths[i] = Math.max(widths[i], keys[i] + (keys[i] > 0 ? 1 : 0) + label);
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
            return new TerminalSize(40, Math.max(1, rows.size()));
        }

        @Override
        public void drawComponent(TextGUIGraphics graphics, KeyHintBar component) {
            graphics.setBackgroundColor(OctopusTheme.BASE);
            graphics.fill(' ');

            int visible = visibleColumns(columnWidths, graphics.getSize().getColumns() - MARGIN);
            for (int y = 0; y < rows.size(); y++) {
                Row row = rows.get(y);
                int x = MARGIN;
                for (int column = 0; column < visible; column++) {
                    if (column > 0) {
                        graphics.setForegroundColor(OctopusTheme.MUTED);
                        graphics.putString(x, y, SEPARATOR);
                        x += SEPARATOR.length();
                    }
                    if (column < row.hints().size()) {
                        draw(graphics, x, y, row.hints().get(column), keyWidths[column]);
                    }
                    x += columnWidths[column];
                }
            }
        }

        private void draw(TextGUIGraphics graphics, int x, int y, Hint hint, int keyWidth) {
            // Açıklama, sütunun en uzun tuşundan sonra başlar (tuş yoksa sütun başında).
            int labelX = keyWidth > 0 ? x + keyWidth + 1 : x;
            if (!hint.key().isEmpty()) {
                graphics.setForegroundColor(hint.keyColor());
                graphics.enableModifiers(SGR.BOLD);
                graphics.putString(x, y, hint.key());
                graphics.disableModifiers(SGR.BOLD);
            }
            graphics.setForegroundColor(hint.labelColor());
            graphics.putString(labelX, y, hint.label());
        }
    }
}
