package com.example;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import java.util.List;

public class HomeScreen {

    private final String username;
    private int selectedDocId = -1;

    public HomeScreen(String username) {
        this.username = username;
    }

    public int show() {
        Stage stage = new Stage();
        stage.setTitle("Collab Editor — Home");

        // ── Header ───────────────────────────────────────────────
        Label title = new Label("Collab Editor");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 24));

        Label welcome = new Label("Welcome, " + username);
        welcome.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 13px;");

        Button newDocBtn = new Button("+ New Document");
        newDocBtn.setStyle(
            "-fx-background-color: #1a73e8; -fx-text-fill: white;" +
            "-fx-font-size: 13px; -fx-padding: 8 16 8 16;" +
            "-fx-background-radius: 4; -fx-cursor: hand;"
        );

        HBox header = new HBox(10, title, new Region(), welcome, newDocBtn);
        HBox.setHgrow(header.getChildren().get(1), Priority.ALWAYS);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(16, 24, 16, 24));
        header.setStyle("-fx-border-color: #e0e0e0; -fx-border-width: 0 0 1 0;");

        // ── Doc list ─────────────────────────────────────────────
        VBox docList = new VBox(8);
        docList.setPadding(new Insets(24));

        ScrollPane scroll = new ScrollPane(docList);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: white; -fx-background-color: white;");

        // Load docs
        refreshDocList(docList, stage);

        // ── New doc button action ─────────────────────────────────
        newDocBtn.setOnAction(e -> {
            TextInputDialog dialog = new TextInputDialog("Untitled Document");
            dialog.setTitle("New Document");
            dialog.setHeaderText(null);
            dialog.setContentText("Document name:");
            dialog.showAndWait().ifPresent(name -> {
                int docId = DatabaseManager.createDocument(name.isEmpty()
                    ? "Untitled Document" : name, username);
                if (docId > 0) {
                    selectedDocId = docId;
                    stage.close();
                }
            });
        });

        // ── Layout ───────────────────────────────────────────────
        VBox root = new VBox(header, scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Scene scene = new Scene(root, 800, 550);
        stage.setScene(scene);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.showAndWait();

        return selectedDocId;
    }

    private void refreshDocList(VBox docList, Stage stage) {
        docList.getChildren().clear();

        Label sectionLabel = new Label("Recent Documents");
        sectionLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        sectionLabel.setPadding(new Insets(0, 0, 8, 0));
        docList.getChildren().add(sectionLabel);

        List<String[]> docs = DatabaseManager.listDocumentsFull();

        if (docs.isEmpty()) {
            Label empty = new Label("No documents yet. Create one!");
            empty.setStyle("-fx-text-fill: #9e9e9e; -fx-font-size: 13px;");
            docList.getChildren().add(empty);
            return;
        }

        for (String[] doc : docs) {
            int    id        = Integer.parseInt(doc[0]);
            String docTitle  = doc[1];
            String createdBy = doc[2] != null ? doc[2] : "unknown";
            String createdAt = doc[3] != null ? doc[3].substring(0, 10) : "";

            // ── Doc row ──────────────────────────────────────────
            Label nameLabel = new Label(docTitle);
            nameLabel.setFont(Font.font("Arial", 14));
            nameLabel.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(nameLabel, Priority.ALWAYS);

            Label metaLabel = new Label("Created by " + createdBy + "  ·  " + createdAt);
            metaLabel.setStyle("-fx-text-fill: #9e9e9e; -fx-font-size: 11px;");

            Button openBtn = new Button("Open");
            openBtn.setStyle(
                "-fx-background-color: transparent; -fx-text-fill: #1a73e8;" +
                "-fx-font-size: 12px; -fx-cursor: hand;"
            );

            Button renameBtn = new Button("Rename");
            renameBtn.setStyle(
                "-fx-background-color: transparent; -fx-text-fill: #555;" +
                "-fx-font-size: 12px; -fx-cursor: hand;"
            );

            Button deleteBtn = new Button("Delete");
            deleteBtn.setStyle(
                "-fx-background-color: transparent; -fx-text-fill: #e53935;" +
                "-fx-font-size: 12px; -fx-cursor: hand;"
            );

            HBox row = new HBox(12, nameLabel, metaLabel, openBtn, renameBtn, deleteBtn);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(10, 16, 10, 16));
            row.setStyle(
                "-fx-background-color: white;" +
                "-fx-border-color: #e0e0e0;" +
                "-fx-border-radius: 6;" +
                "-fx-background-radius: 6;"
            );

            // Hover effect
            row.setOnMouseEntered(e -> row.setStyle(
                "-fx-background-color: #f5f5f5;" +
                "-fx-border-color: #e0e0e0;" +
                "-fx-border-radius: 6;" +
                "-fx-background-radius: 6;"
            ));
            row.setOnMouseExited(e -> row.setStyle(
                "-fx-background-color: white;" +
                "-fx-border-color: #e0e0e0;" +
                "-fx-border-radius: 6;" +
                "-fx-background-radius: 6;"
            ));

            // Open
            openBtn.setOnAction(e -> {
                selectedDocId = id;
                stage.close();
            });

            // Rename
            renameBtn.setOnAction(e -> {
                TextInputDialog dialog = new TextInputDialog(docTitle);
                dialog.setTitle("Rename Document");
                dialog.setHeaderText(null);
                dialog.setContentText("New name:");
                dialog.showAndWait().ifPresent(newName -> {
                    if (!newName.isEmpty()) {
                        DatabaseManager.renameDocument(id, newName);
                        refreshDocList(docList, stage);
                    }
                });
            });

            // Delete
            deleteBtn.setOnAction(e -> {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Delete \"" + docTitle + "\"? This cannot be undone.",
                    ButtonType.YES, ButtonType.CANCEL);
                confirm.setHeaderText(null);
                confirm.showAndWait().ifPresent(btn -> {
                    if (btn == ButtonType.YES) {
                        DatabaseManager.deleteDocument(id);
                        refreshDocList(docList, stage);
                    }
                });
            });

            docList.getChildren().add(row);
        }
    }
}
