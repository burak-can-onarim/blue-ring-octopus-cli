package com.bcoworks.codeanalyzer.tui;

import java.awt.AWTError;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.Optional;

/**
 * Sistem panosu erişimi. Pano başka bir uygulama tarafından kısa süre kilitli olabilir, bu yüzden birkaç kez dener.
 */
final class ClipboardSupport {

    private static final int ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 40;

    private ClipboardSupport() {
    }

    static Optional<String> read() {
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            try {
                Clipboard clipboard = systemClipboard();
                if (clipboard == null || !clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                    return Optional.empty();
                }
                return Optional.ofNullable((String) clipboard.getData(DataFlavor.stringFlavor));
            } catch (IllegalStateException e) {
                if (!pause()) {
                    break;
                }
            } catch (UnsupportedFlavorException | IOException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    static boolean write(String text) {
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            try {
                Clipboard clipboard = systemClipboard();
                if (clipboard == null) {
                    return false;
                }
                clipboard.setContents(new StringSelection(text), null);
                return true;
            } catch (IllegalStateException e) {
                if (!pause()) {
                    break;
                }
            }
        }
        return false;
    }

    private static Clipboard systemClipboard() {
        if (GraphicsEnvironment.isHeadless()) {
            return null;
        }
        try {
            return Toolkit.getDefaultToolkit().getSystemClipboard();
        } catch (HeadlessException | AWTError e) {
            return null; // ekran yok (örn. Docker)
        }
    }

    private static boolean pause() {
        try {
            Thread.sleep(RETRY_DELAY_MS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}