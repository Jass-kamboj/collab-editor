package com.example;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.layout.Priority;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) {

        // ── Create core components ───────────────────────────────
        EditorPane         editorPane = new EditorPane();
        EditorBridge       bridge     = new EditorBridge(editorPane);
        ToolbarController  toolbar    = new ToolbarController(editorPane, bridge, stage);

        // ── Layout ───────────────────────────────────────────────
        VBox root = new VBox(toolbar.getToolbar(), editorPane.getView());
        VBox.setVgrow(editorPane.getView(), Priority.ALWAYS);
        root.setPadding(new Insets(0));

        // ── Scene & Stage ────────────────────────────────────────
        Scene scene = new Scene(root, 1100, 750);
        stage.setTitle("Collab Editor");
        stage.setScene(scene);
        stage.show();

        // ── Init bridge after scene is shown ────────────────────
        editorPane.init(bridge);
    }

    public static void main(String[] args) {
        launch(args);
    }
}