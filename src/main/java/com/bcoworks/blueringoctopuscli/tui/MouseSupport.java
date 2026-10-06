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
     * Piksel konumunu hücre indeksine çevirir (negatif konumlar 0'a çekilir).
     */
    static int cell(int pixel, int cellSize) {
        return cellSize <= 0 ? 0 : Math.max(0, pixel) / cellSize;
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
                MouseAdapter listener = new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent e) {
                        if (e.getButton() == MouseEvent.BUTTON1) {
                            handler.onClick(cell(e.getX(), cellWidth), cell(e.getY(), cellHeight));
                        }
                    }

                    @Override
                    public void mouseWheelMoved(MouseWheelEvent e) {
                        int notches = e.getWheelRotation();
                        if (notches != 0) {
                            handler.onScroll(cell(e.getX(), cellWidth), cell(e.getY(), cellHeight),
                                    notches * LINES_PER_WHEEL_NOTCH);
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
