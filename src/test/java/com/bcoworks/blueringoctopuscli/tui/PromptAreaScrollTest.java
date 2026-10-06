package com.bcoworks.blueringoctopuscli.tui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PromptAreaScrollTest {

    private static PromptArea tenLines() {
        PromptArea area = new PromptArea();
        area.setText("0\n1\n2\n3\n4\n5\n6\n7\n8\n9");
        return area;
    }

    @Test
    void caretIsKeptVisibleWhileTyping() {
        // 10 satır, en fazla 8 görünür: imleç (9. satır) görünümün son satırında
        assertEquals(PromptArea.MAX_ROWS - 1, tenLines().getCursorLocation().getRow());
    }

    @Test
    void wheelScrollsTheViewButNotTheCaret() {
        PromptArea area = tenLines();

        area.scroll(-3);

        // görünüm 2 satır yukarıda başlıyordu, 0'da durur; imleç 9. satırda kaldı
        assertEquals(9, area.getCursorLocation().getRow());
    }

    @Test
    void wheelCannotScrollPastTheContent() {
        PromptArea area = tenLines();

        area.scroll(50);

        assertEquals(PromptArea.MAX_ROWS - 1, area.getCursorLocation().getRow());
    }

    @Test
    void clickingMovesTheCaretAndFollowsTheView() {
        PromptArea area = tenLines();
        area.scroll(-3);

        area.placeCaretAt(0, 1); // görünümün 2. satırı = metnin 1. satırı

        assertEquals(1, area.getCursorLocation().getRow());
    }
}
