package com.example.manager;

import com.example.watcher.LogFileWatcher;
import com.example.watcher.RemoteLogWatcher;
import javafx.geometry.Orientation;
import javafx.scene.control.Alert;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import java.util.List;

import java.io.File;

public class MainLayoutManager {

    private final StackPane root;
    private final SplitPane mainLayout;
    private final LogManager logManager;
    private final DetailManager detailManager;
    private final ProfileManager profileManager;
    private final FileManager fileManager;

    private final LogFileWatcher localLogWatcher;
    private final RemoteLogWatcher remoteLogWatcher;

    private final ProgressIndicator loadingIndicator;
    private boolean windowFocused = true;
    private final ProgressIndicator scanIndicator;
    public boolean firstScanProfile = true;

    public MainLayoutManager() {
        mainLayout = new SplitPane();

        // Инициализация менеджеров
        logManager = new LogManager(this);
        detailManager = new DetailManager(this);
        profileManager = new ProfileManager();
        fileManager = new FileManager(this, profileManager);

        // Watchers
        localLogWatcher = new LogFileWatcher(this, fileManager, logManager);
        remoteLogWatcher = new RemoteLogWatcher(this, fileManager, logManager);

        profileManager.setOnProfileSelected(profile -> {
            fileManager.getFileNames().clear();  // Очистить старый список
            logManager.clearLogs();               // Очистить логи
            localLogWatcher.stopWatching();
            remoteLogWatcher.stopWatching();

            if (profile != null) {
                fileManager.getFormatSelector().setValue(profile.getFormat());
                logManager.setActiveParser(profile.getFormat());

                if (profile.isRemote()) {
                    // ======== РАБОТАЕМ С УДАЛЁННЫМ ПРОФИЛЕМ ==========
                    RemoteLogWatcher watcher = getRemoteLogWatcher();
                    List<String> cachedFiles = watcher.getFileListFromCache(profile);

                    if (!cachedFiles.isEmpty()) {
                        // ⚡ Показать файлы из кэша
                        fileManager.getFileNames().setAll(cachedFiles);
                        System.out.println("⚡ Showing cached remote file list for profile: " + profile.getId());
                        showLoading(false); // сразу скрыть лоадер
                    } else {
                        // ❗ Нет кэша — показываем лоадер до получения списка
                        showLoading(true);
                    }

                    if (profile.getHost() != null && !profile.getHost().isBlank()) {
                        watcher.startWatching(profile);  // вотчер сам загрузит и обновит
                    }

                } else {
                    // ======== РАБОТАЕМ С ЛОКАЛЬНЫМ ПРОФИЛЕМ ==========
                    File path = new File(profile.getPath());
                    if (path.exists() && path.isDirectory()) {
                        localLogWatcher.startWatching(path); // локальный вотчер сам загрузит
                        showLoading(true); // показать лоадер на старте
                    } else {
                        fileManager.getFileNames().clear();
                        showLoading(false); // нет папки — нет файлов
                    }
                }
            } else {
                // если нет профиля
                fileManager.getFileNames().clear();
                showLoading(false);
            }
        });

        VBox leftPanel = new VBox(10, profileManager.getProfilePane(), fileManager.getFileListPane());
        VBox centerPanel = logManager.getLogViewPane();

        SplitPane rightPanel = new SplitPane();
        rightPanel.setOrientation(Orientation.VERTICAL);
        rightPanel.getItems().addAll(centerPanel, detailManager.getDetailPane());
        rightPanel.setDividerPositions(0.4);

        mainLayout.getItems().addAll(leftPanel, rightPanel);
        mainLayout.setDividerPositions(0.2);

        loadingIndicator = new ProgressIndicator();
        loadingIndicator.setVisible(false);
        loadingIndicator.setMaxSize(100, 100);

        scanIndicator = new ProgressIndicator();
        scanIndicator.setVisible(false);
        scanIndicator.getStyleClass().add("tiny-indicator"); // 👈 кастомный класс
        StackPane.setAlignment(scanIndicator, Pos.TOP_RIGHT);
        StackPane.setMargin(scanIndicator, new Insets(5));

        root = new StackPane(mainLayout, loadingIndicator, scanIndicator);
    }

    public StackPane getMainLayout() {
        return root;
    }

    public void showLoading(boolean show) {
        loadingIndicator.setVisible(show);
        mainLayout.setDisable(show); // Опционально, чтобы не давать пользователю взаимодействовать во время загрузки
    }

    public LogManager getLogManager() {
        return logManager;
    }

    public DetailManager getDetailManager() {
        return detailManager;
    }

    public ProfileManager getProfileManager() {
        return profileManager;
    }

    public FileManager getFileManager() {
        return fileManager;
    }

    public void shutdown() {
        localLogWatcher.stopWatching();
        remoteLogWatcher.stopWatching();
        remoteLogWatcher.fullDisconnect();
        logManager.clearLogs();
    }

    public void setWindowFocused(boolean focused) {
        this.windowFocused = focused;
        localLogWatcher.setActive(focused);
        remoteLogWatcher.setActive(focused);
    }

    public void showScanIndicator(boolean show) {
        scanIndicator.setVisible(show);
    }

    public void setFirstScanProfile(boolean show) {
        this.firstScanProfile = show;
    }

    public boolean getFirstScanProfile() {
        return this.firstScanProfile;
    }

    public void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public RemoteLogWatcher getRemoteLogWatcher() {
        return remoteLogWatcher;
    }

    public void clearLogDisplay() {
        getLogManager().clearLogs(); // если такой метод уже есть
    }
}