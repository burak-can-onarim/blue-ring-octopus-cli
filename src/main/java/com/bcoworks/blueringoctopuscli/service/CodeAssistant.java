package com.bcoworks.blueringoctopuscli.service;

import com.bcoworks.blueringoctopuscli.i18n.Language;
import com.bcoworks.blueringoctopuscli.i18n.Messages;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What the application asks of the model: review a file, write a class (and fix it when it does not compile), write
 * tests, document a file. Every call is one system message and one user message built from the files under
 * {@code prompts/} (see {@link PromptLibrary}).
 * <p>
 * A small local model reasons best in English and cannot reliably do an analysis and write the report in another
 * language in one go. So a review is always made in English; for any other language the application puts the frame
 * of the review into that language (titles, severity, "line", "Consequence", "Fix") and the model only translates the
 * free text in a second call. If the translation damages the frame, the localized English review is shown instead.
 */
public class CodeAssistant {

    private static final String FIRST_TITLE = "OVERVIEW";

    private final ChatLanguageModel model;
    private final PromptLibrary prompts;
    private final Messages messages;

    public CodeAssistant(ChatLanguageModel model, PromptLibrary prompts, Messages messages) {
        this.model = model;
        this.prompts = prompts;
        this.messages = messages;
    }

    /**
     * A review of one Java file as plain text: what it does, what is wrong (with line numbers and severity), a verdict.
     */
    public String analyze(String fileName, String code) {
        Map<String, String> variables = variables();
        variables.put("fileName", fileName);
        variables.put("code", ModelOutput.numberLines(code));
        return review("analyze.user", variables);
    }

    /**
     * A review of one part of a file that is too long for one request (see {@link CodeSplitter}). The part already
     * carries the original line numbers and says which lines to review; the rest of the file is context.
     */
    public String analyzePart(String fileName, CodeSplitter.Part part) {
        Map<String, String> variables = variables();
        variables.put("fileName", fileName);
        variables.put("part", String.valueOf(part.number()));
        variables.put("parts", String.valueOf(part.count()));
        variables.put("focus", part.focusText());
        variables.put("code", part.code());
        return review("analyze.part.user", variables);
    }

    private String review(String userPrompt, Map<String, String> variables) {
        String english = ModelOutput.toPlainText(
                ModelOutput.stripNotes(ask("analyze.system", userPrompt, variables), FIRST_TITLE));
        if (messages.language() == Language.EN || english.isBlank()) {
            return english;
        }

        String localized = ReviewLocalizer.localize(english, messages);
        Map<String, String> translation = variables();
        translation.put("text", localized);
        String translated = ModelOutput.toPlainText(ask("translate.system", "translate.user", translation));
        return ReviewLocalizer.keepsStructure(localized, translated, messages) ? translated : localized;
    }

    /**
     * The source of one Java file for the request.
     */
    public String generateCode(String request) {
        Map<String, String> variables = variables();
        variables.put("prompt", request);
        return ask("generate", variables);
    }

    /**
     * The same file with the compile errors fixed. The model gets the file and what the compiler said, line by line.
     */
    public String repairCode(String code, List<CompileCheck.Problem> problems) {
        Map<String, String> variables = variables();
        variables.put("code", code);
        variables.put("errors", describe(problems));
        return ask("repair", variables);
    }

    public static String describe(List<CompileCheck.Problem> problems) {
        StringBuilder errors = new StringBuilder();
        for (CompileCheck.Problem problem : problems) {
            errors.append("line ").append(problem.line()).append(": ").append(problem.message()).append('\n');
            if (!problem.sourceLine().isEmpty()) {
                errors.append("    ").append(problem.sourceLine()).append('\n');
            }
        }
        return errors.toString().stripTrailing();
    }

    /**
     * A JUnit 5 test class for the file.
     */
    public String generateTests(String fileName, String code) {
        return askAboutFile("tests", fileName, code);
    }

    /**
     * Documentation of the file as Markdown (to be turned into Word or PDF).
     */
    public String document(String fileName, String code) {
        return askAboutFile("document", fileName, code);
    }

    /**
     * The public API of the file as a Markdown table (to be turned into a spreadsheet).
     */
    public String inventory(String fileName, String code) {
        return askAboutFile("inventory", fileName, code);
    }

    private String askAboutFile(String prompt, String fileName, String code) {
        Map<String, String> variables = variables();
        variables.put("fileName", fileName);
        variables.put("code", code);
        return ask(prompt, variables);
    }

    private String ask(String prompt, Map<String, String> variables) {
        return ask(prompt + ".system", prompt + ".user", variables);
    }

    private String ask(String systemPrompt, String userPrompt, Map<String, String> variables) {
        List<ChatMessage> conversation = List.of(
                SystemMessage.from(prompts.render(systemPrompt, variables)),
                UserMessage.from(prompts.render(userPrompt, variables)));
        AiMessage answer = model.generate(conversation).content();
        return answer.text() == null ? "" : answer.text();
    }

    /**
     * The instructions are in English; the model is told the English name of the language to write in.
     */
    private Map<String, String> variables() {
        Map<String, String> variables = new HashMap<>();
        variables.put("language", messages.language().englishName());
        return variables;
    }
}
