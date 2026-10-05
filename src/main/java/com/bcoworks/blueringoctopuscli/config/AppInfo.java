package com.bcoworks.blueringoctopuscli.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AppInfo {

    private static final String SNAPSHOT_SUFFIX = "-SNAPSHOT";

    private final String version;

    public AppInfo(@Value("${octopus.version:dev}") String version) {
        this.version = normalize(version);
    }

    /**
     * Boş ya da filtrelenmemiş ("@project.version@") değer "dev" olur; "-SNAPSHOT" son eki gösterilmez.
     */
    static String normalize(String raw) {
        if (raw == null || raw.isBlank() || raw.contains("@")) {
            return "dev";
        }
        String value = raw.strip();
        return value.endsWith(SNAPSHOT_SUFFIX)
                ? value.substring(0, value.length() - SNAPSHOT_SUFFIX.length())
                : value;
    }

    public String version() {
        return version;
    }
}