package com.example;

import com.example.manager.MainLayoutManager;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        MainLayoutManager layoutManager = new MainLayoutManager();

        Scene scene = new Scene(layoutManager.getMainLayout(), 1200, 700);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());

        primaryStage.setTitle("Log Parser");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}