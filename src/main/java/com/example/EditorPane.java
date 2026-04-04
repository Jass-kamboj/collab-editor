package com.example;

import javafx.animation.PauseTransition;
import javafx.concurrent.Worker;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.util.Duration;

public class EditorPane {

    private final WebView webView;
    private final WebEngine engine;
    private final StackPane root;          // ← new
    private final Label editingLabel;      // ← new
    private final PauseTransition fadeTimer; // ← new

    public EditorPane() {
        webView = new WebView();
        engine  = webView.getEngine();
        engine.loadContent(buildHtml());

        // ── Editing indicator ──────────────────────────────────────── // ← new
        editingLabel = new Label();
        editingLabel.setStyle(
            "-fx-text-fill: #6c757d;" +
            "-fx-font-size: 12px;" +
            "-fx-font-style: italic;" +
            "-fx-background-color: rgba(255,255,255,0.75);" +
            "-fx-background-radius: 4;" +
            "-fx-padding: 2 8 2 8;"
        );
        editingLabel.setVisible(false);

        // Float it in the top-right corner of the stack            // ← new
        StackPane.setAlignment(editingLabel, Pos.TOP_RIGHT);

        // Wrap WebView + label together                             // ← new
        root = new StackPane(webView, editingLabel);

        // Auto-hide after 2 s of silence                           // ← new
        fadeTimer = new PauseTransition(Duration.seconds(2));
        fadeTimer.setOnFinished(e -> editingLabel.setVisible(false));
    }

    /** Return this instead of getView() wherever you add EditorPane to your scene. */ // ← new
    public StackPane getRoot() { return root; }

    /** Called by EditorBridge when a peer's message arrives. */    // ← new
    public void showEditingIndicator(String user) {
        editingLabel.setText(user + " is editing…");
        editingLabel.setVisible(true);
        fadeTimer.playFromStart();
    }

    /** Called once after the page loads — injects bridge and connects WebSocket. */
    public void init(EditorBridge bridge) {
        engine.getLoadWorker().stateProperty().addListener((obs, old, state) -> {
            if (state == Worker.State.SUCCEEDED) {
                engine.executeScript("""
                    var editor = document.getElementById('editor');
                    var debounce;
                    editor.addEventListener('input', function() {
                        clearTimeout(debounce);
                        debounce = setTimeout(function() {
                            document.title = '##CONTENT##' + editor.innerHTML;
                        }, 300);
                    });
                """);

                engine.titleProperty().addListener((o, oldTitle, title) -> {
                    if (title != null && title.startsWith("##CONTENT##")) {
                        String html = title.substring("##CONTENT##".length());
                        bridge.onContentChanged(html);
                        engine.executeScript("document.title = '';");
                    }
                });

                bridge.connectWebSocket();
            }
        });
    }

    public WebView getView()  { return webView; }

    public String getContent() {
        return (String) engine.executeScript(
            "document.getElementById('editor').innerHTML;"
        );
    }

    public void setContent(String html) {
        String safe = html.replace("\\", "\\\\").replace("`", "\\`");
        engine.executeScript(
            "document.getElementById('editor').innerHTML = `" + safe + "`;"
        );
    }

    public void execCommand(String cmd) {
        engine.executeScript("document.execCommand('" + cmd + "', false, null);");
    }

    public void execCommand(String cmd, String value) {
        engine.executeScript("document.execCommand('" + cmd + "', false, '" + value + "');");
    }

    public WebEngine getEngine() { return engine; }

    private String buildHtml() {
        return """
            <!DOCTYPE html>
            <html>
            <head>
            <meta charset="UTF-8">
            <style>
              body { margin:0; padding:0; font-family: Arial, sans-serif; }
              #editor {
                min-height: 600px; padding: 40px 60px;
                outline: none; font-size: 14px; line-height: 1.6;
                word-wrap: break-word;
              }
              table { border-collapse: collapse; }
              td, th { border: 1px solid #aaa; padding: 4px 8px; min-width: 60px; }
            </style>
            </head>
            <body>
            <div id="editor" contenteditable="true" spellcheck="true">
              Start typing here...
            </div>
            </body>
            </html>
            """;
    }
}