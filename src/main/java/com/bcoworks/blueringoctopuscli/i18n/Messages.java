package com.bcoworks.blueringoctopuscli.i18n;

import com.bcoworks.blueringoctopuscli.context.AppMode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * All texts shown to the user, in the selected {@link Language}.
 * <p>
 * The language is taken, in this order, from the {@code octopus.lang} property (environment variable
 * {@code OCTOPUS_LANG}), from the saved choice in {@code ~/.octopus-cli/settings.properties}, and finally defaults to
 * English. A missing key falls back to the English text, so an incomplete translation never shows a blank.
 */
@Slf4j
@Component
public class Messages {

    private static final String RESOURCE = "/i18n/messages_%s.properties";
    private static final String SETTING_KEY = "language";
    private static final Map<Language, Map<String, String>> TABLES = loadAll();
    private static final Pattern PROBLEM_LINE = buildProblemPattern();

    private final Path file;
    private volatile Language language;

    @Autowired
    public Messages(@Value("${octopus.lang:}") String override) {
        this(override, Path.of(System.getProperty("user.home"), ".octopus-cli", "settings.properties"));
    }

    Messages(String override, Path file) {
        this.file = file;
        this.language = Language.parse(override)
                .or(this::saved)
                .orElse(Language.DEFAULT);
        if (override != null && !override.isBlank() && Language.parse(override).isEmpty()) {
            log.warn("Unknown language '{}', using {}", override, language.code());
        }
    }

    public Language language() {
        return language;
    }

    /**
     * Switches the language for everything shown from now on and remembers the choice.
     */
    public void setLanguage(Language value) {
        this.language = value;
        save();
    }

    /**
     * The text for {@code key}; {@code args} are applied with {@link String#format}.
     */
    public String get(String key, Object... args) {
        String pattern = TABLES.get(language).get(key);
        if (pattern == null) {
            pattern = TABLES.get(Language.DEFAULT).getOrDefault(key, key);
        }
        return args.length == 0 ? pattern : String.format(language.locale(), pattern, args);
    }

    public String modeName(AppMode mode) {
        return get("mode." + mode.name() + ".name");
    }

    public String promptHint(AppMode mode) {
        return get("mode." + mode.name() + ".prompt");
    }

    public String pathHint(AppMode mode) {
        return get("mode." + mode.name() + ".path");
    }

    /**
     * True for lines such as "Error: ..." or "Warning: ..." in any language, so they can be highlighted even if
     * the language was switched while the text was produced.
     */
    public static boolean isProblemLine(String line) {
        return PROBLEM_LINE.matcher(line).find();
    }

    /**
     * Keys and values of one language (for the consistency tests).
     */
    static Map<String, String> table(Language language) {
        return TABLES.get(language);
    }

    // ---------------------------------------------------------------- persistence

    private Optional<Language> saved() {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        Properties props = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            props.load(reader);
        } catch (IOException e) {
            log.warn("Could not read the language setting: {}", e.getMessage());
            return Optional.empty();
        }
        return Language.parse(props.getProperty(SETTING_KEY));
    }

    private synchronized void save() {
        Properties props = new Properties();
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                props.load(reader); // keep other settings
            } catch (IOException e) {
                log.warn("Could not read the settings file: {}", e.getMessage());
            }
        }
        props.setProperty(SETTING_KEY, language.code());
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                props.store(writer, "Blue Ring Octopus CLI - settings");
            }
        } catch (IOException e) {
            log.warn("Could not save the language setting: {}", e.getMessage());
        }
    }

    // ---------------------------------------------------------------- loading

    private static Map<Language, Map<String, String>> loadAll() {
        Map<Language, Map<String, String>> tables = new EnumMap<>(Language.class);
        for (Language language : Language.values()) {
            tables.put(language, load(language));
        }
        return tables;
    }

    private static Map<String, String> load(Language language) {
        String resource = RESOURCE.formatted(language.code());
        try (InputStream in = Messages.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource " + resource);
            }
            Properties props = new Properties();
            props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            Map<String, String> table = new HashMap<>();
            props.stringPropertyNames().forEach(key -> table.put(key, props.getProperty(key)));
            return Map.copyOf(table);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + resource, e);
        }
    }

    private static Pattern buildProblemPattern() {
        Set<String> labels = new LinkedHashSet<>();
        for (Language language : Language.values()) {
            labels.add(TABLES.get(language).get("label.error"));
            labels.add(TABLES.get(language).get("label.warning"));
        }
        String alternatives = String.join("|", labels.stream().map(Pattern::quote).toList());
        return Pattern.compile("^\\s*(?:" + alternatives + ")\\s?:");
    }
}
