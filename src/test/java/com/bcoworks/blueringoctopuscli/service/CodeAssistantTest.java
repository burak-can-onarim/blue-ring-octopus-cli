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
