package com.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.scene.control.Label;
import javafx.stage.Stage;

public class MainApp extends Application {

    // The text editor
    private TextArea textArea = new TextArea();

    // The client that connects to the server
    private EditorClient client;

    // This flag stops an infinite loop
    // (when server sends us text, we update the UI
    // but we don't want that to trigger ANOTHER send)
    private boolean isRemoteUpdate = false;

    @Override
    public void start(Stage stage) {

        // Status label so you can see connection state
        Label statusLabel = new Label("Connecting to server...");

        // Layout — status bar on top, editor below
        VBox root = new VBox(5, statusLabel, textArea);
        textArea.setPrefHeight(600);

        Scene scene = new Scene(root, 600, 400);
        stage.setTitle("Collab Editor");
        stage.setScene(scene);
        stage.show();

        // Connect to the server
        connectToServer(statusLabel);

        // Listen for typing — every keystroke gets sent to server
        textArea.textProperty().addListener((observable, oldText, newText) -> {
            if (!isRemoteUpdate) {
                // Only send if YOU typed it, not if server sent it
                client.sendChange(newText);
            }
        });
    }

    private void connectToServer(Label statusLabel) {
        try {
            client = new EditorClient("ws://localhost:8887", message -> {
                // This runs when server sends us someone else's changes
                // Platform.runLater is needed because we must
                // update the UI from the JavaFX thread only
                Platform.runLater(() -> {
                    isRemoteUpdate = true;   // stop infinite loop
                    textArea.setText(message);
                    isRemoteUpdate = false;  // re-enable sending
                });
            });

            client.connect();
            statusLabel.setText("Connected to server!");

        } catch (Exception e) {
            statusLabel.setText("Could not connect to server. Start the server first!");
            System.out.println("Connection failed: " + e.getMessage());
        }
    }

    // When app closes, disconnect cleanly
    @Override
    public void stop() throws Exception {
        if (client != null && client.isOpen()) {
            client.close();
        }
    }

    public static void main(String[] args) {
        launch();
    }
}