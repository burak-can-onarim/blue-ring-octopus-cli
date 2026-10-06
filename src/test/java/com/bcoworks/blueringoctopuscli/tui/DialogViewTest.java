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
        assertEquals(Kind.WARNING, DialogView.classify("Hata: yol bulunamadı"));
        assertEquals(Kind.WARNING, DialogView.classify("  Uyarı: geçersiz çıktı"));
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
    void followsNewMessagesUntilTheUserScrollsUp() {
        DialogView view = new DialogView();
        for (int i = 0; i < 30; i++) {
            view.addNote("satır " + i);
        }
        assertEquals(25, view.viewTop(5, 40)); // 30 satır, 5 görünür: en alt
        assertTrue(view.isFollowing());

        view.scroll(-10);
        assertFalse(view.isFollowing());
        assertEquals(15, view.viewTop(5, 40));

        view.addNote("yeni mesaj"); // yukarıdaki kullanıcı olduğu yerde kalır
        assertEquals(15, view.viewTop(5, 40));

        view.scroll(1_000); // en alta dönünce yeniden takip eder
        assertTrue(view.isFollowing());
        assertEquals(26, view.viewTop(5, 40));
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
