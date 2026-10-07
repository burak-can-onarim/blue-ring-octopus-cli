package com.bcoworks.blueringoctopuscli.mode;

import com.bcoworks.blueringoctopuscli.context.AppMode;
import com.bcoworks.blueringoctopuscli.i18n.Messages;
import org.springframework.stereotype.Component;

@Component
class UnitTestModeHandler extends PlannedModeHandler {

    UnitTestModeHandler(Messages messages) {
        super(messages);
    }

    @Override
    public AppMode mode() {
        return AppMode.BIRIM_TEST;
    }
}
