package com.bcoworks.blueringoctopuscli.tui;

import com.googlecode.lanterna.TextColor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The start banner: a small pixel-art blue-ringed octopus next to the name in a 3x5 pixel font, drawn with half-block
 * characters (two pixels per terminal cell, one colour for each). The octopus comes from {@code banner-octopus.txt}.
 * Terminals that are too narrow or too short get a single text line instead.
 */
final class BannerArt {

    /**
     * One terminal cell: a glyph with its foreground and background colour.
     */
    record Cell(String glyph, TextColor fg, TextColor bg) {
    }

    record Banner(List<List<Cell>> rows) {

        int width() {
            return rows.isEmpty() ? 0 : rows.getFirst().size();
        }

        int height() {
            return rows.size();
        }
    }

    static final String NAME = "BLUE RING OCTOPUS CLI";
    static final String TAGLINE = "Local-first AI code analysis & generation";

    private static final int MIN_ROWS_FOR_FULL = 30;
    private static final int GAP = 3;               // pixels between the octopus and the name
    private static final int NAME_TOP = 3;          // pixel row where the name starts
    private static final int TAGLINE_ROW = 5;       // terminal row of the tagline
    private static final int VERSION_ROW = 6;
    private static final int GLYPH_WIDTH = 3;
    private static final int GLYPH_HEIGHT = 5;
    private static final String RESOURCE = "banner-octopus.txt";

    /**
     * 3x5 pixel letters, only those the name needs.
     */
    private static final Map<Character, String[]> FONT = Map.ofEntries(
            Map.entry('B', new String[]{"##.", "#.#", "##.", "#.#", "##."}),
            Map.entry('L', new String[]{"#..", "#..", "#..", "#..", "###"}),
            Map.entry('U', new String[]{"#.#", "#.#", "#.#", "#.#", "###"}),
            Map.entry('E', new String[]{"###", "#..", "##.", "#..", "###"}),
            Map.entry('R', new String[]{"##.", "#.#", "##.", "#.#", "#.#"}),
            Map.entry('I', new String[]{"###", ".#.", ".#.", ".#.", "###"}),
            Map.entry('N', new String[]{"##.", "#.#", "#.#", "#.#", "#.#"}),
            Map.entry('G', new String[]{".##", "#..", "#.#", "#.#", ".##"}),
            Map.entry('O', new String[]{".#.", "#.#", "#.#", "#.#", ".#."}),
            Map.entry('C', new String[]{".##", "#..", "#..", "#..", ".##"}),
            Map.entry('T', new String[]{"###", ".#.", ".#.", ".#.", ".#."}),
            Map.entry('P', new String[]{"##.", "#.#", "##.", "#..", "#.."}),
            Map.entry('S', new String[]{".##", "#..", ".#.", "..#", "##."}));

    private final Banner full;
    private final Banner compact;

    private BannerArt(Banner full, Banner compact) {
        this.full = full;
        this.compact = compact;
    }

    static BannerArt load(String version) {
        Banner compact = compact(version);
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            String raw = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
            return new BannerArt(compose(raw.lines().toList(), version), compact);
        } catch (IOException | RuntimeException e) {
            return new BannerArt(new Banner(List.of()), compact); // a broken sprite must not stop the application
        }
    }

    /**
     * The full banner if it fits, otherwise a single line.
     */
    Banner choose(int availableColumns, int terminalRows) {
        boolean fits = full.width() > 0 && full.width() <= availableColumns && terminalRows >= MIN_ROWS_FOR_FULL;
        return fits ? full : compact;
    }

    // ---------------------------------------------------------------- composing

    /**
     * @param spriteLines lines of {@code banner-octopus.txt}: {@code pal <char> <#rrggbb>} and pixel rows
     */
    static Banner compose(List<String> spriteLines, String version) {
        Map<Character, TextColor> palette = new HashMap<>();
        List<String> rows = new ArrayList<>();
        for (String line : spriteLines) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("pal ")) {
                String[] part = line.split("\\s+");
                palette.put(part[1].charAt(0), rgb(part[2]));
            } else {
                rows.add(line);
            }
        }
        int spriteWidth = rows.getFirst().length();
        int nameWidth = nameWidth();
        int width = spriteWidth + GAP + nameWidth;
        int pixelRows = rows.size();

        TextColor[][] pixels = new TextColor[pixelRows][width];
        for (int y = 0; y < pixelRows; y++) {
            String row = rows.get(y);
            if (row.length() != spriteWidth) {
                throw new IllegalArgumentException("Sprite rows differ in length: " + row);
            }
            for (int x = 0; x < spriteWidth; x++) {
                char ch = row.charAt(x);
                if (ch != '.') {
                    TextColor color = palette.get(ch);
                    if (color == null) {
                        throw new IllegalArgumentException("Unknown sprite colour '" + ch + "'");
                    }
                    pixels[y][x] = color;
                }
            }
        }
        drawName(pixels, spriteWidth + GAP, nameWidth);

        List<List<Cell>> cells = new ArrayList<>();
        for (int r = 0; r < (pixelRows + 1) / 2; r++) {
            List<Cell> line = new ArrayList<>(width);
            for (int x = 0; x < width; x++) {
                TextColor top = pixels[2 * r][x];
                TextColor bottom = 2 * r + 1 < pixelRows ? pixels[2 * r + 1][x] : null;
                line.add(cell(top, bottom));
            }
            cells.add(line);
        }
        int textStart = spriteWidth + GAP;
        putText(cells, TAGLINE_ROW, textStart, TAGLINE, OctopusTheme.TEXT);
        putText(cells, VERSION_ROW, textStart, "v" + version, OctopusTheme.MUTED);
        return new Banner(List.copyOf(cells));
    }

    static Banner compact(String version) {
        String text = NAME + "  ·  " + TAGLINE + "  ·  v" + version;
        List<Cell> line = new ArrayList<>();
        for (char ch : text.toCharArray()) {
            line.add(new Cell(String.valueOf(ch), OctopusTheme.BLUE, OctopusTheme.BASE));
        }
        return new Banner(List.of(line));
    }

    /**
     * Two vertically stacked pixels in one terminal cell. A null colour is transparent (the window background).
     */
    static Cell cell(TextColor top, TextColor bottom) {
        if (top == null && bottom == null) {
            return new Cell(" ", OctopusTheme.TEXT, OctopusTheme.BASE);
        }
        if (bottom == null) {
            return new Cell("▀", top, OctopusTheme.BASE);
        }
        if (top == null) {
            return new Cell("▄", bottom, OctopusTheme.BASE);
        }
        return top.equals(bottom) ? new Cell("█", top, OctopusTheme.BASE) : new Cell("▀", top, bottom);
    }

    static int nameWidth() {
        int width = 0;
        for (char ch : NAME.toCharArray()) {
            width += ch == ' ' ? GLYPH_WIDTH : GLYPH_WIDTH + 1;
        }
        return width - 1;
    }

    private static void drawName(TextColor[][] pixels, int left, int nameWidth) {
        int x = left;
        for (char ch : NAME.toCharArray()) {
            if (ch == ' ') {
                x += GLYPH_WIDTH;
                continue;
            }
            String[] glyph = FONT.get(ch);
            if (glyph == null) {
                throw new IllegalStateException("No pixel letter for '" + ch + "'");
            }
            for (int gy = 0; gy < GLYPH_HEIGHT; gy++) {
                for (int gx = 0; gx < GLYPH_WIDTH; gx++) {
                    if (glyph[gy].charAt(gx) == '#') {
                        double t = (x + gx - left) / (double) Math.max(1, nameWidth - 1);
                        pixels[NAME_TOP + gy][x + gx] = OctopusTheme.lerp(OctopusTheme.BLUE, OctopusTheme.MAUVE, t);
                    }
                }
            }
            x += GLYPH_WIDTH + 1;
        }
    }

    private static void putText(List<List<Cell>> cells, int row, int column, String text, TextColor color) {
        List<Cell> line = cells.get(row);
        for (int i = 0; i < text.length() && column + i < line.size(); i++) {
            line.set(column + i, new Cell(String.valueOf(text.charAt(i)), color, OctopusTheme.BASE));
        }
    }

    private static TextColor rgb(String hex) {
        int value = Integer.parseInt(hex.startsWith("#") ? hex.substring(1) : hex, 16);
        return new TextColor.RGB((value >> 16) & 0xFF, (value >> 8) & 0xFF, value & 0xFF);
    }
}
