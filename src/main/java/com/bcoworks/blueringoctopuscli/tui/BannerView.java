package com.bcoworks.blueringoctopuscli.tui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.AbstractComponent;
import com.googlecode.lanterna.gui2.ComponentRenderer;
import com.googlecode.lanterna.gui2.TextGUIGraphics;

import java.util.List;

/**
 * Draws a {@link BannerArt.Banner} cell by cell, each with its own foreground and background colour.
 */
final class BannerView extends AbstractComponent<BannerView> {

    private BannerArt.Banner banner = new BannerArt.Banner(List.of());

    void setBanner(BannerArt.Banner value) {
        this.banner = value;
        invalidate();
    }

    @Override
    protected ComponentRenderer<BannerView> createDefaultRenderer() {
        return new ComponentRenderer<>() {
            @Override
            public TerminalSize getPreferredSize(BannerView component) {
                return new TerminalSize(banner.width(), banner.height());
            }

            @Override
            public void drawComponent(TextGUIGraphics graphics, BannerView component) {
                graphics.setBackgroundColor(OctopusTheme.BASE);
                graphics.fill(' ');
                List<List<BannerArt.Cell>> rows = banner.rows();
                for (int y = 0; y < rows.size() && y < graphics.getSize().getRows(); y++) {
                    List<BannerArt.Cell> row = rows.get(y);
                    for (int x = 0; x < row.size() && x < graphics.getSize().getColumns(); x++) {
                        BannerArt.Cell cell = row.get(x);
                        graphics.setForegroundColor(cell.fg());
                        graphics.setBackgroundColor(cell.bg());
                        graphics.putString(x, y, cell.glyph());
                    }
                }
            }
        };
    }
}
