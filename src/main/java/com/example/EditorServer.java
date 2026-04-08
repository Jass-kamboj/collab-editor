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
    
    // pageContents: "docId:pageIndex" → html
    private static final Map<String, String> pageContents = new ConcurrentHashMap<>();

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
            // Send page count so client knows how many pages exist
            int pageCount = DatabaseManager.getPageCount(docId);
            if (pageCount == 0) {
                DatabaseManager.ensurePageExists(docId, 0);
                pageCount = 1;
            }
                JsonObject meta = new JsonObject();
                meta.addProperty("type", "pageMeta");
                meta.addProperty("pageCount", pageCount);
                sender.send(meta.toString());
            return;
        }
        if (type.equals("restore")) {
    String html = msg.get("html").getAsString();
    String user = msg.get("user").getAsString();
    Integer docId = connRoom.get(sender);
    if (docId == null) return;

    docContents.put(docId, html);

    // Notify all clients including sender
    JsonObject notify = new JsonObject();
    notify.addProperty("type", "restore");
    notify.addProperty("user", user);
    notify.addProperty("html", html);
    notify.addProperty("cursor", 0);

    Set<WebSocket> room = rooms.get(docId);
    if (room != null) {
        synchronized (room) {
            for (WebSocket client : room) {
                if (client.isOpen()) {
                    client.send(notify.toString());
                }
            }
        }
    }
    DatabaseManager.saveContent(docId, html, user + " (restored)");
    return;
    }
    if (type.equals("page_edit")) {
        int pageIndex = msg.get("pageIndex").getAsInt();
        String html   = msg.get("html").getAsString();
        String user   = msg.get("user").getAsString();
        Integer docId = connRoom.get(sender);
        if (docId == null) return;

    String key = docId + ":" + pageIndex;
    pageContents.put(key, html);

    // Broadcast to room (exclude sender)
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
    DatabaseManager.savePage(docId, pageIndex, html, user);
    return;
}

    if (type.equals("page_switch")) {
        // One client switched page — send them that page's content
        int pageIndex = msg.get("pageIndex").getAsInt();
        Integer docId = connRoom.get(sender);
        if (docId == null) return;

        DatabaseManager.ensurePageExists(docId, pageIndex);
        String key = docId + ":" + pageIndex;
        String content = pageContents.getOrDefault(key, DatabaseManager.loadPage(docId, pageIndex));
        pageContents.put(key, content);

        JsonObject resp = new JsonObject();
        resp.addProperty("type", "page_content");
        resp.addProperty("pageIndex", pageIndex);
        resp.addProperty("html", content);
        sender.send(resp.toString());
        return;
    }

    if (type.equals("page_add")) {
        Integer docId = connRoom.get(sender);
        if (docId == null) return;
        int newIndex = msg.get("pageIndex").getAsInt();
        DatabaseManager.ensurePageExists(docId, newIndex);

        // Notify whole room
        Set<WebSocket> room = rooms.get(docId);
        if (room != null) {
            synchronized (room) {
                for (WebSocket client : room) {
                    if (client.isOpen()) client.send(message);
                }
            }
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