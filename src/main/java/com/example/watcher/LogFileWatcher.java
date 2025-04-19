package com.example.watcher;

import com.example.manager.FileManager;
import com.example.manager.LogManager;
import com.example.model.LogEntry;
import com.example.utils.PagedLogLoader;
import javafx.application.Platform;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class LogFileWatcher {

    private static final long SCAN_INTERVAL_SECONDS = 5;

    // 🔧 daemon thread — не блокирует завершение JVM
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });

    private final Map<File, Long> fileReadOffsets = new HashMap<>();
    private final FileManager fileManager;
    private final LogManager logManager;

    public LogFileWatcher(FileManager fileManager, LogManager logManager) {
        this.fileManager = fileManager;
        this.logManager = logManager;
    }

    public void startWatching(File directory) {
        scheduler.scheduleAtFixedRate(() -> {
            File[] files = directory.listFiles((dir, name) -> !name.startsWith("."));
            if (files == null) return;

            for (File file : files) {
                if (!file.isFile()) continue;

                long currentSize = file.length();
                Long previousOffset = fileReadOffsets.get(file);

                // 🔧 Новый файл
                if (previousOffset == null) {
                    fileReadOffsets.put(file, currentSize);
                    Platform.runLater(() -> fileManager.addNewFile(file));
                    continue;
                }

                // 🔧 Файл изменён
                if (previousOffset < currentSize) {
                    fileReadOffsets.put(file, currentSize); // сразу обновляем offset

                    Platform.runLater(() -> {
                        String selectedFile = fileManager.getSelectedFileName();

                        if (selectedFile != null && selectedFile.equals(file.getName())) {
                            try {
                                PagedLogLoader loader = new PagedLogLoader(file, logManager.getActiveParser());
                                List<LogEntry> newEntries = loader.loadNewLines(previousOffset);
                                logManager.prependLogEntries(newEntries);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        } else {
                            fileManager.markFileAsUpdated(file.getName());
                        }
                    });
                }
            }
        }, 0, SCAN_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    public void stopWatching() {
        scheduler.shutdownNow();
    }
}