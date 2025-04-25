package com.example.watcher;

import com.example.manager.FileManager;
import com.example.manager.LogManager;
import com.example.manager.MainLayoutManager;
import com.example.model.LogEntry;
import com.example.model.Profile;
import com.example.remote.SftpRemoteFileAccessor;
import com.example.utils.PagedLogLoader;
import com.jcraft.jsch.*;

import javafx.application.Platform;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.*;
import com.example.remote.RemoteFileAccessor;
import com.example.remote.SftpRemoteFileAccessor;

public class RemoteLogWatcher {

    private static final long SCAN_INTERVAL_SECONDS = 10;

    private ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });

    private final Map<String, Long> remoteFileSizes = new HashMap<>();
    private final FileManager fileManager;
    private final LogManager logManager;
    private Profile activeProfile;
    private Session session;
    private SftpRemoteFileAccessor sftpAccessor;
    private final MainLayoutManager layoutManager;
    private volatile boolean active = true;

    public RemoteLogWatcher(MainLayoutManager layoutManager, FileManager fileManager, LogManager logManager) {
        this.layoutManager = layoutManager;
        this.fileManager = fileManager;
        this.logManager = logManager;
    }

    public void startWatching(Profile profile) {
        stopWatching(); // ensure no duplicates
        this.layoutManager.setFirstScanProfile(true);
        this.activeProfile = profile;
        this.remoteFileSizes.clear();

        if (scheduler == null || scheduler.isShutdown() || scheduler.isTerminated()) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r);
                t.setDaemon(true);
                return t;
            });
        }
        scheduler.scheduleAtFixedRate(this::checkRemoteFiles, 0, SCAN_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    public void stopWatching() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        if (sftpAccessor != null) {
            sftpAccessor.disconnect();
            sftpAccessor = null;
            System.out.println("🔌 SFTP accessor disconnected by RemoteLogWatcher");
        }
    }

    private ChannelSftp connectSFTP() {
        try {
            if (session == null || !session.isConnected()) {
                JSch jsch = new JSch();
                session = jsch.getSession(activeProfile.getUsername(), activeProfile.getHost(), activeProfile.getPort());
                session.setPassword(activeProfile.getPassword());
                session.setConfig("StrictHostKeyChecking", "no");
                session.connect(5000);
            }

            Channel channel = session.openChannel("sftp");
            channel.connect(3000);
            return (ChannelSftp) channel;

        } catch (Exception e) {
            System.err.println("❌ Could not connect to SFTP: " + e.getMessage());
            return null;
        }
    }

    private void checkRemoteFiles() {
        if (!active) return;

        if (this.layoutManager.getFirstScanProfile()) {
            this.layoutManager.showLoading(true);
            this.layoutManager.setFirstScanProfile(false);
        }
        Platform.runLater(() -> layoutManager.showScanIndicator(true));

        try {
            if (sftpAccessor == null) {
                sftpAccessor = new SftpRemoteFileAccessor(
                        activeProfile.getHost(),
                        activeProfile.getPort(),
                        activeProfile.getUsername(),
                        activeProfile.getPassword(),
                        activeProfile.getPath(),
                        logManager.getActiveParser()
                );
            }

            sftpAccessor.connect(); // ✅ будет подключаться только если нет соединения

            Vector<ChannelSftp.LsEntry> entries = sftpAccessor.getSftpChannel().ls(activeProfile.getPath());

            for (ChannelSftp.LsEntry entry : entries) {
                if (entry.getAttrs().isDir() || entry.getFilename().startsWith(".")) continue;

                String fileName = entry.getFilename();
                long size = entry.getAttrs().getSize();
                long previousSize = remoteFileSizes.getOrDefault(fileName, -1L);

                if (previousSize == -1) {
                    remoteFileSizes.put(fileName, size);
                    Platform.runLater(() ->
                            fileManager.addNewFile(fileName, size, false)
                    );
                } else if (size > previousSize) {
                    remoteFileSizes.put(fileName, size);

                    new Thread(() -> {
                        Platform.runLater(() -> layoutManager.showLoading(true));

                        String selected = fileManager.getSelectedFileName();
                        if (selected != null && selected.equals(fileName)) {
                            try {
                                List<LogEntry> newEntries = sftpAccessor.readFromOffset(previousSize);
                                Platform.runLater(() -> logManager.prependLogEntries(newEntries));
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            } finally {
                                Platform.runLater(() -> layoutManager.showLoading(false));
                            }
                        } else {
                            Platform.runLater(() -> {
                                fileManager.markFileAsUpdated(fileName);
                                layoutManager.showLoading(false);
                            });
                        }
                    }).start();
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            Platform.runLater(() -> layoutManager.showError("Connection Error", "Failed to check remote files:\n" + e.getMessage()));
        } finally {
            layoutManager.showLoading(false);
            Platform.runLater(() -> layoutManager.showScanIndicator(false));
        }
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public SftpRemoteFileAccessor getSftpAccessor() {
        return sftpAccessor;
    }
}