package com.logparser.manager;

import com.logparser.model.LogEntry;
import com.logparser.model.Profile;
import com.logparser.parser.OxLogParser;
import com.logparser.parser.SymfonyLogParser;
import com.logparser.watcher.RemoteLogWatcher;
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
import java.util.*;
import java.util.stream.Collectors;

public class FileManager {

    private final MainLayoutManager layoutManager;
    private final VBox fileListPane;
    private final ComboBox<String> formatSelector;
    private final TextField fileFilterField;
    private final ListView<String> fileListView;
    private final ObservableList<String> fileNames = FXCollections.observableArrayList();
    private final FilteredList<String> filteredFileNames = new FilteredList<>(fileNames, s -> true);
    private final Map<String, Boolean> updatedFiles = new HashMap<>();
    private final Map<String, Long> fileSizes = new HashMap<>(); // Track file sizes
    private final Label fileStatsLabel; // Label for file count and total size

    private ProfileManager profileManager;

    public FileManager(MainLayoutManager layoutManager, ProfileManager profileManager) {
        this.layoutManager = layoutManager;
        this.profileManager = profileManager;

        fileListPane = new VBox(10);
        fileListPane.setPadding(new Insets(10));
        fileListPane.setStyle("-fx-background-color: #f2f2f2;");

        // Initialize file stats label
        fileStatsLabel = new Label("Log Files:");
        fileStatsLabel.setStyle("-fx-font-weight: bold;");

        // Add listener to update stats when file list changes
        fileNames.addListener((javafx.collections.ListChangeListener<String>) change -> updateFileStats());

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
        reloadButton.setOnAction(e -> {
            Profile profile = profileManager.getSelectedProfile();

            // Clear logs and detail view before reloading file list
            layoutManager.getLogManager().clearLogs();
            layoutManager.getDetailManager().showLogDetails(null, null);
            fileListView.getSelectionModel().clearSelection();

            // For remote profiles, clear cache and force refresh from server
            if (profile != null && profile.isRemote()) {
                layoutManager.getRemoteLogWatcher().clearCacheForProfile(profile.getId());
                layoutManager.getRemoteLogWatcher().forceRefresh();
                loadFileList(profile, true); // Force reload without cache
            } else {
                loadFileList(profile);
            }
        });

        Button clearLogsButton = new Button("🗑 Clear Logs");
        clearLogsButton.setOnAction(e -> clearLogs(profileManager.getSelectedProfile()));

        fileListPane.getChildren().addAll(
                new Label("Format:"), formatSelector,
                fileFilterField,
                fileStatsLabel, fileListView,
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
        loadFileList(profile, false);
    }

    public void loadFileList(Profile profile, boolean forceReload) {
        layoutManager.showLoading(true); // 🔥 показываем индикатор
        fileNames.clear();
        fileSizes.clear(); // Clear file sizes
        if (profile == null) {
            layoutManager.showLoading(false); // 🔥 скрываем сразу, если профиль не выбран
            return;
        }

        if (!profile.isRemote()) {
            File path = new File(profile.getPath());
            if (path.isDirectory() && path.exists()) {
                File[] files = path.listFiles();
                if (files != null) {
                    List<String> fileList = Arrays.stream(files)
                            .filter(file -> file.isFile() && !file.getName().startsWith("."))
                            .map(file -> {
                                String fileName = file.getName();
                                long fileSize = file.length();
                                fileSizes.put(fileName, fileSize); // Track file size
                                String size = humanReadableByteCountBin(fileSize);
                                return fileName + " (" + size + ")";
                            })
                            .sorted(String.CASE_INSENSITIVE_ORDER)
                            .collect(Collectors.toList());
                    fileNames.addAll(fileList);
                }
            } else if (path.isFile()) {
                long fileSize = path.length();
                fileSizes.put(path.getName(), fileSize);
                String size = humanReadableByteCountBin(fileSize);
                fileNames.add(path.getName() + " (" + size + ")");
            }
            layoutManager.showLoading(false);
        } else {
            RemoteLogWatcher watcher = layoutManager.getRemoteLogWatcher();
            if (watcher != null && !forceReload) {
                List<String> cachedFiles = watcher.getFileListFromCache(profile);
                if (!cachedFiles.isEmpty()) {
                    // Extract file sizes from cached file names (format: "filename (size)")
                    for (String cachedFile : cachedFiles) {
                        String fileName = cachedFile.replaceAll(" \\(.+?\\)$", "");
                        // Try to extract size from the string (e.g., "file.log (1.5 MB)")
                        String sizeStr = cachedFile.replaceAll("^.*\\((.+?)\\)$", "$1");
                        long sizeBytes = parseSizeString(sizeStr);
                        if (sizeBytes > 0) {
                            fileSizes.put(fileName, sizeBytes);
                        }
                    }

                    // Sort cached files alphabetically
                    List<String> sortedFiles = new ArrayList<>(cachedFiles);
                    sortedFiles.sort(String.CASE_INSENSITIVE_ORDER);
                    fileNames.setAll(sortedFiles);
                    System.out.println("⚡ Loaded cached file list for profile: " + profile.getId());
                    layoutManager.showLoading(false);
                } else {
                    // Кеш пустой, загрузка будет из watcher
                    layoutManager.showLoading(true);
                }
            } else {
                // Force reload - watcher will populate the list
                layoutManager.showLoading(true);
            }
        }
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
            fileSizes.put(fileName, sizeBytes); // Track file size
            // Sort the list alphabetically after adding
            FXCollections.sort(fileNames, String.CASE_INSENSITIVE_ORDER);
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

                // ✅ Очистка UI:
                fileNames.clear();
                updatedFiles.clear();
                fileListView.getSelectionModel().clearSelection(); // сброс выбора
                layoutManager.getLogManager().clearLogs();          // очистка таблицы
                layoutManager.getDetailManager().showLogDetails(null, null); // очистка detail-панели
            }
        });
    }

    public void removeFile(String fileName) {
        fileNames.removeIf(name -> name.startsWith(fileName));
        updatedFiles.remove(fileName);
        fileSizes.remove(fileName);
        refreshFileListView();
    }

    public void showCachedFileList(List<String> cachedFiles) {
        fileNames.setAll(cachedFiles);
    }

    /**
     * Updates file statistics label with file count and total size
     */
    private void updateFileStats() {
        int fileCount = fileNames.size();
        long totalSize = fileSizes.values().stream().mapToLong(Long::longValue).sum();
        String totalSizeStr = humanReadableByteCountBin(totalSize);

        String statsText = String.format("Log Files: %d file%s (%s total)",
            fileCount,
            fileCount == 1 ? "" : "s",
            totalSizeStr);

        fileStatsLabel.setText(statsText);
    }

    /**
     * Parse size string back to bytes (e.g., "1.5 MB" -> 1572864)
     * This is a best-effort approximation for display purposes
     */
    private long parseSizeString(String sizeStr) {
        if (sizeStr == null || sizeStr.trim().isEmpty()) {
            return 0;
        }

        try {
            sizeStr = sizeStr.trim().toUpperCase();
            double value = 0;
            long multiplier = 1;

            if (sizeStr.endsWith("B")) {
                // Remove the 'B' at the end
                sizeStr = sizeStr.substring(0, sizeStr.length() - 1).trim();

                // Check for unit prefix
                if (sizeStr.endsWith("K")) {
                    multiplier = 1024;
                    sizeStr = sizeStr.substring(0, sizeStr.length() - 1);
                } else if (sizeStr.endsWith("M")) {
                    multiplier = 1024 * 1024;
                    sizeStr = sizeStr.substring(0, sizeStr.length() - 1);
                } else if (sizeStr.endsWith("G")) {
                    multiplier = 1024 * 1024 * 1024;
                    sizeStr = sizeStr.substring(0, sizeStr.length() - 1);
                } else if (sizeStr.endsWith("T")) {
                    multiplier = 1024L * 1024 * 1024 * 1024;
                    sizeStr = sizeStr.substring(0, sizeStr.length() - 1);
                }
            }

            value = Double.parseDouble(sizeStr.trim());
            return (long) (value * multiplier);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
