package com.bcoworks.blueringoctopuscli.cli;

import com.bcoworks.blueringoctopuscli.mode.AnalysisModeHandler;
import com.bcoworks.blueringoctopuscli.mode.GenerateModeHandler;
import com.bcoworks.blueringoctopuscli.mode.IModeConsole;
import com.bcoworks.blueringoctopuscli.mode.ModeRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

@ShellComponent
@RequiredArgsConstructor
public class CodeAgentCLI {

    private final AnalysisModeHandler analysisHandler;
    private final GenerateModeHandler generateHandler;

    @ShellMethod(key = "analyze", value = "Analyzes the Java files in the given directory.")
    public void analyzeProject(
            @ShellOption(defaultValue = ".", help = "Directory or file to analyze.") String pathInput)
            throws Exception {
        analysisHandler.handle(new ModeRequest("", pathInput), IModeConsole.stdout());
    }

    @ShellMethod(key = "generate", value = "Has the AI write code and saves it to disk.")
    public void generateCode(
            @ShellOption(help = "What code to write? (in quotes)") String prompt,
            @ShellOption(defaultValue = ShellOption.NULL,
                    help = "Optional output path. Default: generated/<ClassName>.java") String out)
            throws Exception {
        generateHandler.generate(prompt, out, IModeConsole.stdout());
    }
}