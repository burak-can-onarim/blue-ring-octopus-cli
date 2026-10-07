package com.bcoworks.blueringoctopuscli.i18n;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguageTest {

    @Test
    void englishIsTheDefault() {
        assertEquals(Language.EN, Language.DEFAULT);
    }

    @Test
    void parsesCodesLocaleTagsAndNames() {
        assertEquals(Optional.of(Language.DE), Language.parse("de"));
        assertEquals(Optional.of(Language.DE), Language.parse("DE"));
        assertEquals(Optional.of(Language.TR), Language.parse("tr_TR"));
        assertEquals(Optional.of(Language.FR), Language.parse("fr-CA"));
        assertEquals(Optional.of(Language.ES), Language.parse("es_ES.UTF-8"));
        assertEquals(Optional.of(Language.IT), Language.parse("Italiano"));
        assertEquals(Optional.of(Language.TR), Language.parse("turkish"));
        assertEquals(Optional.of(Language.EN), Language.parse("  English "));
    }

    @Test
    void rejectsBlankAndUnknownValues() {
        assertTrue(Language.parse(null).isEmpty());
        assertTrue(Language.parse("").isEmpty());
        assertTrue(Language.parse("  ").isEmpty());
        assertTrue(Language.parse("zh").isEmpty());
        assertTrue(Language.parse("ja").isEmpty());
        assertTrue(Language.parse("klingon").isEmpty());
    }

    @Test
    void offersExactlyTheSupportedLanguages() {
        assertEquals(6, Language.values().length);
    }
}
