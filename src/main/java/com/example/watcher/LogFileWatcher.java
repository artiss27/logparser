package com.example.watcher;

import com.example.manager.FileManager;
import com.example.manager.LogManager;
import com.example.manager.MainLayoutManager;
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
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class LogFileWatcher {

    private static final long SCAN_INTERVAL_SECONDS = 5;
    private volatile boolean active = true;
    private volatile boolean isFirstScan = true;

    private ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r);
        t.setName("LogFileWatcher-Scheduler");
        t.setDaemon(true);
        return t;
    });

    private ScheduledFuture<?> watchTask;
    private final Map<File, Long> fileReadOffsets = new HashMap<>();
    private final FileManager fileManager;
    private final LogManager logManager;
    private final MainLayoutManager layoutManager;

    public LogFileWatcher(MainLayoutManager layoutManager, FileManager fileManager, LogManager logManager) {
        this.layoutManager = layoutManager;
        this.fileManager = fileManager;
        this.logManager = logManager;
    }

    public void startWatching(File directory) {
        stopWatching();
        this.isFirstScan = true;
        this.layoutManager.setFirstScanProfile(true);

        // Recreate scheduler if needed
        if (scheduler.isShutdown()) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r);
                t.setName("LogFileWatcher-Scheduler");
                t.setDaemon(true);
                return t;
            });
        }

        watchTask = scheduler.scheduleAtFixedRate(() -> {
            if (!active) return;

            if (this.layoutManager.getFirstScanProfile()) {
                this.layoutManager.showLoading(true);
                this.layoutManager.setFirstScanProfile(false);
            }
            Platform.runLater(() -> layoutManager.showScanIndicator(true));
            try {
                File[] files = directory.listFiles((dir, name) -> !name.startsWith("."));
                if (files == null) return;

                boolean firstScan = isFirstScan;
                if (isFirstScan) {
                    isFirstScan = false;
                }

                for (File file : files) {
                    if (!file.isFile()) continue;

                    long currentSize = file.length();
                    Long previousOffset = fileReadOffsets.get(file);

                    if (previousOffset == null) {
                        fileReadOffsets.put(file, currentSize);
                        // Only mark as updated if NOT first scan (means it's a newly created file)
                        boolean markAsUpdated = !firstScan;
                        Platform.runLater(() -> fileManager.addNewFile(file.getName(), file.length(), markAsUpdated));
                        continue;
                    }

                    if (previousOffset < currentSize) {
                        fileReadOffsets.put(file, currentSize);

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
            } finally {
                this.layoutManager.showLoading(false);
                Platform.runLater(() -> layoutManager.showScanIndicator(false));
            }
        }, 0, SCAN_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    public void stopWatching() {
        if (watchTask != null && !watchTask.isCancelled()) {
            watchTask.cancel(true);
        }
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
        fileReadOffsets.clear();
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}