package com.bcoworks.blueringoctopuscli.mode;

import com.bcoworks.blueringoctopuscli.context.AppMode;
import com.bcoworks.blueringoctopuscli.i18n.Messages;
import com.bcoworks.blueringoctopuscli.model.AiServiceRegistry;
import com.bcoworks.blueringoctopuscli.service.ICodeAnalyzerService;
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
     * Local-first koruma: bundan büyük dosyalar LLM'e gönderilmez.
     */
    private static final long MAX_FILE_BYTES = 64 * 1024;

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

        ICodeAnalyzerService service = ai.forMode(AppMode.CODE_ANALYSIS);

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
                console.step(messages.get("analysis.analyzing", i + 1, total, file.getFileName()));
                String result = service.analyze(messages.language().englishName(), code);
                console.println("\n--- " + file.getFileName() + " ---\n" + result);
            } catch (IOException e) {
                console.println(messages.get("analysis.unreadable", file.getFileName(), e.getMessage()));
            }
        }
        console.progress(total, total);
        console.step(messages.get("analysis.done"));
    }
}