package com.example;

import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;

public class ToolbarController {

    private final ToolBar toolbar;

    public ToolbarController(EditorPane editor, EditorBridge bridge, Stage stage) {

        // ── Formatting Buttons ──────────────────────────────────
        Button boldBtn      = new Button("B");
        Button italicBtn    = new Button("I");
        Button underlineBtn = new Button("U");

        boldBtn.setStyle("-fx-font-weight:bold; -fx-min-width:30px;");
        italicBtn.setStyle("-fx-font-style:italic; -fx-min-width:30px;");
        underlineBtn.setStyle("-fx-underline:true; -fx-min-width:30px;");

        boldBtn.setOnAction(e -> {
            editor.execCommand("bold");
            bridge.pushChange();
        });

        italicBtn.setOnAction(e -> {
            editor.execCommand("italic");
            bridge.pushChange();
        });

        underlineBtn.setOnAction(e -> {
            editor.execCommand("underline");
            bridge.pushChange();
        });

        // ── Font Size ───────────────────────────────────────────
        ComboBox<String> fontSizeBox = new ComboBox<>();
        fontSizeBox.getItems().addAll("10","12","14","16","18","24","32","48");
        fontSizeBox.setValue("14");
        fontSizeBox.setPrefWidth(70);

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

        // ── Table ───────────────────────────────────────────────
        Button tableBtn = new Button("Table");
        tableBtn.setOnAction(e -> {
            editor.getEngine().executeScript(
                "document.execCommand('insertHTML', false, '" +
                "<table>" +
                "<tr><td>Cell 1</td><td>Cell 2</td></tr>" +
                "<tr><td>Cell 3</td><td>Cell 4</td></tr>" +
                "</table><br>');"
            );
            bridge.pushChange();
        });

        // ── Image ───────────────────────────────────────────────
        Button imageBtn = new Button("Image");
        imageBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Insert Image");
            fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Images","*.png","*.jpg","*.jpeg","*.gif")
            );
            File file = fc.showOpenDialog(stage);
            if (file != null) {
                String uri = file.toURI().toString();
                editor.execCommand("insertImage", uri);
                bridge.pushChange();
            }
        });

        // ── Save .docx ──────────────────────────────────────────
        Button saveDocx = new Button("Save .docx");
        saveDocx.setOnAction(e -> {
            File file = showSaveDialog(stage, "Save as DOCX", "Word Document", "*.docx");
            if (file != null) {
                DocumentService.saveDocx(editor.getContent(), file);
            }
        });

        // ── Save .pdf ───────────────────────────────────────────
        Button savePdf = new Button("Save .pdf");
        savePdf.setOnAction(e -> {
            File file = showSaveDialog(stage, "Save as PDF", "PDF File", "*.pdf");
            if (file != null) {
                DocumentService.savePdf(editor.getContent(), file);
            }
        });

        // ── Open .docx ──────────────────────────────────────────
        Button openDocx = new Button("Open .docx");
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
        // ── History ─────────────────────────────────────────────
        Button historyBtn = new Button("History");
        historyBtn.setOnAction(e -> {
            VersionHistoryScreen history = new VersionHistoryScreen(bridge, editor);
            history.show();
        });

        // ── Assemble Toolbar ────────────────────────────────────
        toolbar = new ToolBar(
            boldBtn, italicBtn, underlineBtn,
            new Separator(),
            new Label("Size:"), fontSizeBox,
            new Separator(),
            tableBtn, imageBtn,
            new Separator(),
            saveDocx, savePdf, openDocx,
            new Separator(),
            historyBtn
        );
    }

    public ToolBar getToolbar() { return toolbar; }

    // ── Helper ──────────────────────────────────────────────────
    private File showSaveDialog(Stage stage, String title, String desc, String ext) {
        FileChooser fc = new FileChooser();
        fc.setTitle(title);
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(desc, ext));
        return fc.showSaveDialog(stage);
    }
}