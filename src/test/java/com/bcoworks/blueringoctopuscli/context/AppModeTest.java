package com.bcoworks.blueringoctopuscli.context;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


class AppModeTest {

    @Test
    void nextWrapsAroundToFirstMode() {
        AppMode[] all = AppMode.values();
        assertEquals(all[0], all[all.length - 1].next());
    }

    @Test
    void previousWrapsAroundToLastMode() {
        AppMode[] all = AppMode.values();
        assertEquals(all[all.length - 1], all[0].previous());
    }

    @Test
    void nextAndPreviousAreInverse() {
        for (AppMode mode : AppMode.values()) {
            assertEquals(mode, mode.next().previous());
            assertEquals(mode, mode.previous().next());
        }
    }


    @Test
    void contextStartsInCodeAnalysisMode() {
        assertEquals(AppMode.CODE_ANALYSIS, new AppContext().getCurrentMode());
    }
}
