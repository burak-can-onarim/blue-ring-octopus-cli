package com.bcoworks.codeanalyzer.tui;

import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * banner.txt'i okur, ayraç satırlarını ve ortak girintiyi kırpar, genişliğe göre sürüm seçer.
 */
final class BannerArt {

    private static final List<String> COMPACT =
            List.of("BLUE RING OCTOPUS CLI  ·  Local AI Code Analyzer  ·  v0.1.0");
    private static final int MIN_ROWS_FOR_FULL = 30;

    private final List<String> full;
    private final int fullWidth;

    private BannerArt(List<String> full) {
        this.full = full;
        this.fullWidth = full.stream().mapToInt(String::length).max().orElse(0);
    }

    static BannerArt load() {
        try (InputStream in = new ClassPathResource("banner.txt").getInputStream()) {
            return new BannerArt(clean(StreamUtils.copyToString(in, StandardCharsets.UTF_8)));
        } catch (IOException e) {
            return new BannerArt(List.of());
        }
    }

    /**
     * Sığıyorsa tam banner, sığmıyorsa tek satırlık sürüm.
     */
    List<String> choose(int availableColumns, int terminalRows) {
        boolean fits = !full.isEmpty() && fullWidth <= availableColumns && terminalRows >= MIN_ROWS_FOR_FULL;
        return fits ? full : COMPACT;
    }

    private static List<String> clean(String raw) {
        List<String> lines = raw.lines()
                .filter(line -> !line.isBlank())
                .filter(line -> !line.strip().matches("=+"))
                .map(String::stripTrailing)
                .toList();
        int indent = lines.stream()
                .mapToInt(line -> line.length() - line.stripLeading().length())
                .min()
                .orElse(0);
        return lines.stream().map(line -> line.substring(indent)).toList();
    }
}