package com.bcoworks.blueringoctopuscli.service;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compiles one generated source file in process, to find out whether it compiles on its own.
 * <p>
 * The application runs on a JDK, so the compiler is at hand. It sees the JDK and nothing else: a library the request
 * names (Spring, Jakarta, ...) is not on its class path. Errors that only say "this library is missing" are therefore
 * not counted as problems of the code; the libraries are reported separately, so the reader knows that part of the file
 * could not be checked. Everything else (a method that does not exist, a missing import of a JDK class, a type mismatch,
 * a missing semicolon) is a problem the model can be asked to fix.
 */
public final class CompileCheck {

    /**
     * One error of the compiler: the line (1-based), its message in one line and the text of that line.
     */
    public record Problem(int line, String message, String sourceLine) {
    }

    /**
     * @param checked   false if there is no compiler (a JRE) and nothing could be checked
     * @param problems  the errors that are the code's own fault
     * @param libraries packages that are not part of the JDK and could not be checked (sorted)
     */
    public record Result(boolean checked, List<Problem> problems, Set<String> libraries) {

        public boolean compiles() {
            return checked && problems.isEmpty();
        }
    }

    private static final Pattern MISSING_PACKAGE = Pattern.compile("package ([\\w.]+) does not exist");
    private static final Pattern MISSING_SYMBOL = Pattern.compile("symbol:\\s+\\w+\\s+(\\w+)");
    private static final Pattern IMPORT = Pattern.compile("(?m)^\\s*import\\s+(static\\s+)?([\\w.]+?)(\\.\\*)?\\s*;");

    private static final String CODE_NO_PACKAGE = "compiler.err.doesnt.exist";
    private static final String CODE_NO_SYMBOL = "compiler.err.cant.resolve";
    private static final String CODE_NO_OVERRIDE = "compiler.err.method.does.not.override.superclass";

    private CompileCheck() {
    }

    /**
     * Compiles {@code source} as the file {@code typeName}.java.
     */
    public static Result check(String typeName, String source) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return new Result(false, List.of(), Set.of());
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager standard = compiler.getStandardFileManager(diagnostics, Locale.ENGLISH, null);
        try (JavaFileManager files = new DiscardingFileManager(standard)) {
            JavaFileObject file = new SourceFile(typeName, source);
            // an empty class path: the application's own libraries must not leak into the check
            List<String> options = List.of("-proc:none", "-Xlint:none", "-implicit:none", "-encoding", "UTF-8",
                    "-classpath", System.getProperty("java.io.tmpdir") + "/octopus-empty-classpath", "-Xmaxerrs", "30");
            compiler.getTask(null, files, diagnostics, options, null, List.of(file)).call();
        } catch (IOException | RuntimeException e) {
            return new Result(false, List.of(), Set.of());
        }
        return classify(source, diagnostics.getDiagnostics());
    }

    private static Result classify(String source, List<Diagnostic<? extends JavaFileObject>> diagnostics) {
        MissingImports missing = missingImports(source, diagnostics);
        String[] lines = source.split("\\R", -1);
        List<Problem> problems = new ArrayList<>();
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics) {
            if (diagnostic.getKind() != Diagnostic.Kind.ERROR || missing.explains(diagnostic)) {
                continue;
            }
            int line = (int) Math.max(1, diagnostic.getLineNumber());
            String text = line <= lines.length ? lines[line - 1].strip() : "";
            problems.add(new Problem(line, oneLine(diagnostic.getMessage(Locale.ENGLISH)), text));
        }
        return new Result(true, List.copyOf(problems), new TreeSet<>(missing.packages));
    }

    /**
     * The imports the compiler could not resolve because a library is missing, and the names they brought in.
     */
    private static MissingImports missingImports(String source, List<Diagnostic<? extends JavaFileObject>> diagnostics) {
        Set<String> packages = new TreeSet<>();
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics) {
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR && CODE_NO_PACKAGE.equals(diagnostic.getCode())) {
                Matcher matcher = MISSING_PACKAGE.matcher(diagnostic.getMessage(Locale.ENGLISH));
                if (matcher.find() && !inJdkNamespace(matcher.group(1))) {
                    packages.add(matcher.group(1));
                }
            }
        }
        Set<String> names = new TreeSet<>();
        Set<Integer> importLines = new TreeSet<>();
        boolean wildcard = false;
        Matcher imports = IMPORT.matcher(source);
        while (imports.find()) {
            String name = imports.group(2);
            boolean onDemand = imports.group(3) != null;
            boolean isStatic = imports.group(1) != null;
            String owner = onDemand ? name : name.substring(0, Math.max(0, name.lastIndexOf('.')));
            if (packages.stream().anyMatch(pack -> owner.equals(pack) || owner.startsWith(pack + "."))) {
                importLines.add(1 + (int) source.substring(0, imports.start(2)).chars().filter(c -> c == '\n').count());
                if (onDemand) {
                    wildcard = true;
                } else {
                    names.add(name.substring(name.lastIndexOf('.') + 1));
                    if (isStatic) {
                        names.add(owner.substring(owner.lastIndexOf('.') + 1));
                    }
                }
            }
        }
        boolean extendsMissing = wildcard || (!names.isEmpty() && Pattern.compile(
                "\\b(?:extends|implements)\\b[^{;]*\\b(?:" + String.join("|", names) + ")\\b").matcher(source).find());
        return new MissingImports(packages, names, importLines, wildcard, extendsMissing);
    }

    /**
     * The packages under java. (and jdk.) are the JDK's: a package there that does not exist is a mistake of the model.
     */
    private static boolean inJdkNamespace(String pack) {
        return pack.equals("java") || pack.startsWith("java.") || pack.startsWith("jdk.");
    }

    private static String oneLine(String message) {
        return message.lines().map(String::strip).filter(line -> !line.isEmpty())
                .reduce((a, b) -> a + " " + b).orElse("");
    }

    /**
     * @param importLines    the lines of the imports that came from a missing library
     * @param extendsMissing the file extends or implements a type from a missing library, so the compiler cannot know
     *                       what its methods override
     */
    private record MissingImports(Set<String> packages, Set<String> names, Set<Integer> importLines, boolean wildcard,
                                  boolean extendsMissing) {

        /**
         * True for the errors of the imports themselves and for every "cannot find symbol" about a name that came
         * from them.
         */
        boolean explains(Diagnostic<? extends JavaFileObject> diagnostic) {
            String code = diagnostic.getCode();
            String message = diagnostic.getMessage(Locale.ENGLISH);
            if (importLines.contains((int) diagnostic.getLineNumber())) {
                return true;
            }
            if (extendsMissing && CODE_NO_OVERRIDE.equals(code)) {
                return true;
            }
            if (code != null && code.startsWith(CODE_NO_SYMBOL) && (wildcard || !names.isEmpty())) {
                Matcher matcher = MISSING_SYMBOL.matcher(message);
                return matcher.find() && (wildcard || names.contains(matcher.group(1)));
            }
            return false;
        }
    }

    private static final class SourceFile extends SimpleJavaFileObject {

        private final String source;

        SourceFile(String typeName, String source) {
            super(URI.create("string:///" + typeName + Kind.SOURCE.extension), Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }

    /**
     * Throws the class files away: only the diagnostics matter.
     */
    private static final class DiscardingFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {

        DiscardingFileManager(StandardJavaFileManager delegate) {
            super(delegate);
        }

        @Override
        public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind,
                                                   javax.tools.FileObject sibling) {
            return new SimpleJavaFileObject(URI.create("mem:///" + className.replace('.', '/') + kind.extension), kind) {
                @Override
                public OutputStream openOutputStream() {
                    return OutputStream.nullOutputStream();
                }
            };
        }
    }
}
