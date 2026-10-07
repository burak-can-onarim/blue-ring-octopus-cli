package com.bcoworks.blueringoctopuscli.mode;

import com.bcoworks.blueringoctopuscli.context.AppMode;
import com.bcoworks.blueringoctopuscli.i18n.Messages;
import com.bcoworks.blueringoctopuscli.model.AiServiceRegistry;
import com.bcoworks.blueringoctopuscli.model.ModelSettings;
import com.bcoworks.blueringoctopuscli.service.CodeAssistant;
import com.bcoworks.blueringoctopuscli.service.PromptLibrary;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerateModeHandlerTest {

    private static final String GOOD = "public class Greeter {\n    public String hi() {\n        return \"hi\";\n    }\n}";
    private static final String MISSING_SEMICOLON = "public class Greeter {\n    public String hi() {\n        return \"hi\"\n    }\n}";
    private static final String TWO_ERRORS = "public class Greeter {\n    public String hi() {\n        int x = \"a\"\n        return 1\n    }\n}";

    @TempDir
    Path dir;

    private final List<String> printed = new ArrayList<>();
    private final List<String> prompts = new ArrayList<>();
    private final IModeConsole console = new IModeConsole() {
        @Override
        public void step(String message) {
        }

        @Override
        public void println(String text) {
            printed.add(text);
        }
    };

    /** A handler whose model answers with the given texts, one per call (the last one is repeated). */
    private GenerateModeHandler handlerAnswering(int numCtx, String... answers) {
        Deque<String> queue = new ArrayDeque<>(List.of(answers));
        Messages messages = new Messages("en");
        CodeAssistant assistant = new CodeAssistant(chat -> {
            prompts.add(((UserMessage) chat.get(1)).singleText());
            return Response.from(AiMessage.from(queue.size() > 1 ? queue.poll() : queue.peek()));
        }, new PromptLibrary(), messages);
        AiServiceRegistry registry = new AiServiceRegistry(new ModelSettings("test-model"), new PromptLibrary(), messages,
                "http://localhost:1", 0.2, Duration.ofSeconds(1), numCtx, 1024) {
            @Override
            public CodeAssistant forMode(AppMode mode) {
                return assistant;
            }
        };
        return new GenerateModeHandler(registry, messages);
    }

    private String saved() throws IOException {
        return Files.readString(dir.resolve("Greeter.java")).strip();
    }

    private String output() {
        return String.join("\n", printed);
    }

    private void generate(GenerateModeHandler handler) throws IOException {
        handler.generate("a greeter", dir.resolve("Greeter.java").toString(), console);
    }

    @Test
    void codeThatCompilesIsSavedAfterOneModelCall() throws IOException {
        generate(handlerAnswering(8192, GOOD));

        assertEquals(1, prompts.size());
        assertEquals(GOOD, saved());
        assertTrue(output().contains("Compile check passed."), output());
    }

    @Test
    void aCompileErrorIsHandedBackToTheModelOnceAndTheFixedFileIsSaved() throws IOException {
        generate(handlerAnswering(8192, MISSING_SEMICOLON, GOOD));

        assertEquals(2, prompts.size());
        assertTrue(prompts.get(1).contains("';' expected"), prompts.get(1));
        assertTrue(prompts.get(1).contains("return \"hi\""));
        assertEquals(GOOD, saved());
        assertTrue(output().contains("The model fixed the compile errors (1)"), output());
        assertFalse(output().contains("Warning"), output());
    }

    @Test
    void whenTheRepairDoesNotHelpTheOriginalIsSavedWithAWarningListingTheErrors() throws IOException {
        generate(handlerAnswering(8192, MISSING_SEMICOLON, MISSING_SEMICOLON));

        assertEquals(2, prompts.size());
        assertEquals(MISSING_SEMICOLON, saved());
        assertTrue(output().contains("Warning: the file was saved, but it still does not compile. Compile errors (1):"), output());
        assertTrue(output().contains("  line 3: ';' expected"), output());
    }

    @Test
    void aRepairIsOnlyUsedIfItHasFewerErrors() throws IOException {
        generate(handlerAnswering(8192, MISSING_SEMICOLON, TWO_ERRORS));

        assertEquals(MISSING_SEMICOLON, saved());
        assertTrue(output().contains("Compile errors (1):"), output());
    }

    @Test
    void aRepairThatRenamesTheTypeIsIgnored() throws IOException {
        generate(handlerAnswering(8192, MISSING_SEMICOLON, GOOD.replace("Greeter", "Other")));

        assertEquals(MISSING_SEMICOLON, saved());
        assertTrue(output().contains("Compile errors (1):"), output());
    }

    @Test
    void aFileThatCannotBeRepairedInTheWindowIsSavedWithItsErrors() throws IOException {
        generate(handlerAnswering(500, MISSING_SEMICOLON, GOOD));

        assertEquals(1, prompts.size(), "the repair prompt and the whole file again would not fit the window");
        assertEquals(MISSING_SEMICOLON, saved());
        assertTrue(output().contains("Compile errors (1):"), output());
    }

    @Test
    void aMissingLibraryIsReportedAndIsNotRepaired() throws IOException {
        String controller = "import org.springframework.web.bind.annotation.RestController;\n\n@RestController\n"
                + "public class Greeter {\n}";

        generate(handlerAnswering(8192, controller));

        assertEquals(1, prompts.size());
        assertEquals(controller, Files.readString(dir.resolve("Greeter.java")).strip());
        assertTrue(output().contains("Compile check passed."), output());
        assertTrue(output().contains("org.springframework.web.bind.annotation"), output());
    }
}
