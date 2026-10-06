package com.bcoworks.blueringoctopuscli.tui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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

    @Test
    void wheelAccumulatorTurnsWholeNotchesIntoLines() {
        MouseSupport.WheelAccumulator wheel = new MouseSupport.WheelAccumulator();

        assertEquals(3, wheel.add(1.0));
        assertEquals(-3, wheel.add(-1.0));
    }

    @Test
    void wheelAccumulatorCollectsTouchpadFractions() {
        MouseSupport.WheelAccumulator wheel = new MouseSupport.WheelAccumulator();

        assertEquals(0, wheel.add(0.25)); // 0.75 satır birikti
        assertEquals(1, wheel.add(0.25)); // 1.5 -> 1 satır, 0.5 artar
        assertEquals(0, wheel.add(0.1));  // 0.8
        assertEquals(1, wheel.add(0.1));  // 1.1 -> 1
    }

    @Test
    void visualOriginRemovesTheWindowOffsetFromBoxes() {
        // pencere içeriği (1,1) kaymış: çerçeve (1,1) bildiriyor, kutu (2,25) bildiriyor -> ekranda (1,24)
        assertArrayEquals(new int[]{1, 24},
                MouseSupport.visualOrigin(new int[]{2, 25}, new int[]{1, 1}, false, true));
    }

    @Test
    void visualOriginAddsTheMissingBorderInsetForInnerComponents() {
        // kutunun içindeki alan: Lanterna girintiyi eklemedi -> (+1,+1) tamamlanır
        assertArrayEquals(new int[]{2, 25},
                MouseSupport.visualOrigin(new int[]{2, 25}, new int[]{1, 1}, true, true));
        // girinti eklenen bir Lanterna sürümünde ek düzeltme yapılmaz
        assertArrayEquals(new int[]{2, 25},
                MouseSupport.visualOrigin(new int[]{3, 26}, new int[]{1, 1}, true, false));
    }
}
