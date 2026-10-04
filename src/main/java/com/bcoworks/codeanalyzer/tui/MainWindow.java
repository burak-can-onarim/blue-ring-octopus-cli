package com.bcoworks.codeanalyzer.tui;

import com.bcoworks.codeanalyzer.context.AppContext;
import com.bcoworks.codeanalyzer.context.AppMode;
import com.bcoworks.codeanalyzer.mode.IModeConsole;
import com.bcoworks.codeanalyzer.mode.ModeDispatcher;
import com.bcoworks.codeanalyzer.mode.ModeRequest;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.input.KeyStroke;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ┌ uygulama çerçevesi ───────────────────────┬ sağ panel ┐
 * │ banner                                    │ (ayrılmış)│
 * │ prompt alanı (çıktı + giriş)              │           │
 * │ path alanı                                │           │
 * ├ mod ┬ loading / step ──────────────────────┴───────────┤
 * │ kısayollar                                            │
 */
@Slf4j
final class MainWindow {

    /**
     * Sağ pop-up alanı. false yapılırsa hiç yer kaplamaz.
     */
    private static final boolean SHOW_SIDE_PANEL = true;
    private static final int SIDE_PANEL_WIDTH = 30; // kenarlık dahil

    private static final String[] SPINNER = {"|", "/", "-", "\\"};
    private static final int PROGRESS_CELLS = 12;
    private static final String BLANK_PROGRESS = " ".repeat(PROGRESS_CELLS + 6);
    private static final int MODE_TEXT_WIDTH = Arrays.stream(AppMode.values())
            .mapToInt(mode -> mode.getDisplayName().length())
            .max()
            .orElse(12) + 4;
    private static final String KEYS =
            " Enter Çalıştır  ·  ↑↓ Prompt/Path  ·  Tab Mod  ·  PgUp/PgDn Kaydır  ·  Esc Çıkış";

    private final MultiWindowTextGUI gui;
    private final AppContext appContext;
    private final ModeDispatcher dispatcher;
    private final ExecutorService aiExecutor;
    private final BannerArt banner;

    private final BasicWindow window = new BasicWindow("Blue Ring Octopus CLI");
    private final Panel bannerPanel = new Panel(linear(Direction.VERTICAL, 0));
    private final TextBox transcript = new TextBox(new TerminalSize(40, 6), TextBox.Style.MULTI_LINE).setReadOnly(true);
    private final TextBox promptInput = new TextBox(new TerminalSize(40, 1));
    private final TextBox pathInput = new TextBox(new TerminalSize(40, 1));
    private final Label hintLabel = new Label("");
    private final Label modeLabel = new Label("");
    private final Label stepLabel = new Label("");
    private final Label progressLabel = new Label(BLANK_PROGRESS);

    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final ScheduledExecutorService spinnerExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "tui-spinner");
        thread.setDaemon(true);
        return thread;
    });
    private volatile String currentStep = "";
    private volatile int progressDone;
    private volatile int progressTotal;
    private ScheduledFuture<?> spinnerTask;
    private List<String> shownBanner = List.of();

    private final IModeConsole console = new IModeConsole() {
        @Override
        public void step(String message) {
            currentStep = message;
        }

        @Override
        public void println(String text) {
            ui(() -> appendOutput(text));
        }

        @Override
        public void progress(int done, int total) {
            progressDone = done;
            progressTotal = total;
        }
    };

    MainWindow(MultiWindowTextGUI gui, AppContext appContext, ModeDispatcher dispatcher,
               ExecutorService aiExecutor, BannerArt banner) {
        this.gui = gui;
        this.appContext = appContext;
        this.dispatcher = dispatcher;
        this.aiExecutor = aiExecutor;
        this.banner = banner;
    }

    void show() {
        build();
        gui.addWindowAndWait(window);
    }

    void shutdown() {
        spinnerExecutor.shutdownNow();
    }

    /**
     * Terminal yeniden boyutlandığında banner sürümünü günceller (başka thread'den çağrılır).
     */
    void onResize(TerminalSize size) {
        ui(() -> applyBanner(size));
    }

    // ---------------------------------------------------------------- layout

    private void build() {
        // --- sol sütun: banner / prompt alanı / path alanı
        Panel bannerHolder = new Panel(linear(Direction.VERTICAL, 0));
        bannerPanel.setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Center));
        bannerHolder.addComponent(bannerPanel);
        Border bannerBox = bannerHolder.withBorder(Borders.singleLine());
        bannerBox.setLayoutData(fill());

        Panel promptContent = new Panel(linear(Direction.VERTICAL, 0));
        transcript.setLayoutData(grow());
        promptContent.addComponent(transcript);

        Separator separator = new Separator(Direction.HORIZONTAL);
        separator.setLayoutData(fill());
        promptContent.addComponent(separator);

        hintLabel.setForegroundColor(OctopusTheme.YELLOW);
        promptContent.addComponent(hintLabel);

        promptContent.addComponent(inputRow(OctopusTheme.MAUVE, promptInput));
        Border promptBox = promptContent.withBorder(Borders.singleLine(" Prompt Alanı "));
        promptBox.setLayoutData(grow());

        Border pathBox = inputRow(OctopusTheme.TEAL, pathInput)
                .withBorder(Borders.singleLine(" Path · çalışma dizini: " + shorten(System.getProperty("user.dir")) + " "));
        pathBox.setLayoutData(fill());

        Panel left = new Panel(linear(Direction.VERTICAL, 0));
        left.setLayoutData(grow());
        left.addComponent(bannerBox);
        left.addComponent(promptBox);
        left.addComponent(pathBox);

        Panel mainRow = new Panel(linear(Direction.HORIZONTAL, 1));
        mainRow.setLayoutData(grow());
        mainRow.addComponent(left);
        if (SHOW_SIDE_PANEL) {
            mainRow.addComponent(buildSidePanel());
        }

        // --- alt satır: mod bilgisi / loading ve step
        modeLabel.setForegroundColor(OctopusTheme.MAUVE);
        Border modeBox = modeLabel.withBorder(Borders.singleLine(" Mod "));

        stepLabel.setLayoutData(grow());
        progressLabel.setForegroundColor(OctopusTheme.BLUE);
        Panel stepRow = new Panel(linear(Direction.HORIZONTAL, 1));
        stepRow.addComponent(stepLabel);
        stepRow.addComponent(progressLabel);
        Border stepBox = stepRow.withBorder(Borders.singleLine(" Durum "));
        stepBox.setLayoutData(grow());

        Panel statusRow = new Panel(linear(Direction.HORIZONTAL, 1));
        statusRow.setLayoutData(fill());
        statusRow.addComponent(modeBox);
        statusRow.addComponent(stepBox);
        if (SHOW_SIDE_PANEL) {
            // sağ panelin altını boş bırakır, kutular sol sütunla hizalı kalır
            statusRow.addComponent(new EmptySpace(new TerminalSize(SIDE_PANEL_WIDTH, 1)));
        }

        Label keysLabel = new Label(KEYS);
        keysLabel.setForegroundColor(OctopusTheme.MUTED);

        // --- uygulama çerçevesi
        Panel content = new Panel(linear(Direction.VERTICAL, 0));
        content.addComponent(mainRow);
        content.addComponent(statusRow);
        content.addComponent(keysLabel);
        Border frame = content.withBorder(Borders.doubleLine(" Blue Ring Octopus CLI "));

        window.setHints(List.of(Window.Hint.FULL_SCREEN, Window.Hint.NO_DECORATIONS, Window.Hint.NO_POST_RENDERING));
        window.setComponent(frame);
        window.setFocusedInteractable(promptInput);
        window.addWindowListener(new WindowListenerAdapter() {
            @Override
            public void onInput(Window basePane, KeyStroke key, AtomicBoolean deliverEvent) {
                handleKey(key, deliverEvent);
            }
        });

        applyBanner(gui.getScreen().getTerminalSize());
        refreshMode();
        setStatus(" Hazır", OctopusTheme.GREEN);
        appendOutput("Hoş geldin. Tab ile mod değiştir, prompt'u yazıp Enter'a bas.");
    }

    private Border buildSidePanel() {
        int width = SIDE_PANEL_WIDTH - 2;
        StringBuilder text = new StringBuilder();
        for (String line : List.of("", " Pop-up ve seçenek", " paneli için ayrıldı.", "",
                " İhtiyaç doğduğunda", " tasarlanacak.")) {
            text.append(String.format("%-" + width + "s", line)).append('\n');
        }
        Label content = new Label(text.toString().stripTrailing());
        content.setForegroundColor(OctopusTheme.MUTED);
        Border box = content.withBorder(Borders.singleLine(" Panel "));
        box.setLayoutData(fill());
        return box;
    }

    private Panel inputRow(TextColor signColor, TextBox box) {
        Label signLabel = new Label(" » ");
        signLabel.setForegroundColor(signColor);
        box.setLayoutData(grow());
        Panel row = new Panel(linear(Direction.HORIZONTAL, 0));
        row.setLayoutData(fill());
        row.addComponent(signLabel);
        row.addComponent(box);
        return row;
    }

    private void applyBanner(TerminalSize size) {
        int available = size.getColumns() - 2 - 2 - (SHOW_SIDE_PANEL ? SIDE_PANEL_WIDTH + 1 : 0);
        List<String> lines = banner.choose(available, size.getRows());
        if (lines.equals(shownBanner)) {
            return;
        }
        shownBanner = lines;
        bannerPanel.removeAllComponents();
        for (int i = 0; i < lines.size(); i++) {
            Label label = new Label(lines.get(i));
            label.setForegroundColor(bannerColor(i, lines.size()));
            bannerPanel.addComponent(label);
        }
    }

    /**
     * Sanat satırlarında mavi → mor geçiş, son satır (alt başlık) soluk.
     */
    private static TextColor bannerColor(int index, int count) {
        if (count == 1) {
            return OctopusTheme.BLUE;
        }
        if (index == count - 1) {
            return OctopusTheme.MUTED;
        }
        double t = count <= 2 ? 0 : index / (double) (count - 2);
        return OctopusTheme.lerp(OctopusTheme.BLUE, OctopusTheme.MAUVE, t);
    }

    // ---------------------------------------------------------------- input

    private void handleKey(KeyStroke key, AtomicBoolean deliverEvent) {
        switch (key.getKeyType()) {
            case Enter -> {
                deliverEvent.set(false);
                submit();
            }
            case Tab -> {
                deliverEvent.set(false);
                switchMode(true);
            }
            case ReverseTab -> {
                deliverEvent.set(false);
                switchMode(false);
            }
            case ArrowDown -> {
                deliverEvent.set(false);
                focus(pathInput);
            }
            case ArrowUp -> {
                deliverEvent.set(false);
                focus(promptInput);
            }
            case PageUp, PageDown -> {
                deliverEvent.set(false);
                transcript.handleInput(key); // giriş kutusu odaktayken çıktıyı kaydır
            }
            case Escape, EOF -> {
                deliverEvent.set(false);
                window.close();
            }
            case Character -> {
                Character c = key.getCharacter();
                if (key.isCtrlDown() && c != null && Character.toLowerCase(c) == 'c') {
                    deliverEvent.set(false);
                    window.close();
                }
            }
            default -> {
            }
        }
    }

    private void focus(Interactable target) {
        window.setFocusedInteractable(target);
        refreshHint();
    }

    private void switchMode(boolean forward) {
        AppMode current = appContext.getCurrentMode();
        appContext.setCurrentMode(forward ? current.next() : current.previous());
        refreshMode();
    }

    private void refreshMode() {
        AppMode mode = appContext.getCurrentMode();
        modeLabel.setText(String.format("%-" + MODE_TEXT_WIDTH + "s", "‹ " + mode.getDisplayName() + " ›"));
        refreshHint();
    }

    private void refreshHint() {
        AppMode mode = appContext.getCurrentMode();
        boolean onPath = window.getFocusedInteractable() == pathInput;
        hintLabel.setText(onPath ? " Path: " + mode.getPathHint() : " Prompt: " + mode.getPromptHint());
    }

    // ---------------------------------------------------------------- execution

    private void submit() {
        AppMode mode = appContext.getCurrentMode();
        String prompt = promptInput.getText().strip();
        String path = pathInput.getText().strip();

        if ("exit".equalsIgnoreCase(prompt) || "cikis".equalsIgnoreCase(prompt)) {
            window.close();
            return;
        }
        if (prompt.isEmpty() && mode != AppMode.KOD_ANALIZI) {
            setStatus(" Önce prompt alanına bir istek yazın.", OctopusTheme.YELLOW);
            return;
        }
        if (!busy.compareAndSet(false, true)) {
            setStatus(" Önceki işlem sürüyor, lütfen bekleyin...", OctopusTheme.YELLOW);
            return;
        }

        promptInput.setText("");
        if (mode == AppMode.KOD_GENERATE) {
            pathInput.setText(""); // aynı dosyaya ikinci kez yazma hatasını önler
        }
        appendOutput("\n» [" + mode.getDisplayName() + "] " + describe(mode, prompt, path));

        currentStep = "Başlatılıyor...";
        progressDone = 0;
        progressTotal = 0;
        startSpinner();

        ModeRequest request = new ModeRequest(prompt, path);
        aiExecutor.submit(() -> run(mode, request));
    }

    private static String describe(AppMode mode, String prompt, String path) {
        return switch (mode) {
            case KOD_ANALIZI -> path.isEmpty() ? "(çalışma dizini)" : path;
            case KOD_GENERATE -> path.isEmpty() ? prompt : prompt + "  →  " + path;
            default -> prompt;
        };
    }

    private void run(AppMode mode, ModeRequest request) {
        try {
            dispatcher.dispatch(mode, request, console);
        } catch (Exception e) {
            log.error("Mod çalıştırılırken hata: {}", mode, e);
            String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            ui(() -> appendOutput("Hata: " + message));
        } finally {
            ui(this::finishTask);
        }
    }

    private void startSpinner() {
        AtomicInteger frame = new AtomicInteger();
        spinnerTask = spinnerExecutor.scheduleAtFixedRate(() -> {
            String step = " " + SPINNER[frame.getAndIncrement() % SPINNER.length] + "  " + currentStep;
            String bar = progressText(progressDone, progressTotal);
            ui(() -> {
                if (!busy.get()) {
                    return; // işlem bittikten sonra gecikmiş bir tik "Hazır"ı ezmesin
                }
                setStatus(step, OctopusTheme.TEXT);
                progressLabel.setText(bar);
            });
        }, 0, 120, TimeUnit.MILLISECONDS);
    }

    private void finishTask() {
        if (spinnerTask != null) {
            spinnerTask.cancel(false);
        }
        setStatus(" Hazır", OctopusTheme.GREEN);
        progressLabel.setText(BLANK_PROGRESS);
        busy.set(false);
    }

    // ---------------------------------------------------------------- helpers

    private void setStatus(String text, TextColor color) {
        stepLabel.setText(text);
        stepLabel.setForegroundColor(color);
    }

    private static String progressText(int done, int total) {
        if (total <= 0) {
            return BLANK_PROGRESS;
        }
        int percent = Math.min(100, done * 100 / total);
        int filled = percent * PROGRESS_CELLS / 100;
        return " " + "█".repeat(filled) + "░".repeat(PROGRESS_CELLS - filled) + String.format(" %3d%%", percent);
    }

    private void ui(Runnable task) {
        gui.getGUIThread().invokeLater(task);
    }

    private void appendOutput(String text) {
        int columns = gui.getScreen().getTerminalSize().getColumns();
        // çerçeve(2) + kutu(2) + kaydırma çubuğu(1) + pay(1) + sağ panel
        int width = Math.max(20, columns - 6 - (SHOW_SIDE_PANEL ? SIDE_PANEL_WIDTH + 1 : 0));
        for (String line : text.replace("\t", "    ").split("\\R", -1)) {
            for (String part : wrap(line, width)) {
                transcript.addLine(part);
            }
        }
        transcript.setCaretPosition(transcript.getLineCount() - 1, 0);
    }

    /**
     * Kelime sınırında böler, boşluk yoksa sert keser.
     */
    private static List<String> wrap(String line, int width) {
        if (line.length() <= width) {
            return List.of(line);
        }
        List<String> parts = new ArrayList<>();
        String rest = line;
        while (rest.length() > width) {
            int lead = rest.length() - rest.stripLeading().length();
            int cut = rest.lastIndexOf(' ', width);
            if (cut <= lead) {
                cut = width;
            }
            parts.add(rest.substring(0, cut).stripTrailing());
            rest = rest.substring(cut).stripLeading();
        }
        parts.add(rest);
        return parts;
    }

    private static String shorten(String text) {
        return text.length() <= 70 ? text : "..." + text.substring(text.length() - 70 + 3);
    }

    private static LinearLayout linear(Direction direction, int spacing) {
        LinearLayout layout = new LinearLayout(direction);
        layout.setSpacing(spacing);
        return layout;
    }

    private static LayoutData fill() {
        return LinearLayout.createLayoutData(LinearLayout.Alignment.Fill);
    }

    private static LayoutData grow() {
        return LinearLayout.createLayoutData(LinearLayout.Alignment.Fill, LinearLayout.GrowPolicy.CanGrow);
    }
}