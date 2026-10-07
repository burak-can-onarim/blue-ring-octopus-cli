package com.bcoworks.blueringoctopuscli.tui;

import com.bcoworks.blueringoctopuscli.tui.BannerArt.Banner;
import com.bcoworks.blueringoctopuscli.tui.BannerArt.Cell;
import com.googlecode.lanterna.TextColor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BannerArtTest {

    private static final TextColor RED = new TextColor.RGB(255, 0, 0);
    private static final TextColor BLUE = new TextColor.RGB(0, 0, 255);

    private static final List<String> TINY_SPRITE = List.of(
            "# a comment",
            "pal r #ff0000",
            "pal b #0000ff",
            ".rr.",
            "rbbr",
            "....",
            "rrrr",
            "....",
            "r..r",
            "....",
            "rrrr",
            "....",
            "....",
            "....",
            "....",
            "....",
            "....",
            "....",
            "....");

    private static String text(Banner banner, int row) {
        StringBuilder sb = new StringBuilder();
        banner.rows().get(row).forEach(cell -> sb.append(cell.glyph()));
        return sb.toString();
    }

    @Test
    void twoPixelsShareOneCellAsHalfBlocks() {
        assertEquals("▀", BannerArt.cell(RED, null).glyph());
        assertEquals(RED, BannerArt.cell(RED, null).fg());
        assertEquals("▄", BannerArt.cell(null, BLUE).glyph());
        assertEquals(BLUE, BannerArt.cell(null, BLUE).fg());
        assertEquals("█", BannerArt.cell(RED, RED).glyph());
        Cell both = BannerArt.cell(RED, BLUE);
        assertEquals("▀", both.glyph());
        assertEquals(RED, both.fg());
        assertEquals(BLUE, both.bg());
        assertEquals(" ", BannerArt.cell(null, null).glyph());
    }

    @Test
    void theNameFitsTheFontAndHasAKnownWidth() {
        // 18 letters of 3+1 pixels, 3 spaces of 3 pixels, minus the trailing gap
        assertEquals(18 * 4 + 3 * 3 - 1, BannerArt.nameWidth());
    }

    @Test
    void composesTheSpriteTheNameAndTheTexts() {
        Banner banner = BannerArt.compose(TINY_SPRITE, "1.2.3");

        assertEquals(8, banner.height()); // 16 pixel rows -> 8 terminal rows
        assertEquals(4 + 3 + BannerArt.nameWidth(), banner.width());
        banner.rows().forEach(row -> assertEquals(banner.width(), row.size()));
        assertTrue(text(banner, 5).contains(BannerArt.TAGLINE));
        assertTrue(text(banner, 6).contains("v1.2.3"));
    }

    @Test
    void theNameIsDrawnInTheGradient() {
        Banner banner = BannerArt.compose(TINY_SPRITE, "1");
        int start = 4 + 3;
        // the name starts on pixel row 3: terminal row 1, lower half; the first letter's top-left pixel is set
        Cell first = banner.rows().get(1).get(start);
        assertNotEquals(" ", first.glyph());
        assertEquals(OctopusTheme.BLUE, first.glyph().equals("▄") ? first.fg() : first.bg());
    }

    @Test
    void rejectsBrokenSprites() {
        assertThrows(IllegalArgumentException.class,
                () -> BannerArt.compose(List.of("pal r #ff0000", "rr", "r"), "1"));
        assertThrows(IllegalArgumentException.class,
                () -> BannerArt.compose(List.of("pal r #ff0000", "rx"), "1"));
    }

    @Test
    void theShippedSpriteLoadsAndHasEightRows() {
        BannerArt art = BannerArt.load("0.0.0");
        Banner banner = art.choose(200, 50);

        assertEquals(8, banner.height());
        assertTrue(banner.width() > 100 && banner.width() < 120, "width " + banner.width());
    }

    @Test
    void choosesTheCompactLineWhenTheTerminalIsTooSmall() {
        BannerArt art = BannerArt.load("0.0.0");
        int fullWidth = art.choose(200, 50).width();

        assertEquals(8, art.choose(fullWidth, 50).height());
        assertEquals(1, art.choose(fullWidth - 1, 50).height());
        assertEquals(1, art.choose(200, 29).height());
        assertTrue(text(art.choose(10, 10), 0).contains(BannerArt.TAGLINE));
        assertTrue(text(art.choose(10, 10), 0).endsWith("v0.0.0"));
    }
}
