package com.bcoworks.blueringoctopuscli.mode;

import com.bcoworks.blueringoctopuscli.context.AppMode;
import com.bcoworks.blueringoctopuscli.i18n.Messages;
import com.bcoworks.blueringoctopuscli.model.AiServiceRegistry;
import com.bcoworks.blueringoctopuscli.util.PathUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class GenerateModeHandler implements IModeHandler {

    private static final int MAX_PROMPT_LENGTH = 2_000;
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
        return AppMode.KOD_GENERATE;
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
        String raw = ai.forMode(AppMode.KOD_GENERATE).generateCode(messages.language().englishName(), prompt);
        if (console.isCancelled()) {
            return; // iptal edildi: dosya yazılmaz
        }
        String code = stripMarkdownFences(raw);

        Matcher type = TYPE_DECLARATION.matcher(code);
        if (!type.find()) {
            console.println(messages.get("warning.invalidOutput", code));
            return;
        }

        Path target = PathUtils.resolve(outPath.isEmpty() ? "generated/" + type.group(1) + ".java" : outPath);

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
        console.step(messages.get("generate.done"));
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
    static String stripMarkdownFences(String raw) {
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