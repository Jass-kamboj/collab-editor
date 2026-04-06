package com.example;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class EditorServer extends WebSocketServer {

    // docId → set of clients in that room
    private static final Map<Integer, Set<WebSocket>> rooms = new ConcurrentHashMap<>();

    // docId → latest HTML content
    private static final Map<Integer, String> docContents = new ConcurrentHashMap<>();

    // conn → docId (so we know which room to leave on disconnect)
    private static final Map<WebSocket, Integer> connRoom = new ConcurrentHashMap<>();

    public EditorServer(int port) {
        super(new InetSocketAddress(port));
    }

    @Override
    public void onStart() {
        System.out.println("Server started on port 8887");
        DatabaseManager.initialize();
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        System.out.println("New client connected: " + conn.getRemoteSocketAddress());
        // Client sends a join message first — handled in onMessage
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        Integer docId = connRoom.remove(conn);
        if (docId != null) {
            Set<WebSocket> room = rooms.get(docId);
            if (room != null) room.remove(conn);
        }
        System.out.println("Client disconnected: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onMessage(WebSocket sender, String message) {
        JsonObject msg = JsonParser.parseString(message).getAsJsonObject();
        String type = msg.has("type") ? msg.get("type").getAsString() : "edit";

        // ── Join a document room ──────────────────────────────────
        if (type.equals("join")) {
            int docId = msg.get("docId").getAsInt();
            connRoom.put(sender, docId);
            rooms.computeIfAbsent(docId, k -> Collections.synchronizedSet(new HashSet<>()))
                 .add(sender);

            // Load from DB if not cached
            if (!docContents.containsKey(docId)) {
                docContents.put(docId, DatabaseManager.loadContent(docId));
            }

            // Send current content to the joining client
            String content = docContents.get(docId);
            if (content != null && !content.isEmpty()) {
                JsonObject init = new JsonObject();
                init.addProperty("type", "init");
                init.addProperty("user", "server");
                init.addProperty("html", content);
                sender.send(init.toString());
            }
            return;
        }

        // ── Regular edit ─────────────────────────────────────────
        String html = msg.get("html").getAsString();
        String user = msg.get("user").getAsString();
        Integer docId = connRoom.get(sender);
        if (docId == null) return;

        docContents.put(docId, html);

        // Broadcast to room only
        Set<WebSocket> room = rooms.get(docId);
        if (room != null) {
            synchronized (room) {
                for (WebSocket client : room) {
                    if (client != sender && client.isOpen()) {
                        client.send(message);
                    }
                }
            }
        }

        DatabaseManager.saveContent(docId, html, user);
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("Server error: " + ex.getMessage());
    }

    public static void main(String[] args) throws Exception {
        EditorServer server = new EditorServer(8887);
        server.start();
        System.out.println("Collab Editor Server running on port 8887");
    }
}