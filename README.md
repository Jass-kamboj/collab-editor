# Collab Editor

A real-time collaborative document editor built with JavaFX and WebSocket. Multiple users can open the editor simultaneously and see each other's changes live. Documents are persisted in MySQL and exported to `.docx` and `.pdf`.

**Team:** Jaskarandeep Singh · Aryan Dhiman· Amrinder Singh Sandhu · Krishna

---

## Tech Stack

| Layer | Technology |
|---|---|
| UI | JavaFX 25 + WebView (HTML `contenteditable`) |
| Real-time sync | Java-WebSocket 1.5.3 |
| Database | MySQL + JDBC (mysql-connector-j 8.3.0) |
| Export | Apache POI 5.2.5 (DOCX), iText 5.5.13.3 (PDF) |
| Messaging | Gson 2.10.1 (JSON over WebSocket) |
| Build | Maven, JDK 25 |

---

## Requirements

Before you can run this project, install the following on your machine:

### 1. JDK 25
Download from: https://jdk.java.net/25/

After installing, verify:
```bash
java -version
```
You should see `openjdk 25` or similar.

### 2. Maven
Download from: https://maven.apache.org/download.cgi

After installing, verify:
```bash
mvn -version
```

### 3. MySQL
Download MySQL Community Server from: https://dev.mysql.com/downloads/mysql/

During setup, note your root password. The project currently uses:
- Username: `root`
- Password: `Kamboj@14767`

> **Important:** If your MySQL password is different, update it in `DatabaseManager.java`:
> ```java
> private static final String PASSWORD = "your_password_here";
> ```

---

## Database Setup

Once MySQL is running, create the database:

```sql
CREATE DATABASE collabeditor;
```

Then create the `documents` table:

```sql
USE collabeditor;

CREATE TABLE documents (
    id VARCHAR(50) PRIMARY KEY,
    title VARCHAR(255),
    content LONGTEXT,
    version INT DEFAULT 0,
    last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

> You do **not** need to create `users` or `document_history` manually. The server creates them automatically on first run.

---

## Getting the Project

Clone the repository:

```bash
git clone https://github.com/Jass-kamboj/collab-editor.git
cd collab-editor
```

---

## Running the Project

> **Rule:** Always start the server first, then the app. And always kill any running `java.exe` process before running `mvn clean`.

### Step 1 — Start the server (Terminal 1)

```bash
cd collab-editor
mvn exec:java "-Dexec.mainClass=com.example.EditorServer"
```

You should see:
```
Server started on port 8887
Database initialized.
Document loaded. Version: 0
Collab Editor Server running on port 8887
```

### Step 2 — Start the app (Terminal 2)

Open a second terminal in the same `collab-editor` folder:

```bash
cd collab-editor
mvn javafx:run
```

The login screen will appear. Register a new account with any username and password, or log in if you already have one.

### Step 3 — Collaborate

To test real-time collaboration, open a third terminal and run `mvn javafx:run` again. Log in as a different user. Both editor windows will now sync live.

---

## File Structure

```
src/main/java/com/example/
├── MainApp.java            ← JavaFX entry point, shows login then opens editor
├── LoginScreen.java        ← Login / register window
├── EditorPane.java         ← WebView + HTML editor + JS bridge injection
├── EditorBridge.java       ← JS↔Java bridge, sends/receives JSON over WebSocket
├── EditorClient.java       ← WebSocket client, calls bridge.applyRemoteChange()
├── EditorServer.java       ← WebSocket server, broadcasts changes, saves to DB
├── ToolbarController.java  ← All toolbar buttons (bold, italic, table, image, export)
├── DocumentService.java    ← Save/open .docx and .pdf
└── DatabaseManager.java    ← MySQL JDBC — users, documents, history tables
```

---

## Database Tables

### `documents`
Stores the current live state of each document.

| Column | Type | Description |
|---|---|---|
| id | VARCHAR(50) | Document ID (currently hardcoded as `doc1`) |
| title | VARCHAR(255) | Document title |
| content | LONGTEXT | Latest HTML content |
| version | INT | Auto-increments on every save |
| last_modified | TIMESTAMP | Updated automatically |

### `document_history`
Every save creates a snapshot here — full version history.

| Column | Type | Description |
|---|---|---|
| id | INT | Auto-increment primary key |
| doc_id | VARCHAR(50) | References `documents.id` |
| content | LONGTEXT | HTML snapshot at this version |
| version | INT | Version number |
| changed_by | VARCHAR(50) | Username who made the change |
| saved_at | TIMESTAMP | When this version was saved |

### `users`
Stores registered accounts.

| Column | Type | Description |
|---|---|---|
| id | INT | Auto-increment primary key |
| username | VARCHAR(50) | Unique username |
| password | VARCHAR(255) | Plain text for now (hashing coming soon) |
| created_at | TIMESTAMP | Registration time |

---

## How Real-Time Sync Works

```
User types in editor (WebView contenteditable div)
    ↓
JS debounce fires after 300ms of inactivity
    ↓
window.javaBridge.onContentChanged(html)
    ↓
EditorBridge → sends JSON: { "user": "jass", "html": "<p>...</p>" }
    ↓
EditorServer receives → broadcasts JSON to all other clients
                      → saves html + username to MySQL
    ↓
Other clients receive JSON → EditorBridge.applyRemoteChange()
                           → applyingRemote = true  (prevents echo loop)
                           → editorPane.setContent(html)
                           → applyingRemote = false
```

---

## Features

- Real-time collaboration over WebSocket
- Login and registration system
- Rich text — bold, italic, underline
- Font size selector
- Table insertion
- Image insertion
- Save / open `.docx`
- Save / open `.pdf`
- MySQL persistence — survives server restarts
- Full version history with username tracking
- Late joiners get the full document instantly
- Echo loop prevention

---

## Known Limitations

- Passwords are stored as plain text — hashing will be added soon
- Only one document (`doc1`) exists for now — multiple documents coming soon
- No cursor tracking yet — you cannot see where teammates are typing

---

## Common Problems

**`mvn clean` fails with a delete error**

A `java.exe` process is still running and locking the `target` folder. Open Task Manager, find all `java.exe` processes, end them, then try again. If your project is on OneDrive, OneDrive may also be locking files — try `mvn compile` instead of `mvn clean compile`.

**Login screen doesn't appear / app crashes immediately**

Make sure the server is running first (`EditorServer`) before launching the app.

**`BUILD FAILURE — No plugin found for prefix 'javafx'`**

You are running `mvn javafx:run` from the wrong folder. Make sure you are inside the `collab-editor` subfolder that contains `pom.xml`:
```bash
cd collab-editor   # the inner folder with pom.xml
mvn javafx:run
```

**Port 8887 already in use**

A previous server session is still running. Kill all `java.exe` processes in Task Manager and try again.
