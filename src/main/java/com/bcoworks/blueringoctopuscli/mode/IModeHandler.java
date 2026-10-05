package com.bcoworks.blueringoctopuscli.mode;

import com.bcoworks.blueringoctopuscli.context.AppMode;

public interface IModeHandler {

    AppMode mode();

    /**
     * Bloklayıcıdır, UI thread'inden çağrılmamalıdır.
     */
    void handle(ModeRequest request, IModeConsole console) throws Exception;
}