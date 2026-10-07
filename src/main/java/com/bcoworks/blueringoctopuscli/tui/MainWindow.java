package com.bcoworks.blueringoctopuscli.tui;

import com.bcoworks.blueringoctopuscli.context.AppContext;
import com.bcoworks.blueringoctopuscli.context.AppMode;
import com.bcoworks.blueringoctopuscli.mode.IModeConsole;
import com.bcoworks.blueringoctopuscli.mode.ModeDispatcher;
import com.bcoworks.blueringoctopuscli.mode.ModeRequest;
import com.bcoworks.blueringoctopuscli.model.InstalledModels;
import com.bcoworks.blueringoctopuscli.model.ModelCatalog;
import com.bcoworks.blueringoctopuscli.model.ModelSettings;
import com.bcoworks.blueringoctopuscli.util.PathUtils;
import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ┌ uygulama çerçevesi ───────────────────────┬ model paneli ┐
 * │ banner                                    │ mod başına   │
 * │ prompt alanı (çıktı + çok satırlı giriş)  │ model seçimi │
 * │ path alanı                                │              │
 * ├ mod ┬ loading / step ──────────────────────┼ aktif model ─┤
 * │ kısayollar                                               │
 */
@Slf4j
final class MainWindow implements MouseSupport.Handler {

    private static final int SIDE_PANEL_WIDTH = 30; // kenarlık dahil
    private static final int SIDE_INNER = SIDE_PANEL_WIDTH - 2;

    private static final String[] SPINNER = {"|", "/", "-", "\\"};
    private static final int PROGRESS_CELLS = 12;
    private static final String BLANK_PROGRESS = " ".repeat(PROGRESS_CELLS + 6);
    private static final int MODE_TEXT_WIDTH = Arrays.stream(AppMode.values())
            .mapToInt(mode -> mode.getDisplayName().length())
            .max()
            .orElse(12) + 4;
    private static final long NOTICE_MILLIS = 3_000;

    private final MultiWindowTextGUI gui;
    private final AppContext appContext;
    private final ModeDispatcher dispatcher;
    private final ExecutorService aiExecutor;
    private final BannerArt banner;
    private final ModelSettings modelSettings;
    private final InstalledModels installedModels;

    private final BasicWindow window = new BasicWindow("Blue Ring Octopus CLI");
    private final Panel bannerPanel = new Panel(linear(Direction.VERTICAL, 0));
    private final DialogView dialog = new DialogView();
    private Border dialogBox;
    private Border promptBox;
    private Border pathBox;
    private final PromptArea promptInput = new PromptArea();
    private final TextBox pathInput = new TextBox(new TerminalSize(40, 1));
    private final Label hintLabel = new Label("");
    private final Label modeLabel = new Label("");
    private final Label stepLabel = new Label("");
    private final Label progressLabel = new Label(BLANK_PROGRESS);

    private final Label modelModeLabel = new Label("");
    private final KeyHintBar modelStatusBar = new KeyHintBar(List.of(new KeyHintBar.Row(List.of())), 0);
    private final Label activeModelLabel = new Label("");
    private final ModelList modelList = new ModelList();

    private final InputHistory promptHistory = new InputHistory();
    private final InputHistory pathHistory = new InputHistory();
    private final AtomicBoolean dialogOpen = new AtomicBoolean(false);
    private final StringBuilder lastOutput = new StringBuilder(); // yalnızca UI thread'i dokunur

    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final ScheduledExecutorService spinnerExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "tui-spinner");
        thread.setDaemon(true);
        return thread;
    });
    private volatile Job activeJob;
    private volatile String currentStep = "";
    private volatile int progressDone;
    private volatile int progressTotal;
    private volatile long noticeUntil;
    private ScheduledFuture<?> spinnerTask;
    private List<String> shownBanner = List.of();

    /**
     * Çalışan tek bir işlem. İptal edilince çıktıları yok sayılır, arayüz hemen serbest kalır.
     */
    private final class Job implements IModeConsole {

        private volatile boolean cancelled;
        private volatile Future<?> future;

        @Override
        public void step(String message) {
            if (!cancelled) {
                currentStep = message;
            }
        }

        @Override
        public void println(String text) {
            if (cancelled) {
                return;
            }
            ui(() -> {
                if (!cancelled) {
                    dialog.addAnswer(text);
                    lastOutput.append(text).append('\n');
                }
            });
        }

        @Override
        public void progress(int done, int total) {
            if (!cancelled) {
                progressDone = done;
                progressTotal = total;
            }
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        void cancel() {
            cancelled = true;
            Future<?> running = future;
            if (running != null) {
                running.cancel(true); // thread'e interrupt gönderir
            }
        }
    }

    MainWindow(MultiWindowTextGUI gui, AppContext appContext, ModeDispatcher dispatcher,
               ExecutorService aiExecutor, BannerArt banner,
               ModelSettings modelSettings, InstalledModels installedModels) {
        this.gui = gui;
        this.appContext = appContext;
        this.dispatcher = dispatcher;
        this.aiExecutor = aiExecutor;
        this.banner = banner;
        this.modelSettings = modelSettings;
        this.installedModels = installedModels;
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

    // ---------------------------------------------------------------- mouse

    /**
     * Sol tık: prompt ya da path kutusuna (kenarlık ve ipucu dahil) odaklanır; alanın içindeyse imleç de oraya gider.
     * Başka hiçbir şey fareyle tetiklenmez.
     */
    @Override
    public void onClick(int column, int row) {
        ui(() -> {
            if (dialogOpen.get()) {
                return;
            }
            if (isOverBox(promptBox, column, row)) {
                focusField(promptInput);
                if (isOver(promptInput, true, column, row)) {
                    int[] at = originOf(promptInput, true);
                    promptInput.placeCaretAt(column - at[0], row - at[1]);
                }
            } else if (isOverBox(pathBox, column, row)) {
                focusField(pathInput);
                if (isOver(pathInput, true, column, row)) {
                    placePathCaret(column - originOf(pathInput, true)[0]);
                }
            }
        });
    }

    /**
     * Tekerlek: imlecin üstündeki diyalog ya da prompt kutusunu kaydırır (negatif yukarı).
     */
    @Override
    public void onScroll(int column, int row, int lines) {
        ui(() -> {
            if (dialogOpen.get()) {
                return;
            }
            if (isOverBox(dialogBox, column, row)) {
                dialog.scroll(lines);
            } else if (isOverBox(promptBox, column, row)) {
                promptInput.scroll(lines);
            }
        });
    }

    /**
     * Bileşenin ekranda çizildiği sol-üst hücre (sütun, satır). Lanterna'nın global konumları çizimden sapar, bkz.
     * {@link MouseSupport#visualOrigin}. Border'ın kendisi için insideBorder=false, içindekiler için true verilir.
     */
    private int[] originOf(Component component, boolean insideBorder) {
        TerminalPosition at = component.getGlobalPosition();
        TerminalPosition frame = window.getComponent().getGlobalPosition();
        // diyalog kutusunun içindeki DialogView ile kutunun konumu aynıysa, Lanterna kenarlık girintisini eklemiyor
        boolean insetMissing = dialog.getGlobalPosition().equals(dialogBox.getGlobalPosition());
        return MouseSupport.visualOrigin(new int[]{at.getColumn(), at.getRow()},
                new int[]{frame.getColumn(), frame.getRow()}, insideBorder, insetMissing);
    }

    private boolean isOver(Component component, boolean insideBorder, int column, int row) {
        int[] at = originOf(component, insideBorder);
        TerminalSize size = component.getSize();
        return MouseSupport.contains(at[0], at[1], size.getColumns(), size.getRows(), column, row);
    }

    private boolean isOverBox(Border box, int column, int row) {
        return isOver(box, false, column, row);
    }

    private void focusField(Interactable field) {
        if (focusedOnModels()) {
            closeModelPanel();
        }
        window.setFocusedInteractable(field);
        refreshHint();
    }

    private void placePathCaret(int column) {
        int left = pathInput.getRenderer().getViewTopLeft().getColumn();
        int target = Math.clamp(column + left, 0, pathInput.getText().length());
        pathInput.setCaretPosition(0, target);
    }

    /**
     * Çıkış onayı ister. Herhangi bir thread'den çağrılabilir (örn. pencere X düğmesi).
     */
    void requestExit() {
        ui(this::confirmExit);
    }

    // ---------------------------------------------------------------- layout

    private void build() {
        // --- üst: banner (tam genişlik)
        Panel bannerHolder = new Panel(linear(Direction.VERTICAL, 0));
        bannerPanel.setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Center));
        bannerHolder.addComponent(bannerPanel);
        Border bannerBox = bannerHolder.withBorder(Borders.singleLine());
        bannerBox.setLayoutData(fill());

        // Diyalog: istekler ve yanıtlar. Prompt girişinden ayrı bir kutudur.
        dialogBox = dialog.withBorder(Borders.singleLine(" Diyalog "));
        dialogBox.setLayoutData(grow());

        // Prompt: ipucu satırı + çok satırlı giriş
        Panel promptContent = new Panel(linear(Direction.VERTICAL, 0));
        hintLabel.setForegroundColor(OctopusTheme.YELLOW);
        promptContent.addComponent(hintLabel);
        promptContent.addComponent(inputRow(OctopusTheme.MAUVE, promptInput));
        promptBox = promptContent.withBorder(Borders.singleLine(" Prompt "));
        promptBox.setLayoutData(fill());

        pathBox = inputRow(OctopusTheme.TEAL, pathInput)
                .withBorder(Borders.singleLine(" Path · çalışma dizini: " + shorten(System.getProperty("user.dir")) + " "));
        pathBox.setLayoutData(fill());

        // --- orta: solda diyalog / prompt / path kutuları, sağda model paneli (bu satırın yüksekliği kadar)
        Panel left = new Panel(linear(Direction.VERTICAL, 0));
        left.setLayoutData(grow());
        left.addComponent(dialogBox);
        left.addComponent(promptBox);
        left.addComponent(pathBox);

        Panel mainRow = new Panel(linear(Direction.HORIZONTAL, 1));
        mainRow.setLayoutData(grow());
        mainRow.addComponent(left);
        mainRow.addComponent(buildModelPanel());

        // --- alt satır: mod bilgisi / loading ve step / aktif model
        modeLabel.setForegroundColor(OctopusTheme.MAUVE);
        Border modeBox = modeLabel.withBorder(Borders.singleLine(" Mod "));

        stepLabel.setLayoutData(grow());
        progressLabel.setForegroundColor(OctopusTheme.BLUE);
        Panel stepRow = new Panel(linear(Direction.HORIZONTAL, 1));
        stepRow.addComponent(stepLabel);
        stepRow.addComponent(progressLabel);
        Border stepBox = stepRow.withBorder(Borders.singleLine(" Durum "));
        stepBox.setLayoutData(grow());

        activeModelLabel.setForegroundColor(OctopusTheme.TEAL);
        activeModelLabel.setPreferredSize(new TerminalSize(SIDE_INNER, 1)); // model paneliyle aynı genişlik
        Border activeModelBox = activeModelLabel.withBorder(Borders.singleLine(" Aktif Model "));

        Panel statusRow = new Panel(linear(Direction.HORIZONTAL, 1));
        statusRow.setLayoutData(fill());
        statusRow.addComponent(modeBox);
        statusRow.addComponent(stepBox);
        statusRow.addComponent(activeModelBox);

        KeyHintBar keyHints = new KeyHintBar(List.of(
                // Sütunlar satırlar arasında hizalanır; benzer genişlikteki maddeler aynı sütuna konur ki boşluklar dengeli kalsın.
                new KeyHintBar.Row("Giriş", List.of(
                        new KeyHintBar.Hint("Enter", "Gönder"),
                        new KeyHintBar.Hint("Shift+Enter", "Yeni satır"),
                        new KeyHintBar.Hint("Ctrl+C", "Kopyala"),
                        new KeyHintBar.Hint("Ctrl+V", "Yapıştır"),
                        new KeyHintBar.Hint("↑↓", "Geçmiş"))),
                new KeyHintBar.Row("Gezinme", List.of(
                        new KeyHintBar.Hint("Tab", "Mod"),
                        new KeyHintBar.Hint("PgUp/PgDn", "Kaydır"),
                        new KeyHintBar.Hint("Ctrl+P", "Prompt/Path"),
                        new KeyHintBar.Hint("Ctrl+L", "Model"),
                        new KeyHintBar.Hint("Esc", "İptal / Çıkış")))));
        keyHints.setLayoutData(fill());

        // --- uygulama çerçevesi
        Panel content = new Panel(linear(Direction.VERTICAL, 0));
        content.addComponent(bannerBox);
        content.addComponent(mainRow);
        content.addComponent(statusRow);
        content.addComponent(keyHints);
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
        refreshInstalledAsync();
        setStatus(" Hazır", OctopusTheme.GREEN);
    }

    private Border buildModelPanel() {
        modelModeLabel.setForegroundColor(OctopusTheme.MAUVE);
        modelList.setLayoutData(grow());

        Separator separator = new Separator(Direction.HORIZONTAL);
        separator.setLayoutData(fill());

        // Gösterge: renkler listedekiyle aynı (seçili yeşil, kurulu mavi, yok soluk)
        KeyHintBar legend = new KeyHintBar(List.of(
                new KeyHintBar.Row(List.of(new KeyHintBar.Hint("●", "seçili", OctopusTheme.GREEN, OctopusTheme.MUTED))),
                new KeyHintBar.Row(List.of(new KeyHintBar.Hint("+", "kurulu", OctopusTheme.BLUE, OctopusTheme.MUTED))),
                new KeyHintBar.Row(List.of(new KeyHintBar.Hint("-", "yok", OctopusTheme.MUTED, OctopusTheme.MUTED)))), 0);
        // Panel genişliğini bu çubuklar belirlemesin: model listesiyle aynı iç genişlik
        legend.setPreferredSize(new TerminalSize(SIDE_INNER, 3));
        modelStatusBar.setPreferredSize(new TerminalSize(SIDE_INNER, 1));
        legend.setLayoutData(fill());
        modelStatusBar.setLayoutData(fill());

        Panel content = new Panel(linear(Direction.VERTICAL, 0));
        content.addComponent(modelModeLabel);
        content.addComponent(modelList);
        content.addComponent(separator);
        content.addComponent(legend);
        content.addComponent(modelStatusBar);

        Border box = content.withBorder(Borders.singleLine(" Model "));
        box.setLayoutData(fill());
        return box;
    }

    private Panel inputRow(TextColor signColor, Component box) {
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
        int available = size.getColumns() - 2 - 2; // uygulama çerçevesi + banner kutusu
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

    private static boolean ctrl(KeyStroke key, char letter) {
        Character c = key.getCharacter();
        // AltGr, Ctrl+Alt olarak gelebilir; yalnızca salt Ctrl kısayol sayılır
        return key.getKeyType() == KeyType.Character && c != null
                && key.isCtrlDown() && !key.isAltDown()
                && Character.toLowerCase(c) == letter;
    }

    private void handleKey(KeyStroke key, AtomicBoolean deliverEvent) {
        if (focusedOnModels() && handleModelKey(key, deliverEvent)) {
            return;
        }

        if (key.getKeyType() == KeyType.Character) {
            if (ctrl(key, 'c')) {
                deliverEvent.set(false);
                copyLastOutput();
            } else if (ctrl(key, 'p')) {
                deliverEvent.set(false);
                toggleFocus();
            } else if (ctrl(key, 'l')) {
                deliverEvent.set(false);
                openModelPanel();
            } else if (ctrl(key, 'v')) {
                deliverEvent.set(false);
                paste();
            }
            return; // normal karakterler olduğu gibi iletilir
        }

        switch (key.getKeyType()) {
            case Enter -> {
                boolean newline = key.isShiftDown() || key.isAltDown();
                if (newline && focusedOnPrompt()) {
                    return; // PromptArea yeni satır ekler
                }
                deliverEvent.set(false);
                if (newline) {
                    return; // path alanında yeni satır anlamsız
                }
                if (focusedOnPrompt() && promptInput.continueLine()) {
                    return; // satır sonundaki "\" yeni satıra dönüştü
                }
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
            case ArrowUp -> {
                deliverEvent.set(false);
                recall(true);
            }
            case ArrowDown -> {
                deliverEvent.set(false);
                recall(false);
            }
            case PageUp -> {
                deliverEvent.set(false);
                dialog.pageUp(); // giriş alanı odaktayken de diyalog kayar
            }
            case PageDown -> {
                deliverEvent.set(false);
                dialog.pageDown();
            }
            case Insert -> {
                if (key.isShiftDown()) {
                    deliverEvent.set(false);
                    paste();
                }
            }
            case Escape -> {
                deliverEvent.set(false);
                onEscapeOrInterrupt();
            }
            case EOF -> { // terminal yok oldu, soru sorulamaz
                deliverEvent.set(false);
                closeApplication();
            }
            default -> {
            }
        }
    }

    /**
     * Model paneli odaktayken tuşları ben yönetirim, liste bileşenine hiçbir tuş gitmez.
     * true dönerse tuş tüketilmiştir.
     */
    private boolean handleModelKey(KeyStroke key, AtomicBoolean deliverEvent) {
        switch (key.getKeyType()) {
            case Tab, ReverseTab, EOF -> {
                return false; // mod değiştirme ve çıkış normal akışta
            }
            case Character -> {
                if (ctrl(key, 'l')) {
                    closeModelPanel();
                } else if (ctrl(key, 'c') || ctrl(key, 'p')) {
                    return false;
                }
            }
            case ArrowUp -> moveModelCursor(-1);
            case ArrowDown -> moveModelCursor(1);
            case Enter -> chooseSelectedModel();
            case Escape -> closeModelPanel();
            default -> {
            }
        }
        deliverEvent.set(false);
        return true;
    }

    private boolean focusedOnPrompt() {
        return window.getFocusedInteractable() == promptInput;
    }

    private boolean focusedOnPath() {
        return window.getFocusedInteractable() == pathInput;
    }

    private boolean focusedOnModels() {
        return window.getFocusedInteractable() == modelList;
    }

    private void toggleFocus() {
        window.setFocusedInteractable(focusedOnPrompt() ? pathInput : promptInput);
        refreshHint();
    }

    /**
     * ↑ (older=true) ve ↓ (older=false): önce satırlar arası gezinir, uçlarda geçmişi getirir.
     */
    private void recall(boolean older) {
        if (focusedOnPath()) {
            String value = older ? pathHistory.previous(pathInput.getText()) : pathHistory.next();
            if (value != null) {
                pathInput.setText(value);
                pathInput.setCaretPosition(0, value.length());
            }
            return;
        }
        boolean moved = older ? promptInput.moveCaretUp() : promptInput.moveCaretDown();
        if (moved) {
            return;
        }
        String value = older ? promptHistory.previous(promptInput.getText()) : promptHistory.next();
        if (value != null) {
            promptInput.setText(value);
        }
    }

    private void switchMode(boolean forward) {
        AppMode current = appContext.getCurrentMode();
        appContext.setCurrentMode(forward ? current.next() : current.previous());
        refreshMode();
    }

    private void refreshMode() {
        AppMode mode = appContext.getCurrentMode();
        modeLabel.setText(String.format("%-" + MODE_TEXT_WIDTH + "s", "‹ " + mode.getDisplayName() + " ›"));
        refreshModelPanel(true);
        refreshHint();
    }

    private void refreshHint() {
        AppMode mode = appContext.getCurrentMode();
        if (focusedOnModels()) {
            hintLabel.setText(" Model seçimi (" + mode.getDisplayName() + "): ↑↓ gez · Enter seç · Esc geri");
        } else {
            hintLabel.setText(focusedOnPath()
                    ? " Path: " + mode.getPathHint()
                    : " Prompt: " + mode.getPromptHint());
        }
        refreshModelHint();
    }

    // ---------------------------------------------------------------- clipboard

    private void paste() {
        if (!focusedOnPrompt() && !focusedOnPath()) {
            return;
        }
        Optional<String> clip = ClipboardSupport.read();
        if (clip.isEmpty() || clip.get().isBlank()) {
            notice(" Panoda yapıştırılacak metin yok.", OctopusTheme.YELLOW);
            return;
        }
        if (focusedOnPrompt()) {
            promptInput.insertText(clip.get());
        } else {
            pasteIntoPath(clip.get());
        }
    }

    /**
     * Path tek satırdır: ilk dolu satır alınır, çevreleyen tırnaklar (Windows "Yol olarak kopyala") temizlenir.
     */
    private void pasteIntoPath(String raw) {
        List<String> lines = raw.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
        String value = PathUtils.clean(lines.isEmpty() ? "" : lines.getFirst());
        if (value.isEmpty()) {
            return;
        }
        String current = pathInput.getText();
        int column = Math.max(0, Math.min(pathInput.getCaretPosition().getColumn(), current.length()));
        pathInput.setText(current.substring(0, column) + value + current.substring(column));
        pathInput.setCaretPosition(0, column + value.length());
        if (lines.size() > 1) {
            notice(" Path tek satırdır, panodaki ilk satır alındı.", OctopusTheme.YELLOW);
        }
    }

    private void copyLastOutput() {
        String text = lastOutput.toString().strip();
        if (text.isEmpty()) {
            notice(" Kopyalanacak çıktı yok.", OctopusTheme.YELLOW);
            return;
        }
        if (ClipboardSupport.write(text)) {
            notice(" Son çıktı panoya kopyalandı (" + text.length() + " karakter).", OctopusTheme.GREEN);
        } else {
            notice(" Panoya erişilemedi.", OctopusTheme.YELLOW);
        }
    }

    // ---------------------------------------------------------------- models

    private void openModelPanel() {
        refreshInstalledAsync();
        refreshModelPanel(true);
        window.setFocusedInteractable(modelList);
        refreshHint();
    }

    private void closeModelPanel() {
        window.setFocusedInteractable(promptInput);
        refreshHint();
    }

    private void moveModelCursor(int delta) {
        modelList.moveCursor(delta);
    }

    private void chooseSelectedModel() {
        ModelList.Row row = modelList.cursorRow();
        if (row == null) {
            return;
        }
        String name = row.name();
        AppMode mode = appContext.getCurrentMode();
        if (installedModels.isKnown() && !installedModels.isInstalled(name)) {
            // Durum kutusu dar; uzun model adlarında komut kesilir, bu yüzden tam komut çıktıya da yazılır.
            notice(" Kurulu değil, komut çıktıda", OctopusTheme.YELLOW);
            dialog.addNote("Model kurulu değil. Terminalde çalıştır: ollama pull " + name);
            return;
        }
        modelSettings.select(mode, name);
        refreshModelPanel(false);
        closeModelPanel();
        notice(" " + mode.getDisplayName() + " için model: " + name, OctopusTheme.GREEN);
    }

    /**
     * Ollama'ya arka planda sorar, bitince paneli yeniler.
     */
    private void refreshInstalledAsync() {
        aiExecutor.submit(() -> {
            installedModels.refresh();
            ui(() -> refreshModelPanel(false));
        });
    }

    /**
     * @param cursorToSelected true ise liste imleci aktif modun seçili modeline taşınır
     */
    private void refreshModelPanel(boolean cursorToSelected) {
        AppMode mode = appContext.getCurrentMode();
        String selected = modelSettings.modelFor(mode);
        List<String> names = modelNames(selected);
        int cursor = Math.max(0, modelList.getCursor());

        modelList.setRows(names.stream()
                .map(name -> new ModelList.Row(name, sameModel(name, selected), installedState(name)))
                .toList());
        if (cursorToSelected) {
            for (int i = 0; i < names.size(); i++) {
                if (sameModel(names.get(i), selected)) {
                    cursor = i;
                    break;
                }
            }
        }
        modelList.setCursor(cursor);

        modelModeLabel.setText(fit(" Mod: " + mode.getDisplayName(), SIDE_INNER));
        refreshModelHint();
        refreshActiveModel();
    }

    /**
     * Katalog + Ollama'da kurulu olup katalogda olmayanlar + (gerekirse) aktif modelin kendisi.
     */
    private List<String> modelNames(String selected) {
        List<String> names = new ArrayList<>(ModelCatalog.SUGGESTED);
        for (String installed : installedModels.names()) {
            String display = InstalledModels.display(installed);
            if (names.stream().noneMatch(name -> sameModel(name, display))) {
                names.add(display);
            }
        }
        if (names.stream().noneMatch(name -> sameModel(name, selected))) {
            names.addFirst(selected);
        }
        return names;
    }

    private ModelList.Installed installedState(String name) {
        if (!installedModels.isKnown()) {
            return ModelList.Installed.UNKNOWN;
        }
        return installedModels.isInstalled(name) ? ModelList.Installed.YES : ModelList.Installed.NO;
    }

    private void refreshModelHint() {
        List<KeyHintBar.Hint> hints;
        if (!installedModels.isChecked()) {
            hints = List.of(new KeyHintBar.Hint("", "Ollama kontrol ediliyor...", OctopusTheme.MUTED, OctopusTheme.MUTED));
        } else if (!installedModels.isKnown()) {
            hints = List.of(new KeyHintBar.Hint("", "Ollama'ya ulaşılamadı", OctopusTheme.YELLOW, OctopusTheme.YELLOW));
        } else if (focusedOnModels()) {
            hints = List.of(new KeyHintBar.Hint("Enter", "Seç"), new KeyHintBar.Hint("Esc", "Geri"));
        } else {
            hints = List.of(); // paneli açan kısayol alttaki genel kısayol çubuğunda yazıyor
        }
        modelStatusBar.setRows(List.of(new KeyHintBar.Row(hints)));
    }

    private void refreshActiveModel() {
        String model = modelSettings.modelFor(appContext.getCurrentMode());
        boolean missing = installedModels.isKnown() && !installedModels.isInstalled(model);
        activeModelLabel.setText(fit(missing ? model + " (yok)" : model, SIDE_INNER));
        activeModelLabel.setForegroundColor(missing ? OctopusTheme.YELLOW : OctopusTheme.TEAL);
    }

    private static boolean sameModel(String a, String b) {
        return InstalledModels.normalize(a).equals(InstalledModels.normalize(b));
    }

    // ---------------------------------------------------------------- exit / interrupt

    /**
     * Çalışan bir işlem varsa Esc yalnızca onu iptal etmeyi önerir, uygulamadan çıkmaz.
     */
    private void onEscapeOrInterrupt() {
        if (busy.get()) {
            confirmInterrupt();
        } else {
            confirmExit();
        }
    }

    private void confirmInterrupt() {
        Job job = activeJob;
        if (job == null || !dialogOpen.compareAndSet(false, true)) {
            return;
        }
        try {
            boolean yes = askYesNo("İşlemi iptal et",
                    "Çalışan işlem iptal edilsin mi?\nSadece bu işlem durur, uygulama açık kalır.");
            if (yes) {
                interruptJob(job);
            }
        } finally {
            dialogOpen.set(false);
        }
    }

    /**
     * Diyalog açıkken iş kendiliğinden bitmiş olabilir, bu yüzden işlemin hâlâ aktif olduğunu kontrol eder.
     */
    private void interruptJob(Job job) {
        if (activeJob != job) {
            return;
        }
        job.cancel();
        dialog.addAnswer("■ İşlem iptal edildi.");
        finishTask(job);
    }

    private void confirmExit() {
        if (!dialogOpen.compareAndSet(false, true)) {
            return; // başka bir onay kutusu zaten açık
        }
        try {
            String message = busy.get()
                    ? "Devam eden bir işlem var, çıkarsanız iptal edilir.\nUygulamadan çıkmak istiyor musunuz?"
                    : "Uygulamadan çıkmak istiyor musunuz?";
            if (askYesNo("Çıkış", message)) {
                closeApplication();
            }
        } finally {
            dialogOpen.set(false);
        }
    }

    private void closeApplication() {
        Job job = activeJob;
        if (job != null) {
            job.cancel();
        }
        window.close();
    }

    /**
     * Varsayılan odak "Hayır"da. E/Y evet, H/N hayır, Esc iptal.
     */
    private boolean askYesNo(String title, String message) {
        AtomicBoolean yes = new AtomicBoolean(false);
        BasicWindow dialog = new BasicWindow(title);
        dialog.setHints(List.of(Window.Hint.CENTERED, Window.Hint.MODAL));

        Label label = new Label(message);
        label.setForegroundColor(OctopusTheme.TEXT);

        Button no = new Button("Hayır", dialog::close);
        Button yesButton = new Button("Evet", () -> {
            yes.set(true);
            dialog.close();
        });
        Panel buttons = new Panel(linear(Direction.HORIZONTAL, 2));
        buttons.setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Center));
        buttons.addComponent(no);
        buttons.addComponent(yesButton);

        Panel content = new Panel(linear(Direction.VERTICAL, 1));
        content.addComponent(label);
        content.addComponent(buttons);
        dialog.setComponent(content);
        dialog.setFocusedInteractable(no);

        dialog.addWindowListener(new WindowListenerAdapter() {
            @Override
            public void onInput(Window basePane, KeyStroke key, AtomicBoolean deliverEvent) {
                if (key.getKeyType() == KeyType.Escape) {
                    deliverEvent.set(false);
                    dialog.close();
                    return;
                }
                Character c = key.getKeyType() == KeyType.Character ? key.getCharacter() : null;
                if (c == null) {
                    return;
                }
                char lower = Character.toLowerCase(c);
                if (lower == 'e' || lower == 'y') {
                    deliverEvent.set(false);
                    yes.set(true);
                    dialog.close();
                } else if (lower == 'h' || lower == 'n') {
                    deliverEvent.set(false);
                    dialog.close();
                }
            }
        });

        gui.addWindowAndWait(dialog);
        return yes.get();
    }

    // ---------------------------------------------------------------- execution

    private void submit() {
        AppMode mode = appContext.getCurrentMode();
        String prompt = promptInput.getText().strip();
        String path = pathInput.getText().strip();

        if ("exit".equalsIgnoreCase(prompt) || "cikis".equalsIgnoreCase(prompt)) {
            closeApplication();
            return;
        }
        if (prompt.isEmpty() && mode != AppMode.KOD_ANALIZI) {
            notice(" Önce prompt alanına bir istek yazın.", OctopusTheme.YELLOW);
            return;
        }
        if (!busy.compareAndSet(false, true)) {
            notice(" Önceki işlem sürüyor. İptal etmek için Esc.", OctopusTheme.YELLOW);
            return;
        }

        promptHistory.add(prompt);
        pathHistory.add(path);
        promptInput.setText("");
        if (mode == AppMode.KOD_GENERATE) {
            pathInput.setText(""); // aynı dosyaya ikinci kez yazma hatasını önler
        }
        lastOutput.setLength(0);
        dialog.startTurn("Sen · " + mode.getDisplayName() + " · " + modelSettings.modelFor(mode),
                describe(mode, prompt, path));

        currentStep = "Başlatılıyor...";
        progressDone = 0;
        progressTotal = 0;
        Job job = new Job();
        activeJob = job;
        startSpinner();

        ModeRequest request = new ModeRequest(prompt, path);
        job.future = aiExecutor.submit(() -> run(job, mode, request));
    }

    private static String describe(AppMode mode, String prompt, String path) {
        return switch (mode) {
            case KOD_ANALIZI -> path.isEmpty() ? "(çalışma dizini)" : path;
            case KOD_GENERATE -> path.isEmpty() ? prompt : prompt + "  →  " + path;
            default -> prompt;
        };
    }

    private void run(Job job, AppMode mode, ModeRequest request) {
        try {
            dispatcher.dispatch(mode, request, job);
        } catch (Exception e) {
            if (!job.isCancelled()) { // iptalin yol açtığı istisnalar kullanıcıya hata olarak gösterilmez
                log.error("Mod çalıştırılırken hata: {}", mode, e);
                String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                ui(() -> dialog.addAnswer("Hata: " + message));
            }
        } finally {
            ui(() -> finishTask(job));
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
                if (System.currentTimeMillis() >= noticeUntil) {
                    setStatus(step, OctopusTheme.TEXT);
                }
                progressLabel.setText(bar);
            });
        }, 0, 120, TimeUnit.MILLISECONDS);
    }

    /**
     * İptal edilmiş eski bir işlemin gecikmiş bitişi, yeni işlemin durumunu bozmasın diye kontrol edilir.
     */
    private void finishTask(Job job) {
        if (activeJob != job) {
            return;
        }
        activeJob = null;
        if (spinnerTask != null) {
            spinnerTask.cancel(false);
        }
        noticeUntil = 0;
        setStatus(" Hazır", OctopusTheme.GREEN);
        progressLabel.setText(BLANK_PROGRESS);
        busy.set(false);
    }

    // ---------------------------------------------------------------- helpers

    private void setStatus(String text, TextColor color) {
        stepLabel.setText(text);
        stepLabel.setForegroundColor(color);
    }

    /**
     * Geçici bildirim: spinner birkaç saniye üzerine yazmaz.
     */
    private void notice(String text, TextColor color) {
        noticeUntil = System.currentTimeMillis() + NOTICE_MILLIS;
        setStatus(text, color);
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

    /**
     * Sabit genişliğe boşlukla doldurur, uzunsa "…" ile keser.
     */
    private static String fit(String text, int width) {
        String value = text.length() > width ? text.substring(0, width - 1) + "…" : text;
        return String.format("%-" + width + "s", value);
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