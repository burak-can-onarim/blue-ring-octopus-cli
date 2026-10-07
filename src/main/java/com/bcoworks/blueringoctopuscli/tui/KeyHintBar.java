package com.bcoworks.blueringoctopuscli.tui;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.AbstractComponent;
import com.googlecode.lanterna.gui2.ComponentRenderer;
import com.googlecode.lanterna.gui2.TextGUIGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * Tek satırlık kısayol çubuğu: solda grup adı, ardından "TUŞ açıklama" çiftleri. Tuşlar vurgulu, açıklamalar soluk
 * çizilir; çiftler boşlukla ayrılır. Dar pencerede metin ortadan kesilmez, sondaki (en az önemli) çiftler atlanır.
 */
final class KeyHintBar extends AbstractComponent<KeyHintBar> {

    record Hint(String key, String label) {

        int width() {
            return key.length() + 1 + label.length();
        }
    }

    static final int GROUP_WIDTH = 10;
    static final int GAP = 3;

    private final String group;
    private final List<Hint> hints;

    KeyHintBar(String group, List<Hint> hints) {
        this.group = group;
        this.hints = List.copyOf(hints);
    }

    /**
     * Verilen genişliğe sığan çiftler, sıralarını koruyarak. Sığmayan ilk çiftten sonrası atlanır.
     */
    static List<Hint> visible(List<Hint> hints, int width) {
        List<Hint> shown = new ArrayList<>();
        int used = 0;
        for (Hint hint : hints) {
            int needed = (shown.isEmpty() ? 0 : GAP) + hint.width();
            if (used + needed > width) {
                break;
            }
            shown.add(hint);
            used += needed;
        }
        return shown;
    }

    @Override
    protected ComponentRenderer<KeyHintBar> createDefaultRenderer() {
        return new Renderer();
    }

    private final class Renderer implements ComponentRenderer<KeyHintBar> {

        @Override
        public TerminalSize getPreferredSize(KeyHintBar component) {
            return new TerminalSize(40, 1);
        }

        @Override
        public void drawComponent(TextGUIGraphics graphics, KeyHintBar component) {
            graphics.setBackgroundColor(OctopusTheme.BASE);
            graphics.fill(' ');

            int x = 1;
            graphics.setForegroundColor(OctopusTheme.MAUVE);
            graphics.enableModifiers(SGR.BOLD);
            graphics.putString(x, 0, group);
            graphics.disableModifiers(SGR.BOLD);
            x += GROUP_WIDTH;

            int available = graphics.getSize().getColumns() - x;
            boolean first = true;
            for (Hint hint : visible(hints, available)) {
                if (!first) {
                    x += GAP;
                }
                first = false;
                graphics.setForegroundColor(OctopusTheme.BLUE);
                graphics.enableModifiers(SGR.BOLD);
                graphics.putString(x, 0, hint.key());
                graphics.disableModifiers(SGR.BOLD);
                x += hint.key().length() + 1;
                graphics.setForegroundColor(OctopusTheme.MUTED);
                graphics.putString(x, 0, hint.label());
                x += hint.label().length();
            }
        }
    }
}
