package com.bcoworks.blueringoctopuscli.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * A chat model for Ollama's {@code /api/chat} that can say how a thinking model should think. LangChain4j 0.35 predates
 * Ollama's {@code think} option and cannot send it. That matters: a thinking model (gemma4, qwen3, gpt-oss, ...) first
 * writes its reasoning, and the reasoning counts against {@code num_predict}. On a long prompt such as a code review it
 * can use up all 4,096 tokens and leave the answer empty. With {@link Think#OFF} the same model answers directly (a
 * review by {@code gemma4:26b} found all 12 known problems, against none with thinking on).
 * <p>
 * Only what the application needs is implemented: system and user messages, one answer, no streaming, no tools. Jackson
 * comes with {@code langchain4j-ollama}.
 */
public class OllamaChat implements ChatLanguageModel {

    /**
     * What to send as {@code think}.
     */
    public enum Think {
        /** Not sent: the model decides (a thinking model thinks). */
        AUTO,
        /** {@code think: false}: models that can think answer directly; models that cannot ignore it. */
        OFF,
        /** {@code think: "low"} (gpt-oss only takes levels). */
        LOW,
        MEDIUM,
        HIGH;

        /**
         * The setting from {@code octopus.model.think}; an unknown or empty text means {@link #OFF}.
         */
        public static Think parse(String text) {
            if (text == null || text.isBlank()) {
                return OFF;
            }
            return switch (text.strip().toLowerCase()) {
                case "auto", "default" -> AUTO;
                case "low" -> LOW;
                case "medium" -> MEDIUM;
                case "high" -> HIGH;
                default -> OFF;
            };
        }
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10)).build();
    private final URI chatUri;
    private final String modelName;
    private final double temperature;
    private final Duration timeout;
    private final int numCtx;
    private final int numPredict;
    private final Think think;
    private final Integer seed; // null: Ollama picks one

    public OllamaChat(String baseUrl, String modelName, double temperature, Duration timeout, int numCtx,
                      int numPredict, Think think) {
        this(baseUrl, modelName, temperature, timeout, numCtx, numPredict, think, null);
    }

    /**
     * @param seed makes the answer repeatable (for measurements); null leaves it to Ollama
     */
    public OllamaChat(String baseUrl, String modelName, double temperature, Duration timeout, int numCtx,
                      int numPredict, Think think, Integer seed) {
        this.chatUri = URI.create(baseUrl.strip().replaceAll("/+$", "") + "/api/chat");
        this.modelName = modelName;
        this.temperature = temperature;
        this.timeout = timeout;
        this.numCtx = numCtx;
        this.numPredict = numPredict;
        this.think = think;
        this.seed = seed;
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        HttpRequest request = HttpRequest.newBuilder(chatUri).timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body(messages))).build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return answer(response.statusCode(), response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // a cancelled task: the caller ignores the result
            throw new IllegalStateException("Interrupted while waiting for Ollama", e);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot reach Ollama at " + chatUri + ": " + e.getMessage(), e);
        }
    }

    String body(List<ChatMessage> messages) {
        ObjectNode root = JSON.createObjectNode();
        root.put("model", modelName);
        root.put("stream", false);
        switch (think) {
            case OFF -> root.put("think", false);
            case LOW -> root.put("think", "low");
            case MEDIUM -> root.put("think", "medium");
            case HIGH -> root.put("think", "high");
            case AUTO -> {
            }
        }
        ArrayNode list = root.putArray("messages");
        for (ChatMessage message : messages) {
            ObjectNode entry = list.addObject();
            switch (message) {
                case SystemMessage system -> {
                    entry.put("role", "system");
                    entry.put("content", system.text());
                }
                case UserMessage user -> {
                    entry.put("role", "user");
                    entry.put("content", user.singleText());
                }
                case AiMessage ai -> {
                    entry.put("role", "assistant");
                    entry.put("content", ai.text() == null ? "" : ai.text());
                }
                default -> throw new IllegalArgumentException("Unsupported message: " + message.type());
            }
        }
        ObjectNode options = root.putObject("options");
        options.put("temperature", temperature);
        options.put("num_ctx", numCtx);
        options.put("num_predict", numPredict);
        if (seed != null) {
            options.put("seed", seed);
        }
        return root.toString();
    }

    static Response<AiMessage> answer(int status, String body) {
        JsonNode json;
        try {
            json = JSON.readTree(body);
        } catch (IOException e) {
            throw new IllegalStateException("Ollama answered with something that is not JSON (HTTP " + status + ")", e);
        }
        if (status != 200 || json == null || json.has("error")) {
            String reason = json != null && json.has("error") ? json.get("error").asText() : body;
            throw new IllegalStateException("Ollama answered HTTP " + status + ": " + reason);
        }
        String content = json.path("message").path("content").asText("");
        FinishReason finish = "length".equals(json.path("done_reason").asText("")) ? FinishReason.LENGTH : FinishReason.STOP;
        TokenUsage usage = new TokenUsage(json.path("prompt_eval_count").asInt(0), json.path("eval_count").asInt(0));
        return Response.from(AiMessage.from(content), usage, finish);
    }
}
