package com.bcoworks.blueringoctopuscli.model;

import java.util.List;

public final class ModelCatalog {

    /**
     * Önerilen Ollama etiketleri. Kurulu olup burada olmayan modeller panelde ayrıca listelenir.
     */
    public static final List<String> SUGGESTED = List.of(
            "qwen2.5-coder",
            "qwen2.5-coder:14b",
            "qwen3-coder:30b",
            "deepseek-coder-v2:16b",
            "llama3.1",
            "codellama",
            "gpt-oss:20b");

    private ModelCatalog() {
    }
}