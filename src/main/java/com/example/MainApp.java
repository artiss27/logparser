package com.example;

import com.example.manager.MainLayoutManager;
import com.example.watcher.LogFileWatcher;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {

    private MainLayoutManager layoutManager;

    @Override
    public void start(Stage primaryStage) {
        MainLayoutManager layoutManager = new MainLayoutManager();

        Scene scene = new Scene(layoutManager.getMainLayout(), 1400, 900);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());

        primaryStage.setTitle("Log Parser");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        if (layoutManager != null) {
            layoutManager.shutdown();
        }
    }
}