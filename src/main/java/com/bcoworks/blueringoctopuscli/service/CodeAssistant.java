package com.bcoworks.blueringoctopuscli.service;

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
 * What the application asks of the model: review a file, write a class, write tests, document a file. Every call is one
 * system message and one user message built from the files under {@code prompts/} (see {@link PromptLibrary}), in the
 * language the user chose. The reviews come back as plain text; code, tests and documentation come back as the model
 * wrote them (the caller removes code fences).
 */
public class CodeAssistant {

    private final ChatLanguageModel model;
    private final PromptLibrary prompts;
    private final Messages messages;

    public CodeAssistant(ChatLanguageModel model, PromptLibrary prompts, Messages messages) {
        this.model = model;
        this.prompts = prompts;
        this.messages = messages;
    }

    /**
     * A review of one Java file: what it does, what is wrong (with line numbers and severity), a verdict. Section
     * titles and labels come from the language files; the model writes the free text in the selected language.
     */
    public String analyze(String fileName, String code) {
        Map<String, String> variables = variables();
        variables.put("fileName", fileName);
        variables.put("code", ModelOutput.numberLines(code));
        String answer = ModelOutput.stripNotes(ask("analyze", variables), messages.get("analysis.title.overview"));
        return ModelOutput.toPlainText(answer);
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
        List<ChatMessage> conversation = List.of(
                SystemMessage.from(prompts.render(prompt + ".system", variables)),
                UserMessage.from(prompts.render(prompt + ".user", variables)));
        AiMessage answer = model.generate(conversation).content();
        return answer.text() == null ? "" : answer.text();
    }

    /**
     * The values every prompt may use: the English name of the language (the instructions are in English) and the
     * words of the review layout in that language, which the model copies instead of translating.
     */
    private Map<String, String> variables() {
        Map<String, String> variables = new HashMap<>();
        variables.put("language", messages.language().englishName());
        variables.put("titleOverview", messages.get("analysis.title.overview"));
        variables.put("titleFindings", messages.get("analysis.title.findings"));
        variables.put("titleSummary", messages.get("analysis.title.summary"));
        variables.put("noIssues", messages.get("analysis.noIssues"));
        variables.put("severityHigh", messages.get("analysis.severity.high"));
        variables.put("severityMedium", messages.get("analysis.severity.medium"));
        variables.put("severityLow", messages.get("analysis.severity.low"));
        variables.put("labelLine", messages.get("analysis.label.line"));
        variables.put("labelConsequence", messages.get("analysis.label.consequence"));
        variables.put("labelFix", messages.get("analysis.label.fix"));
        return variables;
    }
}
