package com.example.manager;

import com.example.model.Profile;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import com.example.utils.CryptoUtils;
import javax.crypto.SecretKey;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.ArrayList;

public class ProfileManager {

    private final VBox profilePane;
    private final ComboBox<Profile> profileSelector;
    private final ObservableList<Profile> profiles = FXCollections.observableArrayList();
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String PROFILE_FILE = getProfileFilePath();
    private Consumer<Profile> profileSelectedCallback;

    static {
        String envHome = System.getenv("HOME");
        if (envHome != null && !envHome.isBlank()) {
            System.setProperty("user.home", envHome);
        }
    }

    public ProfileManager() {
        profilePane = new VBox(10);
        profilePane.setPadding(new Insets(10));

        profileSelector = new ComboBox<>(profiles);
        profileSelector.setPromptText("Select profile...");
        profileSelector.setPrefWidth(250);
        profileSelector.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Profile item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText("Select or Add Profile...");
                } else {
                    setText(item.getName());
                }
            }
        });
        profileSelector.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Profile item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        });

        profileSelector.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (profileSelectedCallback != null && newVal != null) {
                profileSelectedCallback.accept(newVal);
            }
        });

        Button addButton = new Button("+");
        addButton.setOnAction(e -> openAddProfileDialog());

        Button editButton = new Button("✎");
        editButton.setStyle("-fx-font-size: 14px;");
        editButton.setOnAction(e -> openEditProfileDialog());

        Button deleteButton = new Button("-");
        deleteButton.setOnAction(e -> deleteSelectedProfile());

        HBox controls = new HBox(5, profileSelector, addButton, editButton, deleteButton);
        HBox.setHgrow(profileSelector, Priority.ALWAYS);

        profilePane.getChildren().addAll(new Label("Profiles:"), controls);

        profiles.addAll(loadProfiles());
    }

    public VBox getProfilePane() {
        return profilePane;
    }

    public Profile getSelectedProfile() {
        return profileSelector.getSelectionModel().getSelectedItem();
    }

    public void setOnProfileSelected(Consumer<Profile> callback) {
        this.profileSelectedCallback = callback;
    }

    private void deleteSelectedProfile() {
        Profile selected = getSelectedProfile();
        if (selected != null) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Delete Profile");
            confirm.setHeaderText("Are you sure you want to delete this profile?");
            confirm.setContentText(selected.getName());

            Optional<ButtonType> result = confirm.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                profiles.remove(selected);
                saveProfiles();
                if (!profiles.isEmpty()) {
                    profileSelector.getSelectionModel().selectFirst();
                }
            }
        }
    }

    private List<Profile> loadProfiles() {
        File file = new File(PROFILE_FILE.replace(".json", ".json.enc"));
        if (file.exists()) {
            try {
                SecretKey key = CryptoUtils.getOrCreateKey();
                byte[] encrypted = Files.readAllBytes(file.toPath());
                byte[] decrypted = CryptoUtils.decrypt(encrypted, key);
                // JSON to List<Profile>
                List<Profile> list = mapper.readValue(decrypted, new TypeReference<>() {});
                list.sort(Comparator.comparing(Profile::getName, String.CASE_INSENSITIVE_ORDER)); // ✅ сортировка
                return list;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return new ArrayList<>();
    }

    private void saveProfiles() {
        try {
            SecretKey key = CryptoUtils.getOrCreateKey();
            byte[] json = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(profiles);
            byte[] encrypted = CryptoUtils.encrypt(json, key);
            File file = new File(PROFILE_FILE.replace(".json", ".json.enc"));
            file.getParentFile().mkdirs();
            Files.write(file.toPath(), encrypted);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void openAddProfileDialog() {
        Stage dialog = new Stage();
        dialog.setTitle("Add New Profile");
        dialog.initModality(Modality.APPLICATION_MODAL);

        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(20));
        vbox.setStyle("-fx-background-color: #f9f9f9; -fx-background-radius: 10;");

        TextField nameField = new TextField();
        nameField.setPromptText("Profile Name");
        nameField.setPrefWidth(300);

        CheckBox remoteCheck = new CheckBox("Remote SFTP");

        TextField pathField = new TextField();
        pathField.setPromptText("Select Path...");
        pathField.setEditable(false);
        Button browseButton = new Button("Browse");
        browseButton.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Select Log Directory");
            File selectedDir = chooser.showDialog(dialog);
            if (selectedDir != null) {
                pathField.setText(selectedDir.getAbsolutePath());
                if (nameField.getText().isBlank()) {
                    nameField.setText(selectedDir.getName());
                }
            }
        });
        HBox pathBox = new HBox(5, pathField, browseButton);
        HBox.setHgrow(pathField, Priority.ALWAYS);

        VBox pathSection = new VBox(5, new Label("Log Directory:"), pathBox);

        TextField remotePathField = new TextField();
        remotePathField.setPromptText("Remote path (e.g. /src/log)");
        TextField hostField = new TextField();
        hostField.setPromptText("SFTP Host");

        TextField portField = new TextField();
        portField.setPromptText("Port (default 22)");
        portField.setText("22");

        TextField userField = new TextField();
        userField.setPromptText("Username");

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");

        VBox remoteBox = new VBox(5,
                new Label("Remote Path:"), remotePathField,
                new Label("Host:"), hostField,
                new Label("Port:"), portField,
                new Label("Username:"), userField,
                new Label("Password:"), passwordField
        );

        pathSection.setVisible(true);
        pathSection.setManaged(true);
        remoteBox.setVisible(false);
        remoteBox.setManaged(false);

        remoteCheck.selectedProperty().addListener((obs, oldVal, isSelected) -> {
            pathSection.setVisible(!isSelected);
            pathSection.setManaged(!isSelected);
            remoteBox.setVisible(isSelected);
            remoteBox.setManaged(isSelected);
        });

        ComboBox<String> formatBox = new ComboBox<>();
        formatBox.getItems().addAll("OX", "Symfony");
        formatBox.setPromptText("Select Format");
        formatBox.setPrefWidth(300);

        Button saveButton = new Button("Save");
        Button cancelButton = new Button("Cancel");
        HBox buttonBox = new HBox(10, saveButton, cancelButton);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));
        buttonBox.setStyle("-fx-alignment: center-right;");

        saveButton.setOnAction(e -> {
            String name = nameField.getText().trim();
            String format = formatBox.getValue();

            if (name.isEmpty() || format == null ||
                    (!remoteCheck.isSelected() && pathField.getText().isBlank()) ||
                    (remoteCheck.isSelected() && (hostField.getText().isBlank() || userField.getText().isBlank()))) {
                new Alert(Alert.AlertType.WARNING, "Please fill all required fields.", ButtonType.OK).showAndWait();
                return;
            }

            String path = remoteCheck.isSelected()
                    ? remotePathField.getText().trim()
                    : pathField.getText().trim();

            Profile newProfile = new Profile(name, path, format);

            if (remoteCheck.isSelected()) {
                newProfile.setRemote(true);
                newProfile.setHost(hostField.getText().trim());
                newProfile.setPort(Integer.parseInt(portField.getText().trim()));
                newProfile.setUsername(userField.getText().trim());
                newProfile.setPassword(passwordField.getText());
            }

            profiles.add(newProfile);
            saveProfiles();
            profileSelector.getSelectionModel().select(newProfile);
            dialog.close();
        });

        cancelButton.setOnAction(e -> dialog.close());

        vbox.getChildren().addAll(
                new Label("Profile Name:"), nameField,
                remoteCheck,
                pathSection,
                remoteBox,
                new Label("Log Format:"), formatBox,
                buttonBox
        );

        Scene scene = new Scene(vbox, 400, 600);
        dialog.setScene(scene);
        dialog.setResizable(false);
        dialog.showAndWait();
    }

    private static String getProfileFilePath() {
        String os = System.getProperty("os.name").toLowerCase();
        String baseDir;

        if (os.contains("win")) {
            baseDir = System.getenv("APPDATA");
        } else if (os.contains("mac")) {
            baseDir = System.getProperty("user.home") + "/Library/Application Support";
        } else {
            baseDir = System.getProperty("user.home") + "/.config";
        }

        return baseDir + "/LogParser/profiles.json";
    }

    private void openEditProfileDialog() {
        Profile selected = getSelectedProfile();
        if (selected == null) {
            new Alert(Alert.AlertType.WARNING, "Please select a profile to edit.", ButtonType.OK).showAndWait();
            return;
        }

        Stage dialog = new Stage();
        dialog.setTitle("Edit Profile");
        dialog.initModality(Modality.APPLICATION_MODAL);

        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(20));
        vbox.setStyle("-fx-background-color: #f9f9f9; -fx-background-radius: 10;");

        TextField nameField = new TextField(selected.getName());
        CheckBox remoteCheck = new CheckBox("Remote SFTP");
        remoteCheck.setSelected(selected.isRemote());

        TextField pathField = new TextField(selected.getPath());
        pathField.setEditable(!selected.isRemote());

        Button browseButton = new Button("Browse");
        browseButton.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            File selectedDir = chooser.showDialog(dialog);
            if (selectedDir != null) pathField.setText(selectedDir.getAbsolutePath());
        });
        HBox pathBox = new HBox(5, pathField, browseButton);
        VBox pathSection = new VBox(5, new Label("Log Directory:"), pathBox);

        // Remote fields
        TextField hostField = new TextField(selected.getHost());
        TextField portField = new TextField(String.valueOf(selected.getPort()));
        TextField userField = new TextField(selected.getUsername());
        PasswordField passwordField = new PasswordField();
        passwordField.setText(selected.getPassword());
        TextField remotePathField = new TextField(selected.getPath());

        VBox remoteBox = new VBox(5,
                new Label("Remote Path:"), remotePathField,
                new Label("Host:"), hostField,
                new Label("Port:"), portField,
                new Label("Username:"), userField,
                new Label("Password:"), passwordField
        );

        pathSection.setManaged(!selected.isRemote());
        pathSection.setVisible(!selected.isRemote());
        remoteBox.setManaged(selected.isRemote());
        remoteBox.setVisible(selected.isRemote());

        remoteCheck.selectedProperty().addListener((obs, old, isRemote) -> {
            pathSection.setVisible(!isRemote);
            pathSection.setManaged(!isRemote);
            remoteBox.setVisible(isRemote);
            remoteBox.setManaged(isRemote);
        });

        ComboBox<String> formatBox = new ComboBox<>();
        formatBox.getItems().addAll("OX", "Symfony");
        formatBox.setValue(selected.getFormat());

        Button saveButton = new Button("Save");
        saveButton.setOnAction(e -> {
            selected.setName(nameField.getText());
            selected.setFormat(formatBox.getValue());
            selected.setRemote(remoteCheck.isSelected());

            if (remoteCheck.isSelected()) {
                selected.setPath(remotePathField.getText());
                selected.setHost(hostField.getText());
                selected.setPort(Integer.parseInt(portField.getText()));
                selected.setUsername(userField.getText());
                selected.setPassword(passwordField.getText());
            } else {
                selected.setPath(pathField.getText());
            }

            saveProfiles();
            profileSelector.getItems().set(profileSelector.getSelectionModel().getSelectedIndex(), selected);
            profileSelector.getSelectionModel().select(selected);
            dialog.close();
        });

        Button cancelButton = new Button("Cancel");
        cancelButton.setOnAction(e -> dialog.close());

        vbox.getChildren().addAll(
                new Label("Profile Name:"), nameField,
                remoteCheck,
                pathSection,
                remoteBox,
                new Label("Log Format:"), formatBox,
                new HBox(10, saveButton, cancelButton)
        );

        Scene scene = new Scene(vbox, 400, 600);
        dialog.setScene(scene);
        dialog.setResizable(false);
        dialog.showAndWait();
    }
}
