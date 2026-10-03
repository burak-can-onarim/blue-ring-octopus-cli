package com.bcoworks.codeanalyzer.cli;

import com.bcoworks.codeanalyzer.service.ICodeAnalyzerService;
import com.bcoworks.codeanalyzer.service.SourceCodeScanner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Slf4j
@ShellComponent
@RequiredArgsConstructor
public class CodeAgentCLI {

    private final ICodeAnalyzerService aiService;
    private final SourceCodeScanner scanner;

    @ShellMethod(key = "analyze", value = "Verilen dizindeki Java dosyalarını analiz eder.")
    public void analyzeProject(@ShellOption(defaultValue = "src/main/java") String path) {
        System.out.println("Analiz başlatılıyor: " + path);
        try {
            List<Path> files = scanner.scanJavaFiles(path);
            for (Path file : files) {
                System.out.println("\n--- Dosya: " + file.getFileName() + " ---");
                String code = Files.readString(file);
                String result = aiService.analyze(code);
                System.out.println(result);
            }
        } catch (IOException e) {
            log.error("Dosyalar okunurken hata oluştu: {}", e.getMessage());
        }
    }

    @ShellMethod(key = "generate", value = "Yapay zekaya kod yazdırır ve diske kaydeder.")
    public void generateCode(
            @ShellOption(help = "Ne kodu yazılacak?") String prompt,
            @ShellOption(help = "Dosya nereye kaydedilecek? (Örn: src/main/java/Test.java)") String out) {

        System.out.println("Ajan kodu yazıyor... Lütfen bekleyin.");

        String generatedCode = aiService.generateCode(prompt);

        try {
            Path outputPath = Path.of(out);
            Files.createDirectories(outputPath.getParent());
            Files.writeString(outputPath, generatedCode);

            System.out.println("Başarılı! Kod kaydedildi: " + outputPath.toAbsolutePath());
        } catch (IOException e) {
            log.error("Dosya kaydedilemedi: {}", e.getMessage());
        }
    }
}