package com.bcoworks.blueringoctopuscli.command;

import com.bcoworks.blueringoctopuscli.context.AppContext;
import com.bcoworks.blueringoctopuscli.context.AppMode;
import org.springframework.shell.component.SingleItemSelector;
import org.springframework.shell.component.support.SelectorItem;
import org.springframework.shell.standard.AbstractShellComponent;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ShellComponent
public class ModeCommands extends AbstractShellComponent {

    private final AppContext appContext;

    public ModeCommands(AppContext appContext) {
        this.appContext = appContext;
    }

    @ShellMethod(value = "Çalışma modunu değiştirir (Kod Analizi, Generate, Test vb.)", key = {"mode", "mod"})
    public void selectMode() {
        List<SelectorItem<AppMode>> items = new ArrayList<>();
        for (AppMode mode : AppMode.values()) {
            items.add(SelectorItem.of(mode.getDisplayName(), mode));
        }

        SingleItemSelector<AppMode, SelectorItem<AppMode>> selector = new SingleItemSelector<>(
                getTerminal(),
                items,
                "Lütfen çalıştırmak istediğiniz modu seçin:",
                null
        );

        selector.setResourceLoader(getResourceLoader());
        selector.setTemplateExecutor(getTemplateExecutor());

        SingleItemSelector.SingleItemSelectorContext<AppMode, SelectorItem<AppMode>> context = selector.run(
                SingleItemSelector.SingleItemSelectorContext.empty()
        );

        context.getResultItem()
                .flatMap(selectedItem -> Optional.ofNullable(selectedItem.getItem()))
                .ifPresent(selectedMode -> {
                    appContext.setCurrentMode(selectedMode);
                    System.out.println("\nAktif mod değiştirildi: " + selectedMode.getDisplayName());
                });
    }
}