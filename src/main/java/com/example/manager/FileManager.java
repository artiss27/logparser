package com.example.manager;

import com.example.model.Profile;
import com.example.parser.OxLogParser;
import com.example.parser.SymfonyLogParser;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.io.File;
import java.util.Arrays;

public class FileManager {

    private final MainLayoutManager layoutManager;
    private final VBox fileListPane;
    private final ComboBox<String> formatSelector;
    private final ListView<String> fileListView;
    private final ObservableList<String> fileNames = FXCollections.observableArrayList();

    public FileManager(MainLayoutManager layoutManager, ProfileManager profileManager) {
        this.layoutManager = layoutManager;

        fileListPane = new VBox(10);
        fileListPane.setPadding(new Insets(10));
        fileListPane.setStyle("-fx-background-color: #f2f2f2;");

        formatSelector = new ComboBox<>(FXCollections.observableArrayList("OX", "Symfony"));
        formatSelector.setValue("OX");
        formatSelector.setPrefWidth(150);
        formatSelector.setOnAction(e -> layoutManager.getLogManager().setActiveParser(formatSelector.getValue()));

        fileListView = new ListView<>(fileNames);
        fileListView.setPlaceholder(new Label("No log files found."));
        fileListView.getSelectionModel().selectedItemProperty().addListener((obs, oldFile, newFile) -> {
            if (newFile != null) {
                Profile profile = profileManager.getSelectedProfile();
                if (profile != null) {
                    String fileName = newFile.replaceAll(" \\(.*\\)$", "");
                    layoutManager.getLogManager().loadLogsFromFile(new File(profile.getPath(), fileName).getPath());
                }
            }
        });

        Button reloadButton = new Button("Reload Files");
        reloadButton.setOnAction(e -> loadFileList(profileManager.getSelectedProfile()));

        fileListPane.getChildren().addAll(
                new Label("Format:"), formatSelector,
                new Label("Log Files:"), fileListView, reloadButton
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

    private void loadFileList(Profile profile) {
        fileNames.clear();
        if (profile == null) return;

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
}