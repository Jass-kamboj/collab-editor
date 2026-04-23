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
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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

    // ── Authorship palette (matches cursor colors) ────────────────
    private static final String[] AUTHOR_COLORS = {
        "rgba(229,57,53,0.07)",   "rgba(142,36,170,0.07)",
        "rgba(30,136,229,0.07)",  "rgba(0,137,123,0.07)",
        "rgba(244,81,30,0.07)",   "rgba(109,76,65,0.07)",
        "rgba(0,172,193,0.07)",   "rgba(67,160,71,0.07)",
        "rgba(251,140,0,0.07)",   "rgba(57,73,171,0.07)"
    };
    private static final String[] AUTHOR_BORDERS = {
        "rgba(229,57,53,0.35)",   "rgba(142,36,170,0.35)",
        "rgba(30,136,229,0.35)",  "rgba(0,137,123,0.35)",
        "rgba(244,81,30,0.35)",   "rgba(109,76,65,0.35)",
        "rgba(0,172,193,0.35)",   "rgba(67,160,71,0.35)",
        "rgba(251,140,0,0.35)",   "rgba(57,73,171,0.35)"
    };
    // author → index in palette
    private final java.util.Map<String, Integer> authorIndex = new java.util.LinkedHashMap<>();
    private int nextAuthorIndex = 0;

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
                cursor.contentEditable = 'false';
                cursor.style.cssText = [
                    'display:inline-block','width:2px','background:%s',
                    'position:relative','animation:blink 1s step-end infinite',
                    'margin-left:-1px','vertical-align:text-bottom','height:1.2em',
                    'pointer-events:none','user-select:none','-webkit-user-select:none'
                ].join(';');
                cursor.setAttribute('contenteditable', 'false');
                var tag = document.createElement('span');
                tag.textContent = '%s';
                tag.contentEditable = 'false';
                tag.style.cssText = [
                    'position:absolute','top:-1.4em','left:0','background:%s',
                    'color:#fff','font-size:10px','padding:1px 4px',
                    'border-radius:3px','white-space:nowrap','pointer-events:none',
                    'user-select:none','-webkit-user-select:none'
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

    public void applyAuthorship(JsonArray authors) {
        if (authors == null) return;

        // Build hash → {author, color, border} map
        StringBuilder js = new StringBuilder("(function() {\n");
        js.append("  var map = {};\n");

        // Track contribution counts per author
        java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();

        for (JsonElement el : authors) {
            JsonObject a = el.getAsJsonObject();
            String hash   = a.get("hash").getAsString();
            String author = a.get("author").getAsString();
            String time   = a.has("time") ? a.get("time").getAsString() : "";

            // Assign stable color index per author
            if (!authorIndex.containsKey(author)) {
                authorIndex.put(author, nextAuthorIndex % AUTHOR_COLORS.length);
                nextAuthorIndex++;
            }
            int idx = authorIndex.get(author);
            String color  = AUTHOR_COLORS[idx];
            String border = AUTHOR_BORDERS[idx];
            String safeAuthor = author.replace("'", "\\'");
            String safeTime = time.length() > 16 ? time.substring(0, 16) : time;

            js.append("  map['").append(hash).append("'] = {")
              .append("author:'").append(safeAuthor).append("',")
              .append("color:'").append(color).append("',")
              .append("border:'").append(border).append("',")
              .append("time:'").append(safeTime).append("'")
              .append("};\n");

            counts.merge(author, 1, Integer::sum);
        }

        js.append("""
              // First clear all previous authorship tints
              document.querySelectorAll('[data-author-tint]').forEach(function(el) {
                el.style.backgroundColor = '';
                el.style.borderLeft = '';
                el.style.paddingLeft = '';
                el.title = '';
                el.removeAttribute('data-author-tint');
              });

              // Apply tints ONLY to leaf-level block elements with real text
              var editors = document.querySelectorAll('.page-editor, #editor');
              editors.forEach(function(ed) {
                // Only direct block children — not the editor div itself
                var blocks = Array.from(ed.children).filter(function(el) {
                  var tag = el.tagName.toLowerCase();
                  return ['p','h1','h2','h3','h4','h5','h6','li','td','div'].includes(tag)
                    && !el.classList.contains('page-editor')
                    && !el.classList.contains('page-wrap');
                });

                blocks.forEach(function(block) {
                  // Get ALL text content of the block
                  var text = block.innerText ? block.innerText.trim() : block.textContent.trim();
                  if (text.length < 3) return;

                  var hash = String(hashCode(text));
                  var info = map[hash];
                  if (info) {
                    block.style.backgroundColor = info.color;
                    block.style.borderLeft = '2px solid ' + info.border;
                    block.style.paddingLeft = '8px';
                    block.style.borderRadius = '2px';
                    block.title = '✍ ' + info.author + '  •  ' + info.time;
                    block.setAttribute('data-author-tint', info.author);
                  }
                });
              });
            
              function hashCode(str) {
                var h = 0;
                for (var i = 0; i < str.length; i++) {
                  h = 31 * h + str.charCodeAt(i);
                }
                return h;
              }
            })();
        """);

        engine.executeScript(js.toString());

        // ── Update contribution bar ───────────────────────────────
        updateContributionBar(counts);
    }

    private void updateContributionBar(java.util.Map<String, Integer> counts) {
        if (counts.isEmpty()) return;

        int total = counts.values().stream().mapToInt(Integer::intValue).sum();

        // Build the bar as a styled HBox — attach to bottom of root
        javafx.scene.layout.HBox bar = new javafx.scene.layout.HBox(0);
        bar.setPrefHeight(6);
        bar.setMinHeight(6);
        bar.setMaxHeight(6);
        bar.setStyle("-fx-background-color: #e8e6df;");

        javafx.scene.control.Tooltip barTip = new javafx.scene.control.Tooltip();
        StringBuilder tipText = new StringBuilder("Contributions:\n");

        for (java.util.Map.Entry<String, Integer> entry : counts.entrySet()) {
            String author = entry.getKey();
            int count = entry.getValue();
            double pct = (double) count / total;

            if (!authorIndex.containsKey(author)) {
                authorIndex.put(author, nextAuthorIndex % AUTHOR_COLORS.length);
                nextAuthorIndex++;
            }
            int idx = authorIndex.get(author);
            // Convert rgba tint to solid color for the bar
            String solid = CURSOR_COLORS[idx];

            javafx.scene.layout.Region seg = new javafx.scene.layout.Region();
            seg.setPrefHeight(6);
            HBox.setHgrow(seg, javafx.scene.layout.Priority.NEVER);
            seg.setPrefWidth(pct * 10000); // will be normalized by HBox
            seg.setStyle("-fx-background-color: " + solid + ";");
            bar.getChildren().add(seg);

            tipText.append(String.format("  %s: %d%%\n",
                author, (int)(pct * 100)));
        }

        barTip.setText(tipText.toString().trim());
        javafx.scene.control.Tooltip.install(bar, barTip);

        // Attach bar to bottom of root — replace if already there
        if (root.getBottom() instanceof javafx.scene.layout.HBox existing
                && existing.getId() != null
                && existing.getId().equals("contrib-bar")) {
            root.setBottom(bar);
        } else {
            root.setBottom(bar);
        }
        bar.setId("contrib-bar");
    }

    public String getContent() {
        return (String) engine.executeScript("getAllContent();");
    }

    public void setContent(String html) {
            String safe = html.replace("\\", "\\\\").replace("`", "\\`");
            engine.executeScript("""
                (function() {
                    var parts = `%s`.split('<hr class="page-break"/>');
                    // Reset to 1 page
                    var allWraps = document.querySelectorAll('.page-wrap');
                    for (var i = 1; i < allWraps.length; i++) allWraps[i].remove();
                    pageCount = 1;

                    var firstEd = document.getElementById('editor');
                    firstEd.innerHTML = parts[0] || '';

                    for (var p = 1; p < parts.length; p++) {
                        var newEd = addPage();
                        newEd.innerHTML = parts[p];
                    }
                })();
            """.formatted(safe));
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
            html, body { margin:0; padding:0; background: #f0efe9; overflow-y: auto; min-height: 100%; }

            .page-wrap {
                width: 680px;
                height: 880px;
                overflow: hidden;
                margin: 28px auto;
                background: white;
                border-radius: 4px;
                border: 0.5px solid #d3d1c7;
                box-shadow: 0 2px 10px rgba(0,0,0,0.07);
                position: relative;
            }

            .page-editor {
                height: 776px;
                padding: 52px 60px;
                outline: none;
                font-family: 'Georgia', serif;
                font-size: 14px;
                line-height: 1.75;
                color: #1a1a1a;
                word-wrap: break-word;
                overflow: hidden;
            }

            .page-number {
                position: absolute;
                bottom: 10px;
                width: 100%;
                text-align: center;
                font-size: 10px;
                color: #b4b2a9;
                pointer-events: none;
                user-select: none;
            }

            table { border-collapse: collapse; }
            td, th { border: 1px solid #d3d1c7; padding: 4px 8px; min-width: 60px; }
            </style>
            </head>
            <body>
            <div class="page-wrap" id="page-1">
            <div class="page-editor" id="editor" contenteditable="true" spellcheck="true"></div>
            <div class="page-number">1</div>
            </div>

            <script>
            var PAGE_HEIGHT = 776;
            var pageCount = 1;
            var isProcessing = false;

            function getPageEditor(n) {
            return n === 1
                ? document.getElementById('editor')
                : document.getElementById('editor-' + n);
            }

            function addPage() {
            pageCount++;
            var wrap = document.createElement('div');
            wrap.className = 'page-wrap';
            wrap.id = 'page-' + pageCount;

            var ed = document.createElement('div');
            ed.className = 'page-editor';
            ed.id = 'editor-' + pageCount;
            ed.contentEditable = 'true';
            ed.spellcheck = true;

            var num = document.createElement('div');
            num.className = 'page-number';
            num.textContent = pageCount;

            wrap.appendChild(ed);
            wrap.appendChild(num);
            document.body.appendChild(wrap);
            attachListeners(ed);
            return ed;
            }

            function removePage(n) {
            if (n <= 1) return;
            var wrap = document.getElementById('page-' + n);
            if (wrap) wrap.remove();
            pageCount = Math.max(1, pageCount - 1);
            document.querySelectorAll('.page-number').forEach(function(el, i) {
                el.textContent = i + 1;
            });
            }

            function moveLastChildToNext(ed, nextEd) {
            var kids = Array.from(ed.childNodes).filter(function(n) {
                return !(n.nodeType === Node.TEXT_NODE && n.textContent.trim() === '');
            });
            if (kids.length === 0) return false;
            var last = kids[kids.length - 1];
            nextEd.insertBefore(last.cloneNode(true), nextEd.firstChild);
            last.remove();
            return true;
            }

            function moveFirstChildToPrev(ed, prevEd) {
            var kids = Array.from(ed.childNodes).filter(function(n) {
                return !(n.nodeType === Node.TEXT_NODE && n.textContent.trim() === '');
            });
            if (kids.length === 0) return false;
            var first = kids[0];
            prevEd.appendChild(first.cloneNode(true));
            first.remove();
            return true;
            }

            function getContentBottom(ed) {
          // Use scrollHeight vs the fixed height — most reliable in WebView
          // scrollHeight includes all content even if overflow:hidden
          var kids = Array.from(ed.childNodes).filter(function(n) {
            return n.nodeType === Node.ELEMENT_NODE ||
              (n.nodeType === Node.TEXT_NODE && n.textContent.trim() !== '');
          });
          if (kids.length === 0) return 0;

          // Temporarily make overflow visible to get true scrollHeight
          var oldOverflow = ed.style.overflow;
          ed.style.overflow = 'visible';
          var h = ed.scrollHeight;
          ed.style.overflow = oldOverflow || '';
          return h;
        }

        function checkPages() {
          if (isProcessing) return;
          isProcessing = true;
          try {
            // Forward pass — push overflow to next page
            for (var i = 1; i <= pageCount; i++) {
              var ed = getPageEditor(i);
              if (!ed) continue;
              var tries = 0;
              while (getContentBottom(ed) > PAGE_HEIGHT + 104 && tries < 30) {
                var nextNum = i + 1;
                var nextEd = getPageEditor(nextNum);
                if (!nextEd) nextEd = addPage();
                if (!moveLastChildToNext(ed, nextEd)) break;
                tries++;
              }
            }

            // Backward pass — pull content back if previous page has room
            for (var j = pageCount; j >= 2; j--) {
              var cur = getPageEditor(j);
              var prev = getPageEditor(j - 1);
              if (!cur || !prev) continue;
              var kids = Array.from(cur.childNodes).filter(function(n) {
                return !(n.nodeType === Node.TEXT_NODE && n.textContent.trim() === '');
              });
              if (kids.length === 0) {
                removePage(j);
                continue;
              }
              // Test if first child of cur fits in prev
              var testNode = kids[0].cloneNode(true);
              prev.appendChild(testNode);
              if (getContentBottom(prev) <= PAGE_HEIGHT + 104) {
                prev.removeChild(testNode);
                moveFirstChildToPrev(cur, prev);
                j++;
              } else {
                prev.removeChild(testNode);
              }
            }

            // Remove trailing empty pages
            for (var k = pageCount; k >= 2; k--) {
              var ed2 = getPageEditor(k);
              if (ed2 && ed2.innerHTML.trim() === '') {
                removePage(k);
              } else {
                break;
              }
            }
          } finally {
            isProcessing = false;
          }
        }

            function getAllContent() {
            var parts = [];
            for (var i = 1; i <= pageCount; i++) {
                var ed = getPageEditor(i);
                if (ed) parts.push(ed.innerHTML);
            }
            return parts.join('<hr class="page-break"/>');
            }

            function sendUpdate(editorEl) {
            var sel = window.getSelection();
            var offset = 0;
            if (sel && sel.rangeCount > 0) {
                try {
                var range = document.createRange();
                range.setStart(editorEl, 0);
                range.setEnd(sel.anchorNode, sel.anchorOffset);
                offset = range.toString().length;
                } catch(e) {}
            }
            document.title = '##CONTENT##' + offset + '|' + getAllContent();
            }

            function attachListeners(ed) {
            var debounce;
            var updateDebounce;

            ed.addEventListener('input', function() {
            clearTimeout(debounce);
            debounce = setTimeout(function() {
              checkPages();
              clearTimeout(updateDebounce);
              updateDebounce = setTimeout(function() { sendUpdate(ed); }, 150);
            }, 120);
          });

            ed.addEventListener('keyup', function() {
                clearTimeout(updateDebounce);
                updateDebounce = setTimeout(function() { sendUpdate(ed); }, 300);
            });

            ed.addEventListener('mouseup', function() { sendUpdate(ed); });

            ed.addEventListener('keydown', function(e) {
                var sel = window.getSelection();
                if (!sel || sel.rangeCount === 0) return;
                var node = sel.anchorNode;
                while (node && node !== ed) {
                if (node.classList && node.classList.contains('remote-cursor')) {
                    var range = document.createRange();
                    range.setStartAfter(node);
                    range.collapse(true);
                    sel.removeAllRanges();
                    sel.addRange(range);
                    break;
                }
                node = node.parentNode;
                }
            }, true);

            ed.addEventListener('input', function() {
                document.querySelectorAll('.remote-cursor').forEach(function(el) {
                Array.from(el.childNodes).forEach(function(child) {
                    if (child.nodeType === Node.TEXT_NODE) {
                    el.parentNode.insertBefore(child, el);
                    }
                });
                });
            }, true);
            }

            attachListeners(document.getElementById('editor'));
            </script>
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