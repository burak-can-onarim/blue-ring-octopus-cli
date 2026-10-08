package com.bcoworks.blueringoctopuscli.mode;

import com.bcoworks.blueringoctopuscli.context.AppMode;
import com.bcoworks.blueringoctopuscli.i18n.Messages;
import com.bcoworks.blueringoctopuscli.model.AiServiceRegistry;
import com.bcoworks.blueringoctopuscli.service.CodeAssistant;
import com.bcoworks.blueringoctopuscli.service.CodeSplitter;
import com.bcoworks.blueringoctopuscli.service.ContextBudget;
import com.bcoworks.blueringoctopuscli.service.SourceCodeScanner;
import com.bcoworks.blueringoctopuscli.util.PathUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class AnalysisModeHandler implements IModeHandler {

    /**
     * Local-first koruma: bundan büyük dosyalar LLM'e gönderilmez (her parça için ayrı bir model çağrısı gerekir).
     */
    private static final long MAX_FILE_BYTES = 512 * 1024;

    private final AiServiceRegistry ai;
    private final SourceCodeScanner scanner;
    private final Messages messages;
    private final int partTokens;

    /**
     * @param partTokens {@code octopus.analysis.part-tokens}: 0 reviews a file in one piece when it fits the window; above 0
     *                   a file bigger than that many tokens is reviewed in parts of at most that size, also if it fits
     */
    @Autowired
    public AnalysisModeHandler(AiServiceRegistry ai, SourceCodeScanner scanner, Messages messages,
                               @Value("${octopus.analysis.part-tokens:0}") int partTokens) {
        this.ai = ai;
        this.scanner = scanner;
        this.messages = messages;
        this.partTokens = Math.max(0, partTokens);
    }

    public AnalysisModeHandler(AiServiceRegistry ai, SourceCodeScanner scanner, Messages messages) {
        this(ai, scanner, messages, 0);
    }

    @Override
    public AppMode mode() {
        return AppMode.CODE_ANALYSIS;
    }

    @Override
    public void handle(ModeRequest request, IModeConsole console) throws IOException {
        if (!request.prompt().isEmpty()) {
            console.println(messages.get("analysis.noPromptNote"));
        }

        Path target = PathUtils.resolve(request.hasPath() ? request.path() : ".");
        if (!Files.exists(target)) {
            console.println(messages.get("error.pathNotFound", target));
            return;
        }

        console.step(messages.get("analysis.scanning"));
        List<Path> files = scanner.scanJavaFiles(target.toString());
        if (files.isEmpty()) {
            console.println(messages.get("analysis.noFiles", target));
            return;
        }
        int total = files.size();
        console.println(messages.get("analysis.found", total, target));

        CodeAssistant assistant = ai.forMode(AppMode.CODE_ANALYSIS);

        for (int i = 0; i < total; i++) {
            if (console.isCancelled()) {
                return;
            }
            Path file = files.get(i);
            console.progress(i, total);
            try {
                long size = Files.size(file);
                if (size > MAX_FILE_BYTES) {
                    console.println(messages.get("analysis.skipped",
                            file.getFileName(), size / 1024, MAX_FILE_BYTES / 1024));
                    continue;
                }
                String code = Files.readString(file);
                if (code.isBlank()) {
                    continue;
                }
                boolean fits = ContextBudget.fits(ai.numCtx(), code);
                boolean askedForParts = fits && partTokens > 0 && ContextBudget.estimateTokens(code) > partTokens;
                if (!fits || askedForParts) {
                    analyzeInParts(assistant, file.getFileName().toString(), code, fits, i + 1, total, console);
                } else {
                    analyzeWhole(assistant, file.getFileName().toString(), code, i + 1, total, console);
                }
            } catch (IOException e) {
                console.println(messages.get("analysis.unreadable", file.getFileName(), e.getMessage()));
            }
        }
        console.progress(total, total);
        console.step(messages.get("analysis.done"));
    }

    private void analyzeWhole(CodeAssistant assistant, String fileName, String code, int number, int total,
                              IModeConsole console) {
        console.step(messages.get("analysis.analyzing", number, total, fileName));
        String result = assistant.analyze(fileName, code);
        console.println("\n--- " + fileName + " ---\n" + result);
    }

    /**
     * A file that does not fit the model's window (or is bigger than {@code octopus.analysis.part-tokens}) is reviewed in
     * parts (see {@link CodeSplitter}), each part with the outline of the whole file. The reviews of the parts are made
     * in English and merged into one review (duplicates joined, one overview and verdict), which is then put into the
     * selected language once. If the merge cannot be trusted the parts are shown one by one.
     */
    private void analyzeInParts(CodeAssistant assistant, String fileName, String code, boolean fitsWindow, int number,
                                int total, IModeConsole console) {
        int window = ContextBudget.codeBudget(ai.numCtx());
        int budget = partTokens > 0 ? Math.min(partTokens, window) : window;
        List<CodeSplitter.Part> parts = CodeSplitter.split(code, budget);
        if (parts.size() < 2 && fitsWindow) { // the setting asked for parts, but this file does not split
            analyzeWhole(assistant, fileName, code, number, total, console);
            return;
        }
        if (parts.isEmpty()) {
            console.println(messages.get("analysis.tooLarge", fileName, ContextBudget.estimateTokens(code), window));
            return;
        }
        console.println(fitsWindow
                ? messages.get("analysis.splitBySetting", fileName, parts.size(), budget)
                : messages.get("analysis.split", fileName, ContextBudget.estimateTokens(code), parts.size()));

        List<String> reviews = new ArrayList<>();
        for (CodeSplitter.Part part : parts) {
            if (console.isCancelled()) {
                return;
            }
            console.step(messages.get("analysis.analyzingPart", number, total, fileName, part.number(), part.count()));
            reviews.add(assistant.reviewPart(fileName, part));
        }

        if (parts.size() > 1) {
            if (console.isCancelled()) {
                return;
            }
            console.step(messages.get("analysis.merging", fileName));
            String merged = assistant.mergeParts(fileName, parts, reviews, window);
            if (console.isCancelled()) {
                return;
            }
            if (!merged.isBlank()) {
                console.println(messages.get("analysis.merged", fileName, parts.size()) + "\n" + assistant.localize(merged));
                return;
            }
        }
        for (int i = 0; i < parts.size(); i++) { // one part, or a merge that could not be trusted
            if (console.isCancelled()) {
                return;
            }
            CodeSplitter.Part part = parts.get(i);
            console.println(messages.get("analysis.part", fileName, part.number(), part.count(), part.firstLine(),
                    part.lastLine()) + "\n" + assistant.localize(reviews.get(i)));
        }
    }
}
