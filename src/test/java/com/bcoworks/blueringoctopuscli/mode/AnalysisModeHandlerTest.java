package com.bcoworks.blueringoctopuscli.mode;

import com.bcoworks.blueringoctopuscli.context.AppMode;
import com.bcoworks.blueringoctopuscli.i18n.Messages;
import com.bcoworks.blueringoctopuscli.model.AiServiceRegistry;
import com.bcoworks.blueringoctopuscli.model.ModelSettings;
import com.bcoworks.blueringoctopuscli.service.CodeAssistant;
import com.bcoworks.blueringoctopuscli.service.PromptLibrary;
import com.bcoworks.blueringoctopuscli.service.SourceCodeScanner;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalysisModeHandlerTest {

    @TempDir
    Path dir;

    private final List<String> printed = new ArrayList<>();
    private final IModeConsole console = new IModeConsole() {
        @Override
        public void step(String message) {
        }

        @Override
        public void println(String text) {
            printed.add(text);
        }
    };

    private AnalysisModeHandler handlerWith(AtomicInteger calls, int numCtx) {
        Messages messages = new Messages("en");
        CodeAssistant assistant = new CodeAssistant(chat -> {
            calls.incrementAndGet();
            return Response.from(AiMessage.from("OVERVIEW\nFine."));
        }, new PromptLibrary(), messages);
        AiServiceRegistry registry = new AiServiceRegistry(new ModelSettings("test-model"), new PromptLibrary(), messages,
                "http://localhost:1", 0.2, Duration.ofSeconds(1), numCtx, 1024) {
            @Override
            public CodeAssistant forMode(AppMode mode) {
                return assistant;
            }
        };
        return new AnalysisModeHandler(registry, new SourceCodeScanner(), messages);
    }

    @Test
    void aFileThatFitsTheWindowIsAnalysed() throws IOException {
        Files.writeString(dir.resolve("Small.java"), "class Small {\n}\n");
        AtomicInteger calls = new AtomicInteger();

        handlerWith(calls, 8192).handle(new ModeRequest("", dir.toString()), console);

        assertEquals(1, calls.get());
        assertTrue(printed.stream().anyMatch(line -> line.contains("--- Small.java ---") && line.contains("Fine.")), printed.toString());
    }

    @Test
    void aFileTooBigForTheWindowIsSkippedWithAHintInsteadOfBeingCutSilently() throws IOException {
        Files.writeString(dir.resolve("Big.java"), "int value = 1234567;\n".repeat(1_500)); // ~31 KB
        AtomicInteger calls = new AtomicInteger();

        handlerWith(calls, 8192).handle(new ModeRequest("", dir.toString()), console);

        assertEquals(0, calls.get());
        String message = printed.stream().filter(line -> line.contains("Big.java")).findFirst().orElse("");
        assertTrue(message.contains("skipped"), printed.toString());
        assertTrue(message.contains("octopus.model.num-ctx"), message);
    }

    @Test
    void aBiggerWindowTakesTheSameFile() throws IOException {
        Files.writeString(dir.resolve("Big.java"), "int value = 1234567;\n".repeat(1_500));
        AtomicInteger calls = new AtomicInteger();

        handlerWith(calls, 32768).handle(new ModeRequest("", dir.toString()), console);

        assertEquals(1, calls.get());
    }
}
