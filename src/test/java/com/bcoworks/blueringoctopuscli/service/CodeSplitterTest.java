package com.bcoworks.blueringoctopuscli.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeSplitterTest {

    private static final String QUOTES = "\"\"\"";

    /** A class whose text is full of braces that are not code. */
    private static final List<String> SAMPLE = sampleLines();

    private static List<String> sampleLines() {
        List<String> lines = new ArrayList<>(List.of(
            "package com.example;",                                            // 1
            "",                                                                // 2
            "import java.util.List;",                                          // 3
            "import java.util.Map;",                                           // 4
            "",                                                                // 5
            "/** A service. */",                                               // 6
            "public class Service {",                                          // 7
            "    private final Map<String, String> cache = Map.of(\"a\", \"}{\");", // 8
            "    private static final char OPEN = '{';",                       // 9
            "    private static final int[] SIZES = {1, 2, 3};",               // 10
            "",                                                                // 11
            "    /** First. */",                                               // 12
            "    public String first(String key) {",                           // 13
            "        // a comment with a } brace",                             // 14
            "        if (key == null) {",                                      // 15
            "            return \"{\";",                                       // 16
            "        }",                                                       // 17
            "        return cache.get(key);",                                  // 18
            "    }",                                                           // 19
            "",                                                                // 20
            "    @SuppressWarnings({\"unchecked\", \"rawtypes\"})",            // 21
            "    public int second(List<String> items) {",                     // 22
            "        int total = 0;",                                          // 23
            "        for (String item : items) {",                             // 24
            "            total += item.length();",                             // 25
            "        }",                                                       // 26
            "        return total;",                                           // 27
            "    }",                                                           // 28
            "",                                                                // 29
            "    static {",                                                    // 30
            "        System.out.println(\"init\");",                           // 31
            "    }",                                                           // 32
            "",                                                                // 33
            "    private final Runnable task = () -> {",                       // 34
            "        System.out.println(\"run\");",                            // 35
            "    };",                                                          // 36
            "",                                                                // 37
            "    public String third() {",                                     // 38
            "        /* } */ String block = " + QUOTES,                        // 39
            "            {",                                                   // 40
            "            " + QUOTES + ";",                                     // 41
            "        return block;",                                           // 42
            "    }"));                                                         // 43
        lines.add("");
        lines.add("    public int fourth(int x) {");                               // 45
        for (int i = 0; i < 50; i++) {
            lines.add("        x += " + (i * 7) + ";");
        }
        lines.add("        return x;");
        lines.add("    }");                                                        // 97
        lines.add("");
        lines.add("    public int padding(int x) {");                              // 99
        for (int i = 0; i < 400; i++) {
            lines.add("        x += " + (i * 7) + ";");
        }
        lines.add("        return x;");
        lines.add("    }");                                                        // 501
        lines.add("}");
        return lines;
    }

    private static String sample() {
        return String.join("\n", SAMPLE) + "\n";
    }

    private static int tokens(String code) {
        return ContextBudget.estimateTokens(code);
    }

    /** The sample takes about 3,000 tokens in one piece; at this budget it needs several parts. */
    private static final int SMALL = 700;

    private static boolean covers(List<CodeSplitter.Part> parts, int line) {
        return parts.stream().anyMatch(part -> part.focus().stream().anyMatch(range -> range[0] <= line && line <= range[1]));
    }

    @Test
    void aFileThatFitsIsOnePartWithEveryMember() {
        List<CodeSplitter.Part> parts = CodeSplitter.split(sample(), 5_000);

        assertEquals(1, parts.size());
        assertEquals(1, parts.get(0).number());
        assertEquals(1, parts.get(0).count());
        assertTrue(parts.get(0).code().contains("18|         return cache.get(key);"));
        assertTrue(parts.get(0).code().contains("42|         return block;"));
    }

    @Test
    void bracesInStringsCharactersCommentsAndTextBlocksAreNotCode() {
        List<CodeSplitter.Part> parts = CodeSplitter.split(sample(), SMALL);

        List<String> names = parts.stream().flatMap(part -> part.names().stream()).distinct().toList();
        assertEquals(List.of("first", "second", "third", "fourth", "padding"), names, "the fields, the static block and the lambda have no name");
        for (int line = 8; line <= 501; line++) {
            if (!SAMPLE.get(line - 1).isBlank()) {
                assertTrue(covers(parts, line), "line " + line + " is in no part");
            }
        }
    }

    @Test
    void everyPartFitsTheBudgetAndAFileThatDoesNotFitBecomesSeveralParts() {
        List<CodeSplitter.Part> parts = CodeSplitter.split(sample(), SMALL);

        assertTrue(parts.size() >= 2, "parts: " + parts.size());
        for (CodeSplitter.Part part : parts) {
            assertTrue(tokens(part.code()) <= SMALL, "part " + part.number() + " has " + tokens(part.code()) + " tokens");
            assertEquals(parts.size(), part.count());
        }
        for (int i = 0; i < parts.size(); i++) {
            assertEquals(i + 1, parts.get(i).number());
        }
    }

    @Test
    void aPartWritesOutItsOwnMethodsAndOnlyOutlinesTheOthersWithTheOriginalLineNumbers() {
        List<CodeSplitter.Part> parts = CodeSplitter.split(sample(), SMALL);

        CodeSplitter.Part withFourth = parts.stream().filter(part -> part.names().contains("fourth")).findFirst().orElseThrow();
        assertEquals(List.of("fourth"), withFourth.names());
        assertTrue(withFourth.code().contains("96|         return x;"), withFourth.code());
        assertTrue(withFourth.code().contains("45|     public int fourth(int x) {"));
        assertTrue(withFourth.code().contains("7| public class Service {"), "the head of the type is the context of every part");
        assertTrue(withFourth.code().contains("13|     public String first(String key) {"), "the other methods show their signature");
        assertTrue(withFourth.code().contains("22|     public int second(List<String> items) {"));
        assertFalse(withFourth.code().contains("return cache.get(key);"), "and nothing of their bodies");
        assertFalse(withFourth.code().contains("total += item.length();"));
        assertTrue(withFourth.code().contains("| ..."), "the gap is marked");

        CodeSplitter.Part withFirst = parts.get(0);
        assertTrue(withFirst.names().containsAll(List.of("first", "second", "third")));
        assertTrue(withFirst.code().contains("18|         return cache.get(key);"));
        assertTrue(withFirst.code().contains("45|     public int fourth(int x) {"), "later methods show their signature too");
        assertFalse(withFirst.code().contains("x += 7;"));
    }

    @Test
    void theFocusOfAPartIsExactlyTheLinesOfItsMembers() {
        List<CodeSplitter.Part> parts = CodeSplitter.split(sample(), SMALL);

        CodeSplitter.Part withSecond = parts.stream().filter(part -> part.names().contains("second")).findFirst().orElseThrow();
        assertTrue(withSecond.focus().stream().anyMatch(range -> range[0] <= 21 && range[1] >= 28),
                "from the annotation to the closing brace: " + withSecond.focusText());
        assertTrue(withSecond.firstLine() <= 21 && withSecond.lastLine() >= 28);
        assertTrue(withSecond.focusText().matches("\\d+(-\\d+)?(, \\d+(-\\d+)?)*"), withSecond.focusText());
    }

    @Test
    void aMemberLargerThanTheWindowIsCutIntoOverlappingPiecesThatKeepTheSignature() {
        List<String> lines = new ArrayList<>(List.of("public class Big {", "    public int huge(int x) {"));
        for (int i = 0; i < 150; i++) {
            lines.add("        x += " + i + ";");
        }
        lines.add("        return x;");
        lines.add("    }");
        lines.add("}");
        String source = String.join("\n", lines);

        List<CodeSplitter.Part> parts = CodeSplitter.split(source, 400);

        assertTrue(parts.size() >= 3, "parts: " + parts.size());
        for (CodeSplitter.Part part : parts) {
            assertTrue(tokens(part.code()) <= 400, "part " + part.number() + " has " + tokens(part.code()) + " tokens");
            assertTrue(part.code().contains("2|     public int huge(int x) {"), "every piece shows the signature");
            assertEquals(List.of("huge"), part.names());
        }
        for (int line = 3; line <= 153; line++) {
            assertTrue(covers(parts, line), "line " + line + " is in no piece");
        }
        assertTrue(parts.get(1).firstLine() < parts.get(0).lastLine(), "the pieces overlap");
    }

    @Test
    void textWithoutAnyStructureIsCutIntoPiecesOfLines() {
        String source = "int value = 1234567;\n".repeat(400);

        List<CodeSplitter.Part> parts = CodeSplitter.split(source, 500);

        assertTrue(parts.size() >= 3);
        for (CodeSplitter.Part part : parts) {
            assertTrue(tokens(part.code()) <= 500);
        }
        for (int line = 1; line <= 400; line++) {
            assertTrue(covers(parts, line), "line " + line);
        }
    }

    @Test
    void aBudgetThatCannotHoldAUsefulPartGivesNoParts() {
        assertTrue(CodeSplitter.split(sample(), 100).isEmpty());
        assertTrue(CodeSplitter.split("", 5_000).isEmpty());
    }

    @Test
    void windowsLineEndingsGiveTheSameParts() {
        List<CodeSplitter.Part> unix = CodeSplitter.split(sample(), SMALL);
        List<CodeSplitter.Part> windows = CodeSplitter.split(sample().replace("\n", "\r\n"), SMALL);

        assertEquals(unix.size(), windows.size());
        assertEquals(unix.get(0).code(), windows.get(0).code());
    }

    @Test
    void aFileWithAnUnclosedBraceStillGetsParts() {
        String source = sample().replace("    }\n}\n", "    }\n");

        List<CodeSplitter.Part> parts = CodeSplitter.split(source, SMALL);

        assertFalse(parts.isEmpty());
        assertTrue(covers(parts, 42));
    }

    @Test
    void severalTypesInOneFileAreAllCovered() {
        List<String> lines = new ArrayList<>(List.of("class A {", "    int a() {", "        return 1;", "    }", "}", "",
                "class B {", "    int b() {", "        return 2;", "    }", "}"));

        List<CodeSplitter.Part> parts = CodeSplitter.split(String.join("\n", lines), 5_000);

        assertEquals(1, parts.size());
        assertEquals(List.of("a", "b"), parts.get(0).names());
        assertTrue(parts.get(0).code().contains("7| class B {"));
    }
}
