package com.bcoworks.blueringoctopuscli.model;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstalledModelsTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String serve(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/api/tags", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return "http://localhost:" + server.getAddress().getPort();
    }

    @Test
    void nothingIsCheckedBeforeFirstRefresh() {
        InstalledModels models = new InstalledModels("http://localhost:1");

        assertFalse(models.isChecked());
        assertFalse(models.isKnown());
        assertTrue(models.names().isEmpty());
    }

    @Test
    void parsesModelNamesFromOllamaResponse() throws IOException {
        String body = "{\"models\":[{\"name\":\"qwen2.5-coder:latest\",\"model\":\"qwen2.5-coder:latest\"},"
                + "{\"name\":\"llama3.1:8b\",\"model\":\"llama3.1:8b\"}]}";
        InstalledModels models = new InstalledModels(serve(200, body) + "/");

        models.refresh();

        assertTrue(models.isChecked());
        assertTrue(models.isKnown());
        assertEquals(List.of("qwen2.5-coder:latest", "llama3.1:8b"), models.names());
    }

    @Test
    void installedCheckTreatsMissingTagAsLatest() throws IOException {
        InstalledModels models = new InstalledModels(serve(200, "{\"models\":[{\"name\":\"qwen2.5-coder:latest\"}]}"));

        models.refresh();

        assertTrue(models.isInstalled("qwen2.5-coder"));
        assertTrue(models.isInstalled("qwen2.5-coder:latest"));
        assertFalse(models.isInstalled("qwen2.5-coder:14b"));
        assertFalse(models.isInstalled("llama3.1"));
    }

    @Test
    void emptyModelListIsKnownButEmpty() throws IOException {
        InstalledModels models = new InstalledModels(serve(200, "{\"models\":[]}"));

        models.refresh();

        assertTrue(models.isKnown());
        assertTrue(models.names().isEmpty());
        assertFalse(models.isInstalled("llama3.1"));
    }

    @Test
    void serverErrorMeansUnknown() throws IOException {
        InstalledModels models = new InstalledModels(serve(500, "hata"));

        models.refresh();

        assertTrue(models.isChecked());
        assertFalse(models.isKnown());
    }

    @Test
    void unreachableServerMeansUnknown() throws IOException {
        int freePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            freePort = socket.getLocalPort();
        }
        InstalledModels models = new InstalledModels("http://localhost:" + freePort);

        models.refresh();

        assertTrue(models.isChecked());
        assertFalse(models.isKnown());
        assertTrue(models.names().isEmpty());
    }

    @Test
    void normalizeAddsLatestOnlyWhenTagIsMissing() {
        assertEquals("llama3.1:latest", InstalledModels.normalize("llama3.1"));
        assertEquals("llama3.1:8b", InstalledModels.normalize("llama3.1:8b"));
    }

    @Test
    void displayHidesLatestSuffix() {
        assertEquals("llama3.1", InstalledModels.display("llama3.1:latest"));
        assertEquals("llama3.1:8b", InstalledModels.display("llama3.1:8b"));
        assertEquals("llama3.1", InstalledModels.display("llama3.1"));
    }
}
