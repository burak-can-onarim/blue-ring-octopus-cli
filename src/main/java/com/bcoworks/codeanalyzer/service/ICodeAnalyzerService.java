package com.bcoworks.codeanalyzer.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;

@AiService
public interface ICodeAnalyzerService {

    @UserMessage("""
            You are a senior software architect. Review the code below.
            
            Criteria:
            1. Security vulnerabilities or potential bugs
            2. Performance risks
            3. Compliance with Clean Code principles
            
            Be brief, precise and give concrete suggestions.
            Respond in Turkish.
            
            Code:
            {{code}}
            """)
    String analyze(String code);

    @SystemMessage("""
            You are an expert Java backend developer.
            Write only clean, compilable Java code that fulfils the user's request.
            NEVER use markdown or code fences.
            Do not write explanations or introductory sentences. Return raw source code only.
            """)
    @UserMessage("Write a Java class that fulfils this request: {{prompt}}")
    String generateCode(String prompt);
}