package com.bcoworks.codeanalyzer.config;

import com.bcoworks.codeanalyzer.context.AppContext;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.springframework.shell.jline.PromptProvider;
import org.springframework.stereotype.Component;

@Component
public class CustomPromptProvider implements PromptProvider {

    private final AppContext appContext;

    public CustomPromptProvider(AppContext appContext) {
        this.appContext = appContext;
    }

    @Override
    public AttributedString getPrompt() {
        String modeName = appContext.getCurrentMode().getDisplayName();
        String promptText = String.format("octo [%s]> ", modeName);

        return new AttributedString(
                promptText,
                AttributedStyle.DEFAULT.foreground(AttributedStyle.BLUE).bold()
        );
    }
}