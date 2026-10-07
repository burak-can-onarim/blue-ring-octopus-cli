package com.bcoworks.blueringoctopuscli.i18n;

import com.bcoworks.blueringoctopuscli.context.AppMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the language files: the maintainer cannot read every language, so these tests make sure a translation is
 * complete and keeps the format placeholders, line breaks and widths the code relies on.
 */
class MessagesTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("%[sd]");
    /** Width of the model panel's status bar (SIDE_INNER 28 minus the one-cell margin). */
    private static final int PANEL_WIDTH = 27;

    @TempDir
    Path tmp;

    private static Map<String, String> english() {
        return Messages.table(Language.EN);
    }

    private static List<String> placeholders(String text) {
        List<String> found = new ArrayList<>();
        Matcher matcher = PLACEHOLDER.matcher(text);
        while (matcher.find()) {
            found.add(matcher.group());
        }
        return found;
    }

    private static long lineBreaks(String text) {
        return text.chars().filter(c -> c == '\n').count();
    }

    // ---------------------------------------------------------------- the language files

    @Test
    void everyLanguageDefinesExactlyTheEnglishKeys() {
        for (Language language : Language.values()) {
            assertEquals(new TreeSet<>(english().keySet()), new TreeSet<>(Messages.table(language).keySet()),
                    language.code());
        }
    }

    @Test
    void noTextIsBlank() {
        for (Language language : Language.values()) {
            Messages.table(language).forEach((key, value) ->
                    assertFalse(value.isBlank(), language.code() + ": " + key));
        }
    }

    @Test
    void placeholdersAndLineBreaksMatchTheEnglishText() {
        for (Language language : Language.values()) {
            Messages.table(language).forEach((key, value) -> {
                String reference = english().get(key);
                assertEquals(placeholders(reference), placeholders(value), language.code() + ": " + key);
                assertEquals(lineBreaks(reference), lineBreaks(value), language.code() + ": " + key + " (line breaks)");
            });
        }
    }

    @Test
    void everyModeHasANameAndHintsInEveryLanguage() {
        for (Language language : Language.values()) {
            Map<String, String> table = Messages.table(language);
            for (AppMode mode : AppMode.values()) {
                for (String part : List.of("name", "prompt", "path")) {
                    String key = "mode." + mode.name() + "." + part;
                    assertTrue(table.containsKey(key) && !table.get(key).isBlank(), language.code() + ": " + key);
                }
            }
        }
    }

    @Test
    void problemMessagesStartWithTheirLanguageLabel() {
        for (Language language : Language.values()) {
            Map<String, String> table = Messages.table(language);
            table.forEach((key, value) -> {
                if (key.startsWith("error.")) {
                    assertTrue(value.startsWith(table.get("label.error")), language.code() + ": " + key);
                    assertTrue(Messages.isProblemLine(value), language.code() + ": " + key);
                } else if (key.startsWith("warning.")) {
                    assertTrue(value.startsWith(table.get("label.warning")), language.code() + ": " + key);
                    assertTrue(Messages.isProblemLine(value), language.code() + ": " + key);
                }
            });
        }
    }

    @Test
    void yesAndNoAnswerKeysAreLowercaseAndDoNotOverlap() {
        for (Language language : Language.values()) {
            String yes = Messages.table(language).get("dialog.keys.yes");
            String no = Messages.table(language).get("dialog.keys.no");
            assertEquals(yes.toLowerCase(language.locale()), yes, language.code());
            assertEquals(no.toLowerCase(language.locale()), no, language.code());
            assertTrue(yes.chars().noneMatch(c -> no.indexOf(c) >= 0), language.code());
        }
    }

    @Test
    void modelPanelTextsFitTheNarrowPanel() {
        for (Language language : Language.values()) {
            Map<String, String> t = Messages.table(language);
            String code = language.code();
            assertTrue(t.get("model.checking").length() <= PANEL_WIDTH, code + ": model.checking");
            assertTrue(t.get("model.unreachable").length() <= PANEL_WIDTH, code + ": model.unreachable");
            assertTrue(t.get("legend.selected").length() <= PANEL_WIDTH - 2, code + ": legend.selected");
            assertTrue(t.get("legend.installed").length() <= PANEL_WIDTH - 2, code + ": legend.installed");
            assertTrue(t.get("legend.missing").length() <= PANEL_WIDTH - 2, code + ": legend.missing");
            // "Enter <select> · Esc <back>"
            int bar = "Enter ".length() + t.get("model.select").length() + " · Esc ".length() + t.get("model.back").length();
            assertTrue(bar <= PANEL_WIDTH, code + ": Enter/Esc bar is " + bar + " wide");
        }
    }

    // ---------------------------------------------------------------- Messages

    @Test
    void defaultsToEnglish() {
        Messages messages = new Messages("", tmp.resolve("settings.properties"));

        assertEquals(Language.EN, messages.language());
        assertEquals("Ready", messages.get("status.ready"));
    }

    @Test
    void anOverrideSelectsTheLanguage() {
        Messages messages = new Messages("de", tmp.resolve("settings.properties"));

        assertEquals(Language.DE, messages.language());
        assertEquals("Bereit", messages.get("status.ready"));
    }

    @Test
    void anUnknownOverrideFallsBackToTheSavedChoiceThenEnglish() throws IOException {
        Path file = tmp.resolve("settings.properties");
        assertEquals(Language.EN, new Messages("klingon", file).language());

        Files.writeString(file, "language=fr\n");
        assertEquals(Language.FR, new Messages("klingon", file).language());
    }

    @Test
    void theOverrideBeatsTheSavedChoice() throws IOException {
        Path file = tmp.resolve("settings.properties");
        Files.writeString(file, "language=fr\n");

        assertEquals(Language.IT, new Messages("it", file).language());
        assertEquals(Language.FR, new Messages("", file).language());
    }

    @Test
    void theChosenLanguageIsSavedAndRestored() throws IOException {
        Path file = tmp.resolve("sub").resolve("settings.properties");
        Messages messages = new Messages("", file);

        messages.setLanguage(Language.ES);

        assertEquals(Language.ES, messages.language());
        assertTrue(Files.readString(file).contains("language=es"));
        assertEquals(Language.ES, new Messages("", file).language());
    }

    @Test
    void savingKeepsOtherSettings() throws IOException {
        Path file = tmp.resolve("settings.properties");
        Files.writeString(file, "theme=dark\n");

        new Messages("", file).setLanguage(Language.TR);

        String content = Files.readString(file);
        assertTrue(content.contains("theme=dark"));
        assertTrue(content.contains("language=tr"));
    }

    @Test
    void formatsArgumentsAndSwitchesLanguageAtOnce() {
        Messages messages = new Messages("", tmp.resolve("settings.properties"));
        assertEquals("Files found: 1 (src)", messages.get("analysis.found", 1, "src"));

        messages.setLanguage(Language.TR);
        assertEquals("1 dosya bulundu: src", messages.get("analysis.found", 1, "src"));
        assertEquals("Kod Analizi", messages.modeName(AppMode.CODE_ANALYSIS));
    }

    @Test
    void anUnknownKeyIsReturnedAsIs() {
        assertEquals("no.such.key", new Messages("", tmp.resolve("settings.properties")).get("no.such.key"));
    }

    @Test
    void recognizesProblemLinesInEveryLanguageButNotNormalText() {
        for (Language language : Language.values()) {
            Map<String, String> table = Messages.table(language);
            assertTrue(Messages.isProblemLine(table.get("label.error") + ": x"), language.code());
            assertTrue(Messages.isProblemLine(table.get("label.warning") + " : x"), language.code());
        }
        assertFalse(Messages.isProblemLine("Error handling is missing in this method"));
        assertFalse(Messages.isProblemLine("The class is fine"));
    }
}
