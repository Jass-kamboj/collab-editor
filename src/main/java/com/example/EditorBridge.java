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
    public void onContentChanged(String html,int cursorOffset) {
        Platform.runLater(() -> {
            if (!applyingRemote && client != null && client.isOpen()) {
                JsonObject msg = new JsonObject();
                msg.addProperty("user", username);  // ← new
                msg.addProperty("html", html);       // ← new
                msg.addProperty("cursor", cursorOffset);
                client.send(msg.toString());          // ← changed
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
            String html = msg.get("html").getAsString();
            int cursor    = msg.has("cursor") ? msg.get("cursor").getAsInt()     : -1;
            String user = msg.has("user") ? msg.get("user").getAsString() : "Someone";

            System.out.println("DEBUG remote: user=" + user + " cursor=" + cursor);

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

    public void connectWebSocket() {
        try {
            client = new EditorClient("ws://localhost:8887", this, docId);
            client.connect();
            System.out.println("WebSocket connecting...");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}