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

    private final PromptLibrary prompts = new PromptLibrary();

    private static Set<String> allowed(String... extra) {
        Set<String> names = new java.util.HashSet<>(Set.of("language"));
        names.addAll(Set.of(extra));
        return names;
    }

    @Test
    void everyPromptExistsAndOnlyUsesPlaceholdersTheAssistantFills() {
        Map<String, Set<String>> expected = Map.ofEntries(
                Map.entry("analyze.system", Set.of()),
                Map.entry("analyze.user", Set.of("fileName", "code")),
                Map.entry("analyze.part.user", Set.of("fileName", "part", "parts", "focus", "code")),
                Map.entry("translate.system", Set.of("language")),
                Map.entry("translate.user", Set.of("text")),
                Map.entry("generate.system", allowed()),
                Map.entry("generate.user", allowed("prompt")),
                Map.entry("repair.system", allowed()),
                Map.entry("repair.user", allowed("code", "errors")),
                Map.entry("tests.system", allowed()),
                Map.entry("tests.user", allowed("fileName", "code")),
                Map.entry("document.system", allowed()),
                Map.entry("document.user", allowed("fileName", "code")));

        expected.forEach((name, allowedNames) -> {
            Set<String> used = PromptLibrary.placeholders(prompts.template(name));
            assertTrue(allowedNames.containsAll(used), name + " uses " + used + " but only " + allowedNames + " are filled");
        });
        assertTrue(allowed("fileName", "code").containsAll(PromptLibrary.placeholders(prompts.template("inventory.user"))));
        assertTrue(allowed().containsAll(PromptLibrary.placeholders(prompts.template("inventory.system"))));
    }

    @Test
    void theAnalysisIsAlwaysMadeInEnglishSoItsPromptHasNoLanguageOrLayoutPlaceholders() {
        assertTrue(PromptLibrary.placeholders(prompts.template("analyze.system")).isEmpty());
        assertTrue(prompts.template("analyze.system").contains("OVERVIEW"));
        assertTrue(prompts.template("analyze.system").contains("No significant issues found."));
    }

    @Test
    void theTranslationPromptHasAGlossaryLineForEveryNonEnglishLanguage() {
        String translate = prompts.template("translate.system");

        for (String language : List.of("Turkish", "German", "French", "Italian", "Spanish")) {
            assertTrue(translate.contains("\n" + language + ": SQL injection = "), language);
        }
    }

    @Test
    void everySystemPromptExceptTheAnalysisTellsTheModelWhichLanguageToWriteIn() {
        for (String name : List.of("translate", "generate", "repair", "tests", "document", "inventory")) {
            assertTrue(PromptLibrary.placeholders(prompts.template(name + ".system")).contains("language"), name);
        }
    }

    @Test
    void theRepairPromptCarriesTheCodeAndTheCompilerErrors() {
        assertTrue(PromptLibrary.placeholders(prompts.template("repair.user")).containsAll(Set.of("code", "errors")));
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
        for (String name : List.of("analyze", "translate", "generate", "repair", "tests", "document", "inventory")) {
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
        values.put("language", "German");
        values.put("fileName", "A.java");
        values.put("code", "class A {}");
        values.put("prompt", "something");
        values.put("text", "a review");

        for (String name : List.of("analyze", "translate", "generate", "tests", "document", "inventory")) {
            assertFalse(prompts.render(name + ".system", values).contains("{{"), name);
            assertFalse(prompts.render(name + ".user", values).contains("{{"), name);
        }
    }

}
