package com.example;

import javafx.animation.PauseTransition;
import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

public class EditorPane {

    private final WebView webView;
    private final WebEngine engine;
    private final BorderPane root;           // root is now BorderPane (left strip + center editor)
    private final Label editingLabel;
    private final PauseTransition fadeTimer;

    // ── Pages state ──────────────────────────────────────────────
    private final List<String> pageContents = new ArrayList<>(); // local cache per page
    private int currentPage = 0;
    private final VBox thumbnailStrip;       // left panel
    private final ScrollPane stripScroll;
    private EditorBridge bridge;             // set during init()

    // ── Zoom state ───────────────────────────────────────────────
    private double zoomLevel = 1.0;
    private static final double ZOOM_MIN  = 0.5;
    private static final double ZOOM_MAX  = 2.0;
    private static final double ZOOM_STEP = 0.1;
    private Label zoomLabel;                 // updated by ToolbarController

    // ── Cursor color palette ─────────────────────────────────────
    private static final String[] CURSOR_COLORS = {
        "#e53935","#8e24aa","#1e88e5","#00897b",
        "#f4511e","#6d4c41","#00acc1","#43a047",
        "#fb8c00","#3949ab"
    };

    public EditorPane() {
        webView = new WebView();
        engine  = webView.getEngine();
        engine.loadContent(buildHtml());

        // ── Editing indicator label ──────────────────────────────
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
        fadeTimer = new PauseTransition(Duration.seconds(2));
        fadeTimer.setOnFinished(e -> editingLabel.setVisible(false));

        // ── Editor stack (WebView + indicator overlay) ───────────
        StackPane editorStack = new StackPane(webView, editingLabel);
        StackPane.setAlignment(editingLabel, Pos.TOP_RIGHT);

        // ── Ctrl+Scroll zoom ─────────────────────────────────────
        webView.setOnScroll(event -> {
            if (event.isControlDown()) {
                if (event.getDeltaY() > 0) adjustZoom(ZOOM_STEP);
                else                       adjustZoom(-ZOOM_STEP);
                event.consume();
            }
        });

        // ── Thumbnail strip ──────────────────────────────────────
        thumbnailStrip = new VBox(8);
        thumbnailStrip.setPadding(new Insets(10, 6, 10, 6));
        thumbnailStrip.setStyle("-fx-background-color: #f0f0f0;");
        thumbnailStrip.setPrefWidth(100);

        stripScroll = new ScrollPane(thumbnailStrip);
        stripScroll.setFitToWidth(true);
        stripScroll.setPrefWidth(112);
        stripScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        stripScroll.setStyle("-fx-background-color: #f0f0f0; -fx-border-color: #ddd; -fx-border-width: 0 1 0 0;");

        // ── Root layout ──────────────────────────────────────────
        root = new BorderPane();
        root.setLeft(stripScroll);
        root.setCenter(editorStack);

        // ── Seed first page ──────────────────────────────────────
        pageContents.add("");
        refreshThumbnailStrip();
    }

    // ════════════════════════════════════════════════════════════
    //  Init (called once after scene is ready)
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
                    editor.addEventListener('keyup',   sendUpdate);
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
                        // Cache locally
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
        engine.executeScript(
            "document.body.style.zoom = '" + zoomLevel + "';"
        );
        if (zoomLabel != null) {
            zoomLabel.setText((int)(zoomLevel * 100) + "%");
        }
    }

    /** Called by ToolbarController so zoom % label stays in sync. */
    public void setZoomLabel(Label label) {
        this.zoomLabel = label;
    }

    // ════════════════════════════════════════════════════════════
    //  Pages
    // ════════════════════════════════════════════════════════════

    /** Called by server when it tells us how many pages exist for this doc. */
    public void initPages(int count) {
        pageContents.clear();
        for (int i = 0; i < count; i++) pageContents.add("");
        currentPage = 0;
        refreshThumbnailStrip();
    }

    /** Switch to a page — saves current, loads target (from cache or server). */
    public void switchToPage(int index) {
        if (index < 0 || index >= pageContents.size()) return;

        // Save current page content into local cache
        try {
            String current = (String) engine.executeScript(
                "document.getElementById('editor').innerHTML;"
            );
            pageContents.set(currentPage, current);
        } catch (Exception ignored) {}

        currentPage = index;
        String cached = pageContents.get(index);

        if (cached != null && !cached.isEmpty()) {
            setContent(cached);
        } else {
            // Ask server for this page's content
            if (bridge != null) bridge.requestPageSwitch(index);
        }

        refreshThumbnailStrip();
    }

    /** Called when server sends back a page's content. */
    public void applyPageContent(int pageIndex, String html) {
        while (pageContents.size() <= pageIndex) pageContents.add("");
        pageContents.set(pageIndex, html);
        if (pageIndex == currentPage) setContent(html);
        refreshThumbnailStrip();
    }

    /** Add a new blank page. */
    public void addPage() {
        // Save current first
        try {
            String current = (String) engine.executeScript(
                "document.getElementById('editor').innerHTML;"
            );
            pageContents.set(currentPage, current);
        } catch (Exception ignored) {}

        pageContents.add("");
        int newIndex = pageContents.size() - 1;
        refreshThumbnailStrip();
        switchToPage(newIndex);

        if (bridge != null) bridge.notifyPageAdd(newIndex);
    }

    /** Called when a peer adds a page. */
    public void onPeerAddedPage(int pageIndex) {
        while (pageContents.size() <= pageIndex) pageContents.add("");
        refreshThumbnailStrip();
    }

    public int getCurrentPage()  { return currentPage; }
    public int getPageCount()    { return pageContents.size(); }

    private void refreshThumbnailStrip() {
        thumbnailStrip.getChildren().clear();

        for (int i = 0; i < pageContents.size(); i++) {
            final int idx = i;

            Label numLabel = new Label("Page " + (i + 1));
            numLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #555;");

            // Thumbnail box (mini preview — just a styled pane for now)
            Pane thumb = new Pane();
            thumb.setPrefSize(80, 100);
            boolean isActive = (i == currentPage);
            thumb.setStyle(
                "-fx-background-color: white;" +
                "-fx-border-color: " + (isActive ? "#1e88e5" : "#ccc") + ";" +
                "-fx-border-width: " + (isActive ? "2" : "1") + ";" +
                "-fx-border-radius: 2;" +
                "-fx-cursor: hand;"
            );
            thumb.setOnMouseClicked(e -> switchToPage(idx));

            VBox cell = new VBox(4, thumb, numLabel);
            cell.setAlignment(Pos.CENTER);
            thumbnailStrip.getChildren().add(cell);
        }

        // ── Add Page button ──────────────────────────────────────
        Button addBtn = new Button("+ Page");
        addBtn.setStyle(
            "-fx-font-size: 11px; -fx-cursor: hand;" +
            "-fx-background-color: #e3f2fd; -fx-text-fill: #1e88e5;" +
            "-fx-border-color: #90caf9; -fx-border-radius: 3; -fx-background-radius: 3;" +
            "-fx-padding: 4 8 4 8;"
        );
        addBtn.setMaxWidth(Double.MAX_VALUE);
        addBtn.setOnAction(e -> addPage());
        thumbnailStrip.getChildren().add(addBtn);
    }

    // ════════════════════════════════════════════════════════════
    //  Existing API (unchanged signatures — EditorBridge still works)
    // ════════════════════════════════════════════════════════════
    public BorderPane getRoot() { return root; }

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

    public WebView    getView()   { return webView; }
    public WebEngine  getEngine() { return engine; }

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