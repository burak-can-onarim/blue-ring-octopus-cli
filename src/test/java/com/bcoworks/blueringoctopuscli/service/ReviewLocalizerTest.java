package com.bcoworks.blueringoctopuscli.service;

import com.bcoworks.blueringoctopuscli.i18n.Language;
import com.bcoworks.blueringoctopuscli.i18n.Messages;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewLocalizerTest {

    private static final String REVIEW = """
            OVERVIEW
            It builds queries.

            FINDINGS
            1. [HIGH] line 17 - SQL injection. Consequence: data theft. Fix: use prepared statements.
            2. [MEDIUM] Line 22 - empty catch. Consequence: errors vanish. Fix: log them.
            3. [LOW] lines 30, 31 - loop. Consequence: slow. Fix: stream.

            SUMMARY
            Do not use it.""";

    @Test
    void englishStaysEnglish() {
        assertEquals(REVIEW.replace("Line 22", "line 22").replace("lines 30", "line 30"),
                ReviewLocalizer.localize(REVIEW, new Messages("en")));
    }

    @Test
    void turkishGetsItsTitlesSeverityAndLabels() {
        String text = ReviewLocalizer.localize(REVIEW, new Messages("tr"));

        assertTrue(text.startsWith("GENEL BAKIŞ\nIt builds queries.\n\nBULGULAR\n"), text);
        assertTrue(text.contains("1. [YÜKSEK] satır 17 - SQL injection. Sonuç: data theft. Çözüm: use prepared statements."), text);
        assertTrue(text.contains("2. [ORTA] satır 22 - empty catch."), text);
        assertTrue(text.contains("3. [DÜŞÜK] satır 30, 31 - loop."), text);
        assertTrue(text.endsWith("\n\nSONUÇ\nDo not use it."), text);
    }

    @Test
    void everyLanguageReplacesEveryEnglishWordOfTheFrame() {
        for (Language language : Language.values()) {
            if (language == Language.EN) {
                continue;
            }
            String text = ReviewLocalizer.localize(REVIEW, new Messages(language.code()));

            assertFalse(text.contains("OVERVIEW") || text.contains("FINDINGS") || text.contains("SUMMARY"), language.code());
            assertFalse(text.contains("[HIGH]") || text.contains("[MEDIUM]") || text.contains("[LOW]"), language.code());
            assertFalse(text.contains("Consequence:") || text.contains("Fix:"), language.code());
            assertFalse(text.contains("line 17"), language.code());
        }
    }

    @Test
    void theNoIssuesSentenceIsLocalized() {
        String review = "OVERVIEW\nFine.\n\nFINDINGS\nNo significant issues found.\n\nSUMMARY\nOk.";

        assertTrue(ReviewLocalizer.localize(review, new Messages("de")).contains("BEFUNDE\nKeine wesentlichen Probleme gefunden."));
    }

    @Test
    void aTranslationThatKeepsTheFrameIsAccepted() {
        Messages messages = new Messages("tr");
        String localized = ReviewLocalizer.localize(REVIEW, messages);
        String translated = "GENEL BAKIŞ\nSorgular.\n\nBULGULAR\n1. [YÜKSEK] satır 17 - a. Sonuç: b. Çözüm: c.\n2. [ORTA] satır 22 - a.\n"
                + "3. [DÜŞÜK] satır 30 - a.\n\nSONUÇ\nKullanmayın.";

        assertTrue(ReviewLocalizer.keepsStructure(localized, translated, messages));
    }

    @Test
    void aSectionTheEnglishReviewLacksDoesNotHaveToAppearInTheTranslation() {
        Messages messages = new Messages("tr");
        String localized = ReviewLocalizer.localize(
                "FINDINGS\n1. [HIGH] line 17 - a. Consequence: b. Fix: c.\n\nSUMMARY\nDo not use it.", messages);
        String translated = "BULGULAR\n1. [YÜKSEK] satır 17 - a. Sonuç: b. Çözüm: c.\n\nSONUÇ\nKullanmayın.";

        assertTrue(ReviewLocalizer.keepsStructure(localized, translated, messages));
    }

    @Test
    void aTranslationWithoutTheTitlesOrWithFewerFindingsIsRejected() {
        Messages messages = new Messages("tr");
        String localized = ReviewLocalizer.localize(REVIEW, messages);

        assertFalse(ReviewLocalizer.keepsStructure(localized, "Here you go: fine.", messages));
        assertFalse(ReviewLocalizer.keepsStructure(localized,
                "GENEL BAKIŞ\nx\n\nBULGULAR\n1. [YÜKSEK] satır 17 - a.\n\nSONUÇ\ny", messages));
        assertFalse(ReviewLocalizer.keepsStructure(localized,
                "OVERVIEW\nx\n\nFINDINGS\n1. a\n2. b\n3. c\n\nSUMMARY\ny", messages));
    }
}
