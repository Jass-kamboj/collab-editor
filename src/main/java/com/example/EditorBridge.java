package com.example;

import com.google.gson.JsonObject;
import javafx.application.Platform;
import com.google.gson.JsonParser;


public class EditorBridge {

    private final EditorPane editorPane;
    private EditorClient client;
    private boolean applyingRemote = false;
    private final String username;

    public EditorBridge(EditorPane editorPane, String username) {
        this.editorPane = editorPane;
        this.username = username;
    }

    /**
     * Called from JS (on input debounce — every 300ms after user stops typing).
     * Sends the current HTML to the WebSocket server.
     */
    public void onContentChanged(String html) {
        Platform.runLater(() -> {
            if (!applyingRemote && client != null && client.isOpen()) {
                JsonObject msg = new JsonObject();
                msg.addProperty("user", username);  // ← new
                msg.addProperty("html", html);       // ← new
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
             applyingRemote = true; 
             editorPane.setContent(html);  
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
            client.send(msg.toString());
        }
    }

    public void connectWebSocket() {
        try {
            client = new EditorClient("ws://localhost:8887", this);
            client.connect();
            System.out.println("WebSocket connecting...");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}