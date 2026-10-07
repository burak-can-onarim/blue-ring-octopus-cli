package com.bcoworks.blueringoctopuscli.tui;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.Image;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Uygulama ikonu: pencere ve görev çubuğu için birkaç boyut (classpath: /icons/octopus-N.png).
 */
@Slf4j
final class AppIcons {

    static final int[] SIZES = {16, 32, 48, 64, 128, 256};

    private AppIcons() {
    }

    /**
     * Bulunabilen tüm boyutlar. Eksik ya da bozuk bir dosya uygulamayı durdurmaz, atlanır.
     */
    static List<Image> load() {
        List<Image> icons = new ArrayList<>();
        for (int size : SIZES) {
            String resource = "/icons/octopus-" + size + ".png";
            try (InputStream in = AppIcons.class.getResourceAsStream(resource)) {
                if (in == null) {
                    log.warn("Application icon not found: {}", resource);
                    continue;
                }
                Image image = ImageIO.read(in);
                if (image != null) {
                    icons.add(image);
                }
            } catch (IOException e) {
                log.warn("Could not read application icon: {} ({})", resource, e.getMessage());
            }
        }
        return icons;
    }
}
