package com.bcoworks.blueringoctopuscli.tui;

import com.googlecode.lanterna.terminal.swing.SwingTerminal;
import com.googlecode.lanterna.terminal.swing.SwingTerminalFrame;

import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

/**
 * Lanterna'nın Swing terminali fare olayı üretmez; bu sınıf pencerenin AWT fare olaylarını terminal hücrelerine
 * çevirip arayüze iletir. Yalnızca sol tık ve tekerlek desteklenir.
 */
final class MouseSupport {

    /**
     * Terminal hücresi (sütun, satır) cinsinden fare olayları. Herhangi bir thread'den çağrılabilir.
     */
    interface Handler {
        void onClick(int column, int row);

        /**
         * @param lines negatif yukarı, pozitif aşağı
         */
        void onScroll(int column, int row, int lines);
    }

    static final int LINES_PER_WHEEL_NOTCH = 3;

    private MouseSupport() {
    }

    /**
     * Dokunmatik yüzey ve ince taneli (hassas) tekerlekler küçük, kesirli adımlar gönderir; tamsayı {@code getWheelRotation()}
     * bunlarda 0 kalır. Kesirli adımlar biriktirilir, birikim bir satırı geçince o kadar satır kaydırılır.
     */
    static final class WheelAccumulator {

        private double pending;

        /**
         * @param notches {@code MouseWheelEvent.getPreciseWheelRotation()} değeri (negatif yukarı)
         * @return kaydırılacak tam satır sayısı (0 olabilir)
         */
        int add(double notches) {
            pending += notches * LINES_PER_WHEEL_NOTCH;
            int whole = (int) pending;
            pending -= whole;
            return whole;
        }
    }

    /**
     * Piksel konumunu hücre indeksine çevirir (negatif konumlar 0'a çekilir).
     */
    static int cell(int pixel, int cellSize) {
        return cellSize <= 0 ? 0 : Math.max(0, pixel) / cellSize;
    }

    /**
     * Lanterna'nın getGlobalPosition() değerinden ekranda çizildiği gerçek konumu çıkarır. İki sapma var:
     * (1) tüm konumlar, pencere içeriğinin kaydırılmış sayılması yüzünden çerçevenin (ekranda (0,0)) bildirdiği kadar
     * kaymıştır; (2) Border içindeki bileşenlerin konumuna kenarlık girintisi (1,1) eklenmez. İkincisi her sürümde
     * olmayabilir, bu yüzden çağıran tarafından ölçülerek verilir.
     *
     * @param reported          bileşenin getGlobalPosition() değeri
     * @param frameReported     ekranın (0,0) hücresine denk gelen en dış çerçevenin getGlobalPosition() değeri
     * @param insideBorder      bileşen bir Border'ın içinde mi (Border'ın kendisi için false)
     * @param borderInsetMissing Lanterna Border içindekilere girintiyi eklemiyor mu
     */
    static int[] visualOrigin(int[] reported, int[] frameReported, boolean insideBorder, boolean borderInsetMissing) {
        int extra = insideBorder && borderInsetMissing ? 1 : 0;
        return new int[]{reported[0] - frameReported[0] + extra, reported[1] - frameReported[1] + extra};
    }

    /**
     * (column, row) hücresi, sol-üst köşesi (left, top) olan width x height dikdörtgenin içinde mi?
     */
    static boolean contains(int left, int top, int width, int height, int column, int row) {
        return column >= left && column < left + width && row >= top && row < top + height;
    }

    /**
     * Çerçevedeki terminal bileşenine fare dinleyicileri ekler. Hücre boyutu piksel olarak verilir.
     */
    static void attach(SwingTerminalFrame frame, int cellWidth, int cellHeight, Handler handler) {
        for (Component child : frame.getContentPane().getComponents()) {
            if (child instanceof SwingTerminal terminal) {
                WheelAccumulator wheel = new WheelAccumulator();
                MouseAdapter listener = new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent e) {
                        if (e.getButton() == MouseEvent.BUTTON1) {
                            handler.onClick(cell(e.getX(), cellWidth), cell(e.getY(), cellHeight));
                        }
                    }

                    @Override
                    public void mouseWheelMoved(MouseWheelEvent e) {
                        int lines = wheel.add(e.getPreciseWheelRotation());
                        if (lines != 0) {
                            handler.onScroll(cell(e.getX(), cellWidth), cell(e.getY(), cellHeight), lines);
                        }
                    }
                };
                terminal.addMouseListener(listener);
                terminal.addMouseWheelListener(listener);
                return;
            }
        }
    }
}
