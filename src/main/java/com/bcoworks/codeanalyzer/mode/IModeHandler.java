package com.bcoworks.codeanalyzer.mode;

import com.bcoworks.codeanalyzer.context.AppMode;

public interface IModeHandler {

    AppMode mode();

    /**
     * Bloklayıcıdır, UI thread'inden çağrılmamalıdır.
     */
    void handle(ModeRequest request, IModeConsole console) throws Exception;
}