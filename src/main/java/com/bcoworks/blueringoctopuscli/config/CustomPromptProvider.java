package com.bcoworks.blueringoctopuscli.config;

import com.bcoworks.blueringoctopuscli.context.AppContext;
import com.bcoworks.blueringoctopuscli.i18n.Messages;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.springframework.shell.jline.PromptProvider;
import org.springframework.stereotype.Component;

@Component
public class CustomPromptProvider implements PromptProvider {

    private final AppContext appContext;
    private final Messages messages;

    public CustomPromptProvider(AppContext appContext, Messages messages) {
        this.appContext = appContext;
        this.messages = messages;
    }

    @Override
    public AttributedString getPrompt() {
        String modeName = messages.modeName(appContext.getCurrentMode());
        String promptText = String.format("octo [%s]> ", modeName);

        return new AttributedString(
                promptText,
                AttributedStyle.DEFAULT.foreground(AttributedStyle.BLUE).bold()
        );
    }
}