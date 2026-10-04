package com.bcoworks.codeanalyzer.context;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
public class AppContext {
    private AppMode currentMode = AppMode.KOD_ANALIZI;
}