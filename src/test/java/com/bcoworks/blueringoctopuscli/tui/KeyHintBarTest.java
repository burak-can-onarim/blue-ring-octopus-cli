package com.bcoworks.blueringoctopuscli.tui;

import com.bcoworks.blueringoctopuscli.tui.KeyHintBar.Hint;
import com.bcoworks.blueringoctopuscli.tui.KeyHintBar.Row;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class KeyHintBarTest {

    private static final List<Row> ROWS = List.of(
            new Row(List.of(new Hint("Enter", "Gönder"), new Hint("Tab", "Mod"), new Hint("Esc", "İptal"))),
            new Row(List.of(new Hint("Ctrl+L", "Model"), new Hint("PgUp/PgDn", "Kaydır"))));

    @Test
    void widthIsKeyPlusSpacePlusLabel() {
        assertEquals(12, new Hint("Enter", "Gönder").width());
    }

    @Test
    void keyColumnIsAsWideAsTheLongestKeyOfThatColumn() {
        // 1. sütun: Enter (5), Ctrl+L (6); 2. sütun: Tab (3), PgUp/PgDn (9); 3. sütun yalnızca ilk satırda: Esc (3)
        assertArrayEquals(new int[]{6, 9, 3}, KeyHintBar.keyWidths(ROWS));
    }

    @Test
    void columnWidthIsKeyColumnPlusSpacePlusTheLongestLabel() {
        // 1. sütun: 6 + 1 + max("Gönder"=6, "Model"=5) = 13; 2. sütun: 9 + 1 + max(3, 6) = 16; 3. sütun: 3 + 1 + 5 = 9
        assertArrayEquals(new int[]{13, 16, 9}, KeyHintBar.columnWidths(ROWS));
    }

    @Test
    void labelsOfAColumnStartAtTheSameOffsetEvenWhenKeysDiffer() {
        // "Enter Gönder" ve "Ctrl+L Model": açıklamalar 6 + 1 = 7. karakterden başlar; kısa tuşun yanında boşluk kalır
        int keyWidth = KeyHintBar.keyWidths(ROWS)[0];
        assertEquals(6, keyWidth);
        assertEquals(keyWidth + 1, 7);
    }

    @Test
    void messageOnlyColumnsHaveNoKeyColumn() {
        List<Row> rows = List.of(new Row(List.of(new Hint("", "Ollama çalışmıyor", null, null))));

        assertArrayEquals(new int[]{0}, KeyHintBar.keyWidths(rows));
        assertArrayEquals(new int[]{17}, KeyHintBar.columnWidths(rows));
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
    void stackedLegendFitsTheModelPanelWidth() {
        // model paneli iç genişliği 28, çubuk 1 sütun boşlukla başlar; her gösterge satırı "● seçili" gibi 8 sütundur
        List<Row> legend = List.of(
                new Row(List.of(new Hint("●", "seçili"))),
                new Row(List.of(new Hint("+", "kurulu"))),
                new Row(List.of(new Hint("-", "yok"))));
        int[] widths = KeyHintBar.columnWidths(legend);

        assertArrayEquals(new int[]{8}, widths);
        assertEquals(1, KeyHintBar.visibleColumns(widths, 27));
    }
}
