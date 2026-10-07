package com.bcoworks.blueringoctopuscli.tui;

import com.bcoworks.blueringoctopuscli.context.AppContext;
import com.bcoworks.blueringoctopuscli.context.AppMode;
import com.bcoworks.blueringoctopuscli.i18n.Language;
import com.bcoworks.blueringoctopuscli.i18n.Messages;
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
import java.util.concurrent.atomic.AtomicReference;

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

    private static final long NOTICE_MILLIS = 3_000;

    private final MultiWindowTextGUI gui;
    private final AppContext appContext;
    private final ModeDispatcher dispatcher;
    private final ExecutorService aiExecutor;
    private final BannerArt banner;
    private final ModelSettings modelSettings;
    private final InstalledModels installedModels;
    private final Messages messages;

    private final BasicWindow window = new BasicWindow("Blue Ring Octopus CLI");
    private final Panel bannerPanel = new Panel(linear(Direction.VERTICAL, 0));
    private final BannerView bannerView = new BannerView();
    private final DialogView dialog = new DialogView();
    private Border dialogBox;
    private Border promptBox;
    private Border pathBox;
    private Border modeBox;
    private Border stepBox;
    private Border activeModelBox;
    private Border modelBox;
    private KeyHintBar keyHints;
    private KeyHintBar legend;
    private final PromptArea promptInput = new PromptArea();
    private final TextBox pathInput = new TextBox(new TerminalSize(40, 1));
    private final Label hintLabel = new Label("");
    private final Label modePrev = new Label(" ‹ ");
    private final Label modeName = new Label("");
    private final Label modeNext = new Label(" › ");
    private final Label stepLabel = new Label("");
    private final Label progressLabel = new Label(BLANK_PROGRESS);

    private final Label modelModeLabel = new Label("");
    private final KeyHintBar modelStatusBar = new KeyHintBar(List.of(new KeyHintBar.Row(List.of())));
    private final Label activeModelLabel = new Label("");
    private final ModelList modelList = new ModelList();

    private final InputHistory promptHistory = new InputHistory();
    private final InputHistory pathHistory = new InputHistory();
    private final AtomicBoolean dialogOpen = new AtomicBoolean(false);
    private boolean selecting; // a text selection is being dragged in the output box (GUI thread only)

    /**
     * Something in an open dialog that reacts to a mouse click: a button, or the row list of the language picker.
     * The action gets the click position relative to the component.
     */
    private record ClickTarget(Component component, ClickAction action) {
    }

    @FunctionalInterface
    private interface ClickAction {
        void click(int column, int row);
    }

    private volatile BasicWindow openDialog; // the modal dialog being shown, null if none
    private volatile List<ClickTarget> dialogTargets = List.of();
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
    private BannerArt.Banner shownBanner;

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
               ModelSettings modelSettings, InstalledModels installedModels, Messages messages) {
        this.gui = gui;
        this.appContext = appContext;
        this.dispatcher = dispatcher;
        this.aiExecutor = aiExecutor;
        this.banner = banner;
        this.modelSettings = modelSettings;
        this.installedModels = installedModels;
        this.messages = messages;
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
     * Left button down. In the output box it starts a text selection (double click: a word, triple click: a line);
     * on a mode arrow it switches the mode; on the model panel it focuses the panel and, on a row, moves the cursor
     * there (a double click on a row chooses that model). Any other press clears the selection.
     */
    @Override
    public void onPress(int column, int row, int clickCount) {
        ui(() -> {
            if (dialogOpen.get()) {
                clickInDialog(column, row);
                return;
            }
            if (isOver(dialog, true, column, row)) {
                int[] at = originOf(dialog, true);
                if (clickCount >= 3) {
                    dialog.selectLineAt(row - at[1]);
                    selecting = false;
                } else if (clickCount == 2) {
                    dialog.selectWordAt(row - at[1], column - at[0]);
                    selecting = false;
                } else {
                    dialog.startSelection(row - at[1], column - at[0]);
                    selecting = true;
                }
                return;
            }
            dialog.clearSelection();
            selecting = false;
            if (isOver(modePrev, true, column, row)) {
                switchMode(false);
            } else if (isOver(modeNext, true, column, row)) {
                switchMode(true);
            } else if (isOverBox(modelBox, column, row)) {
                clickModelPanel(column, row, clickCount);
            }
        });
    }

    /**
     * Dragging past the top or bottom of the output box scrolls it one line per mouse movement.
     */
    @Override
    public void onDrag(int column, int row) {
        ui(() -> {
            if (!selecting || dialogOpen.get()) {
                return;
            }
            int[] at = originOf(dialog, true);
            int rowInBox = row - at[1];
            if (rowInBox < 0) {
                dialog.scroll(-1);
            } else if (rowInBox >= dialog.getSize().getRows()) {
                dialog.scroll(1);
            }
            dialog.extendSelection(rowInBox, column - at[0]);
        });
    }

    @Override
    public void onRelease(int column, int row) {
        ui(() -> {
            if (selecting) {
                selecting = false;
                if (!dialog.hasSelection()) {
                    dialog.clearSelection();
                }
            }
        });
    }

    private void clickModelPanel(int column, int row, int clickCount) {
        if (!focusedOnModels()) {
            openModelPanel();
        }
        if (isOver(modelList, true, column, row)) {
            int index = modelList.rowAt(row - originOf(modelList, true)[1]);
            if (index >= 0) {
                modelList.setCursor(index);
                if (clickCount >= 2) {
                    chooseSelectedModel();
                }
            }
        }
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
        bannerPanel.addComponent(bannerView);
        bannerHolder.addComponent(bannerPanel);
        Border bannerBox = bannerHolder.withBorder(Borders.singleLine());
        bannerBox.setLayoutData(fill());

        // Diyalog: istekler ve yanıtlar. Prompt girişinden ayrı bir kutudur.
        dialogBox = titled(dialog, messages.get("box.output"));
        dialogBox.setLayoutData(grow());

        // Prompt: ipucu satırı + çok satırlı giriş
        Panel promptContent = new Panel(linear(Direction.VERTICAL, 0));
        hintLabel.setForegroundColor(OctopusTheme.YELLOW);
        promptContent.addComponent(hintLabel);
        promptContent.addComponent(inputRow(OctopusTheme.MAUVE, promptInput));
        promptBox = titled(promptContent, messages.get("box.prompt"));
        promptBox.setLayoutData(fill());

        pathBox = titled(inputRow(OctopusTheme.TEAL, pathInput), pathTitle());
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
        modePrev.setForegroundColor(OctopusTheme.BLUE);
        modeNext.setForegroundColor(OctopusTheme.BLUE);
        modeName.setForegroundColor(OctopusTheme.MAUVE);
        Panel modeRow = new Panel(linear(Direction.HORIZONTAL, 0));
        modeRow.addComponent(modePrev);
        modeRow.addComponent(modeName);
        modeRow.addComponent(modeNext);
        modeBox = titled(modeRow, messages.get("box.mode"));

        stepLabel.setLayoutData(grow());
        progressLabel.setForegroundColor(OctopusTheme.BLUE);
        Panel stepRow = new Panel(linear(Direction.HORIZONTAL, 1));
        stepRow.addComponent(stepLabel);
        stepRow.addComponent(progressLabel);
        stepBox = titled(stepRow, messages.get("box.status"));
        stepBox.setLayoutData(grow());

        activeModelLabel.setForegroundColor(OctopusTheme.TEAL);
        activeModelLabel.setPreferredSize(new TerminalSize(SIDE_INNER, 1)); // model paneliyle aynı genişlik
        activeModelBox = titled(activeModelLabel, messages.get("box.activeModel"));

        Panel statusRow = new Panel(linear(Direction.HORIZONTAL, 1));
        statusRow.setLayoutData(fill());
        statusRow.addComponent(modeBox);
        statusRow.addComponent(stepBox);
        statusRow.addComponent(activeModelBox);

        keyHints = new KeyHintBar(keyHintRows());
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
        setStatus(" " + messages.get("status.ready"), OctopusTheme.GREEN);
    }

    private Border buildModelPanel() {
        modelModeLabel.setForegroundColor(OctopusTheme.MAUVE);
        modelList.setLayoutData(grow());

        Separator separator = new Separator(Direction.HORIZONTAL);
        separator.setLayoutData(fill());

        // Gösterge: renkler listedekiyle aynı (seçili yeşil, kurulu mavi, yok soluk)
        legend = new KeyHintBar(legendRows());
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

        modelBox = titled(content, messages.get("box.models"));
        modelBox.setLayoutData(fill());
        return modelBox;
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
        BannerArt.Banner chosen = banner.choose(available, size.getRows());
        if (chosen.equals(shownBanner)) {
            return;
        }
        shownBanner = chosen;
        bannerView.setBanner(chosen);
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
            } else if (ctrl(key, 'g')) {
                deliverEvent.set(false);
                chooseLanguage();
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
                } else if (ctrl(key, 'c') || ctrl(key, 'p') || ctrl(key, 'g')) {
                    return false;
                }
            }
            case PageUp -> dialog.pageUp();
            case PageDown -> dialog.pageDown();
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
        modeName.setText(center(messages.modeName(mode), modeNameWidth()));
        refreshModelPanel(true);
        refreshHint();
    }

    private void refreshHint() {
        AppMode mode = appContext.getCurrentMode();
        if (focusedOnModels()) {
            hintLabel.setText(" " + messages.get("hint.models", messages.modeName(mode)));
        } else {
            hintLabel.setText(focusedOnPath()
                    ? " Path: " + messages.pathHint(mode)
                    : " Prompt: " + messages.promptHint(mode));
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
            notice(" " + messages.get("notice.clipboardEmpty"), OctopusTheme.YELLOW);
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
            notice(" " + messages.get("notice.pathSingleLine"), OctopusTheme.YELLOW);
        }
    }

    private void copyLastOutput() {
        if (dialog.hasSelection()) {
            String selected = dialog.selectedText();
            if (ClipboardSupport.write(selected)) {
                dialog.clearSelection();
                notice(" " + messages.get("notice.selectionCopied", selected.length()), OctopusTheme.GREEN);
            } else {
                notice(" " + messages.get("notice.clipboardFailed"), OctopusTheme.YELLOW);
            }
            return;
        }
        String text = lastOutput.toString().strip();
        if (text.isEmpty()) {
            notice(" " + messages.get("notice.nothingToCopy"), OctopusTheme.YELLOW);
            return;
        }
        if (ClipboardSupport.write(text)) {
            notice(" " + messages.get("notice.copied", text.length()), OctopusTheme.GREEN);
        } else {
            notice(" " + messages.get("notice.clipboardFailed"), OctopusTheme.YELLOW);
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
            notice(" " + messages.get("notice.notInstalled"), OctopusTheme.YELLOW);
            dialog.addNote(messages.get("note.notInstalled", name));
            return;
        }
        modelSettings.select(mode, name);
        refreshModelPanel(false);
        closeModelPanel();
        notice(" " + messages.get("notice.modelSelected", messages.modeName(mode), name), OctopusTheme.GREEN);
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

        modelModeLabel.setText(fit(" " + messages.get("model.mode", messages.modeName(mode)), SIDE_INNER));
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
            hints = List.of(new KeyHintBar.Hint("", messages.get("model.checking"), OctopusTheme.MUTED, OctopusTheme.MUTED));
        } else if (!installedModels.isKnown()) {
            hints = List.of(new KeyHintBar.Hint("", messages.get("model.unreachable"), OctopusTheme.YELLOW, OctopusTheme.YELLOW));
        } else if (focusedOnModels()) {
            hints = List.of(new KeyHintBar.Hint("Enter", messages.get("model.select")),
                    new KeyHintBar.Hint("Esc", messages.get("model.back")));
        } else {
            hints = List.of(); // paneli açan kısayol alttaki genel kısayol çubuğunda yazıyor
        }
        modelStatusBar.setRows(List.of(new KeyHintBar.Row(hints)));
    }

    private void refreshActiveModel() {
        String model = modelSettings.modelFor(appContext.getCurrentMode());
        boolean missing = installedModels.isKnown() && !installedModels.isInstalled(model);
        activeModelLabel.setText(fit(missing ? model + " " + messages.get("model.missing") : model, SIDE_INNER));
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
            boolean yes = askYesNo(messages.get("dialog.cancelTask.title"),
                    messages.get("dialog.cancelTask.message"));
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
        dialog.addAnswer(messages.get("task.cancelled"));
        finishTask(job);
    }

    private void confirmExit() {
        if (!dialogOpen.compareAndSet(false, true)) {
            return; // başka bir onay kutusu zaten açık
        }
        try {
            String message = messages.get(busy.get() ? "dialog.exit.messageBusy" : "dialog.exit.message");
            if (askYesNo(messages.get("dialog.exit.title"), message)) {
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

        Runnable chooseNo = dialog::close;
        Runnable chooseYes = () -> {
            yes.set(true);
            dialog.close();
        };
        Button no = new Button(messages.get("dialog.no"), chooseNo);
        Button yesButton = new Button(messages.get("dialog.yes"), chooseYes);
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
                if (messages.get("dialog.keys.yes").indexOf(lower) >= 0) {
                    deliverEvent.set(false);
                    yes.set(true);
                    dialog.close();
                } else if (messages.get("dialog.keys.no").indexOf(lower) >= 0) {
                    deliverEvent.set(false);
                    dialog.close();
                }
            }
        });

        showDialog(dialog, List.of(
                new ClickTarget(no, (column, row) -> chooseNo.run()),
                new ClickTarget(yesButton, (column, row) -> chooseYes.run())));
        return yes.get();
    }

    /**
     * Shows a modal dialog and, while it is open, lets mouse clicks reach the given targets.
     */
    private void showDialog(BasicWindow dialog, List<ClickTarget> targets) {
        openDialog = dialog;
        dialogTargets = targets;
        try {
            gui.addWindowAndWait(dialog);
        } finally {
            openDialog = null;
            dialogTargets = List.of();
        }
    }

    private void clickInDialog(int column, int row) {
        if (openDialog == null) {
            return;
        }
        // Unlike in the full-screen main window, the positions Lanterna reports for a dialog are the drawn ones.
        for (ClickTarget target : dialogTargets) {
            TerminalPosition at = target.component().getGlobalPosition();
            TerminalSize size = target.component().getSize();

            if (MouseSupport.contains(at.getColumn(), at.getRow(), size.getColumns(), size.getRows(), column, row)) {
                target.action().click(column - at.getColumn(), row - at.getRow());
                return;
            }
        }
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
        if (prompt.isEmpty() && mode != AppMode.CODE_ANALYSIS) {
            notice(" " + messages.get("notice.emptyPrompt"), OctopusTheme.YELLOW);
            return;
        }
        if (!busy.compareAndSet(false, true)) {
            notice(" " + messages.get("notice.busy"), OctopusTheme.YELLOW);
            return;
        }

        promptHistory.add(prompt);
        pathHistory.add(path);
        promptInput.setText("");
        if (mode == AppMode.CODE_GENERATION) {
            pathInput.setText(""); // aynı dosyaya ikinci kez yazma hatasını önler
        }
        lastOutput.setLength(0);
        dialog.startTurn(messages.get("dialog.you") + " · " + messages.modeName(mode) + " · " + modelSettings.modelFor(mode),
                describe(mode, prompt, path));

        currentStep = messages.get("status.starting");
        progressDone = 0;
        progressTotal = 0;
        Job job = new Job();
        activeJob = job;
        startSpinner();

        ModeRequest request = new ModeRequest(prompt, path);
        job.future = aiExecutor.submit(() -> run(job, mode, request));
    }

    private String describe(AppMode mode, String prompt, String path) {
        return switch (mode) {
            case CODE_ANALYSIS -> path.isEmpty() ? messages.get("describe.workingDir") : path;
            case CODE_GENERATION -> path.isEmpty() ? prompt : prompt + "  →  " + path;
            default -> prompt;
        };
    }

    private void run(Job job, AppMode mode, ModeRequest request) {
        try {
            dispatcher.dispatch(mode, request, job);
        } catch (Exception e) {
            if (!job.isCancelled()) { // iptalin yol açtığı istisnalar kullanıcıya hata olarak gösterilmez
                log.error("Error while running mode: {}", mode, e);
                String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                ui(() -> dialog.addAnswer(messages.get("error.generic", message)));
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
        setStatus(" " + messages.get("status.ready"), OctopusTheme.GREEN);
        progressLabel.setText(BLANK_PROGRESS);
        busy.set(false);
    }

    // ---------------------------------------------------------------- language

    private static Border titled(Component inner, String title) {
        return inner.withBorder(Borders.singleLine(" " + title + " "));
    }

    private String pathTitle() {
        return messages.get("box.path");
    }

    /**
     * Border titles cannot be changed afterwards, so the border is replaced by a new one at the same place.
     */
    private static Border retitle(Border old, String title) {
        Container parent = old.getParent();
        Component inner = old.getComponent();
        LayoutData layout = old.getLayoutData();
        old.removeComponent(inner);
        Border fresh = titled(inner, title);
        fresh.setLayoutData(layout);
        if (parent instanceof Panel panel) {
            int index = panel.getChildrenList().indexOf(old);
            panel.removeComponent(old);
            panel.addComponent(index, fresh);
        }
        return fresh;
    }

    private List<KeyHintBar.Row> keyHintRows() {
        // Columns line up across the rows; items of similar width share a column so the gaps stay even.
        return List.of(
                new KeyHintBar.Row(List.of(
                        new KeyHintBar.Hint("Enter", messages.get("key.send")),
                        new KeyHintBar.Hint("Shift+Enter", messages.get("key.newLine")),
                        new KeyHintBar.Hint("Ctrl+C", messages.get("key.copy")),
                        new KeyHintBar.Hint("Ctrl+V", messages.get("key.paste")),
                        new KeyHintBar.Hint("↑↓", messages.get("key.history")),
                        new KeyHintBar.Hint("Ctrl+G", messages.get("key.language")))),
                new KeyHintBar.Row(List.of(
                        new KeyHintBar.Hint("Tab", messages.get("key.mode")),
                        new KeyHintBar.Hint("PgUp/PgDn", messages.get("key.scroll")),
                        new KeyHintBar.Hint("Ctrl+P", "Prompt/Path"),
                        new KeyHintBar.Hint("Ctrl+L", messages.get("key.model")),
                        new KeyHintBar.Hint("Esc", messages.get("key.cancelExit")))));
    }

    private List<KeyHintBar.Row> legendRows() {
        return List.of(
                new KeyHintBar.Row(List.of(new KeyHintBar.Hint("●", messages.get("legend.selected"), OctopusTheme.GREEN, OctopusTheme.MUTED))),
                new KeyHintBar.Row(List.of(new KeyHintBar.Hint("+", messages.get("legend.installed"), OctopusTheme.BLUE, OctopusTheme.MUTED))),
                new KeyHintBar.Row(List.of(new KeyHintBar.Hint("-", messages.get("legend.missing"), OctopusTheme.MUTED, OctopusTheme.MUTED))));
    }

    private int modeNameWidth() {
        return Arrays.stream(AppMode.values())
                .mapToInt(mode -> messages.modeName(mode).length())
                .max()
                .orElse(12);
    }

    private static String center(String text, int width) {
        int spare = Math.max(0, width - text.length());
        int left = spare / 2;
        return " ".repeat(left) + text + " ".repeat(spare - left);
    }

    /**
     * Ctrl+G: pick the interface language. The choice is applied at once and remembered.
     */
    private void chooseLanguage() {
        if (!dialogOpen.compareAndSet(false, true)) {
            return;
        }
        try {
            Language picked = askLanguage();
            if (picked != null && picked != messages.language()) {
                messages.setLanguage(picked);
                applyLanguage();
            }
        } finally {
            dialogOpen.set(false);
        }
    }

    private Language askLanguage() {
        AtomicReference<Language> result = new AtomicReference<>();
        BasicWindow picker = new BasicWindow(messages.get("dialog.language.title"));
        picker.setHints(List.of(Window.Hint.CENTERED, Window.Hint.MODAL));

        ActionListBox list = new ActionListBox(new TerminalSize(26, Language.values().length));
        for (Language language : Language.values()) {
            String mark = language == messages.language() ? "● " : "  ";
            list.addItem(mark + language.nativeName(), () -> {
                result.set(language);
                picker.close();
            });
        }
        list.setSelectedIndex(messages.language().ordinal());

        Label hint = new Label(messages.get("dialog.language.hint"));
        hint.setForegroundColor(OctopusTheme.MUTED);

        Panel content = new Panel(linear(Direction.VERTICAL, 1));
        content.addComponent(list);
        content.addComponent(hint);
        picker.setComponent(content);
        picker.setFocusedInteractable(list);
        picker.addWindowListener(new WindowListenerAdapter() {
            @Override
            public void onInput(Window basePane, KeyStroke key, AtomicBoolean deliverEvent) {
                if (key.getKeyType() == KeyType.Escape) {
                    deliverEvent.set(false);
                    picker.close();
                }
            }
        });

        showDialog(picker, List.of(new ClickTarget(list, (column, row) -> {
            if (row >= 0 && row < Language.values().length) {
                list.setSelectedIndex(row);
                list.runSelectedItem();
            }
        })));
        return result.get();
    }

    /**
     * Redraws everything that carries text after the language changed. Text already in the dialog stays as it was.
     */
    private void applyLanguage() {
        Interactable focused = window.getFocusedInteractable();
        dialogBox = retitle(dialogBox, messages.get("box.output"));
        promptBox = retitle(promptBox, messages.get("box.prompt"));
        pathBox = retitle(pathBox, pathTitle());
        modeBox = retitle(modeBox, messages.get("box.mode"));
        stepBox = retitle(stepBox, messages.get("box.status"));
        activeModelBox = retitle(activeModelBox, messages.get("box.activeModel"));
        modelBox = retitle(modelBox, messages.get("box.models"));
        window.setFocusedInteractable(focused);

        keyHints.setRows(keyHintRows());
        legend.setRows(legendRows());
        refreshMode();
        if (!busy.get()) {
            setStatus(" " + messages.get("status.ready"), OctopusTheme.GREEN);
        }
        notice(" " + messages.get("notice.languageChanged", messages.language().nativeName()), OctopusTheme.GREEN);
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