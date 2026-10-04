package com.bcoworks.codeanalyzer.config;

import com.bcoworks.codeanalyzer.tui.TuiLauncher;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TuiStartupListener implements ApplicationRunner {

    private final TuiLauncher launcher;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (args.getSourceArgs().length == 0) {
            launcher.launch();
        }
    }
}