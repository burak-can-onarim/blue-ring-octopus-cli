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
    void theListsFollowTheMeasurements() {
        assertEquals("gemma4:26b", ModelCatalog.suggestedFor(AppMode.CODE_ANALYSIS).getFirst());
        assertEquals("gemma4:26b", ModelCatalog.suggestedFor(AppMode.CODE_GENERATION).getFirst());
        assertTrue(ModelCatalog.suggestedFor(AppMode.CODE_ANALYSIS).contains("ornith:9b"), "a fast reviewer");
        assertFalse(ModelCatalog.suggestedFor(AppMode.CODE_GENERATION).contains("ornith:9b"), "it writes code that rarely compiles");
        assertFalse(ModelCatalog.suggestedFor(AppMode.CODE_GENERATION).contains("gpt-oss:20b"),
                "it runs out of tokens while it thinks, unless it is told to think less");
    }

    @Test
    void theModesThatDoNotExistYetUseTheListOfTheNearestMeasuredTask() {
        assertEquals(ModelCatalog.suggestedFor(AppMode.CODE_ANALYSIS), ModelCatalog.suggestedFor(AppMode.DOCUMENTATION));
        assertEquals(ModelCatalog.suggestedFor(AppMode.CODE_GENERATION), ModelCatalog.suggestedFor(AppMode.UNIT_TESTS));
    }
}
