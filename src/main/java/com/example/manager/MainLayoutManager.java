package com.example.manager;

import javafx.geometry.Orientation;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.VBox;

/**
 * Управляет основным расположением интерфейса.
 */
public class MainLayoutManager {

    private final SplitPane mainLayout;
    private final LogManager logManager;
    private final DetailManager detailManager;
    private final ProfileManager profileManager;
    private final FileManager fileManager;

    public MainLayoutManager() {
        mainLayout = new SplitPane();

        // Сначала инициализируем менеджеры
        logManager = new LogManager(this);
        detailManager = new DetailManager(this);
        profileManager = new ProfileManager();
        fileManager = new FileManager(this, profileManager);

        // Левая панель = Профили + Файлы
        VBox leftPanel = new VBox(10, profileManager.getProfilePane(), fileManager.getFileListPane());

        // Центральная панель = Логи + Детальный просмотр
        VBox centerPanel = logManager.getLogViewPane();

        SplitPane rightPanel = new SplitPane();
        rightPanel.setOrientation(Orientation.VERTICAL);
        rightPanel.getItems().addAll(centerPanel, detailManager.getDetailPane());

        // Устанавливаем деление между таблицей и деталями (30% / 70%)
        rightPanel.setDividerPositions(0.4);

        mainLayout.getItems().addAll(leftPanel, rightPanel);

        // Деление между левым и правым блоком (20% / 80%)
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
}