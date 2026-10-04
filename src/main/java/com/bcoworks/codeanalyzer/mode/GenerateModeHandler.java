package com.bcoworks.codeanalyzer.mode;

import com.bcoworks.codeanalyzer.context.AppMode;
import com.bcoworks.codeanalyzer.service.ICodeAnalyzerService;
import com.bcoworks.codeanalyzer.util.PathUtils;
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

    private final ICodeAnalyzerService aiService;

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

        console.step("Model kod üretiyor...");
        String code = stripMarkdownFences(aiService.generateCode(prompt));

        Matcher type = TYPE_DECLARATION.matcher(code);
        if (!type.find()) {
            console.println("Uyarı: Model çıktısı geçerli bir Java tipi içermiyor, kaydedilmedi. Ham çıktı:\n" + code);
            return;
        }

        Path target = PathUtils.resolve(outPath.isEmpty() ? "generated/" + type.group(1) + ".java" : outPath);

        console.step("Dosya kaydediliyor...");
        try {
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.writeString(target, code + System.lineSeparator(), StandardOpenOption.CREATE_NEW);
        } catch (FileAlreadyExistsException e) {
            console.println("Hata: Dosya zaten var, üzerine yazılmadı -> " + target
                    + "\nFarklı bir --out yolu verin.");
            return;
        }

        console.println(code);
        console.println("\nKaydedildi: " + target);
        console.step("Tamamlandı");
    }

    private String validate(String prompt, String out) {
        if (prompt == null || prompt.isBlank()) {
            return "Hata: Ne üretileceğini yazmalısınız.";
        }
        if (prompt.length() > MAX_PROMPT_LENGTH) {
            return "Hata: İstek çok uzun (en fazla %d karakter).".formatted(MAX_PROMPT_LENGTH);
        }
        if (OTHER_LANGUAGE.matcher(prompt).find() && !JAVA_WORD.matcher(prompt).find()) {
            return "Bu ajan yalnızca Java kodu üretir. İsteğinizi Java olarak yeniden yazın.";
        }
        if (out != null && !out.isBlank() && !out.endsWith(".java")) {
            return "Hata: --out yolu .java uzantılı olmalı.";
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