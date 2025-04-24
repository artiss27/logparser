package com.example;

import com.example.manager.MainLayoutManager;
import javafx.application.Application;
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
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        System.out.println("🛑 MainApp.stop() called");
        if (layoutManager != null) {
            layoutManager.shutdown();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}