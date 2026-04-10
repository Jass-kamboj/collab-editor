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

public class LoginScreen {

    private final DatabaseManager db;
    private String loggedInUser = null;

    public LoginScreen(DatabaseManager db) {
        this.db = db;
    }

    public String show() {
        Stage stage = new Stage();
        stage.setTitle("Collab Editor — Login");
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setResizable(false);

        // ── Brand bar ─────────────────────────────────────────────
        StackPane iconBox = new StackPane();
        iconBox.setPrefSize(32, 32);
        iconBox.setMinSize(32, 32);
        Rectangle iconBg = new Rectangle(32, 32);
        iconBg.setArcWidth(8);
        iconBg.setArcHeight(8);
        iconBg.setFill(Color.web("#534AB7"));
        SVGPath iconSvg = new SVGPath();
        iconSvg.setContent(
            "M2 2 h6 a1.5 1.5 0 0 1 1.5 1.5 v3 a1.5 1.5 0 0 1-1.5 1.5 H2 a1.5 1.5 0 0 1-1.5-1.5 v-3 A1.5 1.5 0 0 1 2 2 Z " +
            "M10 2 h6 a1.5 1.5 0 0 1 1.5 1.5 v3 a1.5 1.5 0 0 1-1.5 1.5 h-6 a1.5 1.5 0 0 1-1.5-1.5 v-3 A1.5 1.5 0 0 1 10 2 Z " +
            "M2 10 h6 a1.5 1.5 0 0 1 1.5 1.5 v3 a1.5 1.5 0 0 1-1.5 1.5 H2 a1.5 1.5 0 0 1-1.5-1.5 v-3 A1.5 1.5 0 0 1 2 10 Z"
        );
        iconSvg.setFill(Color.WHITE);
        iconSvg.setScaleX(0.85);
        iconSvg.setScaleY(0.85);
        iconBox.getChildren().addAll(iconBg, iconSvg);

        Label appName = new Label("Collab Editor");
        appName.setFont(Font.font("System", FontWeight.MEDIUM, 16));
        appName.setTextFill(Color.web("#1a1a1a"));

        HBox brandBar = new HBox(10, iconBox, appName);
        brandBar.setAlignment(Pos.CENTER_LEFT);

        // ── Heading ───────────────────────────────────────────────
        Label heading = new Label("Welcome back");
        heading.setFont(Font.font("System", FontWeight.MEDIUM, 20));
        heading.setTextFill(Color.web("#1a1a1a"));

        Label subHeading = new Label("Sign in to your account or create a new one.");
        subHeading.setFont(Font.font("System", 13));
        subHeading.setTextFill(Color.web("#888780"));

        // ── Username field ────────────────────────────────────────
        Label userLabel = new Label("Username");
        userLabel.setFont(Font.font("System", FontWeight.MEDIUM, 12));
        userLabel.setTextFill(Color.web("#888780"));

        TextField usernameField = new TextField();
        usernameField.setPromptText("e.g. jass_kamboj");
        usernameField.setPrefHeight(38);
        usernameField.setStyle(
            "-fx-background-color: #f5f5f3;" +
            "-fx-border-color: #c8c7be;" +
            "-fx-border-width: 0.5px;" +
            "-fx-border-radius: 8px;" +
            "-fx-background-radius: 8px;" +
            "-fx-font-size: 14px;" +
            "-fx-text-fill: #1a1a1a;" +
            "-fx-prompt-text-fill: #b4b2a9;"
        );

        VBox userGroup = new VBox(4, userLabel, usernameField);

        // ── Password field ────────────────────────────────────────
        Label passLabel = new Label("Password");
        passLabel.setFont(Font.font("System", FontWeight.MEDIUM, 12));
        passLabel.setTextFill(Color.web("#888780"));

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("••••••••");
        passwordField.setPrefHeight(38);
        passwordField.setStyle(
            "-fx-background-color: #f5f5f3;" +
            "-fx-border-color: #c8c7be;" +
            "-fx-border-width: 0.5px;" +
            "-fx-border-radius: 8px;" +
            "-fx-background-radius: 8px;" +
            "-fx-font-size: 14px;" +
            "-fx-text-fill: #1a1a1a;" +
            "-fx-prompt-text-fill: #b4b2a9;"
        );

        VBox passGroup = new VBox(4, passLabel, passwordField);

        // ── Error label ───────────────────────────────────────────
        Label messageLabel = new Label("");
        messageLabel.setFont(Font.font("System", 12));
        messageLabel.setTextFill(Color.web("#A32D2D"));
        messageLabel.setMinHeight(20);
        messageLabel.setWrapText(true);

        // ── Buttons ───────────────────────────────────────────────
        Button loginBtn = new Button("Sign in");
        loginBtn.setMaxWidth(Double.MAX_VALUE);
        loginBtn.setPrefHeight(38);
        loginBtn.setFont(Font.font("System", FontWeight.MEDIUM, 14));
        loginBtn.setStyle(
            "-fx-background-color: #534AB7;" +
            "-fx-text-fill: white;" +
            "-fx-background-radius: 8px;" +
            "-fx-cursor: hand;"
        );
        loginBtn.setOnMouseEntered(e -> loginBtn.setStyle(
            "-fx-background-color: #3C3489;" +
            "-fx-text-fill: white;" +
            "-fx-background-radius: 8px;" +
            "-fx-cursor: hand;"
        ));
        loginBtn.setOnMouseExited(e -> loginBtn.setStyle(
            "-fx-background-color: #534AB7;" +
            "-fx-text-fill: white;" +
            "-fx-background-radius: 8px;" +
            "-fx-cursor: hand;"
        ));

        Button registerBtn = new Button("Create account");
        registerBtn.setMaxWidth(Double.MAX_VALUE);
        registerBtn.setPrefHeight(38);
        registerBtn.setFont(Font.font("System", 14));
        registerBtn.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-border-color: #c8c7be;" +
            "-fx-border-width: 0.5px;" +
            "-fx-border-radius: 8px;" +
            "-fx-background-radius: 8px;" +
            "-fx-text-fill: #1a1a1a;" +
            "-fx-cursor: hand;"
        );
        registerBtn.setOnMouseEntered(e -> registerBtn.setStyle(
            "-fx-background-color: #f5f5f3;" +
            "-fx-border-color: #c8c7be;" +
            "-fx-border-width: 0.5px;" +
            "-fx-border-radius: 8px;" +
            "-fx-background-radius: 8px;" +
            "-fx-text-fill: #1a1a1a;" +
            "-fx-cursor: hand;"
        ));
        registerBtn.setOnMouseExited(e -> registerBtn.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-border-color: #c8c7be;" +
            "-fx-border-width: 0.5px;" +
            "-fx-border-radius: 8px;" +
            "-fx-background-radius: 8px;" +
            "-fx-text-fill: #1a1a1a;" +
            "-fx-cursor: hand;"
        ));

        // ── Tagline ───────────────────────────────────────────────
        Label tagline = new Label("Your documents sync in real time.");
        tagline.setFont(Font.font("System", 11));
        tagline.setTextFill(Color.web("#b4b2a9"));

        // ── Actions ───────────────────────────────────────────────
        loginBtn.setOnAction(e -> {
            String user = usernameField.getText().trim();
            String pass = passwordField.getText().trim();
            if (user.isEmpty() || pass.isEmpty()) {
                messageLabel.setText("Please fill in both fields.");
                return;
            }
            if (db.validateUser(user, pass)) {
                loggedInUser = user;
                stage.close();
            } else {
                messageLabel.setText("Invalid username or password.");
            }
        });

        registerBtn.setOnAction(e -> {
            String user = usernameField.getText().trim();
            String pass = passwordField.getText().trim();
            if (user.isEmpty() || pass.isEmpty()) {
                messageLabel.setText("Please fill in both fields.");
                return;
            }
            if (db.createUser(user, pass)) {
                loggedInUser = user;
                stage.close();
            } else {
                messageLabel.setText("Username already taken.");
            }
        });

        // ── Card layout ───────────────────────────────────────────
        VBox card = new VBox(0);
        card.setPadding(new Insets(28, 28, 24, 28));
        card.setStyle(
            "-fx-background-color: white;" +
            "-fx-border-color: #d3d1c7;" +
            "-fx-border-width: 0.5px;" +
            "-fx-border-radius: 12px;" +
            "-fx-background-radius: 12px;"
        );
        card.setMaxWidth(360);
        card.setMinWidth(360);

        VBox.setMargin(brandBar,    new Insets(0, 0, 20, 0));
        VBox.setMargin(heading,     new Insets(0, 0, 2,  0));
        VBox.setMargin(subHeading,  new Insets(0, 0, 18, 0));
        VBox.setMargin(userGroup,   new Insets(0, 0, 10, 0));
        VBox.setMargin(passGroup,   new Insets(0, 0, 10, 0));
        VBox.setMargin(messageLabel,new Insets(0, 0, 10, 0));
        VBox.setMargin(loginBtn,    new Insets(0, 0, 8,  0));
        VBox.setMargin(registerBtn, new Insets(0, 0, 16, 0));
        VBox.setMargin(tagline,     new Insets(0));

        card.getChildren().addAll(
            brandBar, heading, subHeading,
            userGroup, passGroup,
            messageLabel,
            loginBtn, registerBtn,
            tagline
        );

        // Centre tagline
        StackPane taglineWrap = new StackPane(tagline);
        card.getChildren().remove(tagline);
        taglineWrap.setAlignment(Pos.CENTER);
        card.getChildren().add(taglineWrap);

        // ── Scene ─────────────────────────────────────────────────
        StackPane root = new StackPane(card);
        root.setPadding(new Insets(40));
        root.setStyle("-fx-background-color: #f5f5f3;");

        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.sizeToScene();
        stage.showAndWait();
        return loggedInUser;
    }
}