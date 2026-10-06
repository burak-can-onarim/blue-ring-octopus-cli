package com.bcoworks.blueringoctopuscli.tui;

import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * banner.txt'i okur, {{version}} yer tutucusunu doldurur, ayraç satırlarını ve ortak girintiyi kırpar.
 */
final class BannerArt {

    private static final String VERSION_TOKEN = "{{version}}";
    private static final int MIN_ROWS_FOR_FULL = 30;

    private final List<String> full;
    private final List<String> compact;
    private final int fullWidth;

    private BannerArt(List<String> full, String version) {
        this.full = full;
        this.compact = List.of("BLUE RING OCTOPUS CLI  ·  Local AI Code Assistant  ·  v" + version);
        this.fullWidth = full.stream().mapToInt(String::length).max().orElse(0);
    }

    static BannerArt load(String version) {
        try (InputStream in = new ClassPathResource("banner.txt").getInputStream()) {
            String raw = StreamUtils.copyToString(in, StandardCharsets.UTF_8).replace(VERSION_TOKEN, version);
            return new BannerArt(clean(raw), version);
        } catch (IOException e) {
            return new BannerArt(List.of(), version);
        }
    }

    /**
     * Sığıyorsa tam banner, sığmıyorsa tek satırlık sürüm.
     */
    List<String> choose(int availableColumns, int terminalRows) {
        boolean fits = !full.isEmpty() && fullWidth <= availableColumns && terminalRows >= MIN_ROWS_FOR_FULL;
        return fits ? full : compact;
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