package com.bcoworks.blueringoctopuscli.context;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
public class AppContext {
    private AppMode currentMode = AppMode.CODE_ANALYSIS;
}