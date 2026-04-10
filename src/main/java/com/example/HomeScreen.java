package com.example;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import java.util.List;

public class HomeScreen {

    private final String username;
    private int selectedDocId = -1;

    // Brand purple
    private static final String PURPLE      = "#534AB7";
    private static final String PURPLE_DARK = "#3C3489";
    private static final String PURPLE_BG   = "#EEEDFE";

    public HomeScreen(String username) {
        this.username = username;
    }

    public int show() {
        Stage stage = new Stage();
        stage.setTitle("Collab Editor — Home");
        stage.setMinWidth(720);
        stage.setMinHeight(480);

        // ── Header ───────────────────────────────────────────────
        // Logo mark
        StackPane iconBox = new StackPane();
        iconBox.setPrefSize(28, 28);
        iconBox.setMinSize(28, 28);
        Rectangle iconBg = new Rectangle(28, 28);
        iconBg.setArcWidth(7);
        iconBg.setArcHeight(7);
        iconBg.setFill(Color.web(PURPLE));
        SVGPath iconSvg = new SVGPath();
        iconSvg.setContent(
            "M2 2 h6 a1.5 1.5 0 0 1 1.5 1.5 v3 a1.5 1.5 0 0 1-1.5 1.5 H2 " +
            "a1.5 1.5 0 0 1-1.5-1.5 v-3 A1.5 1.5 0 0 1 2 2 Z " +
            "M10 2 h6 a1.5 1.5 0 0 1 1.5 1.5 v3 a1.5 1.5 0 0 1-1.5 1.5 h-6 " +
            "a1.5 1.5 0 0 1-1.5-1.5 v-3 A1.5 1.5 0 0 1 10 2 Z " +
            "M2 10 h6 a1.5 1.5 0 0 1 1.5 1.5 v3 a1.5 1.5 0 0 1-1.5 1.5 H2 " +
            "a1.5 1.5 0 0 1-1.5-1.5 v-3 A1.5 1.5 0 0 1 2 10 Z"
        );
        iconSvg.setFill(Color.WHITE);
        iconSvg.setScaleX(0.8);
        iconSvg.setScaleY(0.8);
        iconBox.getChildren().addAll(iconBg, iconSvg);

        Label appName = new Label("Collab Editor");
        appName.setFont(Font.font("System", FontWeight.MEDIUM, 15));
        appName.setTextFill(Color.web("#1a1a1a"));

        // Avatar initials
        String initials = username.length() >= 2
            ? (username.substring(0, 1) + username.substring(1, 2)).toUpperCase()
            : username.substring(0, 1).toUpperCase();
        Label avatarLabel = new Label(initials);
        avatarLabel.setFont(Font.font("System", FontWeight.MEDIUM, 12));
        avatarLabel.setTextFill(Color.web(PURPLE));
        StackPane avatar = new StackPane(avatarLabel);
        avatar.setPrefSize(30, 30);
        avatar.setMinSize(30, 30);
        Rectangle avatarBg = new Rectangle(30, 30);
        avatarBg.setArcWidth(30);
        avatarBg.setArcHeight(30);
        avatarBg.setFill(Color.web(PURPLE_BG));
        avatar.getChildren().add(0, avatarBg);

        Label usernameLabel = new Label(username);
        usernameLabel.setFont(Font.font("System", 13));
        usernameLabel.setTextFill(Color.web("#888780"));

        Button newDocBtn = new Button("+ New document");
        newDocBtn.setPrefHeight(32);
        newDocBtn.setFont(Font.font("System", FontWeight.MEDIUM, 13));
        styleFilledBtn(newDocBtn);
        newDocBtn.setOnMouseEntered(e ->
            newDocBtn.setStyle(filledBtnStyle(PURPLE_DARK)));
        newDocBtn.setOnMouseExited(e ->
            newDocBtn.setStyle(filledBtnStyle(PURPLE)));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(10, iconBox, appName, spacer,
                               usernameLabel, avatar, newDocBtn);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(12, 20, 12, 20));
        header.setStyle(
            "-fx-background-color: white;" +
            "-fx-border-color: #d3d1c7;" +
            "-fx-border-width: 0 0 0.5 0;"
        );

        // ── Section label ────────────────────────────────────────
        Label sectionLabel = new Label("Recent documents");
        sectionLabel.setFont(Font.font("System", FontWeight.MEDIUM, 13));
        sectionLabel.setTextFill(Color.web("#888780"));
        HBox sectionBar = new HBox(sectionLabel);
        sectionBar.setPadding(new Insets(20, 20, 8, 20));
        sectionBar.setStyle("-fx-background-color: white;");

        // ── Doc list ─────────────────────────────────────────────
        VBox docList = new VBox(6);
        docList.setPadding(new Insets(0, 20, 20, 20));
        docList.setStyle("-fx-background-color: white;");

        ScrollPane scroll = new ScrollPane(docList);
        scroll.setFitToWidth(true);
        scroll.setStyle(
            "-fx-background: white;" +
            "-fx-background-color: white;" +
            "-fx-border-color: transparent;"
        );

        refreshDocList(docList, stage);

        // ── New doc action ────────────────────────────────────────
        newDocBtn.setOnAction(e -> {
            TextInputDialog dialog = new TextInputDialog("Untitled Document");
            dialog.setTitle("New Document");
            dialog.setHeaderText(null);
            dialog.setContentText("Document name:");
            dialog.showAndWait().ifPresent(name -> {
                int docId = DatabaseManager.createDocument(
                    name.isEmpty() ? "Untitled Document" : name, username);
                if (docId > 0) {
                    selectedDocId = docId;
                    stage.close();
                }
            });
        });

        // ── Root ─────────────────────────────────────────────────
        VBox content = new VBox(sectionBar, scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        content.setStyle("-fx-background-color: white;");

        VBox root = new VBox(header, content);
        VBox.setVgrow(content, Priority.ALWAYS);
        root.setStyle("-fx-background-color: white;");

        Scene scene = new Scene(root, 800, 550);
        stage.setScene(scene);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.showAndWait();

        return selectedDocId;
    }

    private void refreshDocList(VBox docList, Stage stage) {
        docList.getChildren().clear();

        List<String[]> docs = DatabaseManager.listDocumentsFull();

        if (docs.isEmpty()) {
            Label empty = new Label("No documents yet — create one to get started.");
            empty.setFont(Font.font("System", 13));
            empty.setTextFill(Color.web("#b4b2a9"));
            VBox emptyBox = new VBox(empty);
            emptyBox.setPadding(new Insets(40, 0, 0, 0));
            emptyBox.setAlignment(Pos.TOP_CENTER);
            docList.getChildren().add(emptyBox);
            return;
        }

        for (String[] doc : docs) {
            int    id        = Integer.parseInt(doc[0]);
            String docTitle  = doc[1];
            String createdBy = doc[2] != null ? doc[2] : "unknown";
            String createdAt = doc[3] != null ? doc[3].substring(0, 10) : "";

            docList.getChildren().add(buildRow(id, docTitle, createdBy, createdAt, docList, stage));
        }
    }

    private HBox buildRow(int id, String docTitle, String createdBy,
                          String createdAt, VBox docList, Stage stage) {

        // Doc icon thumbnail
        StackPane thumb = new StackPane();
        thumb.setPrefSize(34, 38);
        thumb.setMinSize(34, 38);
        Rectangle thumbBg = new Rectangle(34, 38);
        thumbBg.setArcWidth(6);
        thumbBg.setArcHeight(6);
        thumbBg.setFill(Color.web(PURPLE_BG));
        SVGPath docIcon = new SVGPath();
        docIcon.setContent(
            "M1 2 a1 1 0 0 1 1-1 h8 l4 4 v11 a1 1 0 0 1-1 1 H2 a1 1 0 0 1-1-1 Z " +
            "M9 1 v4 h4 " +
            "M3.5 8 h7 M3.5 11 h7 M3.5 14 h4.5"
        );
        docIcon.setFill(Color.TRANSPARENT);
        docIcon.setStroke(Color.web("#7F77DD"));
        docIcon.setStrokeWidth(1.1);
        docIcon.setScaleX(0.85);
        docIcon.setScaleY(0.85);
        thumb.getChildren().addAll(thumbBg, docIcon);

        // Title + meta
        Label nameLabel = new Label(docTitle);
        nameLabel.setFont(Font.font("System", FontWeight.MEDIUM, 14));
        nameLabel.setTextFill(Color.web("#1a1a1a"));
        nameLabel.setMaxWidth(Double.MAX_VALUE);

        Label metaLabel = new Label("Created by " + createdBy + "  ·  " + createdAt);
        metaLabel.setFont(Font.font("System", 11));
        metaLabel.setTextFill(Color.web("#b4b2a9"));

        VBox textGroup = new VBox(2, nameLabel, metaLabel);
        textGroup.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(textGroup, Priority.ALWAYS);

        // Action buttons
        Button openBtn   = new Button("Open");
        Button renameBtn = new Button("Rename");
        Button deleteBtn = new Button("Delete");

        styleFilledBtn(openBtn);
        openBtn.setPrefHeight(28);
        openBtn.setFont(Font.font("System", 12));

        styleGhostBtn(renameBtn, "#1a1a1a");
        renameBtn.setPrefHeight(28);
        renameBtn.setFont(Font.font("System", 12));

        styleGhostBtn(deleteBtn, "#A32D2D");
        deleteBtn.setPrefHeight(28);
        deleteBtn.setFont(Font.font("System", 12));

        HBox actions = new HBox(4, openBtn, renameBtn, deleteBtn);
        actions.setAlignment(Pos.CENTER_RIGHT);

        HBox row = new HBox(12, thumb, textGroup, actions);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10, 14, 10, 14));
        row.setStyle(rowStyle(false));
        row.setOnMouseEntered(e -> row.setStyle(rowStyle(true)));
        row.setOnMouseExited(e  -> row.setStyle(rowStyle(false)));

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

        return row;
    }

    // ── Style helpers ─────────────────────────────────────────────

    private String rowStyle(boolean hovered) {
        String bg = hovered ? "#f5f5f3" : "white";
        return "-fx-background-color: " + bg + ";" +
               "-fx-border-color: #d3d1c7;" +
               "-fx-border-width: 0.5;" +
               "-fx-border-radius: 10;" +
               "-fx-background-radius: 10;";
    }

    private String filledBtnStyle(String bg) {
        return "-fx-background-color: " + bg + ";" +
               "-fx-text-fill: white;" +
               "-fx-background-radius: 6;" +
               "-fx-padding: 0 14 0 14;" +
               "-fx-cursor: hand;";
    }

    private void styleFilledBtn(Button btn) {
        btn.setStyle(filledBtnStyle(PURPLE));
        btn.setOnMouseEntered(e -> btn.setStyle(filledBtnStyle(PURPLE_DARK)));
        btn.setOnMouseExited(e  -> btn.setStyle(filledBtnStyle(PURPLE)));
    }

    private void styleGhostBtn(Button btn, String textColor) {
        String base =
            "-fx-background-color: transparent;" +
            "-fx-border-color: #d3d1c7;" +
            "-fx-border-width: 0.5;" +
            "-fx-border-radius: 6;" +
            "-fx-background-radius: 6;" +
            "-fx-padding: 0 10 0 10;" +
            "-fx-text-fill: " + textColor + ";" +
            "-fx-cursor: hand;";
        String hover =
            "-fx-background-color: #f5f5f3;" +
            "-fx-border-color: #d3d1c7;" +
            "-fx-border-width: 0.5;" +
            "-fx-border-radius: 6;" +
            "-fx-background-radius: 6;" +
            "-fx-padding: 0 10 0 10;" +
            "-fx-text-fill: " + textColor + ";" +
            "-fx-cursor: hand;";
        btn.setStyle(base);
        btn.setOnMouseEntered(e -> btn.setStyle(hover));
        btn.setOnMouseExited(e  -> btn.setStyle(base));
    }
}