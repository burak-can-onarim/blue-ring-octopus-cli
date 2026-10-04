package com.bcoworks.codeanalyzer.command;

import org.jspecify.annotations.NonNull;
import org.springframework.shell.component.SingleItemSelector;
import org.springframework.shell.component.support.SelectorItem;
import org.springframework.shell.standard.AbstractShellComponent;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@ShellComponent
public class MainMenuCommand extends AbstractShellComponent {

    @ShellMethod(key = {"start", "menu"}, value = "Blue Ring Octopus CLI Ana Menüsünü Başlatır")
    public void startMenu() {
        SingleItemSelector<String, SelectorItem<String>> component = getStringSelectorItemSingleItemSelector();

        component.setResourceLoader(getResourceLoader());
        component.setTemplateExecutor(getTemplateExecutor());

        SingleItemSelector.SingleItemSelectorContext<String, SelectorItem<String>> context = component.run(SingleItemSelector.SingleItemSelectorContext.empty());

        String selectedMode = context.getResultItem()
                .flatMap(item -> Optional.ofNullable(item.getItem()))
                .orElse("EXIT");

        processSelection(selectedMode);
    }

    private @NonNull SingleItemSelector<String, SelectorItem<String>> getStringSelectorItemSingleItemSelector() {
        List<SelectorItem<String>> items = Arrays.asList(
                SelectorItem.of("1. Kod Analizi (Maliyet Odaklı Analiz)", "ANALYSIS"),
                SelectorItem.of("2. Kod Üretimi (Local Generate)", "GENERATE"),
                SelectorItem.of("3. Test Yazımı (Unit Test Oluşturucu)", "TEST"),
                SelectorItem.of("4. Çıkış", "EXIT")
        );

        return new SingleItemSelector<>(
                getTerminal(),
                items,
                "Lütfen çalıştırmak istediğiniz modu yön tuşlarıyla seçin:",
                null
        );
    }

    private void processSelection(String mode) {
        if ("EXIT".equals(mode)) {
            System.out.println("Çıkış yapılıyor...");
            System.exit(0);
        }

        System.out.println("\nSeçilen Mod: " + mode);
        System.out.println("Gerekli ajanlar yükleniyor, lütfen bekleyin...\n");

        // TODO: 2. ve 3. Aşamalarda Global Dosya Erişimi ve LangChain4j tetiklemeleri buraya eklenecek.
    }
}