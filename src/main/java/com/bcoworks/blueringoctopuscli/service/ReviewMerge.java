package com.bcoworks.blueringoctopuscli.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The plain parts of merging the reviews of the parts of a file into one: what the model is given, and a check that
 * the merged review has not lost the findings of the parts.
 */
final class ReviewMerge {

    private static final Pattern SUMMARY_TITLE = Pattern.compile("(?m)^[ \\t]*SUMMARY[ \\t]*$");
    private static final Pattern FINDING_LINE = Pattern.compile("\\[(?:HIGH|MEDIUM|LOW)][ \\t]*(?:[Ll]ines?[ \\t]+)?(\\d+)");
    private static final Pattern NUMBER = Pattern.compile("\\d+");
    /** At least this share of the lines that the parts report must still be mentioned in the merged findings. */
    private static final double MIN_KEPT_LINES = 0.7;

    private ReviewMerge() {
    }

    /**
     * The reviews of the parts one after another, each under the lines it covers and without its summary (the merged
     * review gets its own).
     */
    static String input(List<CodeSplitter.Part> parts, List<String> reviews) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            CodeSplitter.Part part = parts.get(i);
            text.append("=== Part ").append(part.number()).append(" of ").append(part.count())
                    .append(" (lines ").append(part.firstLine()).append('-').append(part.lastLine()).append(") ===\n")
                    .append(withoutSummary(reviews.get(i))).append("\n\n");
        }
        return text.toString().stripTrailing();
    }

    static String withoutSummary(String review) {
        Matcher summary = SUMMARY_TITLE.matcher(review);
        return (summary.find() ? review.substring(0, summary.start()) : review).strip();
    }

    /**
     * True if the merged review has the three sections and still mentions most of the lines the parts reported. A merge
     * that dropped findings is worse than no merge, so the caller shows the parts one by one instead.
     */
    static boolean keepsFindings(List<String> reviews, String merged) {
        if (!hasTitle(merged, "OVERVIEW") || !hasTitle(merged, "FINDINGS") || !hasTitle(merged, "SUMMARY")) {
            return false;
        }
        Set<String> reported = new HashSet<>();
        for (String review : reviews) {
            Matcher finding = FINDING_LINE.matcher(review);
            while (finding.find()) {
                reported.add(finding.group(1));
            }
        }
        if (reported.isEmpty()) {
            return true; // nothing was found in the parts, so nothing can be lost
        }
        Set<String> mentioned = new HashSet<>();
        Matcher number = NUMBER.matcher(findings(merged));
        while (number.find()) {
            mentioned.add(number.group());
        }
        long kept = reported.stream().filter(mentioned::contains).count();
        return kept >= Math.ceil(reported.size() * MIN_KEPT_LINES);
    }

    private static String findings(String review) {
        Matcher title = Pattern.compile("(?m)^[ \\t]*FINDINGS[ \\t]*$").matcher(review);
        if (!title.find()) {
            return review;
        }
        String rest = review.substring(title.end());
        Matcher summary = SUMMARY_TITLE.matcher(rest);
        return summary.find() ? rest.substring(0, summary.start()) : rest;
    }

    private static boolean hasTitle(String text, String title) {
        return Pattern.compile("(?m)^[ \\t]*" + title + "[ \\t]*$").matcher(text).find();
    }
}
