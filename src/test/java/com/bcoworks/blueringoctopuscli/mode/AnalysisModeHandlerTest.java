package com.bcoworks.blueringoctopuscli.mode;

import com.bcoworks.blueringoctopuscli.context.AppMode;
import com.bcoworks.blueringoctopuscli.i18n.Messages;
import com.bcoworks.blueringoctopuscli.model.AiServiceRegistry;
import com.bcoworks.blueringoctopuscli.model.ModelSettings;
import com.bcoworks.blueringoctopuscli.service.CodeAssistant;
import com.bcoworks.blueringoctopuscli.service.PromptLibrary;
import com.bcoworks.blueringoctopuscli.service.SourceCodeScanner;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
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
    private final List<String> prompts = new ArrayList<>();
    private int cancelAfterPrintedLines = Integer.MAX_VALUE;
    private final IModeConsole console = new IModeConsole() {
        @Override
        public void step(String message) {
        }

        @Override
        public void println(String text) {
            printed.add(text);
        }

        @Override
        public boolean isCancelled() {
            return printed.size() >= cancelAfterPrintedLines;
        }
    };

    private AnalysisModeHandler handlerWith(AtomicInteger calls, int numCtx) {
        Messages messages = new Messages("en");
        CodeAssistant assistant = new CodeAssistant(chat -> {
            calls.incrementAndGet();
            prompts.add(((UserMessage) chat.get(1)).singleText());
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

    /** A class of 12 methods with 30 lines each: about 3,500 tokens. */
    private static String bigClass() {
        StringBuilder source = new StringBuilder("public class Big {\n");
        for (int method = 1; method <= 12; method++) {
            source.append("    public long method").append(method).append("(long total) {\n");
            for (int line = 0; line < 28; line++) {
                source.append("        total += ").append(1_000_000 + line).append(";\n");
            }
            source.append("        return total;\n    }\n\n");
        }
        return source.append("}\n").toString();
    }

    @Test
    void aFileTooBigForTheWindowIsReviewedInPartsInsteadOfBeingSkipped() throws IOException {
        Files.writeString(dir.resolve("Big.java"), bigClass());
        AtomicInteger calls = new AtomicInteger();

        handlerWith(calls, 4_000).handle(new ModeRequest("", dir.toString()), console);

        assertTrue(calls.get() >= 3, "calls: " + calls.get());
        String announcement = printed.stream().filter(line -> line.contains("does not fit")).findFirst().orElse("");
        assertTrue(announcement.contains("Big.java") && announcement.contains(calls.get() + " parts"), announcement);
        for (int part = 1; part <= calls.get(); part++) {
            String header = "--- Big.java, part " + part + "/" + calls.get() + " (lines ";
            assertTrue(printed.stream().anyMatch(line -> line.contains(header) && line.contains("Fine.")), header);
        }
        assertTrue(prompts.get(0).contains("part 1 of " + calls.get()), prompts.get(0));
        assertTrue(prompts.get(0).contains("Review only the code at lines "), prompts.get(0));
        assertTrue(prompts.get(0).contains("2|     public long method1(long total) {"), "numbers are the lines of the file");
        assertTrue(prompts.get(1).contains("part 2 of " + calls.get()));
    }

    @Test
    void everyMethodOfABigFileIsWrittenOutInAtLeastOnePart() throws IOException {
        Files.writeString(dir.resolve("Big.java"), bigClass());

        handlerWith(new AtomicInteger(), 4_000).handle(new ModeRequest("", dir.toString()), console);

        for (int method = 1; method <= 12; method++) {
            int returnLine = 2 + 32 * (method - 1) + 29; // each method takes 32 lines, the first starts at line 2
            String lastLine = returnLine + "|         return total;";
            assertTrue(prompts.stream().anyMatch(prompt -> prompt.contains(lastLine)), "method" + method + " is in no part");
        }
    }

    @Test
    void aWindowTooSmallForAnyPartKeepsTheOldHintToRaiseIt() throws IOException {
        Files.writeString(dir.resolve("Big.java"), bigClass());
        AtomicInteger calls = new AtomicInteger();

        handlerWith(calls, 3_100).handle(new ModeRequest("", dir.toString()), console);

        assertEquals(0, calls.get());
        String message = printed.stream().filter(line -> line.contains("Big.java")).findFirst().orElse("");
        assertTrue(message.contains("skipped") && message.contains("octopus.model.num-ctx"), printed.toString());
    }

    @Test
    void cancellingStopsTheRemainingParts() throws IOException {
        Files.writeString(dir.resolve("Big.java"), bigClass());
        AtomicInteger calls = new AtomicInteger();
        cancelAfterPrintedLines = 3; // the "found" line, the announcement and the first part

        handlerWith(calls, 4_000).handle(new ModeRequest("", dir.toString()), console);

        assertEquals(1, calls.get());
    }

    @Test
    void aFileOverTheSizeLimitIsStillSkipped() throws IOException {
        Files.writeString(dir.resolve("Huge.java"), "// padding\n".repeat(60_000)); // ~660 KB
        AtomicInteger calls = new AtomicInteger();

        handlerWith(calls, 8192).handle(new ModeRequest("", dir.toString()), console);

        assertEquals(0, calls.get());
        assertTrue(printed.stream().anyMatch(line -> line.contains("Huge.java") && line.contains("skipped")), printed.toString());
    }

    @Test
    void aBiggerWindowTakesTheSameFile() throws IOException {
        Files.writeString(dir.resolve("Big.java"), "int value = 1234567;\n".repeat(1_500));
        AtomicInteger calls = new AtomicInteger();

        handlerWith(calls, 32768).handle(new ModeRequest("", dir.toString()), console);

        assertEquals(1, calls.get());
    }
}
