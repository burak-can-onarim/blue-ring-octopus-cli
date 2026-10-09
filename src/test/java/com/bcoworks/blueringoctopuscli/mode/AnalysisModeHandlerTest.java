package com.bcoworks.blueringoctopuscli.mode;

import com.bcoworks.blueringoctopuscli.context.AppMode;
import com.bcoworks.blueringoctopuscli.i18n.Messages;
import com.bcoworks.blueringoctopuscli.model.AiServiceRegistry;
import com.bcoworks.blueringoctopuscli.model.ModelSettings;
import com.bcoworks.blueringoctopuscli.service.CodeAssistant;
import com.bcoworks.blueringoctopuscli.service.PromptLibrary;
import com.bcoworks.blueringoctopuscli.service.SourceCodeScanner;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
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
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalysisModeHandlerTest {

    private static final String PART_REVIEW = "OVERVIEW\nIt adds numbers.\n\nFINDINGS\n"
            + "1. [HIGH] line 12 - total can overflow. Consequence: wrong sums. Fix: use Math.addExact.\n"
            + "2. [LOW] line 40 - the name is vague. Consequence: confusion. Fix: rename it.\n\nSUMMARY\nFix the overflow.";
    private static final String MERGED_REVIEW = "OVERVIEW\nA class that adds numbers in many ways.\n\nFINDINGS\n"
            + "1. [HIGH] line 12 - total can overflow. Consequence: wrong sums. Fix: use Math.addExact.\n"
            + "2. [LOW] line 40 - the name is vague. Consequence: confusion. Fix: rename it.\n"
            + "3. [LOW] line 70 - a third. Consequence: x. Fix: y.\n\nSUMMARY\nFix the overflow first.";

    @TempDir
    Path dir;

    private final List<String> printed = new ArrayList<>();
    private final List<String> systems = new ArrayList<>();
    private final List<String> prompts = new ArrayList<>();
    private final AtomicInteger calls = new AtomicInteger();
    private int cancelAfterCalls = Integer.MAX_VALUE;
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
            return calls.get() >= cancelAfterCalls;
        }
    };

    private static boolean isMerge(String system) {
        return system.startsWith("You are a senior Java engineer. A long Java file was reviewed in parts");
    }

    /** What a model would answer: a structured review for a part, a merged review for the merge, text for anything else. */
    private final Function<List<ChatMessage>, String> sensibleModel = chat -> {
        String system = ((SystemMessage) chat.get(0)).text();
        if (isMerge(system)) {
            return MERGED_REVIEW;
        }
        return system.startsWith("You translate") ? "OVERVIEW\nTranslated." : PART_REVIEW;
    };

    private AnalysisModeHandler handlerWith(int numCtx, int partTokens, String language, Function<List<ChatMessage>, String> model) {
        Messages messages = new Messages(language);
        CodeAssistant assistant = new CodeAssistant(chat -> {
            calls.incrementAndGet();
            systems.add(((SystemMessage) chat.get(0)).text());
            prompts.add(((UserMessage) chat.get(1)).singleText());
            return Response.from(AiMessage.from(model.apply(chat)));
        }, new PromptLibrary(), messages);
        AiServiceRegistry registry = new AiServiceRegistry(new ModelSettings("test-model"), new PromptLibrary(), messages,
                "http://localhost:1", 0.2, Duration.ofSeconds(1), numCtx, 1024) {
            @Override
            public CodeAssistant forMode(AppMode mode) {
                return assistant;
            }
        };
        return new AnalysisModeHandler(registry, new SourceCodeScanner(), messages, partTokens);
    }

    private AnalysisModeHandler handlerWith(int numCtx) {
        return handlerWith(numCtx, 0, "en", sensibleModel);
    }

    private void review(AnalysisModeHandler handler) throws IOException {
        handler.handle(new ModeRequest("", dir.toString()), console);
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

    private long modelCallsFor(String kind) {
        return systems.stream().filter(system -> switch (kind) {
            case "merge" -> isMerge(system);
            case "translate" -> system.startsWith("You translate");
            default -> !isMerge(system) && !system.startsWith("You translate");
        }).count();
    }

    @Test
    void aFileThatFitsTheWindowIsAnalysed() throws IOException {
        Files.writeString(dir.resolve("Small.java"), "class Small {\n}\n");

        review(handlerWith(8192));

        assertEquals(1, calls.get());
        assertTrue(printed.stream().anyMatch(line -> line.contains("--- Small.java ---") && line.contains("OVERVIEW")), printed.toString());
    }

    @Test
    void aFileTooBigForTheWindowIsReviewedInPartsAndTheReviewsAreMergedIntoOne() throws IOException {
        Files.writeString(dir.resolve("Big.java"), bigClass());

        review(handlerWith(4_000));

        long parts = modelCallsFor("part");
        assertTrue(parts >= 3, "parts: " + parts);
        assertEquals(1, modelCallsFor("merge"));
        assertEquals(parts + 1, calls.get());
        String announcement = printed.stream().filter(line -> line.contains("does not fit")).findFirst().orElse("");
        assertTrue(announcement.contains("Big.java") && announcement.contains(parts + " parts"), announcement);
        assertTrue(printed.stream().anyMatch(line -> line.contains("--- Big.java (" + parts + " parts, merged) ---")
                && line.contains("Fix the overflow first.")), printed.toString());
        assertFalse(printed.stream().anyMatch(line -> line.contains(", part 1/")), "the parts are not shown one by one: " + printed);
        assertTrue(prompts.get(0).contains("part 1 of " + parts), prompts.get(0));
        assertTrue(prompts.get(0).contains("Review only the code at lines "), prompts.get(0));
        assertTrue(prompts.get(0).contains("2|     public long method1(long total) {"), "numbers are the lines of the file");
        String mergePrompt = prompts.get((int) parts);
        assertTrue(mergePrompt.contains("=== Part 1 of " + parts + " (lines "), mergePrompt);
        assertTrue(mergePrompt.contains("[HIGH] line 12 - total can overflow"), mergePrompt);
        assertFalse(mergePrompt.contains("Fix the overflow."), "the summaries of the parts are not handed on");
    }

    @Test
    void everyMethodOfABigFileIsWrittenOutInAtLeastOnePart() throws IOException {
        Files.writeString(dir.resolve("Big.java"), bigClass());

        review(handlerWith(4_000));

        for (int method = 1; method <= 12; method++) {
            int returnLine = 2 + 32 * (method - 1) + 29; // each method takes 32 lines, the first starts at line 2
            String lastLine = returnLine + "|         return total;";
            assertTrue(prompts.stream().anyMatch(prompt -> prompt.contains(lastLine)), "method" + method + " is in no part");
        }
    }

    @Test
    void whenTheMergeCannotBeTrustedTheReviewsOfThePartsAreShownOneByOne() throws IOException {
        Files.writeString(dir.resolve("Big.java"), bigClass());

        review(handlerWith(4_000, 0, "en", chat -> isMerge(((SystemMessage) chat.get(0)).text()) ? "Sorry, I cannot do that." : PART_REVIEW));

        long parts = modelCallsFor("part");
        for (int part = 1; part <= parts; part++) {
            String header = "--- Big.java, part " + part + "/" + parts + " (lines ";
            assertTrue(printed.stream().anyMatch(line -> line.contains(header) && line.contains("total can overflow")), header);
        }
        assertFalse(printed.stream().anyMatch(line -> line.contains("merged")), printed.toString());
    }

    @Test
    void aMergeThatDropsMostOfTheFindingsIsNotUsed() throws IOException {
        Files.writeString(dir.resolve("Big.java"), bigClass());
        String lossy = "OVERVIEW\nx\n\nFINDINGS\nNo significant issues found.\n\nSUMMARY\nAll fine.";

        review(handlerWith(4_000, 0, "en", chat -> isMerge(((SystemMessage) chat.get(0)).text()) ? lossy : PART_REVIEW));

        assertTrue(printed.stream().anyMatch(line -> line.contains(", part 1/") && line.contains("total can overflow")), printed.toString());
        assertFalse(printed.stream().anyMatch(line -> line.contains("All fine.")), printed.toString());
    }

    @Test
    void anotherLanguageIsTranslatedOnceForTheMergedReviewNotOncePerPart() throws IOException {
        Files.writeString(dir.resolve("Big.java"), bigClass());

        review(handlerWith(4_000, 0, "tr", sensibleModel));

        long parts = modelCallsFor("part");
        assertEquals(1, modelCallsFor("merge"));
        assertEquals(1, modelCallsFor("translate"), "one translation for the merged review");
        assertEquals(parts + 2, calls.get());
        Messages turkish = new Messages("tr");
        assertTrue(printed.stream().anyMatch(line -> line.contains(turkish.get("analysis.merged", "Big.java", (int) parts).strip())),
                printed.toString());
    }

    @Test
    void aWindowTooSmallForAnyPartKeepsTheOldHintToRaiseIt() throws IOException {
        Files.writeString(dir.resolve("Big.java"), bigClass());

        review(handlerWith(3_100));

        assertEquals(0, calls.get());
        String message = printed.stream().filter(line -> line.contains("Big.java")).findFirst().orElse("");
        assertTrue(message.contains("skipped") && message.contains("octopus.model.num-ctx"), printed.toString());
    }

    @Test
    void cancellingStopsTheRemainingPartsAndTheMerge() throws IOException {
        Files.writeString(dir.resolve("Big.java"), bigClass());
        cancelAfterCalls = 2;

        review(handlerWith(4_000));

        assertEquals(2, calls.get());
        assertFalse(printed.stream().anyMatch(line -> line.contains("merged")), printed.toString());
    }

    @Test
    void aFileOverTheSizeLimitIsStillSkipped() throws IOException {
        Files.writeString(dir.resolve("Huge.java"), "// padding\n".repeat(60_000)); // ~660 KB

        review(handlerWith(8192));

        assertEquals(0, calls.get());
        assertTrue(printed.stream().anyMatch(line -> line.contains("Huge.java") && line.contains("skipped")), printed.toString());
    }

    @Test
    void aBiggerWindowTakesTheSameFileInOnePiece() throws IOException {
        Files.writeString(dir.resolve("Big.java"), bigClass());

        review(handlerWith(32768));

        assertEquals(1, calls.get());
    }

    @Test
    void thePartSizeSettingCutsAFileThatFitsTheWindowIntoSmallerParts() throws IOException {
        Files.writeString(dir.resolve("Big.java"), bigClass());

        review(handlerWith(32768, 700, "en", sensibleModel));

        long parts = modelCallsFor("part");
        assertTrue(parts >= 3, "parts: " + parts);
        assertEquals(1, modelCallsFor("merge"));
        assertTrue(printed.stream().anyMatch(line -> line.contains("is reviewed in " + parts + " parts of at most 700 tokens")), printed.toString());
        assertTrue(printed.stream().anyMatch(line -> line.contains("(" + parts + " parts, merged)")), printed.toString());
    }

    @Test
    void aFileSmallerThanThePartSizeIsReviewedWhole() throws IOException {
        Files.writeString(dir.resolve("Big.java"), bigClass());

        review(handlerWith(32768, 20_000, "en", sensibleModel));

        assertEquals(1, calls.get());
        assertTrue(printed.stream().anyMatch(line -> line.contains("--- Big.java ---")), printed.toString());
    }

    @Test
    void aPartSizeTooSmallToCutAnythingFallsBackToTheWholeReview() throws IOException {
        Files.writeString(dir.resolve("Big.java"), bigClass());

        review(handlerWith(32768, 50, "en", sensibleModel));

        assertEquals(1, calls.get());
        assertTrue(printed.stream().anyMatch(line -> line.contains("--- Big.java ---")), printed.toString());
    }
}
