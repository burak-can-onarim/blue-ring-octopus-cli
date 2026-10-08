package com.bcoworks.blueringoctopuscli.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cuts a Java file that does not fit the model's window into parts, one or a few members (methods, fields, nested
 * types) at a time, so that it can be reviewed instead of skipped.
 * <p>
 * Every part shows the whole file in outline: the imports, the heads of the types and the signature of every member
 * whose body is not in this part ("| ..." marks what was left out). Only the members of the part are written out in
 * full. The model therefore sees the fields and the other methods a method works with, and every line keeps its
 * original number, so a finding points at the right line of the file. A single member that is larger than the window
 * is cut into overlapping pieces.
 * <p>
 * The structure is found with a small scanner that skips comments, strings, characters and text blocks and follows the
 * braces. It does not parse Java, so it also works on a file that does not compile; on something without any braces
 * it falls back to plain pieces of lines.
 */
public final class CodeSplitter {

    /**
     * One part of a file.
     *
     * @param number    1-based
     * @param count     how many parts the file has
     * @param firstLine first line of the code the part asks to review
     * @param lastLine  last line of the code the part asks to review
     * @param focus     the line ranges ({first, last}) to review; everything else is context
     * @param names     the names of the methods and types in the part
     * @param code      the part as the model sees it: numbered lines, "| ..." where code was left out
     */
    public record Part(int number, int count, int firstLine, int lastLine, List<int[]> focus, List<String> names,
                       String code) {

        /**
         * The ranges to review as text, for example "120-210, 230-245".
         */
        public String focusText() {
            List<String> ranges = new ArrayList<>();
            for (int[] range : focus) {
                ranges.add(range[0] == range[1] ? String.valueOf(range[0]) : range[0] + "-" + range[1]);
            }
            return String.join(", ", ranges);
        }
    }

    private static final double CHARS_PER_TOKEN = 3.5; // the same cautious guess as ContextBudget
    private static final int MARKER_TOKENS = 14;
    private static final int OVERLAP_LINES = 8;
    private static final int MIN_FOCUS_TOKENS = 200;
    private static final double MAX_OUTLINE_SHARE = 0.5;
    private static final String CONTINUES_AFTER_BRACE = ";,).]";
    private static final Pattern ANNOTATION = Pattern.compile("@\\w+(?:\\.\\w+)*(?:\\([^)]*\\))?");
    private static final Pattern METHOD_NAME = Pattern.compile("(\\w+)\\s*\\(");
    private static final Pattern TYPE_NAME = Pattern.compile("\\b(?:class|interface|enum|record)\\s+(\\w+)");

    private final String[] numbered;      // index = line number; "12| code"
    private final boolean[] blank;        // index = line number
    private final int[] nonBlankBefore;   // nonBlankBefore[n] = non-blank lines among 1..n
    private final int lineCount;

    private CodeSplitter(String[] lines, int lineCount) {
        this.lineCount = lineCount;
        numbered = new String[lineCount + 2];
        blank = new boolean[lineCount + 2];
        nonBlankBefore = new int[lineCount + 2];
        for (int n = 1; n <= lineCount; n++) {
            numbered[n] = n + "| " + lines[n - 1];
            blank[n] = lines[n - 1].isBlank();
            nonBlankBefore[n] = nonBlankBefore[n - 1] + (blank[n] ? 0 : 1);
        }
        nonBlankBefore[lineCount + 1] = nonBlankBefore[lineCount];
    }

    /**
     * Splits {@code source} into parts that each fit {@code tokenBudget} tokens (estimated as ContextBudget does).
     * Returns an empty list if even the smallest useful part would not fit.
     */
    public static List<Part> split(String source, int tokenBudget) {
        String text = source.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = text.split("\n", -1);
        int count = lines.length > 0 && lines[lines.length - 1].isEmpty() ? lines.length - 1 : lines.length;
        if (count == 0 || tokenBudget < MIN_FOCUS_TOKENS) {
            return List.of();
        }
        CodeSplitter splitter = new CodeSplitter(lines, count);
        return splitter.cut(Outline.of(text, lines, count), tokenBudget);
    }

    // ---- packing ------------------------------------------------------------------------------------------------

    private List<Part> cut(Outline outline, int budget) {
        TreeSet<Integer> tiny = outline.base(this, Level.TYPE_HEADS);
        if (budget - cost(tiny) < MIN_FOCUS_TOKENS) {
            return List.of();
        }
        List<TreeSet<Integer>> outlines = List.of(outline.base(this, Level.FULL), outline.base(this, Level.NO_IMPORTS), tiny);
        TreeSet<Integer> base = tiny;
        for (TreeSet<Integer> candidate : outlines) {
            if (cost(candidate) <= budget * MAX_OUTLINE_SHARE) {
                base = candidate;
                break;
            }
        }

        List<Draft> drafts = new ArrayList<>();
        TreeSet<Integer> shown = new TreeSet<>(base);
        List<Member> chunk = new ArrayList<>();
        for (Member member : outline.members()) {
            TreeSet<Integer> grown = new TreeSet<>(shown);
            grown.addAll(member.expanded(this));
            if (cost(grown) <= budget) {
                shown = grown;
                chunk.add(member);
                continue;
            }
            if (!chunk.isEmpty()) {
                drafts.add(new Draft(shown, rangesOf(chunk), namesOf(chunk)));
                shown = new TreeSet<>(base);
                chunk = new ArrayList<>();
            }
            grown = new TreeSet<>(shown);
            grown.addAll(member.expanded(this));
            if (cost(grown) <= budget) {
                shown = grown;
                chunk.add(member);
            } else if (!alone(member, outlines, budget, drafts) && !windows(member, tiny, budget, drafts)) {
                return List.of();
            }
        }
        if (!chunk.isEmpty()) {
            drafts.add(new Draft(shown, rangesOf(chunk), namesOf(chunk)));
        }

        List<Part> parts = new ArrayList<>();
        for (int i = 0; i < drafts.size(); i++) {
            Draft draft = drafts.get(i);
            int first = draft.focus().get(0)[0];
            int last = draft.focus().get(draft.focus().size() - 1)[1];
            parts.add(new Part(i + 1, drafts.size(), first, last, draft.focus(), draft.names(), render(draft.shown())));
        }
        return parts;
    }

    /**
     * A member that is too big for the usual outline gets a part of its own with the richest outline that still fits.
     */
    private boolean alone(Member member, List<TreeSet<Integer>> outlines, int budget, List<Draft> drafts) {
        for (TreeSet<Integer> outline : outlines) {
            TreeSet<Integer> shown = new TreeSet<>(outline);
            shown.addAll(member.expanded(this));
            if (cost(shown) <= budget) {
                drafts.add(new Draft(shown, rangesOf(List.of(member)), namesOf(List.of(member))));
                return true;
            }
        }
        return false;
    }

    /**
     * A member that does not fit on its own: overlapping pieces of its lines, each with the outline of the file and the
     * signature of the member.
     */
    private boolean windows(Member member, TreeSet<Integer> base, int budget, List<Draft> drafts) {
        TreeSet<Integer> context = new TreeSet<>(base);
        context.addAll(member.signature(this));
        List<Integer> own = new ArrayList<>();
        for (int n = member.start(); n <= member.end(); n++) {
            if (!blank[n]) {
                own.add(n);
            }
        }
        int from = 0;
        while (from < own.size()) {
            TreeSet<Integer> shown = new TreeSet<>(context);
            int to = from;
            while (to < own.size()) {
                shown.add(own.get(to));
                if (cost(shown) > budget) {
                    shown.remove(own.get(to));
                    break;
                }
                to++;
            }
            if (to == from) {
                return false;
            }
            List<int[]> focus = List.of(new int[]{own.get(from), own.get(to - 1)});
            drafts.add(new Draft(shown, focus, member.name() == null ? List.of() : List.of(member.name())));
            from = to >= own.size() ? own.size() : Math.max(from + 1, to - OVERLAP_LINES);
        }
        return true;
    }

    private static List<int[]> rangesOf(List<Member> members) {
        List<int[]> ranges = new ArrayList<>();
        for (Member member : members) {
            int[] last = ranges.isEmpty() ? null : ranges.get(ranges.size() - 1);
            if (last != null && member.start() <= last[1] + 1) {
                last[1] = Math.max(last[1], member.end());
            } else {
                ranges.add(new int[]{member.start(), member.end()});
            }
        }
        return ranges;
    }

    private static List<String> namesOf(List<Member> members) {
        return members.stream().map(Member::name).filter(name -> name != null).toList();
    }

    private record Draft(TreeSet<Integer> shown, List<int[]> focus, List<String> names) {
    }

    // ---- cost and rendering -------------------------------------------------------------------------------------

    private boolean hasHidden(int after, int before) {
        return before - 1 > after && nonBlankBefore[before - 1] - nonBlankBefore[after] > 0;
    }

    private double cost(Collection<Integer> shown) {
        double tokens = 0;
        int previous = 0;
        for (int n : shown) {
            tokens += (numbered[n].length() + 1) / CHARS_PER_TOKEN + 1;
            if (hasHidden(previous, n)) {
                tokens += MARKER_TOKENS;
            }
            previous = n;
        }
        return Math.ceil(tokens);
    }

    private String render(TreeSet<Integer> shown) {
        StringBuilder out = new StringBuilder();
        int previous = 0;
        for (int n : shown) {
            if (hasHidden(previous, n)) {
                out.append("   | ... (lines ").append(previous + 1).append('-').append(n - 1).append(" not shown)\n");
            }
            out.append(numbered[n]).append('\n');
            previous = n;
        }
        return out.toString().stripTrailing();
    }

    private TreeSet<Integer> nonBlank(int from, int to) {
        TreeSet<Integer> lines = new TreeSet<>();
        for (int n = Math.max(1, from); n <= Math.min(lineCount, to); n++) {
            if (!blank[n]) {
                lines.add(n);
            }
        }
        return lines;
    }

    // ---- structure ----------------------------------------------------------------------------------------------

    private enum Level {
        /** Imports, type heads, every member's signature. */
        FULL,
        /** The same without the imports. */
        NO_IMPORTS,
        /** Only the heads and the closing braces of the types. */
        TYPE_HEADS
    }

    /**
     * A method, field, initializer or nested type.
     *
     * @param start     first line, including the comment above it
     * @param firstCode first line that is code (an annotation counts)
     * @param brace     line of the brace that opens its body, or 0 if it has none
     * @param end       last line
     */
    private record Member(int start, int firstCode, int brace, int end, String name) {

        TreeSet<Integer> expanded(CodeSplitter splitter) {
            return splitter.nonBlank(start, end);
        }

        /** The lines a collapsed member keeps: its signature and its closing line. */
        TreeSet<Integer> collapsed(CodeSplitter splitter) {
            if (brace == 0 || end - firstCode <= 2) {
                return splitter.nonBlank(firstCode, end);
            }
            TreeSet<Integer> lines = signature(splitter);
            lines.add(end);
            return lines;
        }

        TreeSet<Integer> signature(CodeSplitter splitter) {
            return splitter.nonBlank(firstCode, brace == 0 ? firstCode : brace);
        }
    }

    private record TypeBlock(int firstCode, int open, int close, List<Member> members) {
    }

    private static final class Event {
        final char kind; // '{' '}' ';'
        final int line;
        final int depth; // for '{': before it opens; for '}': after it closes
        char next;       // for '}': the next character of code

        Event(char kind, int line, int depth) {
            this.kind = kind;
            this.line = line;
            this.depth = depth;
        }
    }

    private record Outline(int headerEnd, List<TypeBlock> types) {

        List<Member> members() {
            return types.stream().flatMap(type -> type.members().stream()).toList();
        }

        TreeSet<Integer> base(CodeSplitter splitter, Level level) {
            TreeSet<Integer> shown = new TreeSet<>();
            if (level == Level.FULL) {
                shown.addAll(splitter.nonBlank(1, headerEnd));
            }
            for (TypeBlock type : types) {
                shown.addAll(splitter.nonBlank(type.firstCode(), type.open()));
                if (type.close() > 0) {
                    shown.add(type.close());
                }
                if (level != Level.TYPE_HEADS) {
                    for (Member member : type.members()) {
                        shown.addAll(member.collapsed(splitter));
                    }
                }
            }
            return shown;
        }

        static Outline of(String text, String[] lines, int lineCount) {
            boolean[] hasCode = new boolean[lineCount + 2];
            List<Event> events = scan(text, hasCode);
            List<TypeBlock> types = new ArrayList<>();
            int headerEnd = 0;
            int top = 0; // last line of what precedes the next type
            for (int i = 0; i < events.size(); i++) {
                Event event = events.get(i);
                if (event.kind == ';' && event.depth == 0 && types.isEmpty()) {
                    headerEnd = event.line;
                    top = event.line;
                } else if (event.kind == '{' && event.depth == 0) {
                    int close = i + 1;
                    while (close < events.size() && !(events.get(close).kind == '}' && events.get(close).depth == 0)) {
                        close++;
                    }
                    int closeLine = close < events.size() ? events.get(close).line : lineCount;
                    int firstCode = firstCodeLine(hasCode, top + 1, event.line);
                    types.add(new TypeBlock(firstCode, event.line, close < events.size() ? closeLine : 0,
                            members(events, i + 1, close, event.line, closeLine, hasCode, lines)));
                    top = closeLine;
                    i = close;
                }
            }
            if (types.isEmpty()) { // no structure found: the whole text is one big member
                int first = firstCodeLine(hasCode, 1, lineCount);
                types.add(new TypeBlock(0, 0, 0, List.of(new Member(1, first, 0, lineCount, null))));
                headerEnd = 0;
            }
            return new Outline(headerEnd, types);
        }

        private static List<Member> members(List<Event> events, int from, int to, int openLine, int closeLine,
                                            boolean[] hasCode, String[] lines) {
            List<Member> members = new ArrayList<>();
            int previousEnd = openLine;
            int brace = 0;
            for (int i = from; i < to; i++) {
                Event event = events.get(i);
                if (event.kind == '{' && event.depth == 1 && brace == 0) {
                    brace = event.line;
                }
                if (event.kind == '}' && event.depth == 1 && ")],".indexOf(event.next) >= 0) {
                    brace = 0; // the braces of an annotation value, not of a body
                }
                boolean ends = event.depth == 1
                        && (event.kind == ';' || (event.kind == '}' && CONTINUES_AFTER_BRACE.indexOf(event.next) < 0));
                if (ends) {
                    addMember(members, previousEnd, event.line, brace, hasCode, lines);
                    previousEnd = event.line;
                    brace = 0;
                }
            }
            if (closeLine - 1 > previousEnd) { // what is left before the closing brace
                addMember(members, previousEnd, closeLine - 1, brace, hasCode, lines);
            }
            return members;
        }

        private static void addMember(List<Member> members, int previousEnd, int end, int brace, boolean[] hasCode,
                                      String[] lines) {
            int from = previousEnd + 1;
            if (end < from) {
                from = end; // a member that starts on the line where the previous one ended
            }
            int firstCode = firstCodeLine(hasCode, from, end);
            if (firstCode == 0) {
                return; // only blank lines or comments
            }
            int start = from;
            while (start < firstCode && lines[start - 1].isBlank()) {
                start++;
            }
            int signatureEnd = brace == 0 ? Math.min(end, firstCode + 3) : brace;
            members.add(new Member(start, firstCode, brace, end, nameOf(lines, firstCode, signatureEnd)));
        }

        private static int firstCodeLine(boolean[] hasCode, int from, int to) {
            for (int n = Math.max(1, from); n <= Math.min(to, hasCode.length - 1); n++) {
                if (hasCode[n]) {
                    return n;
                }
            }
            return 0;
        }

        private static String nameOf(String[] lines, int from, int to) {
            StringBuilder head = new StringBuilder();
            for (int n = from; n <= to && n <= lines.length; n++) {
                head.append(lines[n - 1]).append(' ');
            }
            String text = ANNOTATION.matcher(head).replaceAll(" ");
            Matcher type = TYPE_NAME.matcher(text);
            if (type.find()) {
                return type.group(1);
            }
            Matcher method = METHOD_NAME.matcher(text);
            return method.find() && !text.substring(0, method.start()).contains("=") ? method.group(1) : null;
        }

        /**
         * The braces and semicolons of the code, with comments, strings, characters and text blocks left out.
         */
        private static List<Event> scan(String t, boolean[] hasCode) {
            List<Event> events = new ArrayList<>();
            int n = t.length();
            int line = 1;
            int depth = 0;
            Event pendingClose = null;
            int i = 0;
            while (i < n) {
                char c = t.charAt(i);
                if (c == '\n') {
                    line++;
                    i++;
                } else if (Character.isWhitespace(c)) {
                    i++;
                } else if (c == '/' && i + 1 < n && t.charAt(i + 1) == '/') {
                    while (i < n && t.charAt(i) != '\n') {
                        i++;
                    }
                } else if (c == '/' && i + 1 < n && t.charAt(i + 1) == '*') {
                    i += 2;
                    while (i < n && !(t.charAt(i) == '*' && i + 1 < n && t.charAt(i + 1) == '/')) {
                        if (t.charAt(i) == '\n') {
                            line++;
                        }
                        i++;
                    }
                    i = Math.min(n, i + 2);
                } else {
                    hasCode[line] = true;
                    if (pendingClose != null) {
                        pendingClose.next = c;
                        pendingClose = null;
                    }
                    if (c == '"' && t.startsWith("\"\"\"", i)) { // text block
                        i += 3;
                        while (i < n && !t.startsWith("\"\"\"", i)) {
                            if (t.charAt(i) == '\\') {
                                i++;
                            } else if (t.charAt(i) == '\n') {
                                line++;
                                hasCode[Math.min(line, hasCode.length - 1)] = true;
                            }
                            i++;
                        }
                        i = Math.min(n, i + 3);
                    } else if (c == '"' || c == '\'') {
                        i++;
                        while (i < n && t.charAt(i) != c && t.charAt(i) != '\n') {
                            i += t.charAt(i) == '\\' ? 2 : 1;
                        }
                        if (i < n && t.charAt(i) == c) {
                            i++;
                        }
                    } else {
                        if (c == '{') {
                            events.add(new Event('{', line, depth));
                            depth++;
                        } else if (c == '}') {
                            depth = Math.max(0, depth - 1);
                            pendingClose = new Event('}', line, depth);
                            events.add(pendingClose);
                        } else if (c == ';') {
                            events.add(new Event(';', line, depth));
                        }
                        i++;
                    }
                }
            }
            return events;
        }
    }
}
