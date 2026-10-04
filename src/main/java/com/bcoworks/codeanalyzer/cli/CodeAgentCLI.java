package com.bcoworks.codeanalyzer.cli;

import com.bcoworks.codeanalyzer.mode.AnalysisModeHandler;
import com.bcoworks.codeanalyzer.mode.GenerateModeHandler;
import com.bcoworks.codeanalyzer.mode.IModeConsole;
import com.bcoworks.codeanalyzer.mode.ModeRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

@ShellComponent
@RequiredArgsConstructor
public class CodeAgentCLI {

    private final AnalysisModeHandler analysisHandler;
    private final GenerateModeHandler generateHandler;

    @ShellMethod(key = "analyze", value = "Verilen dizindeki Java dosyalarını analiz eder.")
    public void analyzeProject(
            @ShellOption(defaultValue = ".", help = "Analiz edilecek dizin veya dosya yolu.") String pathInput)
            throws Exception {
        analysisHandler.handle(new ModeRequest("", pathInput), IModeConsole.stdout());
    }

    @ShellMethod(key = "generate", value = "Yapay zekaya kod yazdırır ve diske kaydeder.")
    public void generateCode(
            @ShellOption(help = "Ne kodu yazılacak? (tırnak içinde yazın)") String prompt,
            @ShellOption(defaultValue = ShellOption.NULL,
                    help = "Opsiyonel kayıt yolu. Boşsa generated/<SınıfAdı>.java") String out)
            throws Exception {
        generateHandler.generate(prompt, out, IModeConsole.stdout());
    }
}