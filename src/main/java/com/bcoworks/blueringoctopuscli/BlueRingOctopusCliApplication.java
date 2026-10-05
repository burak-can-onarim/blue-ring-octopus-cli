package com.bcoworks.blueringoctopuscli;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BlueRingOctopusCliApplication {

    static void main(String[] args) {
        System.setProperty("java.awt.headless", "false");
        SpringApplication.run(BlueRingOctopusCliApplication.class, args);
    }
}