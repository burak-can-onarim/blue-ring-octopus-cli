package com.bcoworks.blueringoctopuscli.model;

import com.bcoworks.blueringoctopuscli.i18n.Messages;
import com.bcoworks.blueringoctopuscli.service.PromptLibrary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiServiceRegistryTest {

    @TempDir
    Path dir;

    private AiServiceRegistry registry(String baseUrl) {
        return new AiServiceRegistry(new ModelSettings("qwen2.5-coder", dir.resolve("models.properties")), new PromptLibrary(),
                new Messages("en"), baseUrl, 0.2, Duration.ofSeconds(1), 8192, 1024);
    }

    @Test
    void aLocalModelOnALocalServerGetsAnAssistant() {
        assertNotNull(registry("http://localhost:11434").forModel("gemma4:26b"));
    }

    @Test
    void aCloudModelNeverGetsAnAssistantWhateverPathAsksForIt() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> registry("http://localhost:11434").forModel("gpt-oss:120b-cloud"));

        assertTrue(e.getMessage().contains("gpt-oss:120b-cloud"), e.getMessage());
    }

    @Test
    void ollamasCloudAddressIsRefusedEvenForALocalModelName() {
        assertThrows(IllegalStateException.class, () -> registry("https://ollama.com").forModel("qwen2.5-coder"));
    }
}
