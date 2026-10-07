package com.bcoworks.blueringoctopuscli.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * The prompts are written in English; {@code language} is the English name of the language the user chose
 * ("English", "Turkish", ...), so the answer comes back in it.
 */
public interface ICodeAnalyzerService {

    @UserMessage("""
            You are a senior software architect. Review the code below.

            Criteria:
            1. Security vulnerabilities or potential bugs
            2. Performance risks
            3. Compliance with Clean Code principles

            Be brief, precise and give concrete suggestions.
            Respond in {{language}}.

            Code:
            {{code}}
            """)
    String analyze(@V("language") String language, @V("code") String code);

    @SystemMessage("""
            You are an expert Java backend developer.
            Write only clean, compilable Java code that fulfils the user's request.
            NEVER use markdown or code fences.
            Do not write explanations or introductory sentences. Return raw source code only.
            Keep identifiers in English and write any code comments in {{language}}.
            """)
    @UserMessage("Write a Java class that fulfils this request: {{prompt}}")
    String generateCode(@V("language") String language, @V("prompt") String prompt);
}
