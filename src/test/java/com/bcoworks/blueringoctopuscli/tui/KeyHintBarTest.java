package com.bcoworks.blueringoctopuscli.tui;

import com.bcoworks.blueringoctopuscli.tui.KeyHintBar.Hint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KeyHintBarTest {

    private static final List<Hint> HINTS = List.of(
            new Hint("Enter", "Gönder"),          // 12
            new Hint("Tab", "Mod"),               // 7
            new Hint("Esc", "İptal / Çıkış"));    // 17

    @Test
    void widthIsKeyPlusSpacePlusLabel() {
        assertEquals(12, HINTS.get(0).width());
    }

    @Test
    void showsEverythingWhenItFits() {
        // 12 + 3 + 7 + 3 + 17 = 42
        assertEquals(HINTS, KeyHintBar.visible(HINTS, 42));
        assertEquals(HINTS, KeyHintBar.visible(HINTS, 100));
    }

    @Test
    void dropsTrailingHintsInsteadOfCuttingOne() {
        assertEquals(HINTS.subList(0, 2), KeyHintBar.visible(HINTS, 41));
        assertEquals(HINTS.subList(0, 2), KeyHintBar.visible(HINTS, 22));
        assertEquals(HINTS.subList(0, 1), KeyHintBar.visible(HINTS, 21));
    }

    @Test
    void showsNothingWhenEvenTheFirstDoesNotFit() {
        assertEquals(List.of(), KeyHintBar.visible(HINTS, 11));
        assertEquals(List.of(), KeyHintBar.visible(HINTS, 0));
    }
}
