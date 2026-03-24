package com.example;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.Collection;

public class EditorServer extends WebSocketServer {

    // Port number — all clients will connect to this
    private static final int PORT = 8887;

    public EditorServer() {
        super(new InetSocketAddress(PORT));
    }

    // Called when a new user connects
    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        System.out.println("New user connected: " + conn.getRemoteSocketAddress());
    }

    // Called when a user disconnects
    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        System.out.println("User disconnected: " + conn.getRemoteSocketAddress());
    }

    // Called when a user sends a message (text change)
    // This is the most important method
    @Override
    public void onMessage(WebSocket sender, String message) {
        System.out.println("Received change, broadcasting to all...");

        // Send the change to EVERYONE except the person who sent it
        Collection<WebSocket> allConnections = getConnections();
        for (WebSocket client : allConnections) {
            if (client != sender) {
                client.send(message);
            }
        }
    }

    // Called if something goes wrong
    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.out.println("Error: " + ex.getMessage());
    }

    // Called when server fully starts
    @Override
    public void onStart() {
        System.out.println("Server started on port " + PORT);
        System.out.println("Waiting for users to connect...");
    }
}