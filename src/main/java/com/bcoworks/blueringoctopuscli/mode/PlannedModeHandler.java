package com.bcoworks.blueringoctopuscli.mode;

import com.bcoworks.blueringoctopuscli.i18n.Messages;

abstract class PlannedModeHandler implements IModeHandler {

    private final Messages messages;

    PlannedModeHandler(Messages messages) {
        this.messages = messages;
    }

    @Override
    public void handle(ModeRequest request, IModeConsole console) {
        console.println(messages.get("planned.notReady", messages.modeName(mode())));
    }
}
