package com.example;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;

import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Popup;
import javafx.stage.Stage;


import java.util.HashMap;
import java.util.Map;

public class CommentPanel {

    private VBox panel;
    private final EditorBridge bridge;
    private final EditorPane editor;
    private final Map<Integer, VBox> commentNodes = new HashMap<>();
    private VBox commentList;
    private Popup popup;
    private boolean popupOpen = false;

    private static final String PURPLE      = "#534AB7";
    private static final String PURPLE_TINT = "#EEEDFE";
    private static final String BORDER      = "#d3d1c7";
    private static final String MUTED       = "#888780";
    private static final String TEXT        = "#2c2b27";
    private static final String SURFACE     = "#f5f5f3";

    public CommentPanel(EditorBridge bridge, EditorPane editor) {
        this.bridge = bridge;
        this.editor = editor;
        buildPanel();
    }

    private void buildPanel() {
        // ── Header ────────────────────────────────────────────────
        Label header = new Label("💬 Comments");
        header.setFont(Font.font("System", FontWeight.BOLD, 13));
        header.setTextFill(Color.web(TEXT));

        Button closeBtn = new Button("✕");
        closeBtn.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-border-color: transparent;" +
            "-fx-text-fill: " + MUTED + ";" +
            "-fx-cursor: hand;" +
            "-fx-font-size: 13px;" +
            "-fx-padding: 0 4 0 4;"
        );
        closeBtn.setOnAction(e -> popup.hide());

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox headerRow = new HBox(header, headerSpacer, closeBtn);
        headerRow.setAlignment(Pos.CENTER_LEFT);
        headerRow.setPadding(new Insets(12, 10, 8, 12));
        headerRow.setStyle(
            "-fx-border-color: transparent transparent " + BORDER + " transparent;" +
            "-fx-border-width: 0 0 1 0;"
        );

        // ── Selected text label ───────────────────────────────────
        Label selectedLabel = new Label("Select text in the editor first");
        selectedLabel.setFont(Font.font("System", 11));
        selectedLabel.setTextFill(Color.web(MUTED));
        selectedLabel.setWrapText(true);
        selectedLabel.setMaxWidth(280);
        selectedLabel.setStyle(
            "-fx-font-style: italic;" +
            "-fx-padding: 4 8 4 8;" +
            "-fx-background-color: " + SURFACE + ";" +
            "-fx-background-radius: 4;" +
            "-fx-border-color: " + BORDER + ";" +
            "-fx-border-radius: 4;" +
            "-fx-border-width: 0.5;"
        );

        // ── Comment input ─────────────────────────────────────────
        TextArea commentInput = new TextArea();
        commentInput.setPromptText("Add a comment…");
        commentInput.setPrefRowCount(3);
        commentInput.setWrapText(true);
        commentInput.setPrefWidth(280);
        commentInput.setStyle(
            "-fx-font-size: 12px;" +
            "-fx-border-color: " + BORDER + ";" +
            "-fx-border-radius: 5px;" +
            "-fx-background-radius: 5px;" +
            "-fx-background-color: white;" +
            "-fx-padding: 6 8 6 8;"
        );

        Button addBtn = new Button("Add Comment");
        addBtn.setMaxWidth(Double.MAX_VALUE);
        addBtn.setStyle(
            "-fx-background-color: " + PURPLE + ";" +
            "-fx-text-fill: white;" +
            "-fx-font-size: 12px;" +
            "-fx-font-weight: bold;" +
            "-fx-background-radius: 6px;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 7 0 7 0;"
        );
        addBtn.setOnAction(e -> {
            String text = commentInput.getText().trim();
            if (text.isEmpty()) return;
            String selected = "";
            try {
                Object sel = editor.getEngine().executeScript(
                    "window.getSelection().toString()");
                if (sel != null) selected = sel.toString().trim();
            } catch (Exception ignored) {}

            String displaySel = selected.isEmpty() ? "No text selected"
                : (selected.length() > 60 ? selected.substring(0, 60) + "…" : selected);
            selectedLabel.setText(displaySel);

            bridge.sendComment(selected, text, null);
            commentInput.clear();
        });

        VBox inputBox = new VBox(8, selectedLabel, commentInput, addBtn);
        inputBox.setPadding(new Insets(10, 12, 10, 12));
        inputBox.setStyle(
            "-fx-border-color: transparent transparent " + BORDER + " transparent;" +
            "-fx-border-width: 0 0 1 0;"
        );

        // ── Comment list ──────────────────────────────────────────
        commentList = new VBox(8);
        commentList.setPadding(new Insets(10, 12, 10, 12));

        ScrollPane listScroll = new ScrollPane(commentList);
        listScroll.setFitToWidth(true);
        listScroll.setPrefHeight(300);
        listScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        listScroll.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-background: transparent;" +
            "-fx-border-color: transparent;"
        );

        // ── Assemble panel ────────────────────────────────────────
        panel = new VBox(headerRow, inputBox, listScroll);
        panel.setPrefWidth(320);
        panel.setStyle(
            "-fx-background-color: white;" +
            "-fx-border-color: " + BORDER + ";" +
            "-fx-border-radius: 10px;" +
            "-fx-background-radius: 10px;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.18), 16, 0, 0, 4);"
        );

        // ── Popup wrapper ─────────────────────────────────────────
        popup = new Popup();
        popup.setAutoHide(true);
        popup.setAutoFix(true);
        popup.getContent().add(panel);
        popup.setOnHidden(e -> popupOpen = false);

        // Load comments when popup opens
        popup.setOnShown(e -> bridge.loadComments());
    }

    public void togglePopup(Stage stage) {
        if (popupOpen) {
            popup.hide();
        } else {
            // Position near top-right of window
            double x = stage.getX() + stage.getWidth() - 340;
            double y = stage.getY() + 80;
            popup.show(stage, x, y);
            popupOpen = true;
        }
    }

    // ── Keep these for ToolbarController ─────────────────────────
    public VBox getPanel() { return panel; }
    public boolean isVisible() { return popupOpen; }
    public void show() { }
    public void hide() { popup.hide(); }

    // ── Called from EditorBridge ──────────────────────────────────
    public void addComment(JsonObject msg) {
        Platform.runLater(() -> {
            int id           = msg.get("id").getAsInt();
            String author    = msg.get("author").getAsString();
            String selected  = msg.has("selectedText") ? msg.get("selectedText").getAsString() : "";
            String text      = msg.get("commentText").getAsString();
            String createdAt = msg.has("createdAt") ? msg.get("createdAt").getAsString() : "";
            Integer parentId = msg.has("parentId") && !msg.get("parentId").isJsonNull()
                               ? msg.get("parentId").getAsInt() : null;

            if (parentId != null) {
                VBox parentNode = commentNodes.get(parentId);
                if (parentNode != null) {
                    VBox replyBox = buildReplyCard(id, author, text, createdAt);
                    parentNode.getChildren().add(replyBox);
                    commentNodes.put(id, replyBox);
                }
            } else {
                VBox card = buildCommentCard(id, author, selected, text, createdAt);
                commentList.getChildren().add(card);
                commentNodes.put(id, card);
            }
        });
    }

    public void removeComment(int commentId) {
        Platform.runLater(() -> {
            VBox node = commentNodes.remove(commentId);
            if (node != null && node.getParent() instanceof VBox parent) {
                parent.getChildren().remove(node);
            }
        });
    }

    public void loadComments(JsonArray comments) {
        Platform.runLater(() -> {
            commentList.getChildren().clear();
            commentNodes.clear();
            for (var el : comments) {
                addComment(el.getAsJsonObject());
            }
        });
    }

    // ── Card builders ─────────────────────────────────────────────
    private VBox buildCommentCard(int id, String author, String selected,
                                   String text, String createdAt) {
        Label authorLabel = new Label(author);
        authorLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        authorLabel.setTextFill(Color.web(PURPLE));

        Label timeLabel = new Label(formatTime(createdAt));
        timeLabel.setFont(Font.font("System", 10));
        timeLabel.setTextFill(Color.web(MUTED));

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        HBox topRow = new HBox(4, authorLabel, sp, timeLabel);
        topRow.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(6, topRow);

        if (selected != null && !selected.isEmpty()) {
            Label quoteLabel = new Label(
                selected.length() > 80 ? selected.substring(0, 80) + "…" : selected);
            quoteLabel.setFont(Font.font("System", 11));
            quoteLabel.setTextFill(Color.web(MUTED));
            quoteLabel.setWrapText(true);
            quoteLabel.setStyle(
                "-fx-font-style: italic;" +
                "-fx-padding: 3 6 3 8;" +
                "-fx-border-color: " + PURPLE + ";" +
                "-fx-border-width: 0 0 0 2;" +
                "-fx-background-color: " + PURPLE_TINT + ";" +
                "-fx-background-radius: 0 3 3 0;"
            );
            card.getChildren().add(quoteLabel);
        }

        Label textLabel = new Label(text);
        textLabel.setFont(Font.font("System", 12));
        textLabel.setTextFill(Color.web(TEXT));
        textLabel.setWrapText(true);
        card.getChildren().add(textLabel);

        // ── Reply input (hidden by default) ───────────────────────
        TextArea replyInput = new TextArea();
        replyInput.setPromptText("Write a reply…");
        replyInput.setPrefRowCount(2);
        replyInput.setWrapText(true);
        replyInput.setVisible(false);
        replyInput.setManaged(false);
        replyInput.setStyle(
            "-fx-font-size: 11px;" +
            "-fx-border-color: " + BORDER + ";" +
            "-fx-border-radius: 4px;" +
            "-fx-background-radius: 4px;" +
            "-fx-padding: 4 6 4 6;"
        );

        Button sendReplyBtn = new Button("Send Reply");
        sendReplyBtn.setVisible(false);
        sendReplyBtn.setManaged(false);
        sendReplyBtn.setStyle(
            "-fx-background-color: " + PURPLE + ";" +
            "-fx-text-fill: white;" +
            "-fx-font-size: 11px;" +
            "-fx-background-radius: 4px;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 4 10 4 10;"
        );
        sendReplyBtn.setOnAction(e -> {
            String replyText = replyInput.getText().trim();
            if (replyText.isEmpty()) return;
            bridge.sendComment("", replyText, id);
            replyInput.clear();
            replyInput.setVisible(false);
            replyInput.setManaged(false);
            sendReplyBtn.setVisible(false);
            sendReplyBtn.setManaged(false);
        });

        Button replyBtn = new Button("↩ Reply");
        replyBtn.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-border-color: transparent;" +
            "-fx-text-fill: " + MUTED + ";" +
            "-fx-font-size: 11px;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 2 0 2 0;"
        );
        replyBtn.setOnAction(e -> {
            boolean show = !replyInput.isVisible();
            replyInput.setVisible(show);
            replyInput.setManaged(show);
            sendReplyBtn.setVisible(show);
            sendReplyBtn.setManaged(show);
        });

        Button deleteBtn = new Button("🗑");
        deleteBtn.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-border-color: transparent;" +
            "-fx-text-fill: #cc4444;" +
            "-fx-font-size: 11px;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 2 0 2 0;"
        );
        deleteBtn.setOnAction(e -> bridge.deleteComment(id));
        deleteBtn.setVisible(author.equals(bridge.getUsername()));
        deleteBtn.setManaged(author.equals(bridge.getUsername()));

        Region actionSpacer = new Region();
        HBox.setHgrow(actionSpacer, Priority.ALWAYS);
        HBox actionRow = new HBox(8, replyBtn, actionSpacer, deleteBtn);

        card.getChildren().addAll(actionRow, replyInput, sendReplyBtn);
        card.setPadding(new Insets(10));
        card.setStyle(
            "-fx-background-color: white;" +
            "-fx-border-color: " + BORDER + ";" +
            "-fx-border-radius: 8px;" +
            "-fx-background-radius: 8px;" +
            "-fx-border-width: 0.5;"
        );
        return card;
    }

    private VBox buildReplyCard(int id, String author, String text, String createdAt) {
        Label authorLabel = new Label(author);
        authorLabel.setFont(Font.font("System", FontWeight.BOLD, 11));
        authorLabel.setTextFill(Color.web(PURPLE));

        Label timeLabel = new Label(formatTime(createdAt));
        timeLabel.setFont(Font.font("System", 10));
        timeLabel.setTextFill(Color.web(MUTED));

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        HBox topRow = new HBox(4, authorLabel, sp, timeLabel);

        Label textLabel = new Label(text);
        textLabel.setFont(Font.font("System", 11));
        textLabel.setTextFill(Color.web(TEXT));
        textLabel.setWrapText(true);

        Button deleteBtn = new Button("🗑");
        deleteBtn.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-border-color: transparent;" +
            "-fx-text-fill: #cc4444;" +
            "-fx-font-size: 10px;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 1 0 1 0;"
        );
        deleteBtn.setOnAction(e -> bridge.deleteComment(id));
        deleteBtn.setVisible(author.equals(bridge.getUsername()));
        deleteBtn.setManaged(author.equals(bridge.getUsername()));

        HBox bottomRow = new HBox(deleteBtn);
        bottomRow.setAlignment(Pos.CENTER_RIGHT);

        VBox reply = new VBox(4, topRow, textLabel, bottomRow);
        reply.setPadding(new Insets(6, 8, 6, 8));
        reply.setStyle(
            "-fx-background-color: " + SURFACE + ";" +
            "-fx-border-color: " + BORDER + ";" +
            "-fx-border-radius: 6px;" +
            "-fx-background-radius: 6px;" +
            "-fx-border-width: 0.5;"
        );
        return reply;
    }

    private String formatTime(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        return raw.length() > 16 ? raw.substring(0, 16) : raw;
    }
}