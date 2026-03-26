package com.example;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class EditorServer extends WebSocketServer {

    // ── Connected clients ────────────────────────────────────────
    private static final Set<WebSocket> clients =
        Collections.synchronizedSet(new HashSet<>());

    // ── Current document in memory ───────────────────────────────
    private static String currentDoc = "";

    // ── Constructor ──────────────────────────────────────────────
    public EditorServer(int port) {
        super(new InetSocketAddress(port));
    }

    // ── Server start ─────────────────────────────────────────────
    @Override
    public void onStart() {
        System.out.println("Server started on port 8887");

        // Initialize DB and load last saved document
        DatabaseManager.initialize();
        currentDoc = DatabaseManager.loadContent();

        System.out.println("Document loaded. Version: "
            + DatabaseManager.getCurrentVersion());
    }

    // ── New client connects ───────────────────────────────────────
    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        clients.add(conn);
        System.out.println("New client connected: "
            + conn.getRemoteSocketAddress());

        // Send current document to late joiner
        if (currentDoc != null && !currentDoc.isEmpty()) {
            conn.send(currentDoc);
        }
    }

    // ── Client disconnects ────────────────────────────────────────
    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        clients.remove(conn);
        System.out.println("Client disconnected: "
            + conn.getRemoteSocketAddress());
    }

    // ── Message received from a client ────────────────────────────
    @Override
    public void onMessage(WebSocket sender, String message) {

        // Update in-memory document
        currentDoc = message;

        // Broadcast to all OTHER clients
        synchronized (clients) {
            for (WebSocket client : clients) {
                if (client != sender && client.isOpen()) {
                    client.send(message);
                }
            }
        }

        // Save to MySQL
        DatabaseManager.saveContent(message);
    }

    // ── Error handling ────────────────────────────────────────────
    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("Server error: " + ex.getMessage());
        ex.printStackTrace();
    }

    // ── Main — entry point ────────────────────────────────────────
    public static void main(String[] args) throws Exception {
        EditorServer server = new EditorServer(8887);
        server.start();
        System.out.println("Collab Editor Server running on port 8887");
    }
}