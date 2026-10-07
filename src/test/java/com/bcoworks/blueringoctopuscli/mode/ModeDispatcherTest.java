package com.bcoworks.blueringoctopuscli.mode;

import com.bcoworks.blueringoctopuscli.context.AppMode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModeDispatcherTest {

    private static final class RecordingHandler implements IModeHandler {
        private final AppMode mode;
        private final List<ModeRequest> requests = new ArrayList<>();

        RecordingHandler(AppMode mode) {
            this.mode = mode;
        }

        @Override
        public AppMode mode() {
            return mode;
        }

        @Override
        public void handle(ModeRequest request, IModeConsole console) {
            requests.add(request);
        }
    }

    private static List<RecordingHandler> handlersForAllModes() {
        List<RecordingHandler> list = new ArrayList<>();
        for (AppMode mode : AppMode.values()) {
            list.add(new RecordingHandler(mode));
        }
        return list;
    }

    @Test
    void dispatchesToHandlerOfRequestedMode() throws Exception {
        List<RecordingHandler> handlers = handlersForAllModes();
        ModeDispatcher dispatcher = new ModeDispatcher(List.copyOf(handlers));
        ModeRequest request = new ModeRequest("merhaba", "");

        dispatcher.dispatch(AppMode.CODE_GENERATION, request, IModeConsole.stdout());

        for (RecordingHandler handler : handlers) {
            List<ModeRequest> expected = handler.mode == AppMode.CODE_GENERATION ? List.of(request) : List.of();
            assertEquals(expected, handler.requests, handler.mode.name());
        }
    }

    @Test
    void failsWhenAModeHasNoHandler() {
        List<RecordingHandler> handlers = handlersForAllModes();
        handlers.removeFirst();

        assertThrows(IllegalStateException.class, () -> new ModeDispatcher(List.copyOf(handlers)));
    }

    @Test
    void failsWhenAModeHasTwoHandlers() {
        List<RecordingHandler> handlers = handlersForAllModes();
        handlers.add(new RecordingHandler(AppMode.CODE_ANALYSIS));

        assertThrows(IllegalStateException.class, () -> new ModeDispatcher(List.copyOf(handlers)));
    }

    @Test
    void propagatesHandlerExceptions() {
        AppMode failingMode = AppMode.values()[0];
        List<IModeHandler> handlers = new ArrayList<>(handlersForAllModes());
        handlers.set(0, new IModeHandler() {
            @Override
            public AppMode mode() {
                return failingMode;
            }

            @Override
            public void handle(ModeRequest request, IModeConsole console) throws Exception {
                throw new Exception("boom");
            }
        });
        ModeDispatcher dispatcher = new ModeDispatcher(handlers);

        Exception thrown = assertThrows(Exception.class,
                () -> dispatcher.dispatch(failingMode, new ModeRequest("", ""), IModeConsole.stdout()));
        assertEquals("boom", thrown.getMessage());
    }
}
