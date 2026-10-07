package com.bcoworks.blueringoctopuscli.mode;

import com.bcoworks.blueringoctopuscli.context.AppMode;
import com.bcoworks.blueringoctopuscli.i18n.Messages;
import com.bcoworks.blueringoctopuscli.model.AiServiceRegistry;
import com.bcoworks.blueringoctopuscli.service.CodeAssistant;
import com.bcoworks.blueringoctopuscli.service.CodeSplitter;
import com.bcoworks.blueringoctopuscli.service.ContextBudget;
import com.bcoworks.blueringoctopuscli.service.SourceCodeScanner;
import com.bcoworks.blueringoctopuscli.util.PathUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AnalysisModeHandler implements IModeHandler {

    /**
     * Local-first koruma: bundan büyük dosyalar LLM'e gönderilmez (her parça için ayrı bir model çağrısı gerekir).
     */
    private static final long MAX_FILE_BYTES = 512 * 1024;

    private final AiServiceRegistry ai;
    private final SourceCodeScanner scanner;
    private final Messages messages;

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
                if (!ContextBudget.fits(ai.numCtx(), code)) {
                    analyzeInParts(assistant, file.getFileName().toString(), code, i + 1, total, console);
                    continue;
                }
                console.step(messages.get("analysis.analyzing", i + 1, total, file.getFileName()));
                String result = assistant.analyze(file.getFileName().toString(), code);
                console.println("\n--- " + file.getFileName() + " ---\n" + result);
            } catch (IOException e) {
                console.println(messages.get("analysis.unreadable", file.getFileName(), e.getMessage()));
            }
        }
        console.progress(total, total);
        console.step(messages.get("analysis.done"));
    }

    /**
     * A file that does not fit the model's window is reviewed in parts (see {@link CodeSplitter}), each part with the
     * outline of the whole file, instead of being skipped.
     */
    private void analyzeInParts(CodeAssistant assistant, String fileName, String code, int number, int total,
                                IModeConsole console) {
        int budget = ContextBudget.codeBudget(ai.numCtx());
        List<CodeSplitter.Part> parts = CodeSplitter.split(code, budget);
        if (parts.isEmpty()) {
            console.println(messages.get("analysis.tooLarge", fileName, ContextBudget.estimateTokens(code), budget));
            return;
        }
        console.println(messages.get("analysis.split", fileName, ContextBudget.estimateTokens(code), parts.size()));
        for (CodeSplitter.Part part : parts) {
            if (console.isCancelled()) {
                return;
            }
            console.step(messages.get("analysis.analyzingPart", number, total, fileName, part.number(), part.count()));
            String result = assistant.analyzePart(fileName, part);
            console.println(messages.get("analysis.part", fileName, part.number(), part.count(), part.firstLine(),
                    part.lastLine()) + "\n" + result);
        }
    }
}