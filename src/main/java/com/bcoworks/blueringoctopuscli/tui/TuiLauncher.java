package com.bcoworks.blueringoctopuscli.tui;

import com.bcoworks.blueringoctopuscli.config.AppInfo;
import com.bcoworks.blueringoctopuscli.context.AppContext;
import com.bcoworks.blueringoctopuscli.mode.ModeDispatcher;
import com.bcoworks.blueringoctopuscli.model.InstalledModels;
import com.bcoworks.blueringoctopuscli.model.ModelSettings;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.MultiWindowTextGUI;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;
import com.googlecode.lanterna.terminal.swing.SwingTerminalFontConfiguration;
import com.googlecode.lanterna.terminal.swing.SwingTerminalFrame;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.swing.WindowConstants;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;

@Component
public class TuiLauncher {

    /**
     * Açılış boyutu aynı zamanda en küçük boyuttur (Swing penceresinde).
     */
    private static final TerminalSize INITIAL_SIZE = new TerminalSize(140, 38);
    private static final int FONT_SIZE = 15;
    private static final List<String> PREFERRED_FONTS =
            List.of("Cascadia Mono", "JetBrains Mono", "Consolas", "DejaVu Sans Mono", "Menlo");

    private final AppContext appContext;
    private final ModeDispatcher dispatcher;
    private final ExecutorService aiExecutor;
    private final BannerArt banner;
    private final ModelSettings modelSettings;
    private final InstalledModels installedModels;

    public TuiLauncher(AppContext appContext,
                       ModeDispatcher dispatcher,
                       @Qualifier("aiTaskExecutor") ExecutorService aiExecutor,
                       AppInfo appInfo,
                       ModelSettings modelSettings,
                       InstalledModels installedModels) {
        this.appContext = appContext;
        this.dispatcher = dispatcher;
        this.aiExecutor = aiExecutor;
        this.banner = BannerArt.load(appInfo.version());
        this.modelSettings = modelSettings;
        this.installedModels = installedModels;
    }

    /**
     * TUI kapanana kadar bloklar.
     */
    public void launch() throws IOException {
        boolean emulator = isWindows();

        DefaultTerminalFactory factory = new DefaultTerminalFactory()
                .setInitialTerminalSize(INITIAL_SIZE)
                .setPreferTerminalEmulator(emulator)
                .setTerminalEmulatorTitle("Blue Ring Octopus CLI");
        if (emulator) {
            factory.setTerminalEmulatorFontConfiguration(SwingTerminalFontConfiguration.newInstance(pickFont()));
        }

        Terminal terminal = factory.createTerminal();
        Screen screen = new TerminalScreen(terminal);
        screen.startScreen();
        try {
            MultiWindowTextGUI gui = new MultiWindowTextGUI(screen);
            gui.setTheme(OctopusTheme.main());

            MainWindow main = new MainWindow(gui, appContext, dispatcher, aiExecutor, banner, modelSettings, installedModels);
            terminal.addResizeListener((_, size) -> main.onResize(size));
            if (terminal instanceof SwingTerminalFrame frame) {
                configureFrame(frame, main);
            }
            try {
                main.show();
            } finally {
                main.shutdown();
            }
        } finally {
            screen.stopScreen();
        }
    }

    /**
     * X düğmesi doğrudan kapatmaz, onay ister. Açılış boyutu en küçük boyut olur. Pencereye uygulama ikonu verilir.
     */
    private static void configureFrame(SwingTerminalFrame frame, MainWindow main) {
        List<Image> icons = AppIcons.load();
        if (!icons.isEmpty()) {
            frame.setIconImages(icons);
        }
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                main.requestExit();
            }
        });

        Dimension size = frame.getSize();
        if (size.width > 0 && size.height > 0) {
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            frame.setMinimumSize(new Dimension(
                    Math.min(size.width, screen.width),
                    Math.min(size.height, screen.height)));
        }
    }

    /**
     * Kurulu ilk tercih edilen monospace aile; yoksa Java'nın mantıksal monospace fontu.
     */
    private static Font pickFont() {
        Set<String> installed = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        String family = PREFERRED_FONTS.stream()
                .filter(installed::contains)
                .findFirst()
                .orElse(Font.MONOSPACED);
        return new Font(family, Font.PLAIN, FONT_SIZE);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}