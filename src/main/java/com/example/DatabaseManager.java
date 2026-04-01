package com.example;

import java.sql.*;

public class DatabaseManager {

    // ── Connection config ────────────────────────────────────────
    private static final String URL      = "jdbc:mysql://localhost:3306/collabeditor";
    private static final String USER     = "root";       // change to your MySQL username
    private static final String PASSWORD = "Kamboj@14767";           // change to your MySQL password
    private static final String DOC_ID   = "doc1";      // default document ID
    // ── Get connection ───────────────────────────────────────────
    private static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    public boolean createUser(String username, String password) {
    String sql = "INSERT INTO users (username, password) VALUES (?, ?)";
    try (PreparedStatement stmt = connection.prepareStatement(sql)) {
        stmt.setString(1, username);
        stmt.setString(2, password); // plain text for now, hash later
        stmt.executeUpdate();
        return true;
    } catch (SQLException e) {
        // DUPLICATE username hits here — return false
        return false;
    }
}

public boolean validateUser(String username, String password) {
    String sql = "SELECT id FROM users WHERE username = ? AND password = ?";
    try (PreparedStatement stmt = connection.prepareStatement(sql)) {
        stmt.setString(1, username);
        stmt.setString(2, password);
        ResultSet rs = stmt.executeQuery();
        return rs.next(); // true if a row matched
    } catch (SQLException e) {
        e.printStackTrace();
        return false;
    }
}
    // ── Initialize — called once when server starts ───────────────
    // Creates a default document row if none exists
    public static void initialize() {
        String sql = "INSERT IGNORE INTO documents (id, title, content, version) "
                   + "VALUES (?, 'Untitled Document', '', 0)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, DOC_ID);
            stmt.executeUpdate();
            System.out.println("Database initialized.");

        } catch (SQLException e) {
            System.err.println("DB init failed: " + e.getMessage());
        }
    }

    // ── Load latest content from documents table ─────────────────
    public static String loadContent() {
        String sql = "SELECT content FROM documents WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, DOC_ID);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String content = rs.getString("content");
                System.out.println("Document loaded from DB.");
                return content != null ? content : "";
            }

        } catch (SQLException e) {
            System.err.println("DB load failed: " + e.getMessage());
        }
        return "";
    }

    // ── Save content — updates documents + inserts into history ──
    public static void saveContent(String html, String changedBy) {
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false); // start transaction

            // Step 1 — get current version number
            int currentVersion = 0;
            String getVersion = "SELECT version FROM documents WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(getVersion)) {
                stmt.setString(1, DOC_ID);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    currentVersion = rs.getInt("version");
                }
            }

            int newVersion = currentVersion + 1;

            // Step 2 — update documents table with new content
            String updateDoc = "UPDATE documents SET content = ?, version = ? "
                             + "WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(updateDoc)) {
                stmt.setString(1, html);
                stmt.setInt(2, newVersion);
                stmt.setString(3, DOC_ID);
                stmt.executeUpdate();
            }

            // Step 3 — insert snapshot into document_history
            String insertHistory = "INSERT INTO document_history "
                                 + "(doc_id, content, version, changed_by) VALUES (?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(insertHistory)) {
                stmt.setString(1, DOC_ID);
                stmt.setString(2, html);  
                stmt.setInt(3, newVersion);
                stmt.setString(4, changedBy);
                stmt.executeUpdate();
            }

            conn.commit(); // commit both steps together
            System.out.println("Saved to DB. Version: " + newVersion);

        } catch (SQLException e) {
            System.err.println("DB save failed: " + e.getMessage());
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            }
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (SQLException ex) { ex.printStackTrace(); }
            }
        }
    }

    // ── Load a specific version from history ─────────────────────
    public static String loadVersion(int version) {
        String sql = "SELECT content FROM document_history "
                   + "WHERE doc_id = ? AND version = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, DOC_ID);
            stmt.setInt(2, version);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                System.out.println("Loaded version " + version + " from history.");
                return rs.getString("content");
            }

        } catch (SQLException e) {
            System.err.println("DB version load failed: " + e.getMessage());
        }
        return "";
    }

    // ── Get current version number ────────────────────────────────
    public static int getCurrentVersion() {
        String sql = "SELECT version FROM documents WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, DOC_ID);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("version");

        } catch (SQLException e) {
            System.err.println("DB version check failed: " + e.getMessage());
        }
        return 0;
    }
}
