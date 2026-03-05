package com.logparser.watcher;

import com.logparser.manager.FileManager;
import com.logparser.manager.LogManager;
import com.logparser.manager.MainLayoutManager;
import com.logparser.model.LogEntry;
import com.logparser.model.Profile;
import com.logparser.remote.SftpRemoteFileAccessor;
import com.jcraft.jsch.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;

import java.util.*;
import java.util.concurrent.*;

public class RemoteLogWatcher {

    private static final Logger log = LoggerFactory.getLogger(RemoteLogWatcher.class);
    private static final long SCAN_INTERVAL_SECONDS = 10;
    private static final long DISCONNECT_DELAY_MS = 60000;

    private final Map<String, Map<String, List<LogEntry>>> profileFileCache = new ConcurrentHashMap<>();
    private final Map<String, List<String>> profileFileListCache = new ConcurrentHashMap<>();
    private final Map<String, Long> remoteFileSizes = new HashMap<>();

    private ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });

    private final FileManager fileManager;
    private final LogManager logManager;
    private final MainLayoutManager layoutManager;

    private Profile activeProfile;
    private SftpRemoteFileAccessor sftpAccessor;
    private volatile boolean active = true;

    public RemoteLogWatcher(MainLayoutManager layoutManager, FileManager fileManager, LogManager logManager) {
        this.layoutManager = layoutManager;
        this.fileManager = fileManager;
        this.logManager = logManager;
    }

    public void startWatching(Profile profile) {
        stopWatching();
        log.info("Starting remote watcher for profile: {}", profile.getName());
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

            if (!sftpAccessor.isAlive()) {
                log.debug("SFTP connection lost, reconnecting...");
                sftpAccessor.disconnect();
                sftpAccessor.connect();
            }

            Channel channel = sftpAccessor.getSession().openChannel("sftp");
            channel.connect(3000);
            ChannelSftp localSftp = (ChannelSftp) channel;
            Vector<ChannelSftp.LsEntry> entries = localSftp.ls(activeProfile.getPath());
            localSftp.disconnect();
            Set<String> currentFileNames = new HashSet<>();

            for (ChannelSftp.LsEntry entry : entries) {
                if (entry.getAttrs().isDir() || entry.getFilename().startsWith(".")) continue;

                String fileName = entry.getFilename();
                long size = entry.getAttrs().getSize();
                long previousSize = remoteFileSizes.getOrDefault(fileName, -1L);
                currentFileNames.add(fileName);

                if (previousSize == -1) {
                    remoteFileSizes.put(fileName, size);
                    String displayName = fileName + " (" + humanReadableByteCountBin(size) + ")";
                    Platform.runLater(() -> fileManager.addNewFile(fileName, size, false));

                    profileFileListCache
                            .computeIfAbsent(activeProfile.getId(), k -> new ArrayList<>())
                            .add(displayName);
                } else if (size > previousSize) {
                    remoteFileSizes.put(fileName, size);

                    new Thread(() -> {
                        Platform.runLater(() -> layoutManager.showLoading(true));
                        String selected = fileManager.getSelectedFileName();
                        if (selected != null && selected.equals(fileName)) {
                            try {
                                List<LogEntry> newEntries = sftpAccessor.readFromOffset(previousSize);

                                profileFileCache
                                        .computeIfAbsent(activeProfile.getId(), k -> new ConcurrentHashMap<>())
                                        .put(fileName, newEntries);

                                Platform.runLater(() -> logManager.prependLogEntries(newEntries));
                            } catch (Exception ex) {
                                log.error("Failed to read new entries from: {}", fileName, ex);
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

            List<String> cached = profileFileListCache.getOrDefault(activeProfile.getId(), new ArrayList<>());
            Iterator<String> iterator = cached.iterator();
            while (iterator.hasNext()) {
                String display = iterator.next();
                String name = display.replaceAll(" \\(.+?\\)$", "");
                if (!currentFileNames.contains(name)) {
                    iterator.remove();
                    Platform.runLater(() -> fileManager.removeFile(name));
                    Map<String, List<LogEntry>> fileMap = profileFileCache.get(activeProfile.getId());
                    if (fileMap != null) fileMap.remove(name);
                }
            }

        } catch (Exception e) {
            log.error("Failed to check remote files", e);
            Platform.runLater(() -> layoutManager.showError("Connection Error", "Failed to check remote files:\n" + e.getMessage()));
        } finally {
            Platform.runLater(() -> {
                layoutManager.showLoading(false);
                layoutManager.showScanIndicator(false);
            });
        }
    }

    private ScheduledExecutorService disconnectExecutor = Executors.newSingleThreadScheduledExecutor();

    public void setActive(boolean active) {
        this.active = active;

        if (!active) {
            disconnectExecutor.schedule(this::stopWatching, DISCONNECT_DELAY_MS, TimeUnit.MILLISECONDS);
        } else {
            if (activeProfile != null && (sftpAccessor == null || !sftpAccessor.isAlive())) {
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
                    sftpAccessor.connect();
                    log.debug("Reconnected SFTP after window focus");
                } catch (Exception e) {
                    log.warn("Failed to reconnect SFTP", e);
                }
            }
        }
    }

    public SftpRemoteFileAccessor getSftpAccessor() {
        return sftpAccessor;
    }

    public List<String> getCachedFileListForProfile(Profile profile) {
        return profileFileListCache.getOrDefault(profile.getId(), new ArrayList<>());
    }

    public void putCachedFileListForProfile(Profile profile, List<String> files) {
        profileFileListCache.put(profile.getId(), files);
    }

    public List<String> getFileListFromCache(Profile profile) {
        return profileFileListCache.getOrDefault(profile.getId(), new ArrayList<>());
    }

    private String humanReadableByteCountBin(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    public Map<String, Map<String, List<LogEntry>>> getProfileFileCache() {
        return profileFileCache;
    }

    public Map<String, List<String>> getProfileFileListCache() {
        return profileFileListCache;
    }

    public void fullDisconnect() {
        if (sftpAccessor != null) {
            sftpAccessor.disconnect();
            sftpAccessor = null;
        }
    }

    public void forceRefresh() {
        if (activeProfile == null) return;

        new Thread(() -> {
            try {
                checkRemoteFiles();
            } catch (Exception e) {
                log.error("Force refresh failed", e);
            }
        }).start();
    }

    public void clearCacheForProfile(String profileId) {
        profileFileListCache.remove(profileId);
        profileFileCache.remove(profileId);
        remoteFileSizes.clear();
    }
}