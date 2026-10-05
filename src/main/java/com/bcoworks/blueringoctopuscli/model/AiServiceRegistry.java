package com.bcoworks.blueringoctopuscli.model;

import com.bcoworks.blueringoctopuscli.context.AppMode;
import com.bcoworks.blueringoctopuscli.service.ICodeAnalyzerService;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AiServiceRegistry {

    private final Map<String, ICodeAnalyzerService> cache = new ConcurrentHashMap<>();

    private final ModelSettings settings;
    private final String baseUrl;
    private final double temperature;
    private final Duration timeout;

    public AiServiceRegistry(
            ModelSettings settings,
            @Value("${langchain4j.ollama.chat-model.base-url:http://localhost:11434}") String baseUrl,
            @Value("${langchain4j.ollama.chat-model.temperature:0.2}") double temperature,
            @Value("${langchain4j.ollama.chat-model.timeout:5m}") Duration timeout) {
        this.settings = settings;
        this.baseUrl = baseUrl;
        this.temperature = temperature;
        this.timeout = timeout;
    }

    /**
     * O an modda seçili modelin servisi. Bir işlem boyunca bir kez çağırın, model tutarlı kalsın.
     */
    public ICodeAnalyzerService forMode(AppMode mode) {
        return forModel(settings.modelFor(mode));
    }

    public ICodeAnalyzerService forModel(String modelName) {
        return cache.computeIfAbsent(modelName, this::create);
    }

    private ICodeAnalyzerService create(String modelName) {
        return AiServices.builder(ICodeAnalyzerService.class)
                .chatLanguageModel(OllamaChatModel.builder()
                        .baseUrl(baseUrl)
                        .modelName(modelName)
                        .temperature(temperature)
                        .timeout(timeout)
                        .build())
                .build();
    }
}