package com.example;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;

public class EditorClient extends WebSocketClient {

    // This is a "callback" — we will connect it to the UI later
    // When server sends a message, this interface tells the UI to update
    public interface OnMessageReceived {
        void onMessage(String message);
    }

    private OnMessageReceived messageListener;

    // Constructor — takes the server address
    public EditorClient(String serverUri, OnMessageReceived listener) throws Exception {
        super(new URI(serverUri));
        this.messageListener = listener;
    }

    // Called when connection to server is established
    @Override
    public void onOpen(ServerHandshake handshake) {
        System.out.println("Connected to server successfully!");
    }

    // Called when SERVER sends us a message (someone else typed something)
    // This triggers the UI to update
    @Override
    public void onMessage(String message) {
        System.out.println("Received update from server: " + message);

        // Tell the UI to update with the new text
        if (messageListener != null) {
            messageListener.onMessage(message);
        }
    }

    // Called when connection drops
    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("Disconnected from server. Reason: " + reason);
    }

    // Called if something goes wrong
    @Override
    public void onError(Exception ex) {
        System.out.println("Connection error: " + ex.getMessage());
    }

    // Call this method to send your typing to the server
    public void sendChange(String text) {
        if (isOpen()) {
            send(text);
        }
    }
}