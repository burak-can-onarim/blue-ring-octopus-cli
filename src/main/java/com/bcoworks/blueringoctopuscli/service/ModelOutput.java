package com.bcoworks.blueringoctopuscli.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helpers around the text a local model returns. Small models often ignore "no Markdown" and sometimes leave their
 * scratch pad in the answer, so the output is cleaned before it is shown.
 */
final class ModelOutput {

    private static final String NOTES_OPEN = "<notes>";
    private static final String NOTES_CLOSE = "</notes>";
    private static final Pattern FENCE_LINE = Pattern.compile("(?m)^[ \\t]*`{3,}[\\w+-]*[ \\t]*\\R?");
    private static final Pattern HEADING_MARK = Pattern.compile("(?m)^[ \\t]{0,3}#{1,6}[ \\t]+");
    private static final Pattern BOLD = Pattern.compile("(\\*\\*|__)(.+?)\\1");
    private static final Pattern BULLET = Pattern.compile("(?m)^([ \\t]*)\\*[ \\t]+");

    private ModelOutput() {
    }

    /**
     * Numbers the lines ("12| code") so the model can point at them and the reader can find them.
     */
    static String numberLines(String code) {
        String[] lines = code.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        int count = lines.length > 0 && lines[lines.length - 1].isEmpty() ? lines.length - 1 : lines.length;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < count; i++) {
            out.append(i + 1).append("| ").append(lines[i]).append('\n');
        }
        return out.toString().stripTrailing();
    }

    /**
     * Removes the model's scratch pad ({@code <notes>...</notes>}). If the model never closed the block, the answer is
     * taken from the line with {@code firstTitle} on; if that is missing too, only the tag is dropped.
     */
    static String stripNotes(String text, String firstTitle) {
        int close = text.lastIndexOf(NOTES_CLOSE);
        if (close >= 0) {
            return text.substring(close + NOTES_CLOSE.length()).strip();
        }
        int open = text.indexOf(NOTES_OPEN);
        if (open < 0) {
            return text.strip();
        }
        Matcher title = Pattern.compile("(?im)^[ \\t]*" + Pattern.quote(firstTitle) + "[ \\t]*$").matcher(text);
        if (title.find(open)) {
            return text.substring(title.start()).strip();
        }
        return (text.substring(0, open) + text.substring(open + NOTES_OPEN.length())).strip();
    }

    /**
     * Turns Markdown the terminal cannot render into plain text: heading marks, bold, inline code, code fences and
     * "*" bullets disappear. Everything else, including indentation, stays.
     */
    static String toPlainText(String text) {
        String result = FENCE_LINE.matcher(text).replaceAll("");
        result = HEADING_MARK.matcher(result).replaceAll("");
        result = BOLD.matcher(result).replaceAll("$2");
        result = BULLET.matcher(result).replaceAll("$1- ");
        result = result.replace("`", "");
        return result.lines().map(String::stripTrailing).reduce((a, b) -> a + "\n" + b).orElse("").strip();
    }
}
