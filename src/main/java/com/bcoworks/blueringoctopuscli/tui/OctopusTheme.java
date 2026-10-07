package com.bcoworks.blueringoctopuscli.tui;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.SimpleTheme;
import com.googlecode.lanterna.graphics.Theme;

final class OctopusTheme {

    static final TextColor.RGB BASE = new TextColor.RGB(0x1e, 0x1e, 0x2e);
    static final TextColor.RGB SURFACE = new TextColor.RGB(0x31, 0x32, 0x44);
    static final TextColor.RGB MUTED = new TextColor.RGB(0x7f, 0x84, 0x9c);
    static final TextColor.RGB TEXT = new TextColor.RGB(0xcd, 0xd6, 0xf4);
    static final TextColor.RGB BLUE = new TextColor.RGB(0x89, 0xb4, 0xfa);
    static final TextColor.RGB MAUVE = new TextColor.RGB(0xcb, 0xa6, 0xf7);
    static final TextColor.RGB TEAL = new TextColor.RGB(0x94, 0xe2, 0xd5);
    static final TextColor.RGB GREEN = new TextColor.RGB(0xa6, 0xe3, 0xa1);
    static final TextColor.RGB YELLOW = new TextColor.RGB(0xf9, 0xe2, 0xaf);

    private OctopusTheme() {
    }

    /**
     * Kenarlıklar ve başlıklar MUTED, giriş kutuları TEXT, tüm zemin BASE (düz görünüm).
     */
    static Theme main() {
        return SimpleTheme.makeTheme(
                false,
                MUTED, BASE,   // temel: kenarlık, başlık, varsayılan etiket
                TEXT, BASE,    // düzenlenebilir alanlar
                BASE, BLUE,    // seçili öğe
                BASE);         // GUI zemini
    }

    static TextColor lerp(TextColor.RGB from, TextColor.RGB to, double t) {
        return new TextColor.RGB(
                mix(from.getRed(), to.getRed(), t),
                mix(from.getGreen(), to.getGreen(), t),
                mix(from.getBlue(), to.getBlue(), t));
    }

    private static int mix(int a, int b, double t) {
        return (int) Math.round(a + (b - a) * t);
    }
}