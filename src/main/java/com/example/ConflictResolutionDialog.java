package com.example;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class ConflictResolutionDialog {

    private final int pageIndex;
    private final String versionA;
    private final String versionB;
    private final String userA;
    private final String userB;
    private final EditorBridge bridge;


    private static final String BORDER       = "#d3d1c7";
    private static final String MUTED        = "#888780";
    private static final String TEXT         = "#2c2b27";
    private static final String WARN_BG      = "#FFF8E1";
    private static final String WARN_BORDER  = "#FFD54F";
    private static final String GREEN        = "#2E7D32";
    private static final String GREEN_TINT   = "#E8F5E9";

    public ConflictResolutionDialog(int pageIndex, String versionA, String versionB,
                                     String userA, String userB, EditorBridge bridge) {
        this.pageIndex = pageIndex;
        this.versionA  = versionA;
        this.versionB  = versionB;
        this.userA     = userA;
        this.userB     = userB;
        this.bridge    = bridge;
    }

    public void show() {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initStyle(StageStyle.DECORATED);
        stage.setTitle("⚠️ Edit Conflict Detected — Page " + (pageIndex + 1));
        stage.setMinWidth(860);
        stage.setMinHeight(620);

        // ── Warning banner ────────────────────────────────────────
        Label warningIcon = new Label("⚠️");
        warningIcon.setFont(Font.font("System", 22));

        Label warningText = new Label(
            "Two editors changed this page at the same time. " +
            "Choose a version to keep, or merge them manually.");
        warningText.setFont(Font.font("System", 13));
        warningText.setTextFill(Color.web("#5D4037"));
        warningText.setWrapText(true);

        HBox banner = new HBox(12, warningIcon, warningText);
        banner.setAlignment(Pos.CENTER_LEFT);
        banner.setPadding(new Insets(14, 16, 14, 16));
        banner.setStyle(
            "-fx-background-color: " + WARN_BG + ";" +
            "-fx-border-color: " + WARN_BORDER + ";" +
            "-fx-border-width: 0 0 1 0;"
        );

        // ── Version A panel ───────────────────────────────────────
        VBox panelA = buildVersionPanel(
            "Version A  —  " + userA + "'s edit",
            versionA, "#1565C0", "#E3F2FD"
        );

        // ── Version B panel ───────────────────────────────────────
        VBox panelB = buildVersionPanel(
            "Version B  —  " + userB + "'s edit",
            versionB, "#6A1B9A", "#F3E5F5"
        );

        // ── VS divider ────────────────────────────────────────────
        Label vs = new Label("VS");
        vs.setFont(Font.font("System", FontWeight.BOLD, 18));
        vs.setTextFill(Color.web(MUTED));
        vs.setPadding(new Insets(0, 8, 0, 8));

        HBox versionsRow = new HBox(vs, panelA, panelB);
        versionsRow.setAlignment(Pos.CENTER);
        HBox.setHgrow(panelA, Priority.ALWAYS);
        HBox.setHgrow(panelB, Priority.ALWAYS);
        versionsRow.setPadding(new Insets(16, 16, 8, 16));
        versionsRow.setSpacing(0);

        // ── Manual merge editor ───────────────────────────────────
        Label mergeLabel = new Label("✏️  Or manually merge — edit below:");
        mergeLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        mergeLabel.setTextFill(Color.web(TEXT));

        WebView mergeEditor = new WebView();
        mergeEditor.setPrefHeight(160);
        mergeEditor.getEngine().loadContent(buildEditorHtml(versionA));

        VBox mergeBox = new VBox(6, mergeLabel, mergeEditor);
        mergeBox.setPadding(new Insets(0, 16, 12, 16));

        // ── Action buttons ────────────────────────────────────────
        Button keepA = makeChoiceBtn(
            "✔ Keep Version A  (" + userA + ")", "#1565C0", "#E3F2FD", "#1565C0");
        Button keepB = makeChoiceBtn(
            "✔ Keep Version B  (" + userB + ")", "#6A1B9A", "#F3E5F5", "#6A1B9A");
        Button keepMerge = makeChoiceBtn(
            "✔ Use My Merged Version", GREEN, GREEN_TINT, GREEN);

        keepA.setOnAction(e -> {
            bridge.sendConflictResolution(pageIndex, versionA);
            stage.close();
        });

        keepB.setOnAction(e -> {
            bridge.sendConflictResolution(pageIndex, versionB);
            stage.close();
        });

        keepMerge.setOnAction(e -> {
            try {
                String merged = (String) mergeEditor.getEngine()
                    .executeScript("document.getElementById('editor').innerHTML;");
                bridge.sendConflictResolution(pageIndex, merged);
            } catch (Exception ex) {
                bridge.sendConflictResolution(pageIndex, versionA);
            }
            stage.close();
        });

        Button keepLatest = makeChoiceBtn(
            "🕐 Keep Most Recent", "#37474F", "#ECEFF1", "#37474F");
        keepLatest.setOnAction(e -> {
            bridge.sendConflictResolution(pageIndex, versionB);
            stage.close();
        });

        HBox btnRow = new HBox(12, keepA, keepB, keepMerge, keepLatest);
        btnRow.setAlignment(Pos.CENTER);
        btnRow.setPadding(new Insets(12, 16, 16, 16));
        btnRow.setStyle(
            "-fx-background-color: #fafaf8;" +
            "-fx-border-color: " + BORDER + " transparent transparent transparent;" +
            "-fx-border-width: 1 0 0 0;"
        );

        // ── Assemble ──────────────────────────────────────────────
        VBox root = new VBox(banner, versionsRow, mergeBox, btnRow);
        root.setStyle("-fx-background-color: white;");

        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.show();
    }

    // ── Helpers ───────────────────────────────────────────────────

    private VBox buildVersionPanel(String title, String html,
                                    String accentColor, String bgColor) {
        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        titleLabel.setTextFill(Color.web(accentColor));
        titleLabel.setPadding(new Insets(8, 10, 8, 10));
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        titleLabel.setStyle(
            "-fx-background-color: " + bgColor + ";" +
            "-fx-border-color: transparent transparent " + BORDER + " transparent;" +
            "-fx-border-width: 0 0 1 0;"
        );

        WebView preview = new WebView();
        preview.setPrefHeight(260);
        preview.setMouseTransparent(true);
        preview.getEngine().loadContent(buildPreviewHtml(html));

        VBox panel = new VBox(titleLabel, preview);
        panel.setStyle(
            "-fx-background-color: white;" +
            "-fx-border-color: " + accentColor + ";" +
            "-fx-border-radius: 8px;" +
            "-fx-background-radius: 8px;" +
            "-fx-border-width: 1.5;"
        );
        panel.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(preview, Priority.ALWAYS);
        return panel;
    }

    private Button makeChoiceBtn(String label, String textColor,
                                  String bgColor, String borderColor) {
        Button btn = new Button(label);
        String base =
            "-fx-font-size: 12px;" +
            "-fx-font-weight: bold;" +
            "-fx-background-color: " + bgColor + ";" +
            "-fx-text-fill: " + textColor + ";" +
            "-fx-border-color: " + borderColor + ";" +
            "-fx-border-radius: 6px;" +
            "-fx-background-radius: 6px;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 8 18 8 18;";
        btn.setStyle(base);
        btn.setOnMouseEntered(e -> btn.setStyle(base
            .replace("-fx-background-color: " + bgColor,
                     "-fx-background-color: " + borderColor)
            .replace("-fx-text-fill: " + textColor, "-fx-text-fill: white")));
        btn.setOnMouseExited(e -> btn.setStyle(base));
        return btn;
    }

    private String buildPreviewHtml(String body) {
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'>" +
            "<style>" +
            "body { margin:0; padding:16px; font-family:'Georgia',serif;" +
            "font-size:13px; line-height:1.6; color:#1a1a1a; }" +
            "table { border-collapse:collapse; }" +
            "td,th { border:1px solid #d3d1c7; padding:4px 8px; }" +
            "</style></head><body>" + body + "</body></html>";
    }

    private String buildEditorHtml(String body) {
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'>" +
            "<style>" +
            "body { margin:0; padding:0; }" +
            "#editor { padding:16px; font-family:'Georgia',serif;" +
            "font-size:13px; line-height:1.6; color:#1a1a1a;" +
            "min-height:120px; outline:none; }" +
            "table { border-collapse:collapse; }" +
            "td,th { border:1px solid #d3d1c7; padding:4px 8px; }" +
            "</style></head><body>" +
            "<div id='editor' contenteditable='true'>" + body + "</div>" +
            "</body></html>";
    }
}