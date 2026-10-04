package com.bcoworks.codeanalyzer.mode;

abstract class PlannedModeHandler implements IModeHandler {

    @Override
    public void handle(ModeRequest request, IModeConsole console) {
        console.println("'" + mode().getDisplayName() + "' modu henüz geliştirme aşamasında.");
    }
}