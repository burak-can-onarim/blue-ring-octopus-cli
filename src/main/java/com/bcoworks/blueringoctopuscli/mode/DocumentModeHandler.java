package com.bcoworks.blueringoctopuscli.mode;

import com.bcoworks.blueringoctopuscli.context.AppMode;
import org.springframework.stereotype.Component;

@Component
class DocumentModeHandler extends PlannedModeHandler {
    @Override
    public AppMode mode() {
        return AppMode.DOKUMAN_HAZIRLAMA;
    }
}