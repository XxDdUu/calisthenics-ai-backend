package com.calisthenics.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class LiveAnalysisExecutorConfig {

    @Bean(name = "liveAnalysisExecutor", destroyMethod = "shutdown")
    public Executor liveAnalysisExecutor() {
        return new ThreadPoolExecutor(
                2,
                4,
                30,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(8),
                runnable -> {
                    Thread thread = new Thread(runnable, "live-analysis");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
    }
}
