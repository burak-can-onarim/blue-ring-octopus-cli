package com.bcoworks.codeanalyzer.context;

import lombok.Getter;

@Getter
public enum AppMode {
    KOD_ANALIZI("Kod Analizi"),
    KOD_GENERATE("Kod Generate"),
    DOKUMAN_HAZIRLAMA("Döküman Hazırlama"),
    BIRIM_TEST("Birim Test Yazdırma");

    private final String displayName;

    AppMode(String displayName) {
        this.displayName = displayName;
    }
}
