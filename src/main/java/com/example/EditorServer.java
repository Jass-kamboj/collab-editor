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

    // conflict tracking metadata
    private static final Map<String, String> conflictMeta = new ConcurrentHashMap<>();

    // conn → username mapping
    private static final Map<WebSocket, String> connUser = new ConcurrentHashMap<>();

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

            // Track username for this connection
            if (msg.has("user")) connUser.put(sender, msg.get("user").getAsString());

            rooms.computeIfAbsent(docId, k -> Collections.synchronizedSet(new HashSet<>()))
                 .add(sender);

            // Load from DB if not cached
            if (!docContents.containsKey(docId)) {
                docContents.put(docId, DatabaseManager.loadContent(docId));
            }

            // Send page 0 content to the joining client
            String key = docId + ":0";
            String content = pageContents.getOrDefault(key, DatabaseManager.loadPage(docId, 0));
            pageContents.put(key, content);
            if (content != null && !content.isEmpty()) {
                JsonObject init = new JsonObject();
                init.addProperty("type", "page_content");
                init.addProperty("pageIndex", 0);
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

                // Send existing authorship for page 0
                List<String[]> authors = DatabaseManager.getParagraphAuthors(docId, 0);
                if (!authors.isEmpty()) {
                    JsonObject authMsg = new JsonObject();
                    authMsg.addProperty("type", "authorship_update");
                    authMsg.addProperty("pageIndex", 0);
                    com.google.gson.JsonArray authArr = new com.google.gson.JsonArray();
                    for (String[] a : authors) {
                        JsonObject entry = new JsonObject();
                        entry.addProperty("hash",   a[0]);
                        entry.addProperty("author", a[1]);
                        entry.addProperty("time",   a[2]);
                        authArr.add(entry);
                    }
                    authMsg.add("authors", authArr);
                    sender.send(authMsg.toString());
                }

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

        String key        = docId + ":" + pageIndex;
        String lastHtml   = pageContents.get(key); // what was there BEFORE this edit

        // ── Snapshot base BEFORE any edits if this is a new editing session ──
        String lastEditUser = conflictMeta.get(key + ":lastEditUser");
        String lastEditTime = conflictMeta.get(key + ":lastEditTime");
        long now = System.currentTimeMillis();
        boolean freshSession = lastEditUser == null
            || (now - Long.parseLong(lastEditTime != null ? lastEditTime : "0")) > 30000;

        if (freshSession) {
            // No one was editing — snapshot current as the clean base
            conflictMeta.put(key + ":baseHtml", lastHtml != null ? lastHtml : html);
            conflictMeta.remove(key + ":versionA_user");
            conflictMeta.remove(key + ":versionA_html");
            conflictMeta.remove(key + ":versionB_user");
            conflictMeta.remove(key + ":versionB_html");
        }

        // ── Store this user's latest version separately ───────────
        String slotA_user = conflictMeta.get(key + ":versionA_user");
        if (slotA_user == null) {
            // First user editing — assign them slot A
            conflictMeta.put(key + ":versionA_user", user);
            conflictMeta.put(key + ":versionA_html", html);
        } else if (slotA_user.equals(user)) {
            // Same user updating their version
            conflictMeta.put(key + ":versionA_html", html);
        } else {
            // Different user — assign slot B
            conflictMeta.put(key + ":versionB_user", user);
            conflictMeta.put(key + ":versionB_html", html);
        }

        // ── Broadcast live to others FIRST (before conflict check) ─
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

        // ── Check for true conflict ───────────────────────────────
        String versionA_user = conflictMeta.get(key + ":versionA_user");
        String versionA_html = conflictMeta.get(key + ":versionA_html");
        String versionB_user = conflictMeta.get(key + ":versionB_user");
        String versionB_html = conflictMeta.get(key + ":versionB_html");
        String baseHtml      = conflictMeta.get(key + ":baseHtml");

        boolean twoUsersEditing = versionA_user != null && versionB_user != null
            && versionA_html != null && versionB_html != null
            && !versionA_html.equals(versionB_html);

        boolean recentConflict = lastEditUser != null
            && !lastEditUser.equals(user)
            && lastEditTime != null
            && (now - Long.parseLong(lastEditTime)) < 8000;

        if (twoUsersEditing && recentConflict && baseHtml != null) {
            // Compare both versions against base
            List<String> parasBase = extractParagraphs(baseHtml);
            List<String> parasA    = extractParagraphs(versionA_html);
            List<String> parasB    = extractParagraphs(versionB_html);

            List<String> conflictedParas = new ArrayList<>();
            for (int i = 0; i < parasBase.size(); i++) {
                String base  = parasBase.get(i);
                String fromA = i < parasA.size() ? parasA.get(i) : "";
                String fromB = i < parasB.size() ? parasB.get(i) : "";
                if (!fromA.equals(base) && !fromB.equals(base)
                        && !fromA.equals(fromB)
                        && fromA.length() > 2 && fromB.length() > 2) {
                    conflictedParas.add("• " + base.substring(0, Math.min(60, base.length())));
                }
            }

            if (!conflictedParas.isEmpty()) {
                JsonObject cfMsg = new JsonObject();
                cfMsg.addProperty("type", "conflict");
                cfMsg.addProperty("pageIndex", pageIndex);
                cfMsg.addProperty("versionA", versionA_html);
                cfMsg.addProperty("versionB", versionB_html);
                cfMsg.addProperty("userA", versionA_user);
                cfMsg.addProperty("userB", versionB_user);
                cfMsg.addProperty("conflictSummary",
                    "These paragraphs were edited differently:\n"
                    + String.join("\n", conflictedParas));

                if (room != null) {
                    synchronized (room) {
                        for (WebSocket client : room) {
                            if (client.isOpen()) client.send(cfMsg.toString());
                        }
                    }
                }

                // Clear slots so conflict doesn't re-fire immediately
                conflictMeta.remove(key + ":versionA_user");
                conflictMeta.remove(key + ":versionA_html");
                conflictMeta.remove(key + ":versionB_user");
                conflictMeta.remove(key + ":versionB_html");
            }
        }

        // ── Always update tracking + save ─────────────────────────
        pageContents.put(key, html);
        conflictMeta.put(key + ":lastEditUser", user);
        conflictMeta.put(key + ":lastEditTime", String.valueOf(now));

        DatabaseManager.savePage(docId, pageIndex, html, user);

        // Track authorship
        List<String> paragraphs = extractParagraphs(html);
        for (String para : paragraphs) {
            DatabaseManager.saveParagraphAuthor(docId, pageIndex,
                String.valueOf(jsHashCode(para)), user);
        }
        broadcastAuthorship(docId, pageIndex, room);
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
    
    if (type.equals("comment_add")) {
            Integer docId = connRoom.get(sender);
            if (docId == null) return;
            String author       = msg.get("author").getAsString();
            String selectedText = msg.has("selectedText") ? msg.get("selectedText").getAsString() : "";
            String commentText  = msg.get("commentText").getAsString();
            Integer parentId    = msg.has("parentId") && !msg.get("parentId").isJsonNull()
                                  ? msg.get("parentId").getAsInt() : null;

            int newId = DatabaseManager.addComment(docId, author, selectedText, commentText, parentId);
            if (newId < 0) return;

            // Broadcast to whole room including sender
            JsonObject broadcast = new JsonObject();
            broadcast.addProperty("type", "comment_add");
            broadcast.addProperty("id", newId);
            broadcast.addProperty("author", author);
            broadcast.addProperty("selectedText", selectedText);
            broadcast.addProperty("commentText", commentText);
            if (parentId != null) broadcast.addProperty("parentId", parentId);
            broadcast.addProperty("createdAt", new java.util.Date().toString());

            Set<WebSocket> room = rooms.get(docId);
            if (room != null) {
                synchronized (room) {
                    for (WebSocket client : room) {
                        if (client.isOpen()) client.send(broadcast.toString());
                    }
                }
            }
            return;
        }

        if (type.equals("comment_delete")) {
            Integer docId = connRoom.get(sender);
            if (docId == null) return;
            int commentId   = msg.get("commentId").getAsInt();
            String reqUser  = msg.get("author").getAsString();

            boolean deleted = DatabaseManager.deleteComment(commentId, reqUser);
            if (!deleted) return; // not author — silently reject

            JsonObject broadcast = new JsonObject();
            broadcast.addProperty("type", "comment_delete");
            broadcast.addProperty("commentId", commentId);

            Set<WebSocket> room = rooms.get(docId);
            if (room != null) {
                synchronized (room) {
                    for (WebSocket client : room) {
                        if (client.isOpen()) client.send(broadcast.toString());
                    }
                }
            }
            return;
        }

        if (type.equals("comments_load")) {
            Integer docId = connRoom.get(sender);
            if (docId == null) return;
            java.util.List<String[]> comments = DatabaseManager.getComments(docId);
            JsonObject resp = new JsonObject();
            resp.addProperty("type", "comments_load");
            com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
            for (String[] c : comments) {
                JsonObject obj = new JsonObject();
                obj.addProperty("id",           c[0]);
                obj.addProperty("author",        c[1]);
                obj.addProperty("selectedText",  c[2]);
                obj.addProperty("commentText",   c[3]);
                obj.addProperty("parentId",      c[4]);
                obj.addProperty("createdAt",     c[5]);
                arr.add(obj);
            }
            resp.add("comments", arr);
            sender.send(resp.toString());
            return;
        }

        if (type.equals("conflict_resolve")) {
            Integer docId = connRoom.get(sender);
            if (docId == null) return;
            int pageIndex   = msg.get("pageIndex").getAsInt();
            String resolved = msg.get("resolvedHtml").getAsString();
            String user     = msg.get("user").getAsString();

            String key = docId + ":" + pageIndex;
            pageContents.put(key, resolved);

            // Clear conflict meta
            conflictMeta.remove(key + ":lastEditUser");
            conflictMeta.remove(key + ":lastEditTime");
            conflictMeta.remove(key + ":baseHtml");
            conflictMeta.remove(key + ":versionA_user");
            conflictMeta.remove(key + ":versionA_html");
            conflictMeta.remove(key + ":versionB_user");
            conflictMeta.remove(key + ":versionB_html");

            // Broadcast resolved version to all
            JsonObject broadcast = new JsonObject();
            broadcast.addProperty("type", "page_edit");
            broadcast.addProperty("pageIndex", pageIndex);
            broadcast.addProperty("html", resolved);
            broadcast.addProperty("user", user + " (resolved conflict)");

            Set<WebSocket> room = rooms.get(docId);
            if (room != null) {
                synchronized (room) {
                    for (WebSocket client : room) {
                        if (client.isOpen()) client.send(broadcast.toString());
                    }
                }
            }
            DatabaseManager.savePage(docId, pageIndex, resolved, user + " (resolved)");
            return;
        }

        if (type.equals("page_delete")) {
            Integer docId = connRoom.get(sender);
            if (docId == null) return;
            int pageIndex = msg.get("pageIndex").getAsInt();
            DatabaseManager.deletePage(docId, pageIndex);

            Set<WebSocket> room = rooms.get(docId);
            if (room != null) {
                synchronized (room) {
                    for (WebSocket client : room) {
                        if (client != sender && client.isOpen()) client.send(message);
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

    /**
     * Returns how many characters differ between two HTML strings.
     * Simple but effective — ignores minor cursor/span changes.
     */ 
     private List<String> extractParagraphs(String html) {
        List<String> result = new ArrayList<>();
        // Split on block-level closing tags
        String[] blocks = html.split("(?i)</(p|div|h[1-6]|li|td|br)>|<br\\s*/?>"); 
        for (String block : blocks) {
            String text = block.replaceAll("<[^>]*>", "")
                               .replaceAll("&nbsp;", " ")
                               .replaceAll("\\s+", " ")
                               .trim();
            if (text.length() >= 3) result.add(text);
        }
        // Also handle case where entire content is plain text without tags
        if (result.isEmpty()) {
            String plain = html.replaceAll("<[^>]*>", "").trim();
            if (plain.length() >= 3) result.add(plain);
        }
        return result;
    }

    /**
     * Replicates JavaScript's hashCode so server and client produce identical hashes.
     * Matches: for(var h=0,i=0;i<str.length;i++) h=(Math.imul(31,h)+charCodeAt(i))|0
     */
    private int jsHashCode(String str) {
        int h = 0;
        for (int i = 0; i < str.length(); i++) {
            h = (31 * h + str.charAt(i));
            // Replicate JS (x | 0) — force 32-bit signed integer overflow
            h = (int)(h & 0xFFFFFFFFL);
            if (h > Integer.MAX_VALUE) h = (int)(h - 0x100000000L);
        }
        return h;
    }

    private void broadcastAuthorship(int docId, int pageIndex, Set<WebSocket> room) {
        List<String[]> authors = DatabaseManager.getParagraphAuthors(docId, pageIndex);
        if (authors.isEmpty()) return;

        JsonObject msg = new JsonObject();
        msg.addProperty("type", "authorship_update");
        msg.addProperty("pageIndex", pageIndex);

        com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
        for (String[] a : authors) {
            JsonObject entry = new JsonObject();
            entry.addProperty("hash",    a[0]);
            entry.addProperty("author",  a[1]);
            entry.addProperty("time",    a[2]);
            arr.add(entry);
        }
        msg.add("authors", arr);

        if (room != null) {
            synchronized (room) {
                for (WebSocket client : room) {
                    if (client.isOpen()) client.send(msg.toString());
                }
            }
        }
    }

}