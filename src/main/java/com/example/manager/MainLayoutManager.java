package com.example.manager;

import com.example.model.Profile;
import com.example.watcher.LogFileWatcher;
import javafx.geometry.Orientation;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.VBox;

import java.io.File;

public class MainLayoutManager {

    private final SplitPane mainLayout;
    private final LogManager logManager;
    private final DetailManager detailManager;
    private final ProfileManager profileManager;
    private final FileManager fileManager;

    private final LogFileWatcher logFileWatcher;

    public MainLayoutManager() {
        mainLayout = new SplitPane();

        // Инициализация менеджеров
        logManager = new LogManager(this);
        detailManager = new DetailManager(this);
        profileManager = new ProfileManager();
        fileManager = new FileManager(this, profileManager);

        // Watcher
        logFileWatcher = new LogFileWatcher(fileManager, logManager);

        // 🔧 Объединённая логика выбора профиля
        profileManager.setOnProfileSelected(profile -> {
            fileManager.getFileNames().clear();              // очищаем список файлов
            logManager.clearLogs();                          // очищаем логи
            if (profile != null) {
                fileManager.getFormatSelector().setValue(profile.getFormat());        // выбираем формат
                logManager.setActiveParser(profile.getFormat());                      // устанавливаем парсер
                fileManager.loadFileList(profile);                                    // загружаем список файлов

                File path = new File(profile.getPath());
                if (path.exists() && path.isDirectory()) {
                    logFileWatcher.startWatching(path);                               // запускаем watcher
                }
            }
        });

        // UI разметка
        VBox leftPanel = new VBox(10, profileManager.getProfilePane(), fileManager.getFileListPane());
        VBox centerPanel = logManager.getLogViewPane();

        SplitPane rightPanel = new SplitPane();
        rightPanel.setOrientation(Orientation.VERTICAL);
        rightPanel.getItems().addAll(centerPanel, detailManager.getDetailPane());
        rightPanel.setDividerPositions(0.4);

        mainLayout.getItems().addAll(leftPanel, rightPanel);
        mainLayout.setDividerPositions(0.2);
    }

    public SplitPane getMainLayout() {
        return mainLayout;
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
        logFileWatcher.stopWatching(); // корректное завершение потока
    }
}