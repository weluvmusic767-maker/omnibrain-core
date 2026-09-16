package com.omnibrain.core.router

import com.omnibrain.core.model.JsonRpcError
import com.omnibrain.core.model.JsonRpcRequest
import com.omnibrain.core.model.JsonRpcResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.json.*

fun Routing.configureMcpRoutes() {

    // MCP JSON-RPC endpoint
    post("/mcp") {
        try {
            val body = call.receiveText()
            val json = Json { ignoreUnknownKeys = true }
            val request = json.decodeFromString<JsonRpcRequest>(body)

            val response = when (request.method) {
                "initialize" -> JsonRpcResponse(
                    id = request.id,
                    result = buildJsonObject {
                        put("protocolVersion", "2024-11-05")
                        putJsonObject("capabilities") {
                            putJsonObject("tools") {}
                        }
                        putJsonObject("serverInfo") {
                            put("name", "OmniBrain Core Android")
                            put("version", "1.0.0")
                        }
                    }
                )

                "tools/list" -> JsonRpcResponse(
                    id = request.id,
                    result = buildJsonObject {
                        putJsonArray("tools") {
                            add(buildJsonObject {
                                put("name", "omnibrain_status")
                                put("description", "Check local OmniBrain service status and memory metrics")
                                putJsonObject("inputSchema") {
                                    put("type", "object")
                                }
                            })
                            add(buildJsonObject {
                                put("name", "omnibrain_ping")
                                put("description", "Ping local Android MCP core server")
                                putJsonObject("inputSchema") {
                                    put("type", "object")
                                }
                            })
                        }
                    }
                )

                "tools/call" -> {
                    val params = request.params?.jsonObject
                    val toolName = params?.get("name")?.jsonPrimitive?.content

                    val resultText = when (toolName) {
                        "omnibrain_status" -> "OmniBrain Core Service: ACTIVE\nStorage Engine: SQLite FTS5 Ready"
                        "omnibrain_ping" -> "pong"
                        else -> "Error: Unknown tool '$toolName'"
                    }

                    JsonRpcResponse(
                        id = request.id,
                        result = buildJsonObject {
                            putJsonArray("content") {
                                add(buildJsonObject {
                                    put("type", "text")
                                    put("text", resultText)
                                })
                            }
                        }
                    )
                }

                else -> JsonRpcResponse(
                    id = request.id,
                    error = JsonRpcError(code = -32601, message = "Method not found: ${request.method}")
                )
            }

            call.respond(response)
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.BadRequest,
                JsonRpcResponse(
                    error = JsonRpcError(code = -32700, message = "Parse error: ${e.localizedMessage}")
                )
            )
        }
    }

    // Health / Status endpoint
    get("/status") {
        call.respondText("""{"status":"ONLINE","server":"OmniBrain Ktor Android","port":8080}""", io.ktor.http.ContentType.Application.Json)
    }
}
