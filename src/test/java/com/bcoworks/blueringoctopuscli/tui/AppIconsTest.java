package com.bcoworks.blueringoctopuscli.tui;

import org.junit.jupiter.api.Test;

import java.awt.Image;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppIconsTest {

    @Test
    void loadsEveryBundledSize() {
        List<Image> icons = AppIcons.load();

        assertEquals(AppIcons.SIZES.length, icons.size());
        for (int i = 0; i < icons.size(); i++) {
            assertEquals(AppIcons.SIZES[i], icons.get(i).getWidth(null));
            assertEquals(AppIcons.SIZES[i], icons.get(i).getHeight(null));
        }
    }
}
