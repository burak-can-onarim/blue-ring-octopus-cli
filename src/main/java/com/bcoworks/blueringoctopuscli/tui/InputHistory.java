package com.bcoworks.blueringoctopuscli.tui;

import java.util.ArrayList;
import java.util.List;

/**
 * Terminal tarzı geçmiş: ↑ eskiye, ↓ yeniye gider, yazılmakta olan taslak korunur.
 */
final class InputHistory {

    private static final int MAX_ENTRIES = 100;

    private final List<String> entries = new ArrayList<>();
    private int cursor;
    private String draft = "";

    /**
     * Boş girişleri ve art arda tekrarları yok sayar. İmleci sona alır.
     */
    void add(String entry) {
        String value = entry == null ? "" : entry.strip();
        if (!value.isEmpty() && (entries.isEmpty() || !entries.getLast().equals(value))) {
            entries.add(value);
            if (entries.size() > MAX_ENTRIES) {
                entries.removeFirst();
            }
        }
        cursor = entries.size();
        draft = "";
    }

    /**
     * Bir önceki kayıt, yoksa null. {@code current} henüz gönderilmemiş taslaktır.
     */
    String previous(String current) {
        if (entries.isEmpty() || cursor == 0) {
            return null;
        }
        if (cursor == entries.size()) {
            draft = current == null ? "" : current;
        }
        cursor--;
        return entries.get(cursor);
    }

    /**
     * Bir sonraki kayıt, en sondaysa taslak. Zaten taslaktaysa null.
     */
    String next() {
        if (cursor >= entries.size()) {
            return null;
        }
        cursor++;
        return cursor == entries.size() ? draft : entries.get(cursor);
    }
}