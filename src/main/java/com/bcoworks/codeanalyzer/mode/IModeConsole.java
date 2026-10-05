package com.bcoworks.codeanalyzer.mode;

public interface IModeConsole {

    /**
     * Durum kutusunda gösterilecek kısa adım bilgisi.
     */
    void step(String message);

    /**
     * Çıktı paneline metin ekler.
     */
    void println(String text);

    /**
     * İlerleme bilgisi. total <= 0 ise ilerleme çubuğu gizlenir.
     */
    default void progress(int done, int total) {
    }

    /**
     * İşlem iptal edildiyse true. Uzun döngülerde ve dosya yazmadan önce kontrol edilmelidir.
     */
    default boolean isCancelled() {
        return false;
    }

    static IModeConsole stdout() {
        return new IModeConsole() {
            @Override
            public void step(String message) {
                System.out.println(">> " + message);
            }

            @Override
            public void println(String text) {
                System.out.println(text);
            }
        };
    }
}