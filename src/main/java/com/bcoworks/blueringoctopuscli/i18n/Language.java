package com.bcoworks.blueringoctopuscli.i18n;

import java.util.Locale;
import java.util.Optional;

/**
 * Languages of the user interface. {@link #englishName()} is what the model is told to answer in.
 */
public enum Language {
    EN("en", "English", "English"),
    TR("tr", "Türkçe", "Turkish"),
    DE("de", "Deutsch", "German"),
    FR("fr", "Français", "French"),
    IT("it", "Italiano", "Italian"),
    ES("es", "Español", "Spanish");

    public static final Language DEFAULT = EN;

    private final String code;
    private final String nativeName;
    private final String englishName;

    Language(String code, String nativeName, String englishName) {
        this.code = code;
        this.nativeName = nativeName;
        this.englishName = englishName;
    }

    public String code() {
        return code;
    }

    public String nativeName() {
        return nativeName;
    }

    public String englishName() {
        return englishName;
    }

    public Locale locale() {
        return Locale.forLanguageTag(code);
    }

    /**
     * Accepts a code ("de"), a locale tag ("de-DE", "de_DE") or a language name ("German", "Deutsch").
     */
    public static Optional<Language> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String text = value.strip().toLowerCase(Locale.ROOT);
        String primary = text.split("[-_.@]", 2)[0];
        for (Language language : values()) {
            if (language.code.equals(primary)
                    || language.nativeName.toLowerCase(Locale.ROOT).equals(text)
                    || language.englishName.toLowerCase(Locale.ROOT).equals(text)) {
                return Optional.of(language);
            }
        }
        return Optional.empty();
    }
}
