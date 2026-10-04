package com.bcoworks.codeanalyzer.mode;

import com.bcoworks.codeanalyzer.context.AppMode;
import org.springframework.stereotype.Component;

@Component
class DocumentModeHandler extends PlannedModeHandler {
    @Override
    public AppMode mode() {
        return AppMode.DOKUMAN_HAZIRLAMA;
    }
}