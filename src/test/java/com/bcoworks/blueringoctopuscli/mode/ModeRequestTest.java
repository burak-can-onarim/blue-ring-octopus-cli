package com.bcoworks.blueringoctopuscli.mode;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModeRequestTest {

    @Test
    void nullValuesBecomeEmptyStrings() {
        ModeRequest request = new ModeRequest(null, null);
        assertEquals("", request.prompt());
        assertEquals("", request.path());
        assertFalse(request.hasPath());
    }

    @Test
    void promptIsStrippedAndPathIsCleaned() {
        ModeRequest request = new ModeRequest("  yaz  ", " \"C:\\proje\" ");
        assertEquals("yaz", request.prompt());
        assertEquals("C:\\proje", request.path());
        assertTrue(request.hasPath());
    }

    @Test
    void blankPathMeansNoPath() {
        assertFalse(new ModeRequest("x", "   ").hasPath());
        assertFalse(new ModeRequest("x", "\"\"").hasPath());
    }
}
