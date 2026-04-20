package com.example;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;

public class ToolbarController {

    private final ToolBar toolbar;

    // ── Brand constants ──────────────────────────────────────────
    private static final String PURPLE       = "#534AB7";
    private static final String PURPLE_HOVER = "#3C3489";
    private static final String PURPLE_TINT  = "#EEEDFE";
    private static final String BORDER       = "#d3d1c7";
    private static final String MUTED        = "#888780";
    private static final String TEXT         = "#2c2b27";

    public ToolbarController(EditorPane editor, EditorBridge bridge, Stage stage) {

        // ── Brand mark ───────────────────────────────────────────
        Label brand = new Label("✦ Collab");
        brand.setStyle(
            "-fx-font-size: 13px;" +
            "-fx-font-weight: bold;" +
            "-fx-text-fill: " + PURPLE + ";" +
            "-fx-padding: 0 12 0 4;"
        );

        // ── Doc title label ──────────────────────────────────────
        String docTitle = "Untitled";
        Label titleLabel = new Label(docTitle);
        titleLabel.setStyle(
            "-fx-font-size: 13px;" +
            "-fx-text-fill: " + TEXT + ";" +
            "-fx-font-weight: 500;" +
            "-fx-padding: 0 8 0 0;" +
            "-fx-max-width: 180px;"
        );
        titleLabel.setEllipsisString("…");

        // ── Separator helper ─────────────────────────────────────
        // We'll use styled Region dividers instead of default Separator
        // (helper defined below as a method)

        // ── Formatting buttons ───────────────────────────────────
        Button boldBtn      = makeFormatBtn("B", "bold",      "font-weight:bold");
        Button italicBtn    = makeFormatBtn("I", "italic",    "font-style:italic");
        Button underlineBtn = makeFormatBtn("U", "underline", "text-decoration:underline");

        boldBtn.setOnAction(e -> { editor.execCommand("bold");      bridge.pushChange(); });
        italicBtn.setOnAction(e -> { editor.execCommand("italic");  bridge.pushChange(); });
        underlineBtn.setOnAction(e -> { editor.execCommand("underline"); bridge.pushChange(); });

        // ── Font size ────────────────────────────────────────────
        ComboBox<String> fontSizeBox = new ComboBox<>();
        fontSizeBox.getItems().addAll("10","12","14","16","18","24","32","48");
        fontSizeBox.setValue("14");
        fontSizeBox.setPrefWidth(64);
        fontSizeBox.setStyle(
            "-fx-font-size: 12px;" +
            "-fx-background-color: white;" +
            "-fx-border-color: " + BORDER + ";" +
            "-fx-border-radius: 5px;" +
            "-fx-background-radius: 5px;" +
            "-fx-text-fill: " + TEXT + ";" +
            "-fx-padding: 2 4 2 4;"
        );
        fontSizeBox.setOnAction(e -> {
            String size = fontSizeBox.getValue();
            editor.getEngine().executeScript(
                "document.execCommand('fontSize', false, '7');" +
                "document.querySelectorAll('font[size=\"7\"]').forEach(function(s){" +
                "  s.removeAttribute('size');" +
                "  s.style.fontSize='" + size + "px';" +
                "});"
            );
            bridge.pushChange();
        });

        // ── Table ────────────────────────────────────────────────
        Button tableBtn = makeSecondaryBtn("⊞ Table");
        tableBtn.setOnAction(e -> {
            editor.getEngine().executeScript(
                "document.execCommand('insertHTML', false, '" +
                "<table><tr><td>Cell 1</td><td>Cell 2</td></tr>" +
                "<tr><td>Cell 3</td><td>Cell 4</td></tr></table><br>');"
            );
            bridge.pushChange();
        });

        // ── Image ────────────────────────────────────────────────
        Button imageBtn = makeSecondaryBtn("🖼 Image");
        imageBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Insert Image");
            fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Images","*.png","*.jpg","*.jpeg","*.gif")
            );
            File file = fc.showOpenDialog(stage);
            if (file != null) {
                editor.execCommand("insertImage", file.toURI().toString());
                bridge.pushChange();
            }
        });

        // ── Open .docx ───────────────────────────────────────────
        Button openDocx = makeSecondaryBtn("📂 Open");
        openDocx.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Open DOCX");
            fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Word Document", "*.docx")
            );
            File file = fc.showOpenDialog(stage);
            if (file != null) {
                editor.setContent(DocumentService.loadDocx(file));
                bridge.pushChange();
            }
        });

        // ── Save .docx ───────────────────────────────────────────
        Button saveDocx = makeSecondaryBtn("⬇ .docx");
        saveDocx.setOnAction(e -> {
            File file = showSaveDialog(stage, "Save as DOCX", "Word Document", "*.docx");
            if (file != null) DocumentService.saveDocx(editor.getContent(), file);
        });

        // ── Save .pdf ────────────────────────────────────────────
        Button savePdf = makeSecondaryBtn("⬇ .pdf");
        savePdf.setOnAction(e -> {
            File file = showSaveDialog(stage, "Save as PDF", "PDF File", "*.pdf");
            if (file != null) DocumentService.savePdf(editor.getContent(), file);
        });

        // ── Zoom controls ────────────────────────────────────────
        Button zoomOut = makeIconBtn("−");
        Button zoomIn  = makeIconBtn("+");
        Label  zoomPct = new Label("100%");
        zoomPct.setStyle(
            "-fx-font-size: 11px;" +
            "-fx-text-fill: " + MUTED + ";" +
            "-fx-min-width: 38px;" +
            "-fx-alignment: center;" +
            "-fx-font-weight: 500;"
        );
        editor.setZoomLabel(zoomPct);
        zoomOut.setOnAction(e -> editor.adjustZoom(-0.1));
        zoomIn.setOnAction(e ->  editor.adjustZoom( 0.1));

        HBox zoomGroup = new HBox(0, zoomOut, zoomPct, zoomIn);
        zoomGroup.setAlignment(Pos.CENTER);
        zoomGroup.setStyle(
            "-fx-background-color: white;" +
            "-fx-border-color: " + BORDER + ";" +
            "-fx-border-radius: 6px;" +
            "-fx-background-radius: 6px;"
        );
        zoomGroup.setPadding(new Insets(0, 2, 0, 2));

        // ── History button ───────────────────────────────────────
        Button historyBtn = makeSecondaryBtn("🕐 History");
        historyBtn.setOnAction(e -> new VersionHistoryScreen(bridge).show());

        // ── Editing indicator pill ───────────────────────────────
        Label editingPill = editor.getEditingLabel();

        // ── Spacer (pushes right-side items to the right) ────────
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // ── Right-side: Save + History ───────────────────────────
        Button saveBtn = makePrimaryBtn("💾 Save");
        saveBtn.setOnAction(e -> bridge.pushChange());

        // ── Assemble into a single HBox inside the toolbar ───────
        HBox left = new HBox(6,
            brand,
            makeDivider(),
            titleLabel,
            makeDivider(),
            boldBtn, italicBtn, underlineBtn,
            makeDivider(),
            fontSizeBox,
            makeDivider(),
            tableBtn, imageBtn, openDocx,
            makeDivider(),
            saveDocx, savePdf
        );
        left.setAlignment(Pos.CENTER_LEFT);

        HBox right = new HBox(6,
            zoomGroup,
            makeDivider(),
            editingPill,
            makeDivider(),
            historyBtn,
            saveBtn
        );
        right.setAlignment(Pos.CENTER_RIGHT);

        HBox container = new HBox(left, spacer, right);
        container.setAlignment(Pos.CENTER);
        container.setPadding(new Insets(0, 8, 0, 8));
        HBox.setHgrow(spacer, Priority.ALWAYS);

        toolbar = new ToolBar(container);
        toolbar.setPadding(new Insets(5, 0, 5, 0));
        toolbar.setStyle(
            "-fx-background-color: white;" +
            "-fx-border-color: transparent transparent " + BORDER + " transparent;" +
            "-fx-border-width: 0 0 1 0;" +
            "-fx-padding: 5 8 5 8;"
        );
    }

    public ToolBar getToolbar() { return toolbar; }

    // ── Button factories ─────────────────────────────────────────

    /** Bold / Italic / Underline toggle-style buttons */
    private Button makeFormatBtn(String label, String cmd, String extraStyle) {
        Button btn = new Button(label);
        String base =
            "-fx-font-size: 13px;" +
            "-fx-min-width: 28px; -fx-min-height: 28px;" +
            "-fx-background-color: white;" +
            "-fx-border-color: " + BORDER + ";" +
            "-fx-border-radius: 5px;" +
            "-fx-background-radius: 5px;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 0 6 0 6;" +
            extraStyle + ";";
        btn.setStyle(base + "-fx-text-fill: " + TEXT + ";");
        btn.setOnMouseEntered(e -> btn.setStyle(base +
            "-fx-text-fill: " + PURPLE + ";" +
            "-fx-border-color: " + PURPLE + ";" +
            "-fx-background-color: " + PURPLE_TINT + ";"));
        btn.setOnMouseExited(e  -> btn.setStyle(base + "-fx-text-fill: " + TEXT + ";"));
        return btn;
    }

    /** Ghost/secondary buttons (Table, Image, Open, Save .docx etc.) */
    private Button makeSecondaryBtn(String label) {
        Button btn = new Button(label);
        String base =
            "-fx-font-size: 12px;" +
            "-fx-background-color: white;" +
            "-fx-border-color: " + BORDER + ";" +
            "-fx-border-radius: 5px;" +
            "-fx-background-radius: 5px;" +
            "-fx-text-fill: " + TEXT + ";" +
            "-fx-cursor: hand;" +
            "-fx-padding: 4 10 4 10;";
        btn.setStyle(base);
        btn.setOnMouseEntered(e -> btn.setStyle(base
            .replace("-fx-text-fill: " + TEXT, "-fx-text-fill: " + PURPLE)
            .replace("-fx-border-color: " + BORDER, "-fx-border-color: " + PURPLE)
            .replace("-fx-background-color: white", "-fx-background-color: " + PURPLE_TINT)));
        btn.setOnMouseExited(e  -> btn.setStyle(base));
        return btn;
    }

    /** Filled primary button (Save) */
    private Button makePrimaryBtn(String label) {
        Button btn = new Button(label);
        String base =
            "-fx-font-size: 12px;" +
            "-fx-background-color: " + PURPLE + ";" +
            "-fx-border-color: transparent;" +
            "-fx-border-radius: 6px;" +
            "-fx-background-radius: 6px;" +
            "-fx-text-fill: white;" +
            "-fx-font-weight: bold;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 5 14 5 14;";
        btn.setStyle(base);
        btn.setOnMouseEntered(e -> btn.setStyle(base
            .replace(PURPLE, PURPLE_HOVER)));
        btn.setOnMouseExited(e  -> btn.setStyle(base));
        return btn;
    }

    /** Small icon-only button (zoom − / +) */
    private Button makeIconBtn(String symbol) {
        Button btn = new Button(symbol);
        String base =
            "-fx-font-size: 14px;" +
            "-fx-min-width: 26px; -fx-min-height: 26px;" +
            "-fx-background-color: transparent;" +
            "-fx-border-color: transparent;" +
            "-fx-text-fill: " + MUTED + ";" +
            "-fx-cursor: hand;" +
            "-fx-padding: 0 4 0 4;";
        btn.setStyle(base);
        btn.setOnMouseEntered(e -> btn.setStyle(base
            .replace("-fx-text-fill: " + MUTED, "-fx-text-fill: " + PURPLE)));
        btn.setOnMouseExited(e  -> btn.setStyle(base));
        return btn;
    }

    /** Thin vertical divider to replace Separator */
    private Region makeDivider() {
        Region div = new Region();
        div.setMinWidth(1);
        div.setMaxWidth(1);
        div.setPrefHeight(20);
        div.setStyle("-fx-background-color: " + BORDER + ";");
        HBox.setMargin(div, new Insets(0, 2, 0, 2));
        return div;
    }

    // ── Helper ───────────────────────────────────────────────────
    private File showSaveDialog(Stage stage, String title, String desc, String ext) {
        FileChooser fc = new FileChooser();
        fc.setTitle(title);
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(desc, ext));
        return fc.showSaveDialog(stage);
    }
}