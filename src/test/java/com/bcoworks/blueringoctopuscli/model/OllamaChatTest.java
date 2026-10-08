package com.bcoworks.blueringoctopuscli.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OllamaChatTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private HttpServer server;
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> request = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String reply = "{}";

    @BeforeEach
    void startFakeOllama() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopFakeOllama() {
        server.stop(0);
    }

    private OllamaChat chat(OllamaChat.Think think) {
        String url = "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort() + "/";
        return new OllamaChat(url, "gemma4:26b", 0.2, Duration.ofSeconds(5), 8192, 4096, think);
    }

    private JsonNode sent() throws IOException {
        return JSON.readTree(request.get());
    }

    @Test
    void sendsTheConversationAndTheOptionsToApiChatWithoutStreaming() throws IOException {
        reply = "{\"message\":{\"role\":\"assistant\",\"content\":\"OK\"},\"done_reason\":\"stop\"}";

        chat(OllamaChat.Think.OFF).generate(List.of(SystemMessage.from("be brief"), UserMessage.from("say \"hi\"\nplease")));

        assertEquals("/api/chat", path.get());
        JsonNode body = sent();
        assertEquals("gemma4:26b", body.get("model").asText());
        assertFalse(body.get("stream").asBoolean());
        assertEquals("system", body.get("messages").get(0).get("role").asText());
        assertEquals("be brief", body.get("messages").get(0).get("content").asText());
        assertEquals("user", body.get("messages").get(1).get("role").asText());
        assertEquals("say \"hi\"\nplease", body.get("messages").get(1).get("content").asText());
        assertEquals(8192, body.get("options").get("num_ctx").asInt());
        assertEquals(4096, body.get("options").get("num_predict").asInt());
        assertEquals(0.2, body.get("options").get("temperature").asDouble(), 1e-9);
    }

    @Test
    void aSeedIsSentOnlyWhenGiven() throws IOException {
        reply = "{\"message\":{\"content\":\"x\"}}";
        String url = "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();

        new OllamaChat(url, "m", 0.2, Duration.ofSeconds(5), 8192, 100, OllamaChat.Think.OFF, 7).generate(List.of(UserMessage.from("q")));
        assertEquals(7, sent().get("options").get("seed").asInt());

        chat(OllamaChat.Think.OFF).generate(List.of(UserMessage.from("q")));
        assertFalse(sent().get("options").has("seed"));
    }

    @Test
    void thinkingOffIsSentAsFalse() throws IOException {
        reply = "{\"message\":{\"content\":\"x\"}}";

        chat(OllamaChat.Think.OFF).generate(List.of(UserMessage.from("q")));

        assertTrue(sent().has("think"));
        assertTrue(sent().get("think").isBoolean());
        assertFalse(sent().get("think").asBoolean());
    }

    @Test
    void autoLeavesTheChoiceToTheModelAndALevelIsSentAsText() throws IOException {
        reply = "{\"message\":{\"content\":\"x\"}}";

        chat(OllamaChat.Think.AUTO).generate(List.of(UserMessage.from("q")));
        assertFalse(sent().has("think"));

        chat(OllamaChat.Think.LOW).generate(List.of(UserMessage.from("q")));
        assertEquals("low", sent().get("think").asText());
        chat(OllamaChat.Think.HIGH).generate(List.of(UserMessage.from("q")));
        assertEquals("high", sent().get("think").asText());
    }

    @Test
    void theAnswerIsTheContentAndTheReasoningIsIgnored() {
        reply = "{\"message\":{\"role\":\"assistant\",\"content\":\"the answer\",\"thinking\":\"long reasoning\"},"
                + "\"done_reason\":\"stop\",\"prompt_eval_count\":120,\"eval_count\":30}";

        Response<AiMessage> response = chat(OllamaChat.Think.OFF).generate(List.of(UserMessage.from("q")));

        assertEquals("the answer", response.content().text());
        assertEquals(FinishReason.STOP, response.finishReason());
        assertEquals(120, response.tokenUsage().inputTokenCount());
        assertEquals(30, response.tokenUsage().outputTokenCount());
    }

    @Test
    void anAnswerCutOffByTheTokenLimitIsMarkedAsLengthAndAnEmptyContentStaysEmpty() {
        reply = "{\"message\":{\"role\":\"assistant\",\"content\":\"\",\"thinking\":\"...\"},\"done_reason\":\"length\"}";

        Response<AiMessage> response = chat(OllamaChat.Think.AUTO).generate(List.of(UserMessage.from("q")));

        assertEquals("", response.content().text());
        assertEquals(FinishReason.LENGTH, response.finishReason());
    }

    @Test
    void anErrorOfOllamaBecomesAnExceptionWithItsReason() {
        status = 404;
        reply = "{\"error\":\"model 'nope' not found\"}";

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> chat(OllamaChat.Think.OFF).generate(List.of(UserMessage.from("q"))));

        assertTrue(e.getMessage().contains("404") && e.getMessage().contains("model 'nope' not found"), e.getMessage());
    }

    @Test
    void somethingThatIsNotJsonIsReportedAndAnUnreachableOllamaToo() {
        reply = "<html>bad gateway</html>";
        status = 502;
        assertThrows(IllegalStateException.class, () -> chat(OllamaChat.Think.OFF).generate(List.of(UserMessage.from("q"))));

        OllamaChat nobody = new OllamaChat("http://127.0.0.1:1", "m", 0.2, Duration.ofSeconds(1), 8192, 10, OllamaChat.Think.OFF);
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> nobody.generate(List.of(UserMessage.from("q"))));
        assertTrue(e.getMessage().contains("Cannot reach Ollama"), e.getMessage());
    }

    @Test
    void theSettingIsReadLeniently() {
        assertEquals(OllamaChat.Think.OFF, OllamaChat.Think.parse(null));
        assertEquals(OllamaChat.Think.OFF, OllamaChat.Think.parse(""));
        assertEquals(OllamaChat.Think.OFF, OllamaChat.Think.parse("false"));
        assertEquals(OllamaChat.Think.OFF, OllamaChat.Think.parse("nonsense"));
        assertEquals(OllamaChat.Think.AUTO, OllamaChat.Think.parse(" Auto "));
        assertEquals(OllamaChat.Think.AUTO, OllamaChat.Think.parse("default"));
        assertEquals(OllamaChat.Think.LOW, OllamaChat.Think.parse("LOW"));
        assertEquals(OllamaChat.Think.MEDIUM, OllamaChat.Think.parse("medium"));
        assertEquals(OllamaChat.Think.HIGH, OllamaChat.Think.parse("high"));
    }
}
