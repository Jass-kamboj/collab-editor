package com.example;

import javafx.animation.PauseTransition;
import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

public class EditorPane {

    private final WebView   webView;
    private final WebEngine engine;
    private final BorderPane root;

    // ── Editing indicator (now lives in toolbar, set by ToolbarController) ──
    private final Label editingLabel;
    private final PauseTransition fadeTimer;

    // ── Pages ────────────────────────────────────────────────────
    private final List<String> pageContents = new ArrayList<>();
    private int currentPage = 0;
    private final VBox       thumbnailStrip;
    private final ScrollPane stripScroll;
    private EditorBridge bridge;

    // ── Zoom ─────────────────────────────────────────────────────
    private double zoomLevel = 1.0;
    private static final double ZOOM_MIN  = 0.5;
    private static final double ZOOM_MAX  = 2.0;
    private static final double ZOOM_STEP = 0.1;
    private Label zoomLabel;

    // ── Cursor palette ───────────────────────────────────────────
    private static final String[] CURSOR_COLORS = {
        "#e53935","#8e24aa","#1e88e5","#00897b",
        "#f4511e","#6d4c41","#00acc1","#43a047",
        "#fb8c00","#3949ab"
    };

    // ── Style constants ──────────────────────────────────────────
    private static final String PURPLE       = "#534AB7";
    private static final String STRIP_BG     = "#f5f5f3";
    private static final String CANVAS_BG    = "#f0efe9";
    private static final String BORDER_COLOR = "#d3d1c7";

    public EditorPane() {
        webView = new WebView();
        engine  = webView.getEngine();
        engine.loadContent(buildHtml());

        // ── Editing indicator pill ────────────────────────────────
        editingLabel = new Label();
        editingLabel.setFont(javafx.scene.text.Font.font("System", 11));
        editingLabel.setTextFill(Color.web("#888780"));
        editingLabel.setStyle(
            "-fx-font-style: italic;" +
            "-fx-background-color: #f5f5f3;" +
            "-fx-background-radius: 5;" +
            "-fx-padding: 3 8 3 8;"
        );
        editingLabel.setVisible(false);

        fadeTimer = new PauseTransition(Duration.seconds(2));
        fadeTimer.setOnFinished(e -> editingLabel.setVisible(false));

        // ── Canvas: WebView on warm-grey background ───────────────
        StackPane canvasWrap = new StackPane(webView);
        canvasWrap.setStyle("-fx-background-color: " + CANVAS_BG + ";");
        StackPane.setAlignment(webView, Pos.TOP_CENTER);

        // ── Ctrl+Scroll zoom ──────────────────────────────────────
        webView.setOnScroll(event -> {
            if (event.isControlDown()) {
                adjustZoom(event.getDeltaY() > 0 ? ZOOM_STEP : -ZOOM_STEP);
                event.consume();
            }
        });

        // ── Thumbnail strip ───────────────────────────────────────
        thumbnailStrip = new VBox(8);
        thumbnailStrip.setPadding(new Insets(10, 6, 10, 6));
        thumbnailStrip.setStyle("-fx-background-color: " + STRIP_BG + ";");
        thumbnailStrip.setPrefWidth(96);
        thumbnailStrip.setAlignment(Pos.TOP_CENTER);

        stripScroll = new ScrollPane(thumbnailStrip);
        stripScroll.setFitToWidth(true);
        stripScroll.setPrefWidth(108);
        stripScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        stripScroll.setStyle(
            "-fx-background-color: " + STRIP_BG + ";" +
            "-fx-border-color: " + BORDER_COLOR + ";" +
            "-fx-border-width: 0 0.5 0 0;"
        );

        // ── Root ──────────────────────────────────────────────────
        root = new BorderPane();
        root.setLeft(stripScroll);
        root.setCenter(canvasWrap);

        // ── Seed first page ───────────────────────────────────────
        pageContents.add("");
        refreshThumbnailStrip();
    }

    // ════════════════════════════════════════════════════════════
    //  Init
    // ════════════════════════════════════════════════════════════
    public void init(EditorBridge bridge) {
        this.bridge = bridge;

        engine.getLoadWorker().stateProperty().addListener((obs, old, state) -> {
            if (state == Worker.State.SUCCEEDED) {
                engine.executeScript("""
                    var style = document.createElement('style');
                    style.textContent = '@keyframes blink { 50% { opacity:0; } }';
                    document.head.appendChild(style);

                    var editor = document.getElementById('editor');
                    var debounce;

                    function sendUpdate() {
                        var sel = window.getSelection();
                        var offset = 0;
                        if (sel && sel.rangeCount > 0) {
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
                    editor.addEventListener('keyup', function() {
                        clearTimeout(debounce);
                        debounce = setTimeout(sendUpdate, 300);
                    });
                    editor.addEventListener('mouseup', sendUpdate);
                """);

                engine.titleProperty().addListener((o, oldTitle, title) -> {
                    if (title != null && title.startsWith("##CONTENT##")) {
                        String payload = title.substring("##CONTENT##".length());
                        int sep = payload.indexOf('|');
                        int cursorOffset = 0;
                        String html;
                        if (sep >= 0) {
                            try { cursorOffset = Integer.parseInt(payload.substring(0, sep)); }
                            catch (NumberFormatException ignored) {}
                            html = payload.substring(sep + 1);
                        } else {
                            html = payload;
                        }
                        if (currentPage < pageContents.size()) {
                            pageContents.set(currentPage, html);
                        }
                        bridge.onContentChanged(html, cursorOffset);
                        engine.executeScript("document.title = '';");
                    }
                });

                bridge.connectWebSocket();
            }
        });
    }

    // ════════════════════════════════════════════════════════════
    //  Zoom
    // ════════════════════════════════════════════════════════════
    public void adjustZoom(double delta) {
        zoomLevel = Math.min(ZOOM_MAX, Math.max(ZOOM_MIN, zoomLevel + delta));
        applyZoom();
    }

    public void setZoom(double level) {
        zoomLevel = Math.min(ZOOM_MAX, Math.max(ZOOM_MIN, level));
        applyZoom();
    }

    public double getZoomLevel() { return zoomLevel; }

    private void applyZoom() {
        engine.executeScript("document.body.style.zoom = '" + zoomLevel + "';");
        if (zoomLabel != null) {
            zoomLabel.setText((int)(zoomLevel * 100) + "%");
        }
    }

    public void setZoomLabel(Label label) { this.zoomLabel = label; }

    // ════════════════════════════════════════════════════════════
    //  Pages
    // ════════════════════════════════════════════════════════════
    public void initPages(int count) {
        pageContents.clear();
        for (int i = 0; i < count; i++) pageContents.add("");
        currentPage = 0;
        refreshThumbnailStrip();
    }

    public void switchToPage(int index) {
        if (index < 0 || index >= pageContents.size()) return;
        try {
            String current = (String) engine.executeScript(
                "document.getElementById('editor').innerHTML;");
            pageContents.set(currentPage, current);
        } catch (Exception ignored) {}

        currentPage = index;
        String cached = pageContents.get(index);
        if (cached != null && !cached.isEmpty()) {
            setContent(cached);
        } else {
            if (bridge != null) bridge.requestPageSwitch(index);
        }
        refreshThumbnailStrip();
    }

    public void applyPageContent(int pageIndex, String html) {
        while (pageContents.size() <= pageIndex) pageContents.add("");
        pageContents.set(pageIndex, html);
        if (pageIndex == currentPage) setContent(html);
        refreshThumbnailStrip();
    }

    public void addPage() {
        try {
            String current = (String) engine.executeScript(
                "document.getElementById('editor').innerHTML;");
            pageContents.set(currentPage, current);
        } catch (Exception ignored) {}

        pageContents.add("");
        int newIndex = pageContents.size() - 1;
        refreshThumbnailStrip();
        switchToPage(newIndex);
        if (bridge != null) bridge.notifyPageAdd(newIndex);
    }

    public void onPeerAddedPage(int pageIndex) {
        while (pageContents.size() <= pageIndex) pageContents.add("");
        refreshThumbnailStrip();
    }

    public int getCurrentPage() { return currentPage; }
    public int getPageCount()   { return pageContents.size(); }

    private void refreshThumbnailStrip() {
        thumbnailStrip.getChildren().clear();

        for (int i = 0; i < pageContents.size(); i++) {
            final int idx    = i;
            boolean isActive = (i == currentPage);

            // Mini paper preview
            VBox lines = new VBox(3);
            lines.setAlignment(Pos.TOP_LEFT);
            lines.setPadding(new Insets(8, 6, 8, 6));
            for (int l = 0; l < 4; l++) {
                Pane line = new Pane();
                line.setPrefHeight(3);
                line.setPrefWidth(l % 3 == 2 ? 32 : 44);
                line.setStyle("-fx-background-color: " +
                    (isActive ? "#d3d1c7" : "#e0dfd8") + ";" +
                    "-fx-background-radius: 2;");
                lines.getChildren().add(line);
            }

            StackPane thumb = new StackPane(lines);
            thumb.setPrefSize(72, 88);
            thumb.setMinSize(72, 88);
            thumb.setStyle(
                "-fx-background-color: white;" +
                "-fx-border-color: " + (isActive ? PURPLE : BORDER_COLOR) + ";" +
                "-fx-border-width: " + (isActive ? "2" : "0.5") + ";" +
                "-fx-border-radius: 4;" +
                "-fx-background-radius: 4;" +
                "-fx-cursor: hand;" +
                (isActive ? "" : "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.06), 3, 0, 0, 1);")
            );
            StackPane.setAlignment(lines, Pos.TOP_LEFT);
            thumb.setOnMouseClicked(e -> { if (e.getClickCount() == 1) switchToPage(idx); });

            Label numLabel = new Label("Page " + (i + 1));
            numLabel.setFont(javafx.scene.text.Font.font("System",
                javafx.scene.text.FontWeight.MEDIUM, 10));
            numLabel.setTextFill(isActive ? Color.web(PURPLE) : Color.web("#888780"));

            VBox cell = new VBox(3, thumb, numLabel);
            cell.setAlignment(Pos.CENTER);

            // Delete button (only on non-active pages when >1 page)
            if (pageContents.size() > 1 && !isActive) {
                Button delBtn = new Button("✕ delete");
                delBtn.setFont(javafx.scene.text.Font.font("System", 9));
                delBtn.setMaxWidth(Double.MAX_VALUE);
                delBtn.setStyle(
                    "-fx-background-color: #FCEBEB;" +
                    "-fx-text-fill: #A32D2D;" +
                    "-fx-border-color: #F7C1C1;" +
                    "-fx-border-width: 0.5;" +
                    "-fx-border-radius: 4;" +
                    "-fx-background-radius: 4;" +
                    "-fx-cursor: hand;" +
                    "-fx-padding: 2 6 2 6;"
                );
                delBtn.setOnAction(e -> deletePage(idx));
                cell.getChildren().add(delBtn);
            }

            thumbnailStrip.getChildren().add(cell);
        }

        // Add Page button — dashed ghost style
        Button addBtn = new Button("+ Page");
        addBtn.setFont(javafx.scene.text.Font.font("System", 11));
        addBtn.setMaxWidth(Double.MAX_VALUE);
        addBtn.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-border-color: #b4b2a9;" +
            "-fx-border-width: 0.5;" +
            "-fx-border-radius: 6;" +
            "-fx-background-radius: 6;" +
            "-fx-text-fill: #888780;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 5 8 5 8;"
        );
        addBtn.setOnMouseEntered(e -> addBtn.setStyle(
            "-fx-background-color: #ebe9e4;" +
            "-fx-border-color: #b4b2a9;" +
            "-fx-border-width: 0.5;" +
            "-fx-border-radius: 6;" +
            "-fx-background-radius: 6;" +
            "-fx-text-fill: #5f5e5a;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 5 8 5 8;"
        ));
        addBtn.setOnMouseExited(e -> addBtn.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-border-color: #b4b2a9;" +
            "-fx-border-width: 0.5;" +
            "-fx-border-radius: 6;" +
            "-fx-background-radius: 6;" +
            "-fx-text-fill: #888780;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 5 8 5 8;"
        ));
        addBtn.setOnAction(e -> addPage());
        thumbnailStrip.getChildren().add(addBtn);
    }

    // ════════════════════════════════════════════════════════════
    //  Public API (unchanged signatures)
    // ════════════════════════════════════════════════════════════
    public BorderPane getRoot()    { return root; }
    public WebView    getView()    { return webView; }
    public WebEngine  getEngine()  { return engine; }

    /** Returns the editing indicator label so ToolbarController can place it. */
    public Label getEditingLabel() { return editingLabel; }

    public void showEditingIndicator(String user) {
        editingLabel.setText(user + " is editing…");
        editingLabel.setVisible(true);
        fadeTimer.playFromStart();
    }

    public void showRemoteCursor(String user, int offset) {
        int colorIndex = Math.abs(user.hashCode()) % CURSOR_COLORS.length;
        String color = CURSOR_COLORS[colorIndex];
        String safe = user.replace("'", "\\'");
        engine.executeScript(String.format("""
            (function() {
                var old = document.getElementById('cursor-%s');
                if (old) old.remove();
                var editor = document.getElementById('editor');
                var walker = document.createTreeWalker(editor, NodeFilter.SHOW_TEXT, null, false);
                var node = null, remaining = %d;
                while (walker.nextNode()) {
                    var n = walker.currentNode;
                    if (n.length >= remaining) { node = n; break; }
                    remaining -= n.length;
                }
                if (!node) return;
                var range = document.createRange();
                range.setStart(node, remaining);
                range.collapse(true);
                var cursor = document.createElement('span');
                cursor.id = 'cursor-%s';
                cursor.className = 'remote-cursor';
                cursor.style.cssText = [
                    'display:inline-block','width:2px','background:%s',
                    'position:relative','animation:blink 1s step-end infinite',
                    'margin-left:-1px','vertical-align:text-bottom','height:1.2em'
                ].join(';');
                var tag = document.createElement('span');
                tag.textContent = '%s';
                tag.style.cssText = [
                    'position:absolute','top:-1.4em','left:0','background:%s',
                    'color:#fff','font-size:10px','padding:1px 4px',
                    'border-radius:3px','white-space:nowrap','pointer-events:none'
                ].join(';');
                cursor.appendChild(tag);
                range.insertNode(cursor);
                clearTimeout(window['cursorTimer_%s']);
                window['cursorTimer_%s'] = setTimeout(function() {
                    var el = document.getElementById('cursor-%s');
                    if (el) el.remove();
                }, 5000);
            })();
        """, safe, offset, safe, color, safe, color, safe, safe, safe));
    }

    public String getContent() {
        return (String) engine.executeScript(
            "document.getElementById('editor').innerHTML;");
    }

    public void setContent(String html) {
        String safe = html.replace("\\", "\\\\").replace("`", "\\`");
        engine.executeScript(
            "document.getElementById('editor').innerHTML = `" + safe + "`;");
    }

    public void execCommand(String cmd) {
        engine.executeScript("document.execCommand('" + cmd + "', false, null);");
    }

    public void execCommand(String cmd, String value) {
        engine.executeScript("document.execCommand('" + cmd + "', false, '" + value + "');");
    }

    // ════════════════════════════════════════════════════════════
    //  HTML template
    // ════════════════════════════════════════════════════════════
    private String buildHtml() {
        return """
            <!DOCTYPE html>
            <html>
            <head>
            <meta charset="UTF-8">
            <style>
              * { box-sizing: border-box; }
              html, body { margin:0; padding:0; background: #f0efe9; }
              #page-wrap {
                width: 680px;
                min-height: 880px;
                margin: 28px auto;
                background: white;
                border-radius: 4px;
                border: 0.5px solid #d3d1c7;
                box-shadow: 0 2px 10px rgba(0,0,0,0.07);
              }
              #editor {
                min-height: 820px;
                padding: 52px 60px;
                outline: none;
                font-family: 'Georgia', serif;
                font-size: 14px;
                line-height: 1.75;
                color: #1a1a1a;
                word-wrap: break-word;
              }
              table { border-collapse: collapse; }
              td, th { border: 1px solid #d3d1c7; padding: 4px 8px; min-width: 60px; }
            </style>
            </head>
            <body>
            <div id="page-wrap">
              <div id="editor" contenteditable="true" spellcheck="true">
                Start typing here…
              </div>
            </div>
            </body>
            </html>
            """;
    }

    // ════════════════════════════════════════════════════════════
    //  Delete page
    // ════════════════════════════════════════════════════════════
    private void deletePage(int index) {
        if (pageContents.size() <= 1) return;
        pageContents.remove(index);
        if (currentPage >= pageContents.size()) {
            currentPage = pageContents.size() - 1;
        }
        refreshThumbnailStrip();
        setContent(pageContents.get(currentPage));
        if (bridge != null) bridge.notifyPageDelete(index);
    }

    public void onPeerDeletedPage(int index) {
        if (index < pageContents.size()) {
            pageContents.remove(index);
            if (currentPage >= pageContents.size()) currentPage = pageContents.size() - 1;
            refreshThumbnailStrip();
            setContent(pageContents.get(currentPage));
        }
    }
}