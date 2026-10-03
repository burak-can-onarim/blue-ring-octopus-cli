package com.bcoworks.codeanalyzer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class CodeAnalyzerApplication {

    static void main(String[] args) {
        SpringApplication.run(CodeAnalyzerApplication.class, args);
    }
}