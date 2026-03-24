package com.example;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.Collection;

public class EditorServer extends WebSocketServer {

    private static final int PORT = 8887;

    //  This is the fix — server remembers the latest document
    private String currentDocument = "";

    public EditorServer() {
        super(new InetSocketAddress(PORT));
    }

    // When a new user joins, send them the current d~~ocument
    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        System.out.println("New user connected: " + conn.getRemoteSocketAddress());

        // Send the latest document to the new user immediately
        if (!currentDocument.isEmpty()) {
            conn.send(currentDocument);
        }
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        System.out.println("User disconnected: " + conn.getRemoteSocketAddress());
    }

    // Save the latest document then broadcast to everyone else
    @Override
    public void onMessage(WebSocket sender, String message) {
        System.out.println("Received change, broadcasting to all...");

        // Remember the latest version
        currentDocument = message;

        // Send to everyone except the sender
        Collection<WebSocket> allConnections = getConnections();
        for (WebSocket client : allConnections) {
            if (client != sender) {
                client.send(message);
            }
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.out.println("Error: " + ex.getMessage());
    }

    @Override
    public void onStart() {
        System.out.println("Server started on port " + PORT);
        System.out.println("Waiting for users to connect...");
    }
}