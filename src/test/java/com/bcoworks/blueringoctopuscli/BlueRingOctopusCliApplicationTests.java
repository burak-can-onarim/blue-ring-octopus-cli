package com.bcoworks.blueringoctopuscli;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Argüman verilmezse TuiStartupListener arayüzü açar ve test askıda kalır; bu yüzden yerleşik "help" komutu argüman olarak verilir.
@SpringBootTest(args = "help")
class BlueRingOctopusCliApplicationTests {

    @Test
    void contextLoads() {
    }

}
