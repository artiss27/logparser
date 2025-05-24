package com.example.manager;

import com.example.model.LogEntry;
import com.example.model.Profile;
import com.example.parser.OxLogParser;
import com.example.parser.SymfonyLogParser;
import com.example.watcher.RemoteLogWatcher;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FileManager {

    private final MainLayoutManager layoutManager;
    private final VBox fileListPane;
    private final ComboBox<String> formatSelector;
    private final TextField fileFilterField;
    private final ListView<String> fileListView;
    private final ObservableList<String> fileNames = FXCollections.observableArrayList();
    private final FilteredList<String> filteredFileNames = new FilteredList<>(fileNames, s -> true);
    private final Map<String, Boolean> updatedFiles = new HashMap<>();

    private ProfileManager profileManager;

    public FileManager(MainLayoutManager layoutManager, ProfileManager profileManager) {
        this.layoutManager = layoutManager;
        this.profileManager = profileManager;

        fileListPane = new VBox(10);
        fileListPane.setPadding(new Insets(10));
        fileListPane.setStyle("-fx-background-color: #f2f2f2;");

        formatSelector = new ComboBox<>(FXCollections.observableArrayList("OX", "Symfony"));
        formatSelector.setValue("OX");
        formatSelector.setPrefWidth(150);
        formatSelector.setOnAction(e -> layoutManager.getLogManager().setActiveParser(formatSelector.getValue()));

        fileFilterField = new TextField();
        fileFilterField.setPromptText("Filter files...");
        fileFilterField.textProperty().addListener((obs, oldVal, newVal) -> {
            String lower = newVal.toLowerCase();
            filteredFileNames.setPredicate(item -> item.toLowerCase().contains(lower));
        });

        fileListView = new ListView<>(filteredFileNames);
        fileListView.setPlaceholder(new Label("No log files found."));
        fileListView.getSelectionModel().selectedItemProperty().addListener((obs, oldFile, newFile) -> {
            if (newFile != null && !newFile.equals(oldFile)) {
                Profile profile = profileManager.getSelectedProfile();
                if (profile != null) {
                    String fileName = newFile.replaceAll(" \\(.+?\\)$", "");
                    updatedFiles.put(fileName, false);
                    refreshFileListView();

                    if (profile.isRemote()) {
                        String remoteFilePath = profile.getPath() + "/" + fileName;
                        layoutManager.getLogManager().loadLogsFromFile(remoteFilePath, true);
                    } else {
                        layoutManager.getLogManager().loadLogsFromFile(new File(profile.getPath(), fileName).getPath(), false);
                    }
                }
            }
        });

        Button reloadButton = new Button("Reload Files");
        reloadButton.setOnAction(e -> loadFileList(profileManager.getSelectedProfile()));

        Button clearLogsButton = new Button("🗑 Clear Logs");
        clearLogsButton.setOnAction(e -> clearLogs(profileManager.getSelectedProfile()));

        fileListPane.getChildren().addAll(
                new Label("Format:"), formatSelector,
                fileFilterField,
                new Label("Log Files:"), fileListView, reloadButton,
                new HBox(10, reloadButton, clearLogsButton)
        );

        layoutManager.getLogManager().registerParser("OX", new OxLogParser());
        layoutManager.getLogManager().registerParser("Symfony", new SymfonyLogParser());
        layoutManager.getLogManager().setActiveParser(formatSelector.getValue());

        profileManager.setOnProfileSelected(profile -> {
            fileNames.clear();
            layoutManager.getLogManager().clearLogs();
            if (profile != null) {
                formatSelector.setValue(profile.getFormat());
                layoutManager.getLogManager().setActiveParser(profile.getFormat());
                loadFileList(profile);
            }
        });
    }

    public ObservableList<String> getFileNames() {
        return fileNames;
    }

    public ComboBox<String> getFormatSelector() {
        return formatSelector;
    }

    public void loadFileList(Profile profile) {
        layoutManager.showLoading(true); // 🔥 показываем индикатор
        fileNames.clear();
        if (profile == null) {
            layoutManager.showLoading(false); // 🔥 скрываем сразу, если профиль не выбран
            return;
        }

        if (!profile.isRemote()) {
            File path = new File(profile.getPath());
            if (path.isDirectory() && path.exists()) {
                File[] files = path.listFiles();
                if (files != null) {
                    Arrays.stream(files)
                            .filter(file -> file.isFile() && !file.getName().startsWith("."))
                            .forEach(file -> {
                                String size = humanReadableByteCountBin(file.length());
                                fileNames.add(file.getName() + " (" + size + ")");
                            });
                }
            } else if (path.isFile()) {
                String size = humanReadableByteCountBin(path.length());
                fileNames.add(path.getName() + " (" + size + ")");
            }
        } else {
            RemoteLogWatcher watcher = layoutManager.getRemoteLogWatcher();
            if (watcher != null) {
                List<String> cachedFiles = watcher.getFileListFromCache(profile);
                if (!cachedFiles.isEmpty()) {
                    fileNames.setAll(cachedFiles); // ✅ заменить, а не добавлять!
                    System.out.println("⚡ Loaded cached file list for profile: " + profile.getId());
                } else {
                    layoutManager.showLoading(true); // ✅ показывать лоадер только если кэш пустой
                }
            }
        }

        layoutManager.showLoading(false); // 🔥 скрываем после загрузки списка
    }

    public VBox getFileListPane() {
        return fileListPane;
    }

    private String humanReadableByteCountBin(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    public void markFileAsUpdated(String fileName) {
        updatedFiles.put(fileName, true);
        refreshFileListView();
    }

    private void refreshFileListView() {
        fileListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    String fileName = item.replaceAll(" \\(.+?\\)$", "");
                    boolean hasUpdates = updatedFiles.getOrDefault(fileName, false);
                    setText(item);
                    if (hasUpdates) {
                        Circle dot = new Circle(5, Color.RED);
                        setGraphic(dot);
                    } else {
                        setGraphic(null);
                    }
                }
            }
        });
    }

    public void addNewFile(String fileName, long sizeBytes, boolean markAsUpdated) {
        String displayName = fileName + " (" + humanReadableByteCountBin(sizeBytes) + ")";
        if (fileNames.stream().noneMatch(name -> name.startsWith(fileName))) {
            fileNames.add(displayName);
            updatedFiles.put(fileName, markAsUpdated);
            refreshFileListView();
        }
    }

    public String getSelectedFileName() {
        String selected = fileListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            return selected.replaceAll(" \\(.+?\\)$", "");
        }
        return null;
    }

    private void clearLogs(Profile profile) {
        if (profile == null || profile.isRemote()) {
            new Alert(Alert.AlertType.WARNING, "Log clearing is available only for local profiles.", ButtonType.OK).showAndWait();
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Clear all logs in the directory?", ButtonType.YES, ButtonType.NO);
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                File dir = new File(profile.getPath());
                File[] logs = dir.listFiles((d, name) -> !name.startsWith("."));
                if (logs != null) {
                    for (File file : logs) file.delete();
                }
                loadFileList(profile);
            }
        });
    }

    public void removeFile(String fileName) {
        fileNames.removeIf(name -> name.startsWith(fileName));
        updatedFiles.remove(fileName);
        refreshFileListView();
    }

    public void showCachedFileList(List<String> cachedFiles) {
        fileNames.setAll(cachedFiles);
    }
}
