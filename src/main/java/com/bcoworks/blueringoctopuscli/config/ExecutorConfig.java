package com.bcoworks.blueringoctopuscli.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ExecutorConfig {

    @Bean(name = "aiTaskExecutor", destroyMethod = "shutdownNow")
    public ExecutorService aiTaskExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}