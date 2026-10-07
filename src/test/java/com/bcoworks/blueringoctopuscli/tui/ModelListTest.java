package com.bcoworks.blueringoctopuscli.tui;

import com.bcoworks.blueringoctopuscli.tui.ModelList.Installed;
import com.bcoworks.blueringoctopuscli.tui.ModelList.Row;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ModelListTest {

    private static final Row SELECTED = new Row("a", true, Installed.YES);
    private static final Row INSTALLED = new Row("b", false, Installed.YES);
    private static final Row MISSING = new Row("c", false, Installed.NO);
    private static final Row UNKNOWN = new Row("d", false, Installed.UNKNOWN);

    @Test
    void selectedInstalledAndMissingModelsHaveDifferentColours() {
        assertNotEquals(ModelList.colorOf(SELECTED), ModelList.colorOf(INSTALLED));
        assertNotEquals(ModelList.colorOf(SELECTED), ModelList.colorOf(MISSING));
        assertNotEquals(ModelList.colorOf(INSTALLED), ModelList.colorOf(MISSING));
    }

    @Test
    void selectedWinsOverInstalledState() {
        // seçili model kurulu değilse de yeşildir; durum işareti kurulu olmadığını ayrıca gösterir
        Row selectedMissing = new Row("x", true, Installed.NO);
        assertEquals(ModelList.colorOf(SELECTED), ModelList.colorOf(selectedMissing));
        assertEquals("-", ModelList.stateMark(selectedMissing));
    }

    @Test
    void stateMarksFollowTheInstalledState() {
        assertEquals("+", ModelList.stateMark(INSTALLED));
        assertEquals("-", ModelList.stateMark(MISSING));
        assertEquals(" ", ModelList.stateMark(UNKNOWN));
        assertEquals(ModelList.colorOf(INSTALLED), ModelList.stateColor(INSTALLED));
    }

    @Test
    void cursorStaysInsideTheList() {
        ModelList list = new ModelList();
        list.setRows(List.of(SELECTED, INSTALLED, MISSING));

        list.moveCursor(-5);
        assertEquals(0, list.getCursor());
        list.moveCursor(1);
        assertEquals("b", list.cursorRow().name());
        list.moveCursor(10);
        assertEquals(2, list.getCursor());
    }

    @Test
    void shrinkingTheListPullsTheCursorBack() {
        ModelList list = new ModelList();
        list.setRows(List.of(SELECTED, INSTALLED, MISSING));
        list.setCursor(2);

        list.setRows(List.of(SELECTED));

        assertEquals(0, list.getCursor());
    }

    @Test
    void emptyListHasNoCursorRow() {
        ModelList list = new ModelList();

        assertNull(list.cursorRow());
        list.moveCursor(3);
        assertEquals(0, list.getCursor());
    }

    @Test
    void viewScrollsOnlyWhenTheCursorLeavesIt() {
        // 10 satır, 4 görünür
        assertEquals(0, ModelList.viewTop(0, 3, 4, 10));
        assertEquals(1, ModelList.viewTop(0, 4, 4, 10));
        assertEquals(6, ModelList.viewTop(6, 9, 4, 10));
        assertEquals(2, ModelList.viewTop(5, 2, 4, 10));
    }
}
