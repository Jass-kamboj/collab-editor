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
    private final StackPane root;
    private final Label editingLabel;
    private final PauseTransition fadeTimer;

    // ── Cursor color palette (10 users max before cycling) ── // ← new
    private static final String[] CURSOR_COLORS = {           // ← new
        "#e53935","#8e24aa","#1e88e5","#00897b",              // ← new
        "#f4511e","#6d4c41","#00acc1","#43a047",              // ← new
        "#fb8c00","#3949ab"                                    // ← new
    };                                                         // ← new

    public EditorPane() {
        webView = new WebView();
        engine  = webView.getEngine();
        engine.loadContent(buildHtml());

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
        StackPane.setAlignment(editingLabel, Pos.TOP_RIGHT);
        root = new StackPane(webView, editingLabel);
        fadeTimer = new PauseTransition(Duration.seconds(2));
        fadeTimer.setOnFinished(e -> editingLabel.setVisible(false));
    }

    public StackPane getRoot() { return root; }

    public void showEditingIndicator(String user) {
        editingLabel.setText(user + " is editing…");
        editingLabel.setVisible(true);
        fadeTimer.playFromStart();
    }

    /** Called by EditorBridge when a peer's cursor position arrives. */ // ← new
    public void showRemoteCursor(String user, int offset) {              // ← new
        // Pick a stable color for this username                         // ← new
        int colorIndex = Math.abs(user.hashCode()) % CURSOR_COLORS.length; // ← new
        String color = CURSOR_COLORS[colorIndex];                        // ← new

        String safe = user.replace("'", "\\'");                          // ← new
        engine.executeScript(String.format("""
            (function() {
                // Remove old cursor for this user
                var old = document.getElementById('cursor-%s');
                if (old) old.remove();

                // Find the text node + offset inside #editor
                var editor = document.getElementById('editor');
                var walker = document.createTreeWalker(
                    editor, NodeFilter.SHOW_TEXT, null, false
                );
                var node = null, remaining = %d;
                while (walker.nextNode()) {
                    var n = walker.currentNode;
                    if (n.length >= remaining) { node = n; break; }
                    remaining -= n.length;
                }
                if (!node) return;

                // Insert the cursor span at that position
                var range = document.createRange();
                range.setStart(node, remaining);
                range.collapse(true);

                var cursor = document.createElement('span');
                cursor.id = 'cursor-%s';
                cursor.className = 'remote-cursor';
                cursor.style.cssText = [
                    'display:inline-block',
                    'width:2px',
                    'background:%s',
                    'position:relative',
                    'animation:blink 1s step-end infinite',
                    'margin-left:-1px',
                    'vertical-align:text-bottom',
                    'height:1.2em'
                ].join(';');

                // Name tag above cursor
                var tag = document.createElement('span');
                tag.textContent = '%s';
                tag.style.cssText = [
                    'position:absolute',
                    'top:-1.4em',
                    'left:0',
                    'background:%s',
                    'color:#fff',
                    'font-size:10px',
                    'padding:1px 4px',
                    'border-radius:3px',
                    'white-space:nowrap',
                    'pointer-events:none'
                ].join(';');
                cursor.appendChild(tag);

                range.insertNode(cursor);

                // Auto-remove after 5s of no updates
                clearTimeout(window['cursorTimer_%s']);
                window['cursorTimer_%s'] = setTimeout(function() {
                    var el = document.getElementById('cursor-%s');
                    if (el) el.remove();
                }, 5000);
            })();
        """, safe, offset, safe, color, safe, color, safe, safe, safe)); // ← new
    }                                                                     // ← new

    /** Called once after the page loads — injects bridge and connects WebSocket. */
    public void init(EditorBridge bridge) {
        engine.getLoadWorker().stateProperty().addListener((obs, old, state) -> {
            if (state == Worker.State.SUCCEEDED) {
                engine.executeScript("""
                    // ── Cursor blink animation ──────────────────── // new
                    var style = document.createElement('style');
                    style.textContent = '@keyframes blink { 50% { opacity:0; } }';
                    document.head.appendChild(style);

                    var editor = document.getElementById('editor');
                    var debounce;

                    // ── Send content + cursor offset ─────────────── // new
                    function sendUpdate() {
                        var sel = window.getSelection();
                        var offset = 0;
                        if (sel.rangeCount > 0) {
                            var range = document.createRange();
                            range.setStart(editor, 0);
                            range.setEnd(sel.anchorNode, sel.anchorOffset);
                            offset = range.toString().length;
                        }
                        document.title = '##CONTENT##' + offset + '|' + editor.innerHTML;
                    }

                    editor.addEventListener('input', function() {
                        clearTimeout(debounce);
                        debounce = setTimeout(sendUpdate, 300);
                    });

                    // Also send cursor on click/keyboard navigation   // new
                    editor.addEventListener('keyup',   sendUpdate);    // new
                    editor.addEventListener('mouseup', sendUpdate);    // new
                """);

                engine.titleProperty().addListener((o, oldTitle, title) -> {
                    if (title != null && title.startsWith("##CONTENT##")) {
                        String payload = title.substring("##CONTENT##".length());

                        // Split offset and html on first '|'          // ← new
                        int sep = payload.indexOf('|');                 // ← new
                        int cursorOffset = 0;                           // ← new
                        String html;                                    // ← new
                        if (sep >= 0) {                                 // ← new
                            try { cursorOffset = Integer.parseInt(payload.substring(0, sep)); } // ← new
                            catch (NumberFormatException ignored) {}    // ← new
                            html = payload.substring(sep + 1);         // ← new
                        } else {                                        // ← new
                            html = payload;                             // ← new
                        }                                              

                        bridge.onContentChanged(html, cursorOffset);   // ← new signature
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