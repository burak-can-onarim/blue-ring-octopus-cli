package com.bcoworks.blueringoctopuscli.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ModelOutputTest {

    @Test
    void numbersTheLinesFromOne() {
        assertEquals("1| class A {\n2|     int x;\n3| }", ModelOutput.numberLines("class A {\n    int x;\n}\n"));
    }

    @Test
    void numberingNormalisesLineEndingsAndKeepsBlankLines() {
        assertEquals("1| a\n2| \n3| b", ModelOutput.numberLines("a\r\n\r\nb"));
    }

    @Test
    void removesAClosedNotesBlock() {
        String text = "<notes>\nline 3: suspicion -> check -> REAL\n</notes>\n\nOVERVIEW\nIt does things.";

        assertEquals("OVERVIEW\nIt does things.", ModelOutput.stripNotes(text, "OVERVIEW"));
    }

    @Test
    void anUnclosedNotesBlockFallsBackToTheFirstTitle() {
        String text = "<notes>\nline 3: a\nline 4: b\n\nGENEL BAKIŞ\nBir şey yapar.\n\nBULGULAR\nYok.";

        assertEquals("GENEL BAKIŞ\nBir şey yapar.\n\nBULGULAR\nYok.", ModelOutput.stripNotes(text, "GENEL BAKIŞ"));
    }

    @Test
    void anUnclosedNotesBlockWithoutATitleOnlyLosesTheTag() {
        String text = "<notes>\nline 3: a\nline 4: b";

        assertEquals("line 3: a\nline 4: b", ModelOutput.stripNotes(text, "OVERVIEW"));
    }

    @Test
    void textWithoutNotesIsOnlyTrimmed() {
        assertEquals("OVERVIEW\nx", ModelOutput.stripNotes("\n  OVERVIEW\nx\n\n", "OVERVIEW"));
    }

    @Test
    void theLastClosingTagWinsWhenTheModelRepeatsTheBlock() {
        String text = "<notes>a</notes>\n<notes>b</notes>\nANSWER";

        assertEquals("ANSWER", ModelOutput.stripNotes(text, "OVERVIEW"));
    }

    @Test
    void plainTextLosesMarkdown() {
        String markdown = "### FINDINGS\n1. **[HIGH]** `findOrders` - uses __string__ concatenation.\n* first\n  * nested\n";

        String plain = ModelOutput.toPlainText(markdown);

        assertEquals("FINDINGS\n1. [HIGH] findOrders - uses string concatenation.\n- first\n  - nested", plain);
    }

    @Test
    void plainTextDropsCodeFencesButKeepsTheCode() {
        String markdown = "Fix:\n```java\nif (x == null) {\n    return;\n}\n```\nDone.";

        assertEquals("Fix:\nif (x == null) {\n    return;\n}\nDone.", ModelOutput.toPlainText(markdown));
    }

    @Test
    void plainTextLeavesOrdinaryTextAlone() {
        String text = "OVERVIEW\nThe method returns a * b for every pair.\n\nSUMMARY\nFine.";

        assertEquals(text, ModelOutput.toPlainText(text));
        assertFalse(ModelOutput.toPlainText("a\t \nb").contains(" \n"));
    }
}
