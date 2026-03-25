package com.example;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import java.net.URI;

public class EditorClient extends WebSocketClient {

    private final EditorBridge bridge;

    public EditorClient(String serverUri, EditorBridge bridge) throws Exception {
        super(new URI(serverUri));
        this.bridge = bridge;
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        System.out.println("Connected to server");
    }

    @Override
    public void onMessage(String message) {
        bridge.applyRemoteChange(message);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("Disconnected: " + reason);
    }

    @Override
    public void onError(Exception ex) {
        ex.printStackTrace();
    }
}