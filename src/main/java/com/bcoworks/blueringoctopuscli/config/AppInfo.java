package com.bcoworks.blueringoctopuscli.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AppInfo {

    private final String version;

    public AppInfo(@Value("${octopus.version:dev}") String version) {
        this.version = normalize(version);
    }

    /**
     * Boş ya da filtrelenmemiş ("@project.version@") değer "dev" olur; diğerleri olduğu gibi (kırpılmış) gösterilir.
     */
    static String normalize(String raw) {
        if (raw == null || raw.isBlank() || raw.contains("@")) {
            return "dev";
        }
        return raw.strip();
    }

    public String version() {
        return version;
    }
}
