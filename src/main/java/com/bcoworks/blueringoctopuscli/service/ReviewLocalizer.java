package com.bcoworks.blueringoctopuscli.service;

import com.bcoworks.blueringoctopuscli.i18n.Messages;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Puts a code review that the model wrote in English into the layout words of the selected language: section titles,
 * severity tags, "line", "Consequence", "Fix" and the "no issues" sentence. This is plain text substitution, so the
 * frame of the review is always right; only the free text between the words is left for the translation step.
 */
final class ReviewLocalizer {

    private static final Pattern FINDING = Pattern.compile(
            "(?m)^([ \\t]*\\d+\\.[ \\t]*)\\[(HIGH|MEDIUM|LOW)][ \\t]*(?:[Ll]ines?[ \\t]+)?(\\d+)");
    private static final Pattern FINDING_LINE = Pattern.compile("(?m)^[ \\t]*\\d+\\.[ \\t]");

    private ReviewLocalizer() {
    }

    static String localize(String english, Messages messages) {
        String text = title(english, "OVERVIEW", messages.get("analysis.title.overview"));
        text = title(text, "FINDINGS", messages.get("analysis.title.findings"));
        text = title(text, "SUMMARY", messages.get("analysis.title.summary"));

        String line = messages.get("analysis.label.line");
        text = FINDING.matcher(text).replaceAll(match -> Matcher.quoteReplacement(
                match.group(1) + "[" + severity(match.group(2), messages) + "] " + line + " " + match.group(3)));

        text = text.replace("Consequence:", messages.get("analysis.label.consequence") + ":");
        text = text.replace("Fix:", messages.get("analysis.label.fix") + ":");
        return text.replace("No significant issues found.", messages.get("analysis.noIssues"));
    }

    /**
     * Did the translation keep the frame? Every title the localized review has must still be there on a line of its
     * own, and the number of numbered findings must be the same. If not, the localized original is shown instead of a
     * broken translation. (A section the English review lacks does not have to appear.)
     */
    static boolean keepsStructure(String localized, String translated, Messages messages) {
        for (String key : new String[]{"analysis.title.overview", "analysis.title.findings", "analysis.title.summary"}) {
            String title = messages.get(key);
            if (hasLine(localized, title) && !hasLine(translated, title)) {
                return false;
            }
        }
        return count(FINDING_LINE, localized) == count(FINDING_LINE, translated);
    }

    private static String severity(String level, Messages messages) {
        return messages.get("analysis.severity." + level.toLowerCase(java.util.Locale.ROOT));
    }

    private static String title(String text, String english, String localized) {
        return Pattern.compile("(?m)^[ \\t]*" + english + "[ \\t]*$").matcher(text)
                .replaceAll(Matcher.quoteReplacement(localized));
    }

    private static boolean hasLine(String text, String line) {
        return Pattern.compile("(?m)^[ \\t]*" + Pattern.quote(line) + "[ \\t]*$").matcher(text).find();
    }

    private static int count(Pattern pattern, String text) {
        int count = 0;
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}
