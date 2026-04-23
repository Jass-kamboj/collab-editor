package com.example;
import javafx.scene.control.Button;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) {

        // ── Login ────────────────────────────────────────────────
        DatabaseManager db = new DatabaseManager();
        LoginScreen loginScreen = new LoginScreen(db);
        String username = loginScreen.show();

        if (username == null) {
            Platform.exit();
            return;
        }

        // ── Home screen — pick or create doc ─────────────────────
        openHomeScreen(stage, username);
    }

    private void openHomeScreen(Stage stage, String username) {
        HomeScreen home = new HomeScreen(username);
        int docId = home.show();

        if (docId < 0) {
            Platform.exit(); // closed without picking
            return;
        }

        openEditor(stage, username, docId);
    }

    private void openEditor(Stage stage, String username, int docId) {

        EditorPane        editorPane = new EditorPane();
        EditorBridge      bridge     = new EditorBridge(editorPane, username, docId);
        ToolbarController toolbar    = new ToolbarController(editorPane, bridge, stage);

        // ── Confirm before leaving doc ───────────────────────────
        Button homeBtn = new javafx.scene.control.Button("⌂ Home");
        homeBtn.setStyle(
            "-fx-background-color: transparent; -fx-font-size: 13px;" +
            "-fx-cursor: hand; -fx-padding: 4 10 4 10;"
        );
        homeBtn.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Leave this document and go back to Home?",
                ButtonType.YES, ButtonType.CANCEL);
            confirm.setHeaderText(null);
            confirm.showAndWait().ifPresent(btn -> {
                if (btn == ButtonType.YES) {
                    stage.getScene().setRoot(new VBox()); // clear scene
                    openHomeScreen(stage, username);
                }
            });
        });

        // Add home button to toolbar
        // Add home button to toolbar
        javafx.scene.layout.Region homeDivider = new javafx.scene.layout.Region();
        homeDivider.setMinWidth(1); homeDivider.setMaxWidth(1);
        homeDivider.setPrefHeight(20);
        homeDivider.setStyle("-fx-background-color: #d3d1c7;");
        javafx.geometry.Insets divMargin = new javafx.geometry.Insets(0, 3, 0, 3);
        javafx.scene.layout.HBox.setMargin(homeDivider, divMargin);

        toolbar.getToolbar().getChildren().add(0, homeBtn);
        toolbar.getToolbar().getChildren().add(1, homeDivider);

       VBox root = new VBox(toolbar.getToolbar(), editorPane.getRoot());
       VBox.setVgrow(editorPane.getRoot(), Priority.ALWAYS);

        root.setPadding(new Insets(0));

        if (stage.getScene() == null) {
            javafx.scene.Scene scene = new javafx.scene.Scene(root, 1100, 750);
            stage.setTitle("Collab Editor");
            stage.setScene(scene);
            stage.show();
        } else {
            stage.getScene().setRoot(root);
        }

          // ── Global keyboard shortcuts ────────────────────────────
        stage.getScene().setOnKeyPressed(event -> {
            if (event.isControlDown()) {
                switch (event.getCode()) {
                    case Z -> { editorPane.execCommand("undo"); bridge.pushChange(); }
                    case Y -> { editorPane.execCommand("redo"); bridge.pushChange(); }
                    default -> {}
                }
            }
        });

        editorPane.init(bridge);
    }

    public static void main(String[] args) {
        launch(args);
    }
}