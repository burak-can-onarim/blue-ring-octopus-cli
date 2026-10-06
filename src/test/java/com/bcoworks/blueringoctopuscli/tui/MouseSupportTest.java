package com.bcoworks.blueringoctopuscli.tui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MouseSupportTest {

    @Test
    void convertsPixelsToCells() {
        assertEquals(0, MouseSupport.cell(0, 10));
        assertEquals(0, MouseSupport.cell(9, 10));
        assertEquals(1, MouseSupport.cell(10, 10));
        assertEquals(2, MouseSupport.cell(25, 10));
    }

    @Test
    void clampsNegativePixelsAndBadCellSizes() {
        assertEquals(0, MouseSupport.cell(-5, 10));
        assertEquals(0, MouseSupport.cell(50, 0));
    }

    @Test
    void containsIncludesTopLeftAndExcludesBottomRight() {
        // 10 sütun x 3 satır, sol-üst (5, 2)
        assertTrue(MouseSupport.contains(5, 2, 10, 3, 5, 2));
        assertTrue(MouseSupport.contains(5, 2, 10, 3, 14, 4));
        assertFalse(MouseSupport.contains(5, 2, 10, 3, 15, 4));
        assertFalse(MouseSupport.contains(5, 2, 10, 3, 14, 5));
        assertFalse(MouseSupport.contains(5, 2, 10, 3, 4, 2));
        assertFalse(MouseSupport.contains(5, 2, 10, 3, 5, 1));
    }

    @Test
    void emptyAreaContainsNothing() {
        assertFalse(MouseSupport.contains(0, 0, 0, 0, 0, 0));
    }
}
