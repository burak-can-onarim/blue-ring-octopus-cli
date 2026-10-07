package com.bcoworks.blueringoctopuscli.mode;

import com.bcoworks.blueringoctopuscli.context.AppMode;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class ModeDispatcher {

    private final Map<AppMode, IModeHandler> handlers;

    public ModeDispatcher(List<IModeHandler> handlerList) {
        EnumMap<AppMode, IModeHandler> map = new EnumMap<>(AppMode.class);
        for (IModeHandler handler : handlerList) {
            if (map.put(handler.mode(), handler) != null) {
                throw new IllegalStateException("More than one handler for the same mode: " + handler.mode());
            }
        }
        for (AppMode mode : AppMode.values()) {
            if (!map.containsKey(mode)) {
                throw new IllegalStateException("No handler defined for this mode: " + mode);
            }
        }
        this.handlers = Collections.unmodifiableMap(map);
    }

    public void dispatch(AppMode mode, ModeRequest request, IModeConsole console) throws Exception {
        handlers.get(mode).handle(request, console);
    }
}