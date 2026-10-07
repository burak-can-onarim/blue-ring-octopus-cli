package com.bcoworks.blueringoctopuscli.model;

import com.bcoworks.blueringoctopuscli.context.AppMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

        settings.select(AppMode.CODE_GENERATION, "llama3.1");

        assertEquals("llama3.1", settings.modelFor(AppMode.CODE_GENERATION));
        assertEquals("qwen2.5-coder", settings.modelFor(AppMode.CODE_ANALYSIS));
    }

    @Test
    void selectionSurvivesRestart() {
        new ModelSettings("qwen2.5-coder", file()).select(AppMode.CODE_ANALYSIS, " codellama ");

        ModelSettings reloaded = new ModelSettings("qwen2.5-coder", file());

        assertEquals("codellama", reloaded.modelFor(AppMode.CODE_ANALYSIS));
        assertEquals("qwen2.5-coder", reloaded.modelFor(AppMode.CODE_GENERATION));
    }

    @Test
    void blankOrNullSelectionIsIgnored() {
        ModelSettings settings = new ModelSettings("qwen2.5-coder", file());

        settings.select(AppMode.CODE_ANALYSIS, null);
        settings.select(AppMode.CODE_ANALYSIS, "   ");

        assertEquals("qwen2.5-coder", settings.modelFor(AppMode.CODE_ANALYSIS));
        assertTrue(Files.notExists(file()));
    }

    @Test
    void savedSelectionWinsOverNewDefault() {
        new ModelSettings("qwen2.5-coder", file()).select(AppMode.CODE_ANALYSIS, "llama3.1");

        assertEquals("llama3.1", new ModelSettings("baska-model", file()).modelFor(AppMode.CODE_ANALYSIS));
    }

    @Test
    void ignoresUnknownKeysAndBlankValuesInFile() throws IOException {
        Files.createDirectories(file().getParent());
        Files.writeString(file(), "UNKNOWN_MODE=x\nCODE_ANALYSIS=\nCODE_GENERATION=llama3.1\n", StandardCharsets.UTF_8);

        ModelSettings settings = new ModelSettings("qwen2.5-coder", file());

        assertEquals("qwen2.5-coder", settings.modelFor(AppMode.CODE_ANALYSIS));
        assertEquals("llama3.1", settings.modelFor(AppMode.CODE_GENERATION));
    }

    @Test
    void readsChoicesSavedUnderTheOldTurkishModeNames() throws IOException {
        Files.createDirectories(file().getParent());
        Files.writeString(file(), "KOD_ANALIZI=llama3.1\nKOD_GENERATE=codellama\nDOKUMAN_HAZIRLAMA=a\nBIRIM_TEST=b\n",
                StandardCharsets.UTF_8);

        ModelSettings settings = new ModelSettings("qwen2.5-coder", file());

        assertEquals("llama3.1", settings.modelFor(AppMode.CODE_ANALYSIS));
        assertEquals("codellama", settings.modelFor(AppMode.CODE_GENERATION));
        assertEquals("a", settings.modelFor(AppMode.DOCUMENTATION));
        assertEquals("b", settings.modelFor(AppMode.UNIT_TESTS));
    }

    @Test
    void aChoiceUnderTheCurrentNameBeatsTheOldName() throws IOException {
        Files.createDirectories(file().getParent());
        Files.writeString(file(), "KOD_ANALIZI=old\nCODE_ANALYSIS=new\n", StandardCharsets.UTF_8);

        assertEquals("new", new ModelSettings("qwen2.5-coder", file()).modelFor(AppMode.CODE_ANALYSIS));
    }

    @Test
    void savingWritesTheCurrentNames() throws IOException {
        Files.createDirectories(file().getParent());
        Files.writeString(file(), "KOD_ANALIZI=llama3.1\n", StandardCharsets.UTF_8);
        ModelSettings settings = new ModelSettings("qwen2.5-coder", file());

        settings.select(AppMode.CODE_GENERATION, "codellama");

        String saved = Files.readString(file());
        assertTrue(saved.contains("CODE_ANALYSIS=llama3.1"), saved);
        assertTrue(saved.contains("CODE_GENERATION=codellama"), saved);
        assertFalse(saved.contains("KOD_"), saved);
    }

    @Test
    void unreadableSettingsFallBackToDefault() throws IOException {
        Files.createDirectories(file().getParent());
        Files.write(file(), new byte[]{(byte) 0xC3, (byte) 0x28}); // geçersiz UTF-8

        ModelSettings settings = new ModelSettings("qwen2.5-coder", file());

        assertEquals("qwen2.5-coder", settings.modelFor(AppMode.CODE_ANALYSIS));
    }
}
