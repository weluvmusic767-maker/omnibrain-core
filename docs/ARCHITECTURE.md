---
project: Android MCP Platform App
component: OmniBrain Module
architecture_version: 1.0.0
target_platform: Android (Kotlin / Jetpack Compose / Ktor)
cloud_backend: Cloudflare Workers + KV Storage
format_spec: Hybrid (YAML Frontmatter + Markdown)
---

# Android MCP Platform Architecture

## Overview
This native Android app hosts an embedded Ktor MCP server providing various tools and system extensions. One core tool module is **OmniBrain**, a shared memory engine that stores context locally and allows sharing snapshots with non-MCP/web-browsing models via Cloudflare KV web links.

---

## 1. OmniBrain Tool Payload Specification (Hybrid Format)

When the OmniBrain share feature is triggered, it exports context using YAML frontmatter for programmatic metadata followed by scannable Markdown:

\`\`\`yaml
---
session_id: "session_12345"
timestamp: "YYYY-MM-DD HH:MM:SS"
last_active_model: "ChatGPT-4o"
current_task: "Task description"
tags: [omnibrain, sync, snapshot]
---

# Current Task & Focus
> Description of active work

# Recent Architectural Decisions
* Decision 1
* Decision 2

# Context Notes & Memory State
Raw context notes and detailed project history...
\`\`\`

---

## 2. Cloudflare Worker Script (`src/index.js`)

This handles uploading the hybrid snapshot from the Android app to Cloudflare KV and serving clean plain text to web-browsing AIs:

\`\`\`javascript
export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    // ROUTE 1: Android App Upload (POST /upload)
    if (request.method === "POST" && url.pathname === "/upload") {
      const authHeader = request.headers.get("Authorization");
      if (authHeader !== `Bearer ${env.SECRET_AUTH_KEY}`) {
        return new Response("Unauthorized", { status: 401 });
      }

      const hybridMarkdown = await request.text();
      const token = Math.random().toString(36).substring(2, 10);

      // Save to KV with 24-hour expiration (86,400 seconds)
      await env.BRAIN_KV.put(`share:${token}`, hybridMarkdown, { expirationTtl: 86400 });

      const shareUrl = `https://${url.hostname}/read/${token}`;
      return new Response(JSON.stringify({ shareUrl }), {
        headers: { "Content-Type": "application/json" }
      });
    }

    // ROUTE 2: Web-Browsing AI Reader (GET /read/{token})
    if (request.method === "GET" && url.pathname.startsWith("/read/")) {
      const token = url.pathname.split("/")[2];
      const snapshot = await env.BRAIN_KV.get(`share:${token}`);

      if (!snapshot) {
        return new Response("OmniBrain snapshot expired or invalid.", { status: 404 });
      }

      return new Response(snapshot, {
        headers: { "Content-Type": "text/plain; charset=utf-8" }
      });
    }

    return new Response("OmniBrain Cloud Worker Active", { status: 200 });
  }
};
\`\`\`

---

## 3. Kotlin Implementation Components

### A. Exporter Class (`OmniBrainExporter.kt`)

\`\`\`kotlin
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object OmniBrainExporter {

    fun generateHybridSnapshot(
        activeModel: String,
        currentTask: String,
        decisions: List<String>,
        markdownNotes: String
    ): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val sessionId = "session_${System.currentTimeMillis()}"

        return """
        ---
        session_id: $sessionId
        timestamp: "$timestamp"
        last_active_model: "$activeModel"
        current_task: "$currentTask"
        tags: [omnibrain, sync, snapshot]
        ---
        
        # Current Task & Focus
        > $currentTask
        
        # Recent Architectural Decisions
        ${decisions.joinToString("\n") { "* $it" }}
        
        # Context Notes & Memory State
        $markdownNotes
        """.trimIndent()
    }
}
\`\`\`

### B. Cloudflare HTTP Client (`CloudflareUploader.kt`)

\`\`\`kotlin
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

object CloudflareUploader {
    
    fun uploadSnapshot(
        workerUrl: String, 
        secretKey: String, 
        payload: String
    ): String? {
        return try {
            val url = URL("$workerUrl/upload")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $secretKey")
            conn.setRequestProperty("Content-Type", "text/plain")
            conn.doOutput = true

            OutputStreamWriter(conn.outputStream).use { it.write(payload) }

            if (conn.responseCode == 200) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                JSONObject(responseText).getString("shareUrl")
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
\`\`\`

### C. Jetpack Compose UI Button (`OmniBrainShareButton.kt`)

\`\`\`kotlin
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun OmniBrainShareButton(context: Context) {
    val coroutineScope = rememberCoroutineScope()
    var isUploading by remember { mutableStateOf(false) }

    Button(
        onClick = {
            isUploading = true
            coroutineScope.launch(Dispatchers.IO) {
                val snapshot = OmniBrainExporter.generateHybridSnapshot(
                    activeModel = "ChatGPT-4o",
                    currentTask = "Migrating OmniBrain to Native Android App",
                    decisions = listOf(
                        "Cloudflare Workers KV chosen for snapshot hosting",
                        "Adopted YAML frontmatter + Markdown format"
                    ),
                    markdownNotes = "OmniBrain architecture notes updated..."
                )

                val shareUrl = CloudflareUploader.uploadSnapshot(
                    workerUrl = "https://omnibrain-worker.YOUR-SUBDOMAIN.workers.dev",
                    secretKey = "YOUR_SHARED_SECRET_KEY",
                    payload = snapshot
                )

                withContext(Dispatchers.Main) {
                    if (shareUrl != null) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("OmniBrain Share Link", shareUrl)
                        clipboard.setPrimaryClip(clip)
                    }
                    isUploading = false
                }
            }
        },
        enabled = !isUploading
    ) {
        Text(if (isUploading) "Uploading..." else "⚡ Copy OmniBrain Share Link")
    }
}
\`\`\`

---

## 4. System Components & Status

- [x] **OmniBrain Format Spec:** Hybrid YAML + Markdown designed.
- [x] **Cloudflare Share Proxy:** Worker script & KV storage defined.
- [x] **Android Share Action:** Kotlin exporter & Compose share button defined.
- [x] **OmniBrain Local Store:** SQLite FTS5 database helper (`OmniBrainDbHelper.kt`).
- [ ] **App Core MCP Engine:** Ktor HTTP/MCP server routing (`/mcp` + `/share`).
- [ ] **Android App Shell:** Main app UI and `ForegroundService` for background MCP hosting.
