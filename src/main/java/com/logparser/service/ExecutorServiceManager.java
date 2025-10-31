package com.logparser.service;

import com.logparser.config.AppConfig;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Centralized executor service for async operations
 */
public final class ExecutorServiceManager {

    private static ExecutorServiceManager instance;
    private final ExecutorService executorService;

    private ExecutorServiceManager() {
        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setName("LogParser-Worker-" + counter.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        };

        this.executorService = Executors.newFixedThreadPool(
            AppConfig.EXECUTOR_THREAD_POOL_SIZE,
            threadFactory
        );
    }

    /**
     * Get singleton instance
     * @return ExecutorServiceManager instance
     */
    public static synchronized ExecutorServiceManager getInstance() {
        if (instance == null) {
            instance = new ExecutorServiceManager();
        }
        return instance;
    }

    /**
     * Get the executor service
     * @return ExecutorService
     */
    public ExecutorService getExecutor() {
        return executorService;
    }

    /**
     * Shutdown the executor service gracefully
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Execute a task asynchronously
     * @param task The task to execute
     */
    public void execute(Runnable task) {
        executorService.execute(task);
    }
}

