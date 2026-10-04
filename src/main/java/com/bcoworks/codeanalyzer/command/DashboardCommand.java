package com.bcoworks.codeanalyzer.command;

import com.bcoworks.codeanalyzer.context.AppContext;
import com.bcoworks.codeanalyzer.context.AppMode;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.Reference;
import org.jline.reader.Widget;
import org.jline.terminal.Terminal;
import org.jline.utils.InfoCmp;
import org.springframework.core.io.ClassPathResource;
import org.springframework.shell.standard.AbstractShellComponent;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@ShellComponent
public class DashboardCommand extends AbstractShellComponent {

    private final AppContext appContext;
    private boolean isRunning = true;
    private String bannerText = "";

    public DashboardCommand(AppContext appContext) {
        this.appContext = appContext;
        // banner.txt dosyasını bir kez oku ve belleğe al
        try {
            this.bannerText = StreamUtils.copyToString(new ClassPathResource("banner.txt").getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            this.bannerText = "--- Blue Ring Octopus CLI ---";
        }
    }

    @ShellMethod(key = {"ui", "dashboard"}, value = "Tam ekran interaktif TUI arayüzünü başlatır.")
    public void startDashboard() {
        Terminal terminal = getTerminal();
        LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .build();

        // İleri ve geri mod geçişleri için özel widget'lar
        Widget nextModeWidget = () -> {
            cycleMode(1);
            drawScreen(terminal);
            return true;
        };
        Widget prevModeWidget = () -> {
            cycleMode(-1);
            drawScreen(terminal);
            return true;
        };

        reader.getWidgets().put("next-mode", nextModeWidget);
        reader.getWidgets().put("prev-mode", prevModeWidget);

        // TAB tuşu (\t) ve Sağ/Sol Ok tuşları atamaları
        reader.getKeyMaps().get(LineReader.MAIN).bind(new Reference("next-mode"), "\t");
        reader.getKeyMaps().get(LineReader.MAIN).bind(new Reference("next-mode"), "\033[C"); // Sağ Ok
        reader.getKeyMaps().get(LineReader.MAIN).bind(new Reference("prev-mode"), "\033[D"); // Sol Ok

        while (isRunning) {
            drawScreen(terminal);

            try {
                // Kullanıcıdan giriş alma (Prompt alanı)
                String input = reader.readLine(">> ");

                if ("exit".equalsIgnoreCase(input.trim()) || "cikis".equalsIgnoreCase(input.trim())) {
                    isRunning = false;
                    System.out.println("Arayüz kapatılıyor...");
                    break;
                }

                // Girilen komutu veya path'i işleme alanı
                executeAction(input, terminal);

            } catch (org.jline.reader.UserInterruptException e) {
                // Ctrl+C basıldığında çıkış
                isRunning = false;
            }
        }
    }

    private void cycleMode(int direction) {
        AppMode[] modes = AppMode.values();
        int currentIndex = appContext.getCurrentMode().ordinal();
        int nextIndex = (currentIndex + direction + modes.length) % modes.length;
        appContext.setCurrentMode(modes[nextIndex]);
    }

    private void drawScreen(Terminal terminal) {
        // Ekranı temizle
        terminal.puts(InfoCmp.Capability.clear_screen);
        terminal.flush();

        String modeName = appContext.getCurrentMode().getDisplayName();

        // txt dosyasından okunan banner'ı ekrana yazdır
        terminal.writer().println(this.bannerText);

        terminal.writer().println("\n[ AKTİF MOD ]: " + modeName + " | (Mod değiştirmek için TAB veya Sağ/Sol okları kullanın)");
        terminal.writer().println("[ ÇALIŞMA DİZİNİ ]: " + System.getProperty("user.dir"));
        terminal.writer().println("------------------------------------------------------------------------------------------------------------------");

        terminal.writer().println("[ PROMPT ALANI ] Kod üretmek, analiz yapmak veya path girmek için yazın ('exit' ile çıkılır):");
        terminal.flush();
    }

    private void executeAction(String input, Terminal terminal) {
        // Çizimdeki "loading ve step bilgisi" alanı
        terminal.writer().println("\n[SİSTEM]: '" + input + "' işleniyor... (Loading bar ve model adımları buraya gelecek)");
        terminal.writer().println("İşlem tamamlandı. Devam etmek için Enter'a basın...");
        terminal.flush();
        try {
            int ignoredKey = terminal.reader().read();
        } catch (IOException ignored) {
        }
    }
}