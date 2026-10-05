package com.bcoworks.blueringoctopuscli.util;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class PathUtils {

    private PathUtils() {
    }

    /**
     * Boşlukları ve çevreleyen çift tırnakları temizler. null güvenlidir.
     */
    public static String clean(String input) {
        if (input == null) {
            return "";
        }
        String value = input.strip();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1).strip();
        }
        return value;
    }

    /**
     * Absolute yolu aynen, relative yolu çalışma dizinine göre çözer.
     */
    public static Path resolve(String input) {
        Path path = Paths.get(clean(input));
        return path.isAbsolute()
                ? path
                : Paths.get(System.getProperty("user.dir")).resolve(path).normalize();
    }
}