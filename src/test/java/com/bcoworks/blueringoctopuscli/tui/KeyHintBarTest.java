package com.bcoworks.blueringoctopuscli.tui;

import com.bcoworks.blueringoctopuscli.tui.KeyHintBar.Hint;
import com.bcoworks.blueringoctopuscli.tui.KeyHintBar.Row;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class KeyHintBarTest {

    private static final List<Row> ROWS = List.of(
            new Row("A", List.of(new Hint("Enter", "Gönder"), new Hint("Tab", "Mod"), new Hint("Esc", "İptal"))),
            new Row("B", List.of(new Hint("Ctrl+L", "Model"), new Hint("PgUp/PgDn", "Kaydır"))));

    @Test
    void widthIsKeyPlusSpacePlusLabel() {
        assertEquals(12, new Hint("Enter", "Gönder").width());
    }

    @Test
    void columnWidthIsTheWidestHintOfThatColumnAcrossRows() {
        // 1. sütun: "Enter Gönder" (12) ve "Ctrl+L Model" (12); 2. sütun: "Tab Mod" (7) ve "PgUp/PgDn Kaydır" (16);
        // 3. sütun yalnızca ilk satırda: "Esc İptal" (9)
        assertArrayEquals(new int[]{12, 16, 9}, KeyHintBar.columnWidths(ROWS));
    }

    @Test
    void noRowsMeansNoColumns() {
        assertArrayEquals(new int[0], KeyHintBar.columnWidths(List.of()));
    }

    @Test
    void showsAllColumnsWhenTheyFit() {
        // 12 + 3 + 16 + 3 + 9 = 43
        int[] widths = {12, 16, 9};
        assertEquals(3, KeyHintBar.visibleColumns(widths, 43));
        assertEquals(3, KeyHintBar.visibleColumns(widths, 200));
    }

    @Test
    void dropsTheRightmostColumnsInsteadOfCuttingOne() {
        int[] widths = {12, 16, 9};
        assertEquals(2, KeyHintBar.visibleColumns(widths, 42));
        assertEquals(2, KeyHintBar.visibleColumns(widths, 31)); // 12 + 3 + 16
        assertEquals(1, KeyHintBar.visibleColumns(widths, 30));
        assertEquals(1, KeyHintBar.visibleColumns(widths, 12));
        assertEquals(0, KeyHintBar.visibleColumns(widths, 11));
    }

    @Test
    void messageHintWithoutKeyIsJustItsLabel() {
        assertEquals(3, new Hint("", "abc", null, null).width());
    }

    @Test
    void legendFitsTheModelPanelWidth() {
        // model paneli iç genişliği 28, çubuk 1 sütun boşlukla başlar: 27 sütun kullanılabilir
        int[] widths = KeyHintBar.columnWidths(List.of(new Row(List.of(
                new Hint("●", "seçili"), new Hint("+", "kurulu"), new Hint("-", "yok")))));
        assertEquals(3, KeyHintBar.visibleColumns(widths, 27));
    }
}