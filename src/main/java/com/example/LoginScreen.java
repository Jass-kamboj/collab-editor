package com.example;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class LoginScreen {

    private final DatabaseManager db;
    private String loggedInUser = null;

    public LoginScreen(DatabaseManager db) {
        this.db = db;
    }

    // Returns the username if login/register succeeded, null if user closed the window
    public String show() {
        Stage stage = new Stage();
        stage.setTitle("Collab Editor — Login");
        stage.initModality(Modality.APPLICATION_MODAL);

        TextField usernameField = new TextField();
        usernameField.setPromptText("Username");

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");

        Label messageLabel = new Label("");
        messageLabel.setStyle("-fx-text-fill: red;");

        Button loginBtn = new Button("Login");
        Button registerBtn = new Button("Register");

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

        VBox layout = new VBox(12,
            new Label("Collab Editor"),
            usernameField,
            passwordField,
            loginBtn,
            registerBtn,
            messageLabel
        );
        layout.setPadding(new Insets(30));
        layout.setAlignment(Pos.CENTER);
        layout.setMinWidth(300);

        stage.setScene(new Scene(layout));
        stage.showAndWait(); // blocks until stage.close() is called
        return loggedInUser;
    }
}