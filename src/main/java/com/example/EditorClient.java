package com.example;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import com.google.gson.JsonObject;

import java.net.URI;

public class EditorClient extends WebSocketClient {

    private final EditorBridge bridge;
    private final int docId;

    public EditorClient(String serverUri, EditorBridge bridge, int docId) throws Exception {
        super(new URI(serverUri));
        this.bridge = bridge;
        this.docId = docId;
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        JsonObject join = new JsonObject();
        join.addProperty("type", "join");
        join.addProperty("docId", docId);
        send(join.toString());
        System.out.println("Connected to server, joined doc " + docId);
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
        public void sendPageEdit(int pageIndex, String html, String user) {
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "page_edit");
        msg.addProperty("pageIndex", pageIndex);
        msg.addProperty("html", html);
        msg.addProperty("user", user);
        send(msg.toString());
    }

    public void requestPageSwitch(int pageIndex) {
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "page_switch");
        msg.addProperty("pageIndex", pageIndex);
        send(msg.toString());
    }

    public void sendPageAdd(int pageIndex) {
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "page_add");
        msg.addProperty("pageIndex", pageIndex);
        send(msg.toString());
    }
}