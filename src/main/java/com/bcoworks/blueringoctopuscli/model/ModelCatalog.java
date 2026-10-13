package com.bcoworks.blueringoctopuscli.model;

import com.bcoworks.blueringoctopuscli.context.AppMode;

import java.util.List;
import java.util.Locale;

/**
 * The Ollama models the model panel suggests, one list per mode, best first. Every model in a list was measured with
 * the application's own prompts (see docs/PROMPTS.md, "Which model"): the review on a seeded sample (known problems found,
 * invented problems on a clean file), generation on 14 requests (files that compile, with one repair). The order follows
 * those numbers. {@code qwen2.5-coder} (7B) is in every list because the prompts were tuned with it and because it runs
 * on a graphics card with 8 GB; the larger models need Ollama to put part of the model into the main memory.
 * <p>
 * The Documentation and Unit Tests modes do not exist yet, so their lists are the ones of the nearest measured task
 * (explaining code, writing code). Models that are installed but not listed here appear at the end of the panel.
 */
public final class ModelCatalog {

    /** Review: gemma4:26b found every seeded problem in 4 of 4 runs; gpt-oss:20b is as good but 2.6 times slower. */
    private static final List<String> ANALYSIS = List.of(
            "gemma4:26b", "gpt-oss:20b", "ornith:9b", "gemma4:12b", "qwen2.5-coder");

    /** Generation: files that compile after one repair, gemma4:26b 27 of 28, qwen2.5-coder 25 of 28. */
    private static final List<String> GENERATION = List.of(
            "gemma4:26b", "qwen2.5-coder", "gemma4:12b");

    private static final List<String> DOCUMENTATION = ANALYSIS;

    private static final List<String> UNIT_TESTS = GENERATION;

    private ModelCatalog() {
    }

    /**
     * Ollama's cloud models ({@code gpt-oss:120b-cloud}, {@code gemma4:cloud}, ...) run on Ollama's servers: the code
     * leaves the computer and the use is billed to the Ollama account, which is not what a local-first tool promises.
     */
    public static boolean isCloud(String model) {
        if (model == null) {
            return false;
        }
        String name = model.strip().toLowerCase(Locale.ROOT);
        int colon = name.indexOf(':');
        String tag = colon < 0 ? "" : name.substring(colon + 1);
        return tag.equals("cloud") || tag.endsWith("-cloud");
    }

    public static List<String> suggestedFor(AppMode mode) {
        return switch (mode) {
            case CODE_ANALYSIS -> ANALYSIS;
            case CODE_GENERATION -> GENERATION;
            case DOCUMENTATION -> DOCUMENTATION;
            case UNIT_TESTS -> UNIT_TESTS;
        };
    }
}
