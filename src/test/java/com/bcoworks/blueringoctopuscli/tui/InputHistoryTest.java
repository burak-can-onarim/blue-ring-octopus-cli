package com.bcoworks.blueringoctopuscli.tui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class InputHistoryTest {

    @Test
    void emptyHistoryHasNothingToNavigate() {
        InputHistory history = new InputHistory();
        assertNull(history.previous("taslak"));
        assertNull(history.next());
    }

    @Test
    void previousWalksBackwardsAndStopsAtOldest() {
        InputHistory history = new InputHistory();
        history.add("bir");
        history.add("iki");
        assertEquals("iki", history.previous(""));
        assertEquals("bir", history.previous(""));
        assertNull(history.previous(""));
    }

    @Test
    void nextReturnsToDraftAfterNewestEntry() {
        InputHistory history = new InputHistory();
        history.add("bir");
        history.add("iki");
        assertEquals("iki", history.previous("yarım yazı"));
        assertEquals("bir", history.previous("yarım yazı"));
        assertEquals("iki", history.next());
        assertEquals("yarım yazı", history.next());
        assertNull(history.next());
    }

    @Test
    void ignoresBlankAndConsecutiveDuplicateEntries() {
        InputHistory history = new InputHistory();
        history.add("bir");
        history.add("   ");
        history.add(null);
        history.add("bir");
        history.add(" bir ");
        assertEquals("bir", history.previous(""));
        assertNull(history.previous(""));
    }

    @Test
    void keepsNonConsecutiveDuplicates() {
        InputHistory history = new InputHistory();
        history.add("a");
        history.add("b");
        history.add("a");
        assertEquals("a", history.previous(""));
        assertEquals("b", history.previous(""));
        assertEquals("a", history.previous(""));
    }

    @Test
    void keepsOnlyLastHundredEntries() {
        InputHistory history = new InputHistory();
        for (int i = 1; i <= 105; i++) {
            history.add("giris-" + i);
        }
        String oldest = null;
        String value;
        while ((value = history.previous("")) != null) {
            oldest = value;
        }
        assertEquals("giris-6", oldest);
    }

    @Test
    void addResetsCursorAndDraft() {
        InputHistory history = new InputHistory();
        history.add("bir");
        history.previous("taslak");
        history.add("iki");
        assertNull(history.next());
        assertEquals("iki", history.previous("yeni"));
    }
}
