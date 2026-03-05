package com.logparser;

import com.logparser.manager.MainLayoutManager;
import com.logparser.service.ExecutorServiceManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {

    private MainLayoutManager layoutManager;

    @Override
    public void start(Stage primaryStage) {
        layoutManager = new MainLayoutManager();

        Scene scene = new Scene(layoutManager.getMainLayout(), 1400, 900);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());

        primaryStage.setTitle("Log Parser");
        primaryStage.setScene(scene);
        primaryStage.show();

        primaryStage.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            layoutManager.setWindowFocused(isNowFocused);
        });

        primaryStage.setOnCloseRequest(event -> {
            if (layoutManager != null) {
                layoutManager.shutdown();
            }
            Platform.exit();
            System.exit(0);
        });
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        if (layoutManager != null) {
            layoutManager.shutdown();
        }
        ExecutorServiceManager.getInstance().shutdown();
    }

    public static void main(String[] args) {
        launch(args);
    }
}