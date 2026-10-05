package com.bcoworks.blueringoctopuscli.model;

import com.bcoworks.blueringoctopuscli.context.AppMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelSettingsTest {

    @TempDir
    Path dir;

    private Path file() {
        return dir.resolve("config").resolve("models.properties");
    }

    @Test
    void usesDefaultModelWhenNothingIsSaved() {
        ModelSettings settings = new ModelSettings("qwen2.5-coder", file());

        for (AppMode mode : AppMode.values()) {
            assertEquals("qwen2.5-coder", settings.modelFor(mode));
        }
    }

    @Test
    void selectionIsPerMode() {
        ModelSettings settings = new ModelSettings("qwen2.5-coder", file());

        settings.select(AppMode.KOD_GENERATE, "llama3.1");

        assertEquals("llama3.1", settings.modelFor(AppMode.KOD_GENERATE));
        assertEquals("qwen2.5-coder", settings.modelFor(AppMode.KOD_ANALIZI));
    }

    @Test
    void selectionSurvivesRestart() {
        new ModelSettings("qwen2.5-coder", file()).select(AppMode.KOD_ANALIZI, " codellama ");

        ModelSettings reloaded = new ModelSettings("qwen2.5-coder", file());

        assertEquals("codellama", reloaded.modelFor(AppMode.KOD_ANALIZI));
        assertEquals("qwen2.5-coder", reloaded.modelFor(AppMode.KOD_GENERATE));
    }

    @Test
    void blankOrNullSelectionIsIgnored() {
        ModelSettings settings = new ModelSettings("qwen2.5-coder", file());

        settings.select(AppMode.KOD_ANALIZI, null);
        settings.select(AppMode.KOD_ANALIZI, "   ");

        assertEquals("qwen2.5-coder", settings.modelFor(AppMode.KOD_ANALIZI));
        assertTrue(Files.notExists(file()));
    }

    @Test
    void savedSelectionWinsOverNewDefault() {
        new ModelSettings("qwen2.5-coder", file()).select(AppMode.KOD_ANALIZI, "llama3.1");

        assertEquals("llama3.1", new ModelSettings("baska-model", file()).modelFor(AppMode.KOD_ANALIZI));
    }

    @Test
    void ignoresUnknownKeysAndBlankValuesInFile() throws IOException {
        Files.createDirectories(file().getParent());
        Files.writeString(file(), "ESKI_MOD=x\nKOD_ANALIZI=\nKOD_GENERATE=llama3.1\n", StandardCharsets.UTF_8);

        ModelSettings settings = new ModelSettings("qwen2.5-coder", file());

        assertEquals("qwen2.5-coder", settings.modelFor(AppMode.KOD_ANALIZI));
        assertEquals("llama3.1", settings.modelFor(AppMode.KOD_GENERATE));
    }

    @Test
    void unreadableSettingsFallBackToDefault() throws IOException {
        Files.createDirectories(file().getParent());
        Files.write(file(), new byte[]{(byte) 0xC3, (byte) 0x28}); // geçersiz UTF-8

        ModelSettings settings = new ModelSettings("qwen2.5-coder", file());

        assertEquals("qwen2.5-coder", settings.modelFor(AppMode.KOD_ANALIZI));
    }
}
