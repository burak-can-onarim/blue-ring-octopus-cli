package com.bcoworks.blueringoctopuscli.model;

import com.bcoworks.blueringoctopuscli.context.AppMode;

import java.util.List;

/**
 * The Ollama models the model panel suggests, one list per mode, best first. The order is a recommendation based on
 * what the models are good at (explaining code, writing code, writing prose, writing tests) and on published
 * benchmarks; the prompts themselves were tuned and measured with {@code qwen2.5-coder} (7B), which every list
 * contains because it runs on a graphics card with 8 GB. Models that are installed but not listed here appear at the
 * end of the panel.
 */
public final class ModelCatalog {

    private static final List<String> ANALYSIS = List.of(
            "gpt-oss:20b", "qwen3-coder:30b", "qwen2.5-coder:14b", "qwen2.5-coder",
            "deepseek-coder-v2:16b", "llama3.1", "codellama");

    private static final List<String> GENERATION = List.of(
            "qwen3-coder:30b", "qwen2.5-coder:32b", "qwen2.5-coder:14b", "qwen2.5-coder",
            "deepseek-coder-v2:16b", "gpt-oss:20b", "codellama");

    private static final List<String> DOCUMENTATION = List.of(
            "gpt-oss:20b", "gemma3:12b", "qwen3:14b", "llama3.1", "qwen2.5-coder:14b", "qwen2.5-coder");

    private static final List<String> UNIT_TESTS = List.of(
            "qwen3-coder:30b", "qwen2.5-coder:32b", "qwen2.5-coder:14b", "qwen2.5-coder",
            "deepseek-coder-v2:16b", "gpt-oss:20b");

    private ModelCatalog() {
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
