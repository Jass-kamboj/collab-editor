package com.example;

import com.google.gson.JsonObject;
import javafx.application.Platform;
import com.google.gson.JsonParser;


public class EditorBridge {
    private final int docId;
    private final EditorPane editorPane;
    private EditorClient client;
    private boolean applyingRemote = false;
    private final String username;

    public EditorBridge(EditorPane editorPane, String username, int docId) {
        this.editorPane = editorPane;
        this.username = username;
        this.docId = docId;
    }

    /**
     * Called from JS (on input debounce — every 300ms after user stops typing).
     * Sends the current HTML to the WebSocket server.
     */
    public void onContentChanged(String html, int cursorOffset) {
    Platform.runLater(() -> {
        if (!applyingRemote && client != null && client.isOpen()) {
            client.sendPageEdit(editorPane.getCurrentPage(), html, username, cursorOffset);
        }
    });
    }

    /**
     * Called by EditorClient when server broadcasts a peer's change.
     * Applies the incoming HTML to the editor without triggering another send.
     */
    public void applyRemoteChange(String rawMessage) {
    Platform.runLater(() -> {
        JsonObject msg = JsonParser.parseString(rawMessage).getAsJsonObject();
        String type = msg.has("type") ? msg.get("type").getAsString() : "edit";

        if (type.equals("page_content")) {
            int pageIndex = msg.get("pageIndex").getAsInt();
            String html   = msg.get("html").getAsString();
            editorPane.applyPageContent(pageIndex, html);
            return;
        }
        if (type.equals("page_add")) {
            int pageIndex = msg.get("pageIndex").getAsInt();
            editorPane.onPeerAddedPage(pageIndex);
            return;
        }
        if (type.equals("pageMeta")) {
            int count = msg.get("pageCount").getAsInt();
            editorPane.initPages(count);
            return;
        }
        if (type.equals("page_delete")) {
            int pageIndex = msg.get("pageIndex").getAsInt();
            editorPane.onPeerDeletedPage(pageIndex);
            return;
        }

        // ── Regular edit / restore / init / page_edit ─────────
        if (!msg.has("html")) return;
        String html   = msg.get("html").getAsString();
        int cursor    = msg.has("cursor") ? msg.get("cursor").getAsInt() : -1;
        String user   = msg.has("user")   ? msg.get("user").getAsString() : "Someone";

        // For page_edit, cache it always but only render if it's the current page
        if (type.equals("page_edit")) {
            int pageIndex = msg.has("pageIndex") ? msg.get("pageIndex").getAsInt() : 0;
            editorPane.applyPageContent(pageIndex, html);  // always cache
            if (pageIndex != editorPane.getCurrentPage()) return;  // only render if active
        }
        applyingRemote = true;
        editorPane.setContent(html);
        editorPane.showEditingIndicator(user);
        if (cursor >= 0) editorPane.showRemoteCursor(user, cursor);
        applyingRemote = false;
    });
    }

    /**
     * Called by ToolbarController after local mutations like bold, insert table etc.
     * since those don't fire the JS 'input' event.
     */
    public void pushChange() {
        if (!applyingRemote && client != null && client.isOpen()) {
            JsonObject msg = new JsonObject();
            msg.addProperty("user", username);
            msg.addProperty("html", editorPane.getContent());
             msg.addProperty("cursor", 0);
            client.send(msg.toString());
        }
    }
    public void restoreVersion(String html) {
    Platform.runLater(() -> {
        if (client != null && client.isOpen()) {
            JsonObject msg = new JsonObject();
            msg.addProperty("type", "restore");
            msg.addProperty("user", username);
            msg.addProperty("html", html);
            msg.addProperty("cursor", 0);
            applyingRemote = true;
            editorPane.setContent(html);
            applyingRemote = false;
            client.send(msg.toString());
        }
    });
    }
    public int getDocId() { return docId; }

    public void connectWebSocket() {
        try {
            client = new EditorClient("ws://localhost:8887", this, docId);
            client.connect();
            System.out.println("WebSocket connecting...");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void requestPageSwitch(int pageIndex) {
    if (client != null && client.isOpen()) {
        client.requestPageSwitch(pageIndex);
    }
    }

    public void notifyPageAdd(int pageIndex) {
    if (client != null && client.isOpen()) {
        client.sendPageAdd(pageIndex);
    }
    }
    public void notifyPageDelete(int pageIndex) {
    if (client != null && client.isOpen()) {
        com.google.gson.JsonObject msg = new com.google.gson.JsonObject();
        msg.addProperty("type", "page_delete");
        msg.addProperty("pageIndex", pageIndex);
        client.send(msg.toString());
    }
  }
}