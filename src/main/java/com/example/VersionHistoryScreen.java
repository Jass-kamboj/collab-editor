package com.example;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import java.util.List;

public class VersionHistoryScreen {

    private final EditorBridge bridge;
    

    public VersionHistoryScreen(EditorBridge bridge){
        this.bridge     = bridge;
        
    }

    public void show() {
        Stage stage = new Stage();
        stage.setTitle("Version History");
        stage.initModality(Modality.APPLICATION_MODAL);

        // ── Version list (left) ───────────────────────────────────
        VBox versionList = new VBox(6);
        versionList.setPadding(new Insets(12));
        versionList.setPrefWidth(220);
        versionList.setStyle("-fx-background-color: #f5f5f5;");

        Label listTitle = new Label("Versions");
        listTitle.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        listTitle.setPadding(new Insets(0, 0, 8, 0));
        versionList.getChildren().add(listTitle);

        // ── Preview (right) ───────────────────────────────────────
        WebView preview = new WebView();
        preview.setPrefWidth(600);

        Label previewLabel = new Label("Select a version to preview");
        previewLabel.setStyle("-fx-text-fill: #9e9e9e;");
        StackPane previewPane = new StackPane(previewLabel, preview);
        preview.setVisible(false);

        // ── Restore button ────────────────────────────────────────
        Button restoreBtn = new Button("Restore this version");
        restoreBtn.setStyle(
            "-fx-background-color: #1a73e8; -fx-text-fill: white;" +
            "-fx-font-size: 13px; -fx-padding: 8 16 8 16;" +
            "-fx-background-radius: 4; -fx-cursor: hand;"
        );
        restoreBtn.setDisable(true);
        restoreBtn.setAlignment(Pos.CENTER);

        // Store selected html
        final String[] selectedHtml = {null};

        // ── Load history ──────────────────────────────────────────
        List<String[]> history = DatabaseManager.getHistory(bridge.getDocId());

        if (history.isEmpty()) {
            versionList.getChildren().add(
                new Label("No history yet.")
            );
        }

        for (String[] entry : history) {
            String version   = entry[0];
            String changedBy = entry[1] != null ? entry[1] : "unknown";
            String savedAt   = entry[2] != null ? entry[2].substring(0, 16) : "";
            String content   = entry[3];

            Label vLabel = new Label("v" + version);
            vLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));

            Label metaLabel = new Label(changedBy + "\n" + savedAt);
            metaLabel.setStyle("-fx-text-fill: #9e9e9e; -fx-font-size: 10px;");

            VBox row = new VBox(2, vLabel, metaLabel);
            row.setPadding(new Insets(8, 10, 8, 10));
            row.setStyle(
                "-fx-background-color: white;" +
                "-fx-border-color: #e0e0e0;" +
                "-fx-border-radius: 4;" +
                "-fx-background-radius: 4;" +
                "-fx-cursor: hand;"
            );

            // Hover
            row.setOnMouseEntered(e -> row.setStyle(
                "-fx-background-color: #e8f0fe;" +
                "-fx-border-color: #1a73e8;" +
                "-fx-border-radius: 4;" +
                "-fx-background-radius: 4;" +
                "-fx-cursor: hand;"
            ));
            row.setOnMouseExited(e -> row.setStyle(
                "-fx-background-color: white;" +
                "-fx-border-color: #e0e0e0;" +
                "-fx-border-radius: 4;" +
                "-fx-background-radius: 4;" +
                "-fx-cursor: hand;"
            ));

            // Click — preview
            row.setOnMouseClicked(e -> {
                selectedHtml[0] = content;
                preview.getEngine().loadContent(content != null ? content : "");
                preview.setVisible(true);
                previewLabel.setVisible(false);
                restoreBtn.setDisable(false);
            });

            versionList.getChildren().add(row);
        }

        // ── Restore action ────────────────────────────────────────
        restoreBtn.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Restore this version? Current content will be overwritten for all teammates.",
                ButtonType.YES, ButtonType.CANCEL);
            confirm.setHeaderText(null);
            confirm.showAndWait().ifPresent(btn -> {
                if (btn == ButtonType.YES && selectedHtml[0] != null) {
                    bridge.restoreVersion(selectedHtml[0]);
                    stage.close();
                }
            });
        });

        // ── Layout ────────────────────────────────────────────────
        ScrollPane scrollList = new ScrollPane(versionList);
        scrollList.setFitToWidth(true);
        scrollList.setPrefWidth(240);
        scrollList.setStyle("-fx-background: #f5f5f5;");

        VBox rightPane = new VBox(10, previewPane, restoreBtn);
        rightPane.setPadding(new Insets(12));
        rightPane.setAlignment(Pos.TOP_CENTER);
        VBox.setVgrow(previewPane, Priority.ALWAYS);

        HBox layout = new HBox(scrollList, rightPane);
        HBox.setHgrow(rightPane, Priority.ALWAYS);

        Scene scene = new Scene(layout, 860, 520);
        stage.setScene(scene);
        stage.show();
    }
}