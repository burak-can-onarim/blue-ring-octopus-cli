package com.bcoworks.blueringoctopuscli.context;

/**
 * The working modes. The constant names are stored in the settings file, so they must not change; the texts shown
 * for a mode (name, hints) live in the language files under {@code i18n/}, keyed by {@code mode.<NAME>.*}.
 */
public enum AppMode {
    KOD_ANALIZI,
    KOD_GENERATE,
    DOKUMAN_HAZIRLAMA,
    BIRIM_TEST;

    public AppMode next() {
        AppMode[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    public AppMode previous() {
        AppMode[] all = values();
        return all[(ordinal() - 1 + all.length) % all.length];
    }
}
