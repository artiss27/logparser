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

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.ArrayList;

public class ProfileManager {

    private final VBox profilePane;
    private final ComboBox<Profile> profileSelector;
    private final ObservableList<Profile> profiles = FXCollections.observableArrayList();
    private static final String PROFILE_FILE = "profiles.json";
    private static final ObjectMapper mapper = new ObjectMapper();
    private Consumer<Profile> profileSelectedCallback;

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

        profileSelector.setOnAction(e -> {
            if (profileSelectedCallback != null) {
                Profile selected = getSelectedProfile();
                if (selected != null) {
                    profileSelectedCallback.accept(selected);
                }
            }
        });

        Button addButton = new Button("+");
        addButton.setOnAction(e -> openAddProfileDialog());

        Button deleteButton = new Button("-");
        deleteButton.setOnAction(e -> deleteSelectedProfile());

        HBox controls = new HBox(5, profileSelector, addButton, deleteButton);
        HBox.setHgrow(profileSelector, Priority.ALWAYS);

        profilePane.getChildren().addAll(new Label("Profiles:"), controls);

        // Загрузка при старте
        profiles.addAll(loadProfiles());
        if (!profiles.isEmpty()) {
            profileSelector.getSelectionModel();
        }
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

    private void addProfile() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Log Directory");
        File selectedDir = chooser.showDialog(null);
        if (selectedDir != null) {
            TextInputDialog formatDialog = new TextInputDialog("OX");
            formatDialog.setTitle("Select Format");
            formatDialog.setHeaderText("Enter log format for this directory (e.g., OX or Symfony):");
            Optional<String> result = formatDialog.showAndWait();
            result.ifPresent(format -> {
                Profile newProfile = new Profile(selectedDir.getName(), selectedDir.getAbsolutePath(), format);
                profiles.add(newProfile);
                saveProfiles();
                profileSelector.getSelectionModel().select(newProfile);
            });
        }
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
        File file = new File(PROFILE_FILE);
        if (file.exists()) {
            try {
                return mapper.readValue(file, new TypeReference<List<Profile>>() {});
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return new ArrayList<>();
    }

    private void saveProfiles() {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(PROFILE_FILE), profiles);
        } catch (IOException e) {
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

        // Name
        TextField nameField = new TextField();
        nameField.setPromptText("Profile Name");
        nameField.setPrefWidth(300);

        // Path
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

        // Format
        ComboBox<String> formatBox = new ComboBox<>();
        formatBox.getItems().addAll("OX", "Symfony");
        formatBox.setPromptText("Select Format");
        formatBox.setPrefWidth(300);

        // Buttons
        Button saveButton = new Button("Save");
        Button cancelButton = new Button("Cancel");

        HBox buttonBox = new HBox(10, saveButton, cancelButton);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));
        buttonBox.setStyle("-fx-alignment: center-right;");

        saveButton.setOnAction(e -> {
            String name = nameField.getText().trim();
            String path = pathField.getText().trim();
            String format = formatBox.getValue();

            if (name.isEmpty() || path.isEmpty() || format == null) {
                Alert alert = new Alert(Alert.AlertType.WARNING, "Please fill all fields.", ButtonType.OK);
                alert.showAndWait();
                return;
            }

            Profile newProfile = new Profile(name, path, format);
            profiles.add(newProfile);
            saveProfiles();
            profileSelector.getSelectionModel().select(newProfile);

            dialog.close();
        });

        cancelButton.setOnAction(e -> dialog.close());

        vbox.getChildren().addAll(
                new Label("Profile Name:"), nameField,
                new Label("Log Directory:"), pathBox,
                new Label("Log Format:"), formatBox,
                buttonBox
        );

        Scene scene = new Scene(vbox);
        dialog.setScene(scene);
        dialog.setResizable(false);
        dialog.showAndWait();
    }
}