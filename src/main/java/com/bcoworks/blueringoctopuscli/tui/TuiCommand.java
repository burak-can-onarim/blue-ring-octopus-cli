package com.bcoworks.blueringoctopuscli.tui;

import lombok.RequiredArgsConstructor;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

import java.io.IOException;

@ShellComponent
@RequiredArgsConstructor
public class TuiCommand {

    private final TuiLauncher launcher;

    @ShellMethod(key = {"ui", "dashboard"}, value = "Tam ekran Lanterna TUI arayüzünü başlatır.")
    public void startUi() throws IOException {
        launcher.launch();
    }
}