package com.bcoworks.blueringoctopuscli.service;

import com.bcoworks.blueringoctopuscli.i18n.Messages;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeAssistantTest {

    private static final String ENGLISH_REVIEW = "OVERVIEW\nIt builds queries.\n\nFINDINGS\n"
            + "1. [HIGH] line 17 - SQL injection. Consequence: data theft. Fix: use prepared statements.\n\nSUMMARY\nDo not use it.";

    /** Records every call and answers with the next prepared text (the last one is repeated). */
    static final class FakeModel implements ChatLanguageModel {
        final List<List<ChatMessage>> calls = new ArrayList<>();
        private final Deque<String> answers = new ArrayDeque<>();

        FakeModel(String... answers) {
            this.answers.addAll(List.of(answers));
        }

        @Override
        public Response<AiMessage> generate(List<ChatMessage> messages) {
            calls.add(List.copyOf(messages));
            String answer = answers.size() > 1 ? answers.poll() : answers.isEmpty() ? "" : answers.peek();
            return Response.from(AiMessage.from(answer));
        }

        String system(int call) {
            return ((SystemMessage) calls.get(call).get(0)).text();
        }

        String user(int call) {
            return ((UserMessage) calls.get(call).get(1)).singleText();
        }
    }

    private final PromptLibrary prompts = new PromptLibrary();

    @Test
    void anEnglishReviewIsOneCallWithNumberedCode() {
        FakeModel model = new FakeModel(ENGLISH_REVIEW);

        String review = new CodeAssistant(model, prompts, new Messages("en")).analyze("A.java", "class A {\n}\n");

        assertEquals(1, model.calls.size());
        assertTrue(model.system(0).contains("OVERVIEW") && model.system(0).contains("<notes>"));
        assertTrue(model.user(0).contains("File: A.java"));
        assertTrue(model.user(0).contains("1| class A {\n2| }"));
        assertEquals(ENGLISH_REVIEW, review);
    }

    @Test
    void aPartOfALongFileIsReviewedWithItsOwnNumbersAndTheLinesToReview() {
        FakeModel model = new FakeModel(ENGLISH_REVIEW);
        CodeSplitter.Part part = new CodeSplitter.Part(2, 5, 120, 210, List.of(new int[]{120, 150}, new int[]{200, 210}),
                List.of("run"), "7| class A {\n   | ... (lines 8-119 not shown)\n120|     void run() {\n210|     }\n211| }");

        String review = new CodeAssistant(model, prompts, new Messages("en")).analyzePart("A.java", part);

        assertEquals(ENGLISH_REVIEW, review);
        assertEquals(1, model.calls.size());
        assertTrue(model.system(0).contains("OVERVIEW") && model.system(0).contains("<notes>"), "the same system prompt as a whole file");
        assertTrue(model.user(0).contains("File: A.java (part 2 of 5)"), model.user(0));
        assertTrue(model.user(0).contains("Review only the code at lines 120-150, 200-210."), model.user(0));
        assertTrue(model.user(0).contains("7| class A {\n   | ... (lines 8-119 not shown)\n120|     void run() {"));
        assertFalse(model.user(0).contains("1| 7| "), "the numbers of the part are not numbered again");
    }

    private static CodeSplitter.Part part(int number, int count, int first, int last) {
        return new CodeSplitter.Part(number, count, first, last, List.of(new int[]{first, last}), List.of(), "code");
    }

    private static final String PART_REVIEW = "OVERVIEW\nIt adds.\n\nFINDINGS\n"
            + "1. [HIGH] line 12 - overflow. Consequence: wrong sums. Fix: use Math.addExact.\n"
            + "2. [LOW] line 40 - vague name. Consequence: confusion. Fix: rename.\n"
            + "3. [LOW] line 50 - another. Consequence: x. Fix: y.\n\nSUMMARY\nFix it.";

    @Test
    void thePartsAreMergedWithOneCallInEnglishAndWithoutTheirSummaries() {
        String merged = "OVERVIEW\nIt adds.\n\nFINDINGS\n1. [HIGH] line 12 - overflow.\n2. [LOW] line 40 - vague name.\n"
                + "3. [LOW] line 50 - another.\n\nSUMMARY\nFix the overflow.";
        FakeModel model = new FakeModel(merged);
        CodeAssistant assistant = new CodeAssistant(model, prompts, new Messages("tr"));

        String result = assistant.mergeParts("A.java", List.of(part(1, 2, 1, 20), part(2, 2, 22, 60)),
                List.of(PART_REVIEW, PART_REVIEW), 5_000);

        assertEquals(merged, result);
        assertEquals(1, model.calls.size(), "no translation inside the merge");
        assertTrue(model.system(0).contains("ONE review of the whole file"));
        assertTrue(model.user(0).contains("File: A.java"));
        assertTrue(model.user(0).contains("=== Part 2 of 2 (lines 22-60) ==="));
        assertFalse(model.user(0).contains("Fix it."), "the summaries of the parts are left out");
    }

    @Test
    void aMergeThatLosesTheFindingsOrDoesNotFitTheWindowGivesNothing() {
        FakeModel lossy = new FakeModel("OVERVIEW\nx\n\nFINDINGS\nNo significant issues found.\n\nSUMMARY\nx");
        assertEquals("", new CodeAssistant(lossy, prompts, new Messages("en"))
                .mergeParts("A.java", List.of(part(1, 2, 1, 20), part(2, 2, 22, 60)), List.of(PART_REVIEW, PART_REVIEW), 5_000));

        FakeModel never = new FakeModel("anything");
        assertEquals("", new CodeAssistant(never, prompts, new Messages("en"))
                .mergeParts("A.java", List.of(part(1, 2, 1, 20), part(2, 2, 22, 60)), List.of(PART_REVIEW, PART_REVIEW), 10));
        assertEquals(0, never.calls.size(), "reviews that do not fit are not sent");
    }

    @Test
    void aMergedEnglishReviewIsLocalizedWithOneTranslationCall() {
        FakeModel model = new FakeModel("OVERVIEW\nCeviri.");
        CodeAssistant assistant = new CodeAssistant(model, prompts, new Messages("tr"));

        String result = assistant.localize("OVERVIEW\nIt adds.\n\nFINDINGS\nNo significant issues found.");

        assertEquals(1, model.calls.size());
        assertTrue(model.user(0).contains(new Messages("tr").get("analysis.title.overview")), model.user(0));
        assertFalse(result.isBlank());
        assertEquals("", new CodeAssistant(new FakeModel("x"), prompts, new Messages("tr")).localize(""));
    }

    @Test
    void aPartIsTranslatedLikeAWholeReview() {
        FakeModel model = new FakeModel(ENGLISH_REVIEW, "OVERVIEW\nÇeviri.");
        CodeSplitter.Part part = new CodeSplitter.Part(1, 2, 1, 10, List.of(new int[]{1, 10}), List.of(), "1| class A {}");

        new CodeAssistant(model, prompts, new Messages("tr")).analyzePart("A.java", part);

        assertEquals(2, model.calls.size());
        assertTrue(model.system(1).contains("Turkish"));
    }

    @Test
    void theNotesAndTheMarkdownNeverReachTheReader() {
        FakeModel model = new FakeModel("<notes>\nline 1: a -> b -> REAL\n</notes>\n\n### OVERVIEW\nIt **does** `things`.");

        String review = new CodeAssistant(model, prompts, new Messages("en")).analyze("A.java", "class A {}");

        assertEquals("OVERVIEW\nIt does things.", review);
    }

    @Test
    void anUnclosedNotesBlockStillYieldsTheAnswer() {
        FakeModel model = new FakeModel("<notes>\n1. line 1: a -> NOT REAL\n1. line 1: a -> NOT REAL\n\nOVERVIEW\nIt works.");

        assertEquals("OVERVIEW\nIt works.", new CodeAssistant(model, prompts, new Messages("en")).analyze("A.java", "class A {}"));
    }

    @Test
    void anotherLanguageIsAnalysedInEnglishAndThenTranslatedAroundTheLocalizedFrame() {
        String translated = "GENEL BAKIŞ\nSorgular oluşturur.\n\nBULGULAR\n"
                + "1. [YÜKSEK] satır 17 - SQL enjeksiyonu. Sonuç: veri hırsızlığı. Çözüm: hazırlanmış sorgu kullanın.\n\nSONUÇ\nKullanmayın.";
        FakeModel model = new FakeModel("<notes>x</notes>\n" + ENGLISH_REVIEW, translated);

        String review = new CodeAssistant(model, prompts, new Messages("tr")).analyze("A.java", "class A {\n}\n");

        assertEquals(2, model.calls.size());
        assertFalse(model.system(0).contains("Turkish"), "the analysis is made in English");
        assertTrue(model.system(1).contains("Turkish"));
        assertTrue(model.system(1).contains("SQL enjeksiyonu"), "the glossary is part of the translation prompt");
        String sent = model.user(1);
        assertTrue(sent.contains("GENEL BAKIŞ") && sent.contains("BULGULAR") && sent.contains("SONUÇ"), sent);
        assertTrue(sent.contains("1. [YÜKSEK] satır 17 - SQL injection. Sonuç: data theft. Çözüm: use prepared statements."), sent);
        assertEquals(translated, review);
    }

    @Test
    void aBrokenTranslationFallsBackToTheLocalizedEnglishReview() {
        FakeModel model = new FakeModel(ENGLISH_REVIEW, "Here is the translation: it is fine.");

        String review = new CodeAssistant(model, prompts, new Messages("de")).analyze("A.java", "class A {}");

        assertTrue(review.startsWith("ÜBERBLICK\nIt builds queries."), review);
        assertTrue(review.contains("1. [HOCH] Zeile 17 - SQL injection. Folge: data theft. Lösung: use prepared statements."), review);
        assertTrue(review.contains("FAZIT\nDo not use it."), review);
    }

    @Test
    void aTranslationThatLosesAFindingFallsBackToo() {
        FakeModel model = new FakeModel(ENGLISH_REVIEW, "APERÇU\nx\n\nCONSTATS\n\nCONCLUSION\ny");

        String review = new CodeAssistant(model, prompts, new Messages("fr")).analyze("A.java", "class A {}");

        assertTrue(review.contains("1. [ÉLEVÉ] ligne 17"), review);
    }

    @Test
    void anEmptyAnalysisIsNotTranslated() {
        FakeModel model = new FakeModel("");

        String review = new CodeAssistant(model, prompts, new Messages("es")).analyze("A.java", "class A {}");

        assertEquals("", review);
        assertEquals(1, model.calls.size());
    }

    @Test
    void generatingCodeSendsTheRequestAndTheLanguageAndReturnsTheRawAnswer() {
        FakeModel model = new FakeModel("```java\nclass A {}\n```");

        String code = new CodeAssistant(model, prompts, new Messages("de")).generateCode("a thread-safe cache {{x}}");

        assertTrue(model.system(0).contains("German"));
        assertTrue(model.user(0).contains("a thread-safe cache {{x}}"));
        assertEquals("```java\nclass A {}\n```", code);
    }

    @Test
    void testsDocumentationAndInventoryGetTheFileAsItIs() {
        for (String kind : List.of("tests", "document", "inventory")) {
            FakeModel model = new FakeModel("answer");
            CodeAssistant assistant = new CodeAssistant(model, prompts, new Messages("fr"));

            String result = switch (kind) {
                case "tests" -> assistant.generateTests("Money.java", "class Money {\n}\n");
                case "document" -> assistant.document("Money.java", "class Money {\n}\n");
                default -> assistant.inventory("Money.java", "class Money {\n}\n");
            };

            assertEquals("answer", result, kind);
            assertTrue(model.system(0).contains("French"), kind);
            assertTrue(model.user(0).contains("File: Money.java"), kind);
            assertTrue(model.user(0).contains("class Money {\n}"), kind);
            assertFalse(model.user(0).contains("1| "), kind + " must not number the lines");
        }
    }

    @Test
    void aRepairSendsTheCodeAndTheCompilerErrorsLineByLine() {
        FakeModel model = new FakeModel("class A {}");
        List<CompileCheck.Problem> problems = List.of(
                new CompileCheck.Problem(5, "cannot find symbol symbol: method push(String)", "list.push(\"x\");"),
                new CompileCheck.Problem(9, "';' expected", ""));

        String fixed = new CodeAssistant(model, prompts, new Messages("en")).repairCode("class A {\n}", problems);

        assertEquals("class A {}", fixed);
        assertEquals(1, model.calls.size());
        assertTrue(model.system(0).contains("complete corrected file"));
        assertTrue(model.user(0).contains("class A {\n}"));
        assertTrue(model.user(0).contains(
                "line 5: cannot find symbol symbol: method push(String)\n    list.push(\"x\");\nline 9: ';' expected"),
                model.user(0));
        assertFalse(model.user(0).contains("1| "), "the code is not numbered, the answer must be raw source");
    }

    @Test
    void anEmptyAnswerStaysEmptyAndTheModelIsAskedOnce() {
        FakeModel model = new FakeModel();

        assertEquals("", new CodeAssistant(model, prompts, new Messages("en")).generateCode("anything"));
        assertEquals(1, model.calls.size());
    }
}
