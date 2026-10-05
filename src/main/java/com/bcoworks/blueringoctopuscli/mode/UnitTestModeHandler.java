package com.bcoworks.blueringoctopuscli.mode;

import com.bcoworks.blueringoctopuscli.context.AppMode;
import org.springframework.stereotype.Component;

@Component
class UnitTestModeHandler extends PlannedModeHandler {
    @Override
    public AppMode mode() {
        return AppMode.BIRIM_TEST;
    }
}