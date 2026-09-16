---
project: OmniBrain Android Core
architecture_version: 1.0.0
target_platform: Android (Kotlin / Jetpack Compose / Ktor)
cloud_backend: Cloudflare Workers + KV Storage
format_spec: Hybrid (YAML Frontmatter + Markdown)
---

# OmniBrain Core Architecture & Roadmap

## Overview
OmniBrain is moving from Termux shell scripts to a native Android application. It serves local AI models via an embedded Ktor MCP server and non-MCP/web-browsing models via temporary Cloudflare KV web links.

## Payload Specification (Hybrid Format)
All shared memory dumps must adhere to YAML frontmatter at the top for programmatic metadata, followed by scannable Markdown for narrative context:

\`\`\`yaml
---
session_id: "session_12345"
timestamp: "YYYY-MM-DD HH:MM:SS"
last_active_model: "ChatGPT-4o"
current_task: "Task description"
tags: [omnibrain, sync, snapshot]
---
# Current Task & Focus
...
# Recent Architectural Decisions
...
# Context Notes & Memory State
...
\`\`\`

## System Components & Status
- [x] **Format Spec:** Hybrid YAML + Markdown designed.
- [x] **Cloud Backend:** Cloudflare Worker + KV Storage script defined.
- [x] **Android Client Exporter:** Kotlin snapshot compiler & Compose UI specs complete.
- [ ] **Local Storage:** SQLite FTS5 database helper (`OmniBrainDbHelper.kt`).
- [ ] **Embedded Server:** Ktor HTTP/MCP Server engine.
- [ ] **Service Management:** Android `ForegroundService` for background execution.

## Deployment Stack
- **Worker Script:** `src/index.js` (Handles `/upload` POST and `/read/:token` GET).
- **TTL Expiration:** 86,400 seconds (24 hours).
- **Client Action:** HTTP POST to Worker -> Copy received `/read/:token` URL to Android Clipboard.
