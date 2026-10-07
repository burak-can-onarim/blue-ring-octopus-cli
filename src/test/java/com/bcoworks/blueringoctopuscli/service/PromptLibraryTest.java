package com.bcoworks.blueringoctopuscli.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptLibraryTest {

    /** The values CodeAssistant supplies for the layout of a review. */
    private static final Set<String> REVIEW_LAYOUT = Set.of(
            "titleOverview", "titleFindings", "titleSummary", "noIssues", "severityHigh", "severityMedium",
            "severityLow", "labelLine", "labelConsequence", "labelFix");

    private final PromptLibrary prompts = new PromptLibrary();

    private static Set<String> allowed(String... extra) {
        Set<String> names = new java.util.HashSet<>(Set.of("language"));
        names.addAll(Set.of(extra));
        return names;
    }

    @Test
    void everyPromptExistsAndOnlyUsesPlaceholdersTheAssistantFills() {
        Map<String, Set<String>> expected = Map.of(
                "analyze.system", allowedWith(REVIEW_LAYOUT),
                "analyze.user", allowedWith(Set.of("fileName", "code")),
                "generate.system", allowed(),
                "generate.user", allowed("prompt"),
                "tests.system", allowed(),
                "tests.user", allowed("fileName", "code"),
                "document.system", allowed(),
                "document.user", allowed("fileName", "code"),
                "inventory.system", allowed(),
                "inventory.user", allowed("fileName", "code"));

        expected.forEach((name, allowedNames) -> {
            Set<String> used = PromptLibrary.placeholders(prompts.template(name));
            assertTrue(allowedNames.containsAll(used), name + " uses " + used + " but only " + allowedNames + " are filled");
        });
    }

    @Test
    void theAnalysisPromptUsesEveryWordOfTheReviewLayout() {
        Set<String> used = PromptLibrary.placeholders(prompts.template("analyze.system"));

        assertTrue(used.containsAll(REVIEW_LAYOUT), "unused: " + REVIEW_LAYOUT.stream().filter(n -> !used.contains(n)).toList());
    }

    @Test
    void everySystemPromptTellsTheModelWhichLanguageToWriteIn() {
        for (String name : List.of("analyze", "generate", "tests", "document", "inventory")) {
            assertTrue(PromptLibrary.placeholders(prompts.template(name + ".system")).contains("language"), name);
        }
    }

    @Test
    void everyUserPromptCarriesTheCodeOrTheRequest() {
        for (String name : List.of("analyze", "tests", "document", "inventory")) {
            assertTrue(PromptLibrary.placeholders(prompts.template(name + ".user")).contains("code"), name);
        }
        assertTrue(PromptLibrary.placeholders(prompts.template("generate.user")).contains("prompt"));
    }

    @Test
    void thePromptsStayShortEnoughForASmallContextWindow() {
        for (String name : List.of("analyze", "generate", "tests", "document", "inventory")) {
            int words = prompts.template(name + ".system").split("\\s+").length;
            assertTrue(words <= 700, name + ".system has " + words + " words");
        }
    }

    @Test
    void theAnalysisPromptHasTheSafeguardsThatWereMeasured() {
        String system = prompts.template("analyze.system");

        assertTrue(system.contains("<notes>") && system.contains("</notes>"));
        assertTrue(system.contains("at most 12 lines"));
        assertTrue(system.contains("No Markdown"));
        assertTrue(system.contains("line numbers"));
    }

    @Test
    void renderingFillsEveryPlaceholder() {
        String text = PromptLibrary.fill("Answer in {{language}}: {{prompt}}", Map.of("language", "German", "prompt", "x"));

        assertEquals("Answer in German: x", text);
    }

    @Test
    void aMissingValueIsAnError() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> PromptLibrary.fill("Hello {{name}}", Map.of()));

        assertTrue(error.getMessage().contains("name"));
    }

    @Test
    void valuesAreInsertedAsTheyAreAndNeverScannedAgain() {
        String code = "int[][] a = {{1, 2}, {3}}; String s = \"{{language}} $1 \\\\\";";

        String text = PromptLibrary.fill("<code>{{code}}</code> in {{language}}", Map.of("code", code, "language", "French"));

        assertEquals("<code>" + code + "</code> in French", text);
    }

    @Test
    void anUnknownPromptIsAnError() {
        assertThrows(IllegalStateException.class, () -> prompts.template("no-such-prompt"));
    }

    @Test
    void renderedPromptsHaveNoPlaceholderLeft() {
        Map<String, String> values = new java.util.HashMap<>();
        for (String name : allowedWith(REVIEW_LAYOUT)) {
            values.put(name, "x");
        }
        values.put("fileName", "A.java");
        values.put("code", "class A {}");
        values.put("prompt", "something");

        for (String name : List.of("analyze", "generate", "tests", "document", "inventory")) {
            assertFalse(prompts.render(name + ".system", values).contains("{{"), name);
            assertFalse(prompts.render(name + ".user", values).contains("{{"), name);
        }
    }

    private static Set<String> allowedWith(Set<String> names) {
        Set<String> result = new java.util.HashSet<>(names);
        result.add("language");
        return result;
    }
}
