package com.example;

import javafx.concurrent.Worker;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

public class EditorPane {

    private final WebView webView;
    private final WebEngine engine;

    public EditorPane() {
        webView = new WebView();
        engine  = webView.getEngine();
        engine.loadContent(buildHtml());
    }

    /** Called once after the page loads — injects bridge and connects WebSocket. */
    public void init(EditorBridge bridge) {
        engine.getLoadWorker().stateProperty().addListener((obs, old, state) -> {
            if (state == Worker.State.SUCCEEDED) {
                // No JSObject needed — JS signals Java by writing to document.title
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

                // Java listens for the title signal
                engine.titleProperty().addListener((o, oldTitle, title) -> {
                    if (title != null && title.startsWith("##CONTENT##")) {
                        String html = title.substring("##CONTENT##".length());
                        bridge.onContentChanged(html);
                        // Reset so the next keystroke fires the listener again
                        engine.executeScript("document.title = '';");
                    }
                });

                bridge.connectWebSocket();
            }
        });
    }

    public WebView getView() { return webView; }

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