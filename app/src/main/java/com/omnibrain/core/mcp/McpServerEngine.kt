package com.omnibrain.core.mcp

import android.content.Context
import com.omnibrain.core.db.OmniBrainDbHelper
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.*

class McpServerEngine(private val context: Context, private val port: Int = 8080) {

    private val dbHelper = OmniBrainDbHelper(context)
    private var server: NettyApplicationEngine? = null

    fun start() {
        server = embeddedServer(Netty, port = port) {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }

            routing {
                get("/health") {
                    call.respond(HttpStatusCode.OK, mapOf("status" to "ok", "service" to "OmniBrain MCP"))
                }

                post("/mcp") {
                    val requestBody = call.receive<JsonObject>()
                    val response = handleMcpRequest(requestBody)
                    call.respond(HttpStatusCode.OK, response)
                }
            }
        }.start(wait = false)
    }

    fun stop() {
        server?.stop(1000, 2000)
    }

    private fun handleMcpRequest(json: JsonObject): JsonObject {
        val method = json["method"]?.jsonPrimitive?.content ?: ""
        val id = json["id"] ?: JsonPrimitive(1)

        return when (method) {
            "initialize" -> buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", id)
                putJsonObject("result") {
                    put("protocolVersion", "2024-11-05")
                    putJsonObject("capabilities") {
                        putJsonObject("tools") {}
                    }
                    putJsonObject("serverInfo") {
                        put("name", "OmniBrain-Android-MCP")
                        put("version", "1.0.0")
                    }
                }
            }

            "tools/list" -> buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", id)
                putJsonObject("result") {
                    putJsonArray("tools") {
                        add(buildJsonObject {
                            put("name", "search_omnibrain_memory")
                            put("description", "Search local SQLite FTS5 database for prior context, task state, and architectural decisions.")
                            putJsonObject("inputSchema") {
                                put("type", "object")
                                putJsonObject("properties") {
                                    putJsonObject("query") {
                                        put("type", "string")
                                        put("description", "Keyword or topic search string")
                                    }
                                }
                                putJsonArray("required") { add(JsonPrimitive("query")) }
                            }
                        })
                    }
                }
            }

            "tools/call" -> {
                val params = json["params"]?.jsonObject
                val toolName = params?.get("name")?.jsonPrimitive?.content
                val arguments = params?.get("arguments")?.jsonObject

                if (toolName == "search_omnibrain_memory") {
                    val query = arguments?.get("query")?.jsonPrimitive?.content ?: ""
                    val records = dbHelper.searchSnapshots(query)

                    val resultText = if (records.isEmpty()) {
                        "No matching OmniBrain context found for query: '$query'"
                    } else {
                        records.joinToString("\n---\n") { rec ->
                            "Session: ${rec.sessionId}\nTask: ${rec.currentTask}\nDecisions: ${rec.decisionsJson}\nNotes:\n${rec.markdownNotes}"
                        }
                    }

                    buildJsonObject {
                        put("jsonrpc", "2.0")
                        put("id", id)
                        putJsonObject("result") {
                            putJsonArray("content") {
                                add(buildJsonObject {
                                    put("type", "text")
                                    put("text", resultText)
                                })
                            }
                        }
                    }
                } else {
                    buildJsonObject {
                        put("jsonrpc", "2.0")
                        put("id", id)
                        putJsonObject("error") {
                            put("code", -32601)
                            put("message", "Method or Tool not found: $toolName")
                        }
                    }
                }
            }

            else -> buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", id)
                putJsonObject("error") {
                    put("code", -32601)
                    put("message", "Method not supported: $method")
                }
            }
        }
    }
}
