package com.bcoworks.codeanalyzer.mode;

import com.bcoworks.codeanalyzer.context.AppMode;
import org.springframework.stereotype.Component;

@Component
class UnitTestModeHandler extends PlannedModeHandler {
    @Override
    public AppMode mode() {
        return AppMode.BIRIM_TEST;
    }
}