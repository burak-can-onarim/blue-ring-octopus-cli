package com.bcoworks.codeanalyzer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CodeAnalyzerApplication {

    static void main(String[] args) {
        System.setProperty("java.awt.headless", "false");
        SpringApplication.run(CodeAnalyzerApplication.class, args);
    }
}