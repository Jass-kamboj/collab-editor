package com.example;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class EditorServer extends WebSocketServer {

    private static final Set<WebSocket> clients =
        Collections.synchronizedSet(new HashSet<>());

    private static String currentDoc = "";

    public EditorServer(int port) {
        super(new InetSocketAddress(port));
    }

    @Override
    public void onStart() {
        System.out.println("Server started on port 8887");
        DatabaseManager.initialize();
        currentDoc = DatabaseManager.loadContent();
        System.out.println("Document loaded. Version: "
            + DatabaseManager.getCurrentVersion());
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        clients.add(conn);
        System.out.println("New client connected: "
            + conn.getRemoteSocketAddress());

        // Wrap stored HTML in JSON before sending to late joiner  ← fixed
        if (currentDoc != null && !currentDoc.isEmpty()) {
            JsonObject init = new JsonObject();
            init.addProperty("user", "server");
            init.addProperty("html", currentDoc);
            conn.send(init.toString());
        }
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        clients.remove(conn);
        System.out.println("Client disconnected: "
            + conn.getRemoteSocketAddress());
    }

    @Override
    public void onMessage(WebSocket sender, String message) {

        // Parse JSON to extract html and user  ← new
        JsonObject msg = JsonParser.parseString(message).getAsJsonObject();
        String html = msg.get("html").getAsString();
        String user = msg.get("user").getAsString();

        // Store only the HTML in memory, not the full JSON  ← fixed
        currentDoc = html;

        // Broadcast full JSON to all other clients (they need user too)
        synchronized (clients) {
            for (WebSocket client : clients) {
                if (client != sender && client.isOpen()) {
                    client.send(message);
                }
            }
        }

        // Save with username  ← fixed
        DatabaseManager.saveContent(html, user);
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("Server error: " + ex.getMessage());
        ex.printStackTrace();
    }

    public static void main(String[] args) throws Exception {
        EditorServer server = new EditorServer(8887);
        server.start();
        System.out.println("Collab Editor Server running on port 8887");
    }
}