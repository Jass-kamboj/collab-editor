package com.example;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {

    private static final String URL      = "jdbc:mysql://localhost:3306/collabeditor";
    private static final String USER     = "root";
    private static final String PASSWORD = "Kamboj@14767";

    private static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    // ── Auth ─────────────────────────────────────────────────────
    public boolean createUser(String username, String password) {
        String sql = "INSERT INTO users (username, password) VALUES (?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, password);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean validateUser(String username, String password) {
        String sql = "SELECT id FROM users WHERE username = ? AND password = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, password);
            return stmt.executeQuery().next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // ── Init ─────────────────────────────────────────────────────
    public static void initialize() {
        String createUsers = "CREATE TABLE IF NOT EXISTS users ("
                + "id INT AUTO_INCREMENT PRIMARY KEY, "
                + "username VARCHAR(50) UNIQUE NOT NULL, "
                + "password VARCHAR(255) NOT NULL, "
                + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)";

        String createDocs = "CREATE TABLE IF NOT EXISTS documents ("
                + "id INT AUTO_INCREMENT PRIMARY KEY, "
                + "title VARCHAR(255) NOT NULL DEFAULT 'Untitled Document', "
                + "content LONGTEXT, "
                + "version INT DEFAULT 0, "
                + "created_by VARCHAR(50), "
                + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)";

        String createHistory = "CREATE TABLE IF NOT EXISTS document_history ("
                + "id INT AUTO_INCREMENT PRIMARY KEY, "
                + "doc_id INT, "
                + "content LONGTEXT, "
                + "version INT, "
                + "changed_by VARCHAR(50), "
                + "saved_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)";

        try (Connection conn = getConnection()) {
            conn.createStatement().executeUpdate(createUsers);
            conn.createStatement().executeUpdate(createDocs);
            conn.createStatement().executeUpdate(createHistory);
            System.out.println("Database initialized.");
        } catch (SQLException e) {
            System.err.println("DB init failed: " + e.getMessage());
        }
    }

    // ── Document CRUD ─────────────────────────────────────────────
    public static int createDocument(String title, String createdBy) {
        String sql = "INSERT INTO documents (title, content, version, created_by) "
                   + "VALUES (?, '', 0, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, title);
            stmt.setString(2, createdBy);
            stmt.executeUpdate();
            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            System.err.println("Create doc failed: " + e.getMessage());
        }
        return -1;
    }

    public static List<int[]> listDocuments() {
        // returns list of [id, title] as int[] — we use Object[] below
        return new ArrayList<>(); // placeholder — see listDocumentsFull()
    }

    public static List<String[]> listDocumentsFull() {
        // returns [id, title, created_by, created_at]
        List<String[]> docs = new ArrayList<>();
        String sql = "SELECT id, title, created_by, created_at FROM documents ORDER BY created_at DESC";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                docs.add(new String[]{
                    String.valueOf(rs.getInt("id")),
                    rs.getString("title"),
                    rs.getString("created_by"),
                    rs.getString("created_at")
                });
            }
        } catch (SQLException e) {
            System.err.println("List docs failed: " + e.getMessage());
        }
        return docs;
    }

    public static boolean renameDocument(int docId, String newTitle) {
        String sql = "UPDATE documents SET title = ? WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newTitle);
            stmt.setInt(2, docId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Rename failed: " + e.getMessage());
            return false;
        }
    }

    public static boolean deleteDocument(int docId) {
        String sql = "DELETE FROM documents WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, docId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Delete doc failed: " + e.getMessage());
            return false;
        }
    }

    // ── Content ───────────────────────────────────────────────────
    public static String loadContent(int docId) {
        String sql = "SELECT content FROM documents WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, docId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getString("content") != null ? rs.getString("content") : "";
        } catch (SQLException e) {
            System.err.println("Load failed: " + e.getMessage());
        }
        return "";
    }

    public static void saveContent(int docId, String html, String changedBy) {
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);

            int currentVersion = 0;
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT version FROM documents WHERE id = ?")) {
                stmt.setInt(1, docId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) currentVersion = rs.getInt("version");
            }

            int newVersion = currentVersion + 1;

            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE documents SET content = ?, version = ? WHERE id = ?")) {
                stmt.setString(1, html);
                stmt.setInt(2, newVersion);
                stmt.setInt(3, docId);
                stmt.executeUpdate();
            }

            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO document_history (doc_id, content, version, changed_by) "
                  + "VALUES (?, ?, ?, ?)")) {
                stmt.setInt(1, docId);
                stmt.setString(2, html);
                stmt.setInt(3, newVersion);
                stmt.setString(4, changedBy);
                stmt.executeUpdate();
            }

            conn.commit();
            System.out.println("Saved doc " + docId + " v" + newVersion + " by " + changedBy);

        } catch (SQLException e) {
            System.err.println("Save failed: " + e.getMessage());
            try { if (conn != null) conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
        } finally {
            try { if (conn != null) conn.close(); } catch (SQLException ex) { ex.printStackTrace(); }
        }
    }

    public static int getCurrentVersion(int docId) {
        String sql = "SELECT version FROM documents WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, docId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("version");
        } catch (SQLException e) {
            System.err.println("Version check failed: " + e.getMessage());
        }
        return 0;
    }
}