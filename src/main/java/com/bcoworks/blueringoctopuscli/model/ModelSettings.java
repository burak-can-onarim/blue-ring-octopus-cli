package com.bcoworks.blueringoctopuscli.model;

import com.bcoworks.blueringoctopuscli.context.AppMode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ModelSettings {

    private final Map<AppMode, String> selected = new ConcurrentHashMap<>();
    private final String defaultModel;
    private final Path file;

    /**
     * Varsayılan: application.yaml (AI_MODEL_NAME ortam değişkeni). Kayıtlı seçim varsa o önceliklidir.
     */
    @Autowired
    public ModelSettings(@Value("${langchain4j.ollama.chat-model.model-name:qwen2.5-coder}") String defaultModel) {
        this(defaultModel, Path.of(System.getProperty("user.home"), ".octopus-cli", "models.properties"));
    }

    ModelSettings(String defaultModel, Path file) {
        this.defaultModel = defaultModel;
        this.file = file;
        load();
    }

    public String modelFor(AppMode mode) {
        return selected.getOrDefault(mode, defaultModel);
    }

    public void select(AppMode mode, String model) {
        if (model == null || model.isBlank()) {
            return;
        }
        selected.put(mode, model.strip());
        save();
    }

    private void load() {
        if (!Files.isRegularFile(file)) {
            return;
        }
        Properties props = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            props.load(reader);
        } catch (IOException e) {
            log.warn("Model ayarları okunamadı: {}", e.getMessage());
            return;
        }
        for (AppMode mode : AppMode.values()) {
            String value = props.getProperty(mode.name());
            if (value != null && !value.isBlank()) {
                selected.put(mode, value.strip());
            }
        }
    }

    private synchronized void save() {
        Properties props = new Properties();
        selected.forEach((mode, model) -> props.setProperty(mode.name(), model));
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                props.store(writer, "Blue Ring Octopus CLI - mod bazli model secimi");
            }
        } catch (IOException e) {
            log.warn("Model ayarları kaydedilemedi: {}", e.getMessage());
        }
    }
}