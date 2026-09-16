package com.omnibrain.core.router

import android.content.ContentValues
import com.omnibrain.core.db.OmniBrainDbHelper
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.*

fun Application.configureMcpRoutes(dbHelper: OmniBrainDbHelper) {
    routing {
        get("/status") {
            call.respondText("""{"status": "ONLINE", "service": "OmniBrain Core"}""")
        }

        post("/mcp") {
            val startTime = System.currentTimeMillis()
            val requestText = call.receiveText()
            val json = Json.parseToJsonElement(requestText).jsonObject

            val method = json["method"]?.jsonPrimitive?.content ?: ""
            val id = json["id"]?.jsonPrimitive?.intOrNull ?: 1

            val response = when (method) {
                "initialize" -> buildJsonObject {
                    put("jsonrpc", "2.0")
                    put("id", id)
                    putJsonObject("result") {
                        putJsonObject("serverInfo") {
                            put("name", "OmniBrain Core")
                            put("version", "1.0.0")
                        }
                    }
                }
                "tools/list" -> buildJsonObject {
                    put("jsonrpc", "2.0")
                    put("id", id)
                    putJsonObject("result") {
                        putJsonArray("tools") {
                            addJsonObject {
                                put("name", "omnibrain_status")
                                put("description", "Check the status of OmniBrain engine")
                            }
                            addJsonObject {
                                put("name", "omnibrain_query_logs")
                                put("description", "Search tool execution logs stored in SQLite")
                                putJsonObject("inputSchema") {
                                    put("type", "object")
                                    putJsonObject("properties") {
                                        putJsonObject("limit") {
                                            put("type", "integer")
                                            put("description", "Number of recent logs to return (default: 10)")
                                        }
                                        putJsonObject("tool_filter") {
                                            put("type", "string")
                                            put("description", "Optional filter by tool name")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                "tools/call" -> {
                    val params = json["params"]?.jsonObject
                    val toolName = params?.get("name")?.jsonPrimitive?.content ?: ""
                    val arguments = params?.get("arguments")?.jsonObject

                    val resultText = when (toolName) {
                        "omnibrain_status" -> "OmniBrain status: Active & Ready."
                        "omnibrain_query_logs" -> {
                            val limit = arguments?.get("limit")?.jsonPrimitive?.intOrNull ?: 10
                            val toolFilter = arguments?.get("tool_filter")?.jsonPrimitive?.contentOrNull
                            queryLogs(dbHelper, limit, toolFilter)
                        }
                        else -> "Error: Unknown tool '$toolName'"
                    }

                    // Log execution to SQLite DB
                    val executionTime = System.currentTimeMillis() - startTime
                    logExecution(dbHelper, toolName, if (resultText.startsWith("Error")) "ERROR" else "SUCCESS", executionTime)

                    buildJsonObject {
                        put("jsonrpc", "2.0")
                        put("id", id)
                        putJsonObject("result") {
                            putJsonArray("content") {
                                addJsonObject {
                                    put("type", "text")
                                    put("text", resultText)
                                }
                            }
                        }
                    }
                }
                else -> buildJsonObject {
                    put("jsonrpc", "2.0")
                    put("id", id)
                    putJsonObject("error") {
                        put("code", -32601)
                        put("message", "Method not found: $method")
                    }
                }
            }

            call.respondText(response.toString())
        }
    }
}

private fun logExecution(dbHelper: OmniBrainDbHelper, toolName: String, status: String, executionTimeMs: Long) {
    try {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("tool_name", toolName)
            put("status", status)
            put("execution_time_ms", executionTimeMs)
        }
        db.insert("tool_execution_logs", null, values)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun queryLogs(dbHelper: OmniBrainDbHelper, limit: Int, toolFilter: String?): String {
    return try {
        val db = dbHelper.readableDatabase
        val selection = if (toolFilter != null) "tool_name = ?" else null
        val selectionArgs = if (toolFilter != null) arrayOf(toolFilter) else null

        val cursor = db.query(
            "tool_execution_logs",
            arrayOf("id", "timestamp", "tool_name", "status", "execution_time_ms"),
            selection,
            selectionArgs,
            null,
            null,
            "id DESC",
            limit.toString()
        )

        val logs = mutableListOf<String>()
        while (cursor.moveToNext()) {
            val id = cursor.getInt(0)
            val time = cursor.getString(1)
            val name = cursor.getString(2)
            val status = cursor.getString(3)
            val duration = cursor.getInt(4)
            logs.add("[$id] $time | Tool: $name | Status: $status | Duration: ${duration}ms")
        }
        cursor.close()

        if (logs.isEmpty()) "No logs found." else logs.joinToString("\n")
    } catch (e: Exception) {
        "Failed to query logs: ${e.message}"
    }
}
