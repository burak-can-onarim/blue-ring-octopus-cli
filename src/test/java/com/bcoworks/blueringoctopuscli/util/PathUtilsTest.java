package com.bcoworks.blueringoctopuscli.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PathUtilsTest {

    @Test
    void cleanReturnsEmptyForNull() {
        assertEquals("", PathUtils.clean(null));
    }

    @Test
    void cleanTrimsWhitespace() {
        assertEquals("C:\\proje", PathUtils.clean("   C:\\proje \t"));
    }

    @Test
    void cleanRemovesSurroundingDoubleQuotes() {
        assertEquals("C:\\Program Files\\app", PathUtils.clean("\"C:\\Program Files\\app\""));
        assertEquals("a b", PathUtils.clean("  \" a b \"  "));
    }

    @Test
    void cleanKeepsUnbalancedOrLoneQuote() {
        assertEquals("\"abc", PathUtils.clean("\"abc"));
        assertEquals("\"", PathUtils.clean("\""));
    }

    @Test
    void resolveKeepsAbsolutePath(@TempDir Path dir) {
        assertEquals(dir, PathUtils.resolve(dir.toString()));
        assertEquals(dir, PathUtils.resolve("\"" + dir + "\""));
    }

    @Test
    void resolveMakesRelativePathAbsoluteAgainstWorkingDirectory() {
        Path expected = Paths.get(System.getProperty("user.dir")).resolve("src").normalize();
        assertEquals(expected, PathUtils.resolve("src"));
    }

    @Test
    void resolveNormalizesDotSegments() {
        Path expected = Paths.get(System.getProperty("user.dir")).resolve("b").normalize();
        assertEquals(expected, PathUtils.resolve("a/../b"));
    }
}
