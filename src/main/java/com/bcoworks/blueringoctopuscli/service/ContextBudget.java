package com.bcoworks.blueringoctopuscli.service;

/**
 * How much code fits into the model's context window. Ollama silently cuts a prompt that is too long, so a big file
 * would be reviewed from a fragment. The numbers are deliberately cautious: it is better to skip a file with a clear
 * message than to analyse part of it.
 */
public final class ContextBudget {

    /**
     * Tokens kept free for the instructions (about 1,300 with the labels), the model's notes and its answer.
     */
    static final int RESERVED_TOKENS = 3_000;
    private static final double CHARS_PER_TOKEN = 3.5;
    private static final int TOKENS_PER_LINE = 1; // the line number in front of each line

    private ContextBudget() {
    }

    /**
     * A cautious guess of how many tokens the code takes once its lines are numbered.
     */
    public static int estimateTokens(String code) {
        long lines = code.lines().count();
        return (int) Math.ceil(code.length() / CHARS_PER_TOKEN) + (int) lines * TOKENS_PER_LINE;
    }

    /**
     * Tokens available for code in a window of numCtx tokens.
     */
    public static int codeBudget(int numCtx) {
        return Math.max(0, numCtx - RESERVED_TOKENS);
    }

    public static boolean fits(int numCtx, String code) {
        return estimateTokens(code) <= codeBudget(numCtx);
    }
}
