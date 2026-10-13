package com.bcoworks.blueringoctopuscli.mode;

import com.bcoworks.blueringoctopuscli.context.AppMode;
import com.bcoworks.blueringoctopuscli.i18n.Messages;
import com.bcoworks.blueringoctopuscli.model.AiServiceRegistry;
import com.bcoworks.blueringoctopuscli.model.ModelCatalog;

/**
 * Refuses Ollama's cloud before anything is sent: a cloud model (or Ollama's cloud address) would send the code away and can
 * be billed, and this project must stay free and local.
 */
final class CloudGuard {

    private CloudGuard() {
    }

    /**
     * Prints why and returns true if the model of the mode or the Ollama address belongs to Ollama's cloud.
     */
    static boolean blocks(AiServiceRegistry ai, AppMode mode, Messages messages, IModeConsole console) {
        if (ModelCatalog.isCloudServer(ai.baseUrl())) {
            console.println(messages.get("error.cloudServer", ai.baseUrl()));
            return true;
        }
        String model = ai.modelNameFor(mode);
        if (ModelCatalog.isCloud(model)) {
            console.println(messages.get("error.cloudModel", model));
            return true;
        }
        return false;
    }
}
