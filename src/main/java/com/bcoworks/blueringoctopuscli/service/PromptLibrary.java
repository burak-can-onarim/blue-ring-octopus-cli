package com.bcoworks.blueringoctopuscli.service;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The prompts sent to the model. They are plain text files under {@code prompts/} (so they can be read, diffed and
 * tuned without touching code) with {@code {{name}}} placeholders that are filled in here. Rendering is strict: a
 * placeholder without a value is an error, so a typo in a prompt or a missing value shows up in the tests instead of
 * as a strange answer from the model.
 */
@Component
public class PromptLibrary {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");
    private static final String PATH = "/prompts/%s.txt";

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    /**
     * The template called {@code name} (for example {@code analyze.system}).
     */
    public String template(String name) {
        return cache.computeIfAbsent(name, PromptLibrary::read);
    }

    /**
     * The template with every placeholder replaced by its value.
     *
     * @throws IllegalStateException if the template uses a placeholder that has no value
     */
    public String render(String name, Map<String, String> variables) {
        return fill(template(name), variables);
    }

    static String fill(String template, Map<String, String> variables) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String value = variables.get(matcher.group(1));
            if (value == null) {
                throw new IllegalStateException("No value for the placeholder {{" + matcher.group(1) + "}}");
            }
            // values are inserted as they are and never scanned again, so code containing "{{x}}" stays untouched
            matcher.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /**
     * The names of the placeholders a template uses.
     */
    static Set<String> placeholders(String template) {
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = PLACEHOLDER.matcher(template);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private static String read(String name) {
        String resource = PATH.formatted(name);
        try (InputStream in = PromptLibrary.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing prompt " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n").strip();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read prompt " + resource, e);
        }
    }
}
