package com.bcoworks.codeanalyzer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AppInfo {

    private final String version;

    public AppInfo(@Value("${octopus.version:dev}") String version) {
        this.version = (version.isBlank() || version.contains("@")) ? "dev" : version;
    }

    public String version() {
        return version;
    }
}