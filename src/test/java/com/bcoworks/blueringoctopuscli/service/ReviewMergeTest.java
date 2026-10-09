package com.bcoworks.blueringoctopuscli.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewMergeTest {

    private static final String PART_ONE = """
            OVERVIEW
            It reads orders.

            FINDINGS
            1. [HIGH] line 17 - SQL injection. Consequence: data theft. Fix: use a PreparedStatement.
            2. [MEDIUM] line 21 - empty catch block. Consequence: errors are hidden. Fix: log it.

            SUMMARY
            Do not use it.""";

    private static final String PART_TWO = """
            OVERVIEW
            It computes averages.

            FINDINGS
            1. [HIGH] line 31 - division by zero. Consequence: NaN. Fix: check isEmpty().
            2. [LOW] line 40 - hard-coded password. Consequence: leak. Fix: use the environment.

            SUMMARY
            Fix the division.""";

    private static CodeSplitter.Part part(int number, int count, int first, int last) {
        return new CodeSplitter.Part(number, count, first, last, List.of(new int[]{first, last}), List.of(), "code");
    }

    @Test
    void theInputListsTheReviewsUnderTheLinesTheyCoverWithoutTheirSummaries() {
        String input = ReviewMerge.input(List.of(part(1, 2, 8, 24), part(2, 2, 26, 44)), List.of(PART_ONE, PART_TWO));

        assertTrue(input.startsWith("=== Part 1 of 2 (lines 8-24) ==="), input);
        assertTrue(input.contains("=== Part 2 of 2 (lines 26-44) ==="));
        assertTrue(input.contains("[HIGH] line 17 - SQL injection"));
        assertTrue(input.contains("[LOW] line 40 - hard-coded password"));
        assertFalse(input.contains("Do not use it."), "the summaries are left out");
        assertFalse(input.contains("Fix the division."));
        assertFalse(input.contains("SUMMARY"));
    }

    @Test
    void aReviewWithoutASummaryIsTakenAsItIs() {
        assertEquals("No significant issues found.", ReviewMerge.withoutSummary("No significant issues found."));
    }

    @Test
    void aMergedReviewThatKeepsTheLinesIsAccepted() {
        String merged = """
                OVERVIEW
                It reads orders and computes averages.

                FINDINGS
                1. [HIGH] line 17 - SQL injection. Consequence: data theft. Fix: use a PreparedStatement.
                2. [HIGH] line 31 - division by zero. Consequence: NaN. Fix: check isEmpty().
                3. [MEDIUM] line 21 - empty catch block. Consequence: errors are hidden. Fix: log it.
                4. [LOW] line 40 - hard-coded password. Consequence: leak. Fix: use the environment.

                SUMMARY
                Do not use it as it is.""";

        assertTrue(ReviewMerge.keepsFindings(List.of(PART_ONE, PART_TWO), merged));
    }

    @Test
    void joiningDuplicatesIsFineAsLongAsTheirLinesAreStillNamed() {
        String reviewThree = PART_ONE.replace("line 21", "line 18").replace("empty catch block", "SQL injection again");
        String merged = """
                OVERVIEW
                x

                FINDINGS
                1. [HIGH] lines 17 and 18 - SQL injection. Consequence: data theft. Fix: use a PreparedStatement.
                2. [HIGH] line 31 - division by zero.
                3. [LOW] line 40 - hard-coded password.

                SUMMARY
                x""";

        assertTrue(ReviewMerge.keepsFindings(List.of(PART_ONE, PART_TWO, reviewThree), merged));
    }

    @Test
    void aMergeThatDroppedMostFindingsIsRefused() {
        String merged = """
                OVERVIEW
                x

                FINDINGS
                1. [HIGH] line 17 - SQL injection.

                SUMMARY
                x""";

        assertFalse(ReviewMerge.keepsFindings(List.of(PART_ONE, PART_TWO), merged));
    }

    @Test
    void aMergeWithoutTheThreeSectionsIsRefused() {
        assertFalse(ReviewMerge.keepsFindings(List.of(PART_ONE, PART_TWO), "Here is the merged review: ..."));
        assertFalse(ReviewMerge.keepsFindings(List.of(PART_ONE, PART_TWO), "OVERVIEW\nx\n\nFINDINGS\n1. [HIGH] line 17 - x"));
    }

    @Test
    void withFewFindingsToCheckTheStructureIsEnough() {
        String clean = "OVERVIEW\nIt is fine.\n\nFINDINGS\nNo significant issues found.\n\nSUMMARY\nNothing to change.";

        assertTrue(ReviewMerge.keepsFindings(List.of("No significant issues found.", "OVERVIEW\nx\n\nFINDINGS\nNo significant issues found."), clean));
    }
}
