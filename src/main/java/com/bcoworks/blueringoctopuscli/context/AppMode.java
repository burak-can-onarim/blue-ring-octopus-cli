package com.bcoworks.blueringoctopuscli.context;

/**
 * The working modes. The constant names are the keys of the settings file (see {@code ModelSettings}, which still
 * reads the old Turkish names), so renaming one needs a migration; the texts shown for a mode (name, hints) live in
 * the language files under {@code i18n/}, keyed by {@code mode.<NAME>.*}.
 */
public enum AppMode {
    CODE_ANALYSIS,
    CODE_GENERATION,
    DOCUMENTATION,
    UNIT_TESTS;

    public AppMode next() {
        AppMode[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    public AppMode previous() {
        AppMode[] all = values();
        return all[(ordinal() - 1 + all.length) % all.length];
    }
}
