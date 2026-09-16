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

## OmniBrain Tool Module Specification

### Payload Format (Hybrid)
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
...
# Recent Architectural Decisions
...
# Context Notes & Memory State
...
\`\`\`

### System Components & Status
- [x] **OmniBrain Format Spec:** Hybrid YAML + Markdown designed.
- [x] **Cloudflare Share Proxy:** Worker script & KV storage defined.
- [x] **Android Share Action:** Kotlin exporter & Compose share button defined.
- [ ] **OmniBrain Local Store:** SQLite FTS5 database helper (`OmniBrainDbHelper.kt`).
- [ ] **App Core MCP Engine:** Ktor HTTP/MCP server routing (`/mcp` + `/share`).
- [ ] **Android App Shell:** Main app UI and `ForegroundService` for background MCP hosting.
