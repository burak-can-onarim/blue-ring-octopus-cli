package com.bcoworks.blueringoctopuscli.mode;

import com.bcoworks.blueringoctopuscli.context.AppMode;
import com.bcoworks.blueringoctopuscli.i18n.Messages;
import com.bcoworks.blueringoctopuscli.model.AiServiceRegistry;
import com.bcoworks.blueringoctopuscli.service.CodeAssistant;
import com.bcoworks.blueringoctopuscli.service.CompileCheck;
import com.bcoworks.blueringoctopuscli.service.ContextBudget;
import com.bcoworks.blueringoctopuscli.util.PathUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class GenerateModeHandler implements IModeHandler {

    private static final int MAX_PROMPT_LENGTH = 2_000;
    private static final int REPAIR_OVERHEAD_TOKENS = 600; // the instructions of the repair prompt
    private static final String FENCE = "`".repeat(3);

    // Local-first dil doğrulaması: Java dışı bir dil isteniyorsa LLM çağrılmaz (sezgisel kontrol)
    private static final Pattern OTHER_LANGUAGE = Pattern.compile(
            "(?i)\\b(python|javascript|typescript|golang|rust|php|ruby|kotlin|swift)\\b|\\bc#|\\bc\\+\\+");
    private static final Pattern JAVA_WORD = Pattern.compile("(?i)\\bjava\\b");

    private static final Pattern TYPE_DECLARATION = Pattern.compile(
            "(?m)^\\s*(?:(?:public|protected|private|abstract|final|sealed|non-sealed|static)\\s+)*"
                    + "(?:class|interface|enum|record)\\s+(\\w+)");
    private static final Pattern FENCED_BLOCK =
            Pattern.compile("(?s)" + FENCE + "[\\w+-]*\\s*\\R(.*?)\\R?" + FENCE);

    private final AiServiceRegistry ai;
    private final Messages messages;

    @Override
    public AppMode mode() {
        return AppMode.CODE_GENERATION;
    }

    @Override
    public void handle(ModeRequest request, IModeConsole console) throws IOException {
        generate(request.prompt(), request.hasPath() ? request.path() : null, console);
    }

    /**
     * CLI komutu da bu metodu doğrudan kullanır. {@code out} null olabilir.
     */
    public void generate(String prompt, String out, IModeConsole console) throws IOException {
        String outPath = PathUtils.clean(out);
        String error = validate(prompt, outPath);
        if (error != null) {
            console.println(error);
            return;
        }

        console.step(messages.get("generate.working"));
        CodeAssistant assistant = ai.forMode(AppMode.CODE_GENERATION);
        String code = stripMarkdownFences(assistant.generateCode(prompt));
        if (console.isCancelled()) {
            return; // iptal edildi: dosya yazılmaz
        }

        Matcher type = TYPE_DECLARATION.matcher(code);
        if (!type.find()) {
            console.println(messages.get("warning.invalidOutput", code));
            return;
        }
        String typeName = type.group(1);

        console.step(messages.get("generate.checking"));
        Verified verified = verify(assistant, typeName, code, console);
        if (console.isCancelled()) {
            return;
        }
        code = verified.code();

        Path target = PathUtils.resolve(outPath.isEmpty() ? "generated/" + typeName + ".java" : outPath);

        console.step(messages.get("generate.saving"));
        try {
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.writeString(target, code + System.lineSeparator(), StandardOpenOption.CREATE_NEW);
        } catch (FileAlreadyExistsException e) {
            console.println(messages.get("error.fileExists", target));
            return;
        }

        console.println(code);
        console.println(messages.get("generate.saved", target));
        report(verified, console);
        console.step(messages.get("generate.done"));
    }

    /**
     * Compiles the generated file in process. If it has errors that are its own fault, the model gets the file and the
     * errors once and writes it again; the second version is used only if it has fewer errors than the first.
     */
    private Verified verify(CodeAssistant assistant, String typeName, String code, IModeConsole console) {
        CompileCheck.Result first = CompileCheck.check(typeName, code);
        if (!first.checked() || first.compiles() || !repairFits(code, first.problems())) {
            return new Verified(code, first, 0);
        }

        console.step(messages.get("generate.repairing", first.problems().size()));
        String repaired = stripMarkdownFences(assistant.repairCode(code, first.problems()));
        if (console.isCancelled() || !declaresType(repaired, typeName)) {
            return new Verified(code, first, 0);
        }
        CompileCheck.Result second = CompileCheck.check(typeName, repaired);
        if (second.checked() && second.problems().size() < first.problems().size()) {
            return new Verified(repaired, second, first.problems().size());
        }
        return new Verified(code, first, 0);
    }

    /**
     * The repair prompt holds the file and the errors, and the answer is the whole file again.
     */
    private boolean repairFits(String code, List<CompileCheck.Problem> problems) {
        int needed = 2 * ContextBudget.estimateTokens(code)
                + ContextBudget.estimateTokens(CodeAssistant.describe(problems)) + REPAIR_OVERHEAD_TOKENS;
        return needed <= ai.numCtx();
    }

    /**
     * A repaired file must still be the file that was asked for: same main type, so the file name stays valid.
     */
    private static boolean declaresType(String code, String typeName) {
        Matcher type = TYPE_DECLARATION.matcher(code);
        return type.find() && type.group(1).equals(typeName);
    }

    private void report(Verified verified, IModeConsole console) {
        CompileCheck.Result result = verified.result();
        if (!result.checked()) {
            console.println(messages.get("generate.notChecked"));
            return;
        }
        if (result.compiles()) {
            console.println(verified.fixedErrors() > 0
                    ? messages.get("generate.compilesFixed", verified.fixedErrors())
                    : messages.get("generate.compiles"));
        } else {
            String list = result.problems().stream()
                    .map(problem -> "  " + messages.get("analysis.label.line") + " " + problem.line() + ": " + problem.message())
                    .collect(Collectors.joining("\n"));
            console.println(messages.get("warning.compileErrors", result.problems().size(), list));
        }
        if (!result.libraries().isEmpty()) {
            console.println(messages.get("generate.libraries", String.join(", ", result.libraries())));
        }
    }

    /**
     * The file to save, what the compiler said about it, and how many errors the repair removed (0 if none was made).
     */
    private record Verified(String code, CompileCheck.Result result, int fixedErrors) {
    }

    private String validate(String prompt, String out) {
        if (prompt == null || prompt.isBlank()) {
            return messages.get("error.noPrompt");
        }
        if (prompt.length() > MAX_PROMPT_LENGTH) {
            return messages.get("error.tooLong", MAX_PROMPT_LENGTH);
        }
        if (OTHER_LANGUAGE.matcher(prompt).find() && !JAVA_WORD.matcher(prompt).find()) {
            return messages.get("generate.notJava");
        }
        if (out != null && !out.isBlank() && !out.endsWith(".java")) {
            return messages.get("error.outExtension");
        }
        return null;
    }

    /**
     * Yerel modeller "markdown kullanma" kuralını sık çiğner, çıktıyı temizler.
     */
    public static String stripMarkdownFences(String raw) {
        String text = raw == null ? "" : raw.strip();
        Matcher block = FENCED_BLOCK.matcher(text);
        if (block.find()) {
            return block.group(1).strip();
        }
        if (text.startsWith(FENCE)) { // kapanış çiti olmayan kesik çıktı
            int newline = text.indexOf('\n');
            text = newline >= 0 ? text.substring(newline + 1) : "";
            if (text.endsWith(FENCE)) {
                text = text.substring(0, text.length() - FENCE.length());
            }
        }
        return text.strip();
    }
}