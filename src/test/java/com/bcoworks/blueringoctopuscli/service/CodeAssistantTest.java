package com.bcoworks.blueringoctopuscli.service;

import com.bcoworks.blueringoctopuscli.i18n.Messages;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeAssistantTest {

    /** Records what is sent and answers with a fixed text. */
    static final class FakeModel implements ChatLanguageModel {
        final List<ChatMessage> seen = new ArrayList<>();
        String answer = "";
        int calls;

        @Override
        public Response<AiMessage> generate(List<ChatMessage> messages) {
            calls++;
            seen.clear();
            seen.addAll(messages);
            return Response.from(AiMessage.from(answer));
        }

        String system() {
            return ((SystemMessage) seen.get(0)).text();
        }

        String user() {
            return ((UserMessage) seen.get(1)).singleText();
        }
    }

    private final PromptLibrary prompts = new PromptLibrary();

    @Test
    void theReviewPromptCarriesNumberedCodeAndTheWordsOfTheSelectedLanguage() {
        FakeModel model = new FakeModel();
        model.answer = "OK";
        CodeAssistant assistant = new CodeAssistant(model, prompts, new Messages("tr"));

        assistant.analyze("A.java", "class A {\n}\n");

        assertTrue(model.system().contains("Turkish"));
        for (String word : List.of("GENEL BAKIŞ", "BULGULAR", "SONUÇ", "YÜKSEK", "ORTA", "DÜŞÜK", "satır", "Sonuç", "Çözüm",
                "Önemli bir sorun bulunamadı.")) {
            assertTrue(model.system().contains(word), word);
        }
        assertTrue(model.user().contains("File: A.java"));
        assertTrue(model.user().contains("1| class A {\n2| }"));
        assertFalse(model.system().contains("{{"));
        assertFalse(model.user().contains("{{"));
    }

    @Test
    void theReviewUsesEnglishWordsWhenEnglishIsSelected() {
        FakeModel model = new FakeModel();
        CodeAssistant assistant = new CodeAssistant(model, prompts, new Messages("en"));

        assistant.analyze("A.java", "class A {}");

        assertTrue(model.system().contains("English"));
        assertTrue(model.system().contains("OVERVIEW"));
        assertTrue(model.system().contains("Consequence"));
    }

    @Test
    void theReviewAnswerIsShownWithoutNotesAndWithoutMarkdown() {
        FakeModel model = new FakeModel();
        model.answer = "<notes>\nline 1: a -> b -> REAL\n</notes>\n\nOVERVIEW\nIt **does** `things`.\n\nFINDINGS\n"
                + "1. [HIGH] line 3 - bad. Consequence: x. Fix: y.\n\nSUMMARY\nNo.";
        CodeAssistant assistant = new CodeAssistant(model, prompts, new Messages("en"));

        String review = assistant.analyze("A.java", "class A {}");

        assertEquals("OVERVIEW\nIt does things.\n\nFINDINGS\n1. [HIGH] line 3 - bad. Consequence: x. Fix: y.\n\nSUMMARY\nNo.", review);
    }

    @Test
    void anUnclosedNotesBlockStillYieldsTheAnswerInTheSelectedLanguage() {
        FakeModel model = new FakeModel();
        model.answer = "<notes>\n1. line 1: a -> NOT REAL\n1. line 1: a -> NOT REAL\n\nGENEL BAKIŞ\nBir şey.";
        CodeAssistant assistant = new CodeAssistant(model, prompts, new Messages("tr"));

        assertEquals("GENEL BAKIŞ\nBir şey.", assistant.analyze("A.java", "class A {}"));
    }

    @Test
    void generatingCodeSendsTheRequestAndTheLanguageAndReturnsTheRawAnswer() {
        FakeModel model = new FakeModel();
        model.answer = "```java\nclass A {}\n```";
        CodeAssistant assistant = new CodeAssistant(model, prompts, new Messages("de"));

        String code = assistant.generateCode("a thread-safe cache {{x}}");

        assertTrue(model.system().contains("German"));
        assertTrue(model.user().contains("a thread-safe cache {{x}}"));
        assertEquals("```java\nclass A {}\n```", code);
    }

    @Test
    void testsDocumentationAndInventoryGetTheFileAsItIs() {
        for (String kind : List.of("tests", "document", "inventory")) {
            FakeModel model = new FakeModel();
            model.answer = "answer";
            CodeAssistant assistant = new CodeAssistant(model, prompts, new Messages("fr"));

            String result = switch (kind) {
                case "tests" -> assistant.generateTests("Money.java", "class Money {\n}\n");
                case "document" -> assistant.document("Money.java", "class Money {\n}\n");
                default -> assistant.inventory("Money.java", "class Money {\n}\n");
            };

            assertEquals("answer", result, kind);
            assertTrue(model.system().contains("French"), kind);
            assertTrue(model.user().contains("File: Money.java"), kind);
            assertTrue(model.user().contains("class Money {\n}"), kind);
            assertFalse(model.user().contains("1| "), kind + " must not number the lines");
        }
    }

    @Test
    void anEmptyAnswerStaysEmptyAndTheModelIsAskedOnce() {
        FakeModel model = new FakeModel();
        CodeAssistant assistant = new CodeAssistant(model, prompts, new Messages("en"));

        assertEquals("", assistant.generateCode("anything"));
        assertEquals(1, model.calls);
    }
}
