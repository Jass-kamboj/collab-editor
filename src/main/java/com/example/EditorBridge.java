package com.example;

import javafx.application.Platform;

public class EditorBridge {

    private final EditorPane editorPane;
    private EditorClient client;
    private boolean applyingRemote = false;

    public EditorBridge(EditorPane editorPane) {
        this.editorPane = editorPane;
    }

    /**
     * Called from JS (on input debounce — every 300ms after user stops typing).
     * Sends the current HTML to the WebSocket server.
     */
    public void onContentChanged(String html) {
        Platform.runLater(() -> {
            if (!applyingRemote && client != null && client.isOpen()) {
                client.send(html);
            }
        });
    }

    /**
     * Called by EditorClient when server broadcasts a peer's change.
     * Applies the incoming HTML to the editor without triggering another send.
     */
    public void applyRemoteChange(String html) {
        Platform.runLater(() -> {
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
            client.send(editorPane.getContent());
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