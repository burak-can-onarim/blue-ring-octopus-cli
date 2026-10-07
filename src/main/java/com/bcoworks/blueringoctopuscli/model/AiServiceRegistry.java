package com.bcoworks.blueringoctopuscli.model;

import com.bcoworks.blueringoctopuscli.context.AppMode;
import com.bcoworks.blueringoctopuscli.i18n.Messages;
import com.bcoworks.blueringoctopuscli.service.CodeAssistant;
import com.bcoworks.blueringoctopuscli.service.PromptLibrary;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AiServiceRegistry {

    private final Map<String, CodeAssistant> cache = new ConcurrentHashMap<>();

    private final ModelSettings settings;
    private final PromptLibrary prompts;
    private final Messages messages;
    private final String baseUrl;
    private final double temperature;
    private final Duration timeout;
    private final int numCtx;
    private final int maxTokens;

    public AiServiceRegistry(
            ModelSettings settings,
            PromptLibrary prompts,
            Messages messages,
            @Value("${langchain4j.ollama.chat-model.base-url:http://localhost:11434}") String baseUrl,
            @Value("${langchain4j.ollama.chat-model.temperature:0.2}") double temperature,
            @Value("${langchain4j.ollama.chat-model.timeout:5m}") Duration timeout,
            @Value("${octopus.model.num-ctx:8192}") int numCtx,
            @Value("${octopus.model.max-tokens:4096}") int maxTokens) {
        this.settings = settings;
        this.prompts = prompts;
        this.messages = messages;
        this.baseUrl = baseUrl;
        this.temperature = temperature;
        this.timeout = timeout;
        this.numCtx = numCtx;
        this.maxTokens = maxTokens;
    }

    /**
     * The assistant for the model chosen for the mode. Call it once per task so the model stays the same throughout.
     */
    public CodeAssistant forMode(AppMode mode) {
        return forModel(settings.modelFor(mode));
    }

    public CodeAssistant forModel(String modelName) {
        return cache.computeIfAbsent(modelName, this::create);
    }

    /**
     * The context window (in tokens) the model is asked to use. Ollama's own default is much smaller and cuts long
     * prompts without a word.
     */
    public int numCtx() {
        return numCtx;
    }

    private CodeAssistant create(String modelName) {
        OllamaChatModel model = OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(temperature)
                .timeout(timeout)
                .numCtx(numCtx)
                .numPredict(maxTokens)
                .build();
        return new CodeAssistant(model, prompts, messages);
    }
}
