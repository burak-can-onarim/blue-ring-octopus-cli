package com.bcoworks.codeanalyzer.mode;

import com.bcoworks.codeanalyzer.context.AppMode;
import com.bcoworks.codeanalyzer.service.ICodeAnalyzerService;
import com.bcoworks.codeanalyzer.service.SourceCodeScanner;
import com.bcoworks.codeanalyzer.util.PathUtils;
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

    private final ICodeAnalyzerService aiService;
    private final SourceCodeScanner scanner;

    @Override
    public AppMode mode() {
        return AppMode.KOD_ANALIZI;
    }

    @Override
    public void handle(ModeRequest request, IModeConsole console) throws IOException {
        if (!request.prompt().isEmpty()) {
            console.println("Not: Analiz modunda prompt kullanılmaz, yolu Path alanına yazın.");
        }

        Path target = PathUtils.resolve(request.hasPath() ? request.path() : ".");
        if (!Files.exists(target)) {
            console.println("Hata: Belirtilen yol bulunamadı -> " + target);
            return;
        }

        console.step("Java dosyaları taranıyor...");
        List<Path> files = scanner.scanJavaFiles(target.toString());
        if (files.isEmpty()) {
            console.println("Analiz edilecek Java dosyası bulunamadı: " + target);
            return;
        }
        int total = files.size();
        console.println("%d dosya bulundu: %s".formatted(total, target));

        for (int i = 0; i < total; i++) {
            Path file = files.get(i);
            console.progress(i, total);
            try {
                long size = Files.size(file);
                if (size > MAX_FILE_BYTES) {
                    console.println("\n--- %s atlandı (%d KB, limit %d KB) ---"
                            .formatted(file.getFileName(), size / 1024, MAX_FILE_BYTES / 1024));
                    continue;
                }
                String code = Files.readString(file);
                if (code.isBlank()) {
                    continue;
                }
                console.step("Analiz ediliyor [%d/%d] %s".formatted(i + 1, total, file.getFileName()));
                String result = aiService.analyze(code);
                console.println("\n--- " + file.getFileName() + " ---\n" + result);
            } catch (IOException e) {
                console.println("\n--- " + file.getFileName() + " okunamadı: " + e.getMessage());
            }
        }
        console.progress(total, total);
        console.step("Analiz tamamlandı");
    }
}