package com.bcoworks.blueringoctopuscli.tui;

import com.bcoworks.blueringoctopuscli.tui.DialogView.Kind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialogViewTest {

    private static List<Kind> kinds(DialogView view, int width) {
        return view.layout(width).stream().map(DialogView.VisualLine::kind).toList();
    }

    @Test
    void classifiesAnswerLines() {
        assertEquals(Kind.WARNING, DialogView.classify("Error: path not found"));
        assertEquals(Kind.WARNING, DialogView.classify("  Warning: invalid output"));
        assertEquals(Kind.WARNING, DialogView.classify("Hata: yol bulunamadı"));
        assertEquals(Kind.WARNING, DialogView.classify("Fehler: Pfad nicht gefunden"));
        assertEquals(Kind.WARNING, DialogView.classify("Erreur : chemin introuvable"));
        assertEquals(Kind.ANSWER, DialogView.classify("Error handling is missing in this method"));
        assertEquals(Kind.HEADING, DialogView.classify("--- PathUtils.java ---"));
        assertEquals(Kind.INFO, DialogView.classify("■ İşlem iptal edildi."));
        assertEquals(Kind.ANSWER, DialogView.classify("Normal bir satır"));
    }

    @Test
    void aTurnIsUserHeaderUserTextThenAnswerHeaderAndAnswer() {
        DialogView view = new DialogView();

        view.startTurn("Sen · Kod Analizi", "src");
        view.addAnswer("1 dosya bulundu");
        view.addAnswer("tamam");

        assertEquals(List.of(Kind.USER_HEADER, Kind.USER, Kind.INFO, Kind.ANSWER_HEADER, Kind.ANSWER, Kind.ANSWER),
                kinds(view, 40));
    }

    @Test
    void answerHeaderIsOpenedOncePerTurn() {
        DialogView view = new DialogView();

        view.startTurn("Sen", "a");
        view.addAnswer("x");
        view.startTurn("Sen", "b");
        view.addAnswer("y");

        long answerHeaders = kinds(view, 40).stream().filter(kind -> kind == Kind.ANSWER_HEADER).count();
        assertEquals(2, answerHeaders);
    }

    @Test
    void wrapsLongLinesAtWordBoundariesAndReflowsOnResize() {
        DialogView view = new DialogView();
        view.addNote("bir iki üç dört beş altı yedi sekiz dokuz on");

        int narrow = view.layout(15).size();
        int wide = view.layout(80).size();

        assertTrue(narrow > 1);
        assertEquals(1, wide);
        assertEquals(List.of("abc", "def"), DialogView.wrap("abcdef", 3));
    }

    @Test
    void headerFillsTheWidth() {
        assertEquals(30, DialogView.header("Octopus", 30).length());
        assertTrue(DialogView.header("Octopus", 30).startsWith("── Octopus "));
    }

    @Test
    void scrollingUpPausesFollowingUntilTheBottomIsReached() {
        DialogView view = new DialogView();
        for (int i = 0; i < 30; i++) {
            view.addNote("line " + i);
        }
        assertEquals(25, view.viewTop(5, 40)); // 30 lines, 5 visible: the bottom
        assertTrue(view.isFollowing());

        view.scroll(-10);
        assertFalse(view.isFollowing());
        assertEquals(15, view.viewTop(5, 40));

        view.scroll(1_000);
        assertTrue(view.isFollowing());
        assertEquals(25, view.viewTop(5, 40));
    }

    @Test
    void everyNewMessageScrollsToTheBottomEvenAfterScrollingUp() {
        DialogView view = new DialogView();
        for (int i = 0; i < 30; i++) {
            view.addNote("line " + i);
        }
        view.scroll(-10);
        assertFalse(view.isFollowing());

        view.addNote("a note");
        assertTrue(view.isFollowing());
        assertEquals(26, view.viewTop(5, 40));

        view.scroll(-10);
        view.addAnswer("an answer");
        assertTrue(view.isFollowing());

        view.scroll(-10);
        view.startTurn("You", "a request");
        assertTrue(view.isFollowing());
    }

    @Test
    void selectsTextAcrossLines() {
        DialogView view = new DialogView();
        view.viewport(40, 10);
        view.addNote("first line");
        view.addNote("second line");
        view.addNote("third line");

        view.startSelection(0, 6);
        view.extendSelection(2, 4);

        assertTrue(view.hasSelection());
        assertEquals("line\nsecond line\nthird", view.selectedText());
    }

    @Test
    void aPlainClickSelectsNothing() {
        DialogView view = new DialogView();
        view.viewport(40, 10);
        view.addNote("some text");

        view.startSelection(0, 3);

        assertFalse(view.hasSelection());
        assertEquals("", view.selectedText());
    }

    @Test
    void selectingBackwardsGivesTheSameText() {
        DialogView view = new DialogView();
        view.viewport(40, 10);
        view.addNote("abcdef");

        view.startSelection(0, 4);
        view.extendSelection(0, 1);

        assertEquals("bcde", view.selectedText());
    }

    @Test
    void draggingBeyondTheEdgesSelectsToTheEnds() {
        DialogView view = new DialogView();
        view.viewport(40, 3);
        view.addNote("one");
        view.addNote("two");
        view.addNote("three");

        view.startSelection(0, 0);
        view.extendSelection(10, 100);

        assertEquals("one\ntwo\nthree", view.selectedText());
    }

    @Test
    void doubleClickSelectsTheWordUnderThePointer() {
        DialogView view = new DialogView();
        view.viewport(60, 10);
        view.addNote("run: ollama pull qwen2.5-coder:14b now");

        view.selectWordAt(0, 20);

        assertEquals("qwen2.5-coder:14b", view.selectedText());
    }

    @Test
    void doubleClickOnABlankSelectsNothing() {
        DialogView view = new DialogView();
        view.viewport(60, 10);
        view.addNote("a b");
        view.selectWordAt(0, 0);
        assertTrue(view.hasSelection());

        view.selectWordAt(0, 1);

        assertFalse(view.hasSelection());
    }

    @Test
    void tripleClickSelectsTheWholeWrappedLineAndJoinsItAtTheSpaces() {
        DialogView view = new DialogView();
        view.viewport(15, 10);
        view.addNote("one two three four five six seven");
        assertTrue(view.layout(15).size() > 1);

        view.selectLineAt(1);

        assertEquals("one two three four five six seven", view.selectedText());
    }

    @Test
    void aHardWrappedWordIsJoinedWithoutASpace() {
        DialogView view = new DialogView();
        view.viewport(10, 10);
        view.addNote("abcdefghijklmnopqrstuvwxyz");

        view.selectLineAt(0);

        assertEquals("abcdefghijklmnopqrstuvwxyz", view.selectedText());
    }

    @Test
    void headingsAreCopiedAsTheirLabel() {
        DialogView view = new DialogView();
        view.viewport(40, 10);
        view.startTurn("You", "hi");
        view.addAnswer("ok");

        view.startSelection(0, 0);
        view.extendSelection(4, 5);

        assertEquals("You\nhi\n\nOctopus\nok", view.selectedText());
    }

    @Test
    void theSelectionIsClearedWhenTheWrappingChanges() {
        DialogView view = new DialogView();
        view.viewport(40, 10);
        view.addNote("some text to select");
        view.selectLineAt(0);
        assertTrue(view.hasSelection());

        view.layout(20);

        assertFalse(view.hasSelection());
    }

    @Test
    void theSelectionIsClearedWhenOldEntriesAreDropped() {
        DialogView view = new DialogView();
        view.viewport(40, 10);
        view.addNote("x");
        view.selectLineAt(0);

        for (int i = 0; i < DialogView.MAX_ENTRIES + 1; i++) {
            view.addNote("line " + i);
        }

        assertFalse(view.hasSelection());
    }

    @Test
    void keepsOnlyTheMostRecentEntries() {
        DialogView view = new DialogView();

        for (int i = 0; i < DialogView.MAX_ENTRIES + 50; i++) {
            view.addNote("satır " + i);
        }

        assertEquals(DialogView.MAX_ENTRIES, view.entryCount());
        List<DialogView.VisualLine> lines = view.layout(40);
        assertEquals("satır " + (DialogView.MAX_ENTRIES + 49), lines.getLast().text());
    }
}
