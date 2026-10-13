package com.bcoworks.blueringoctopuscli.mode;

import com.bcoworks.blueringoctopuscli.context.AppMode;
import com.bcoworks.blueringoctopuscli.i18n.Messages;
import com.bcoworks.blueringoctopuscli.model.AiServiceRegistry;
import com.bcoworks.blueringoctopuscli.model.ModelCatalog;

/**
 * Tells the user when the model of a mode is one of Ollama's cloud models, before any code is sent to it.
 */
final class CloudModelNotice {

    private CloudModelNotice() {
    }

    static void warnIfCloud(AiServiceRegistry ai, AppMode mode, Messages messages, IModeConsole console) {
        String model = ai.modelNameFor(mode);
        if (ModelCatalog.isCloud(model)) {
            console.println(messages.get("warning.cloudModel", model));
        }
    }
}
