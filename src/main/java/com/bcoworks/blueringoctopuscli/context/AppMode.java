package com.bcoworks.blueringoctopuscli.context;

import lombok.Getter;

@Getter
public enum AppMode {
    KOD_ANALIZI("Kod Analizi",
            "Bu modda prompt gerekmez, Enter ile analizi başlat.",
            "Analiz edilecek dizin veya dosya (boşsa çalışma dizini)."),
    KOD_GENERATE("Kod Generate",
            "Üretilecek Java sınıfını anlatın. Örn: ürünleri listeleyen bir Spring REST controller",
            "Kayıt yolu, isteğe bağlı (boşsa generated/<SınıfAdı>.java)."),
    DOKUMAN_HAZIRLAMA("Döküman Hazırlama",
            "Bu mod henüz hazır değil.",
            "Bu mod henüz hazır değil."),
    BIRIM_TEST("Birim Test Yazdırma",
            "Bu mod henüz hazır değil.",
            "Bu mod henüz hazır değil.");

    private final String displayName;
    private final String promptHint;
    private final String pathHint;

    AppMode(String displayName, String promptHint, String pathHint) {
        this.displayName = displayName;
        this.promptHint = promptHint;
        this.pathHint = pathHint;
    }

    public AppMode next() {
        AppMode[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    public AppMode previous() {
        AppMode[] all = values();
        return all[(ordinal() - 1 + all.length) % all.length];
    }
}