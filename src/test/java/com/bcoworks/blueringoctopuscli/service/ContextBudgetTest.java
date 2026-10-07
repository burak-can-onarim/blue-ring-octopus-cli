package com.bcoworks.blueringoctopuscli.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextBudgetTest {

    @Test
    void theEstimateGrowsWithTheCode() {
        assertTrue(ContextBudget.estimateTokens("a\nb\nc\n") < ContextBudget.estimateTokens("a\nb\nc\n".repeat(50)));
        assertEquals(0, ContextBudget.estimateTokens(""));
    }

    @Test
    void theEstimateIsCautiousForRealCode() {
        // OrderRepository.java from the measurements: 1,250 characters, 34 lines, about 300 tokens once numbered
        String code = ("x".repeat(36) + "\n").repeat(34);

        assertTrue(ContextBudget.estimateTokens(code) >= 300);
    }

    @Test
    void theBudgetIsTheWindowMinusTheReservedTokens() {
        assertEquals(8192 - ContextBudget.RESERVED_TOKENS, ContextBudget.codeBudget(8192));
        assertEquals(0, ContextBudget.codeBudget(1000));
    }

    @Test
    void aSmallFileFitsAndAHugeOneDoesNot() {
        assertTrue(ContextBudget.fits(8192, "class A {\n}\n"));
        assertFalse(ContextBudget.fits(8192, ("int x = 1;\n").repeat(5_000)));
    }

    @Test
    void aBiggerWindowTakesBiggerFiles() {
        String code = ("String s = \"some line of code\";\n").repeat(600);

        assertFalse(ContextBudget.fits(8192, code));
        assertTrue(ContextBudget.fits(32768, code));
    }
}
