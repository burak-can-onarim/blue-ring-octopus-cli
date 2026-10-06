package com.bcoworks.blueringoctopuscli.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppInfoTest {

    @Test
    void keepsReleaseVersion() {
        assertEquals("1.0.0", AppInfo.normalize("1.0.0"));
    }

    @Test
    void stripsSurroundingWhitespace() {
        assertEquals("0.1.4", AppInfo.normalize("  0.1.4 "));
    }

    @Test
    void fallsBackToDevWhenBlankOrUnfiltered() {
        assertEquals("dev", AppInfo.normalize(""));
        assertEquals("dev", AppInfo.normalize("  "));
        assertEquals("dev", AppInfo.normalize("@project.version@"));
        assertEquals("dev", AppInfo.normalize(null));
    }
}
