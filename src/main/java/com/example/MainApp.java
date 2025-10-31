package com.example;

import com.example.manager.MainLayoutManager;
import com.example.service.ExecutorServiceManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {

    private MainLayoutManager layoutManager;

    @Override
    public void start(Stage primaryStage) {
        layoutManager = new MainLayoutManager();  // 🔧 Используем поле класса вместо локальной переменной

        Scene scene = new Scene(layoutManager.getMainLayout(), 1400, 900);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());

        primaryStage.setTitle("Log Parser");
        primaryStage.setScene(scene);
        primaryStage.show();

        // 🔍 Отслеживаем активность окна
        primaryStage.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            layoutManager.setWindowFocused(isNowFocused);
        });

        primaryStage.setOnCloseRequest(event -> {
            System.out.println("🛑 Window closed — calling shutdown");
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
        System.out.println("🛑 MainApp.stop() called");
        if (layoutManager != null) {
            layoutManager.shutdown();
        }
        ExecutorServiceManager.getInstance().shutdown();
    }

    public static void main(String[] args) {
        launch(args);
    }
}