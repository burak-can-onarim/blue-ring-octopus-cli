package com.bcoworks.blueringoctopuscli.model;

import com.bcoworks.blueringoctopuscli.context.AppMode;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelCatalogTest {

    @Test
    void everyModeHasASuggestionListWithoutDuplicatesOrBlanks() {
        for (AppMode mode : AppMode.values()) {
            List<String> models = ModelCatalog.suggestedFor(mode);

            assertFalse(models.isEmpty(), mode.name());
            assertEquals(models.size(), new HashSet<>(models).size(), mode + " has duplicates");
            assertTrue(models.stream().noneMatch(String::isBlank), mode.name());
        }
    }

    @Test
    void everyListOffersTheModelThePromptsWereTunedWith() {
        for (AppMode mode : AppMode.values()) {
            assertTrue(ModelCatalog.suggestedFor(mode).contains("qwen2.5-coder"), mode.name());
        }
    }

    @Test
    void theListsDifferByWhatTheModeNeeds() {
        assertEquals("qwen3-coder:30b", ModelCatalog.suggestedFor(AppMode.CODE_GENERATION).getFirst());
        assertEquals("qwen3-coder:30b", ModelCatalog.suggestedFor(AppMode.UNIT_TESTS).getFirst());
        assertEquals("gpt-oss:20b", ModelCatalog.suggestedFor(AppMode.CODE_ANALYSIS).getFirst());
        assertEquals("gpt-oss:20b", ModelCatalog.suggestedFor(AppMode.DOCUMENTATION).getFirst());
    }
}
