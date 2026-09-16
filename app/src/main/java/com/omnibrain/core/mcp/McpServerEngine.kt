package com.omnibrain.core.mcp

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import com.omnibrain.core.db.OmniBrainDbHelper
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

class McpServerEngine(
    private val context: Context,
    private val port: Int = 8080
) {
    private val dbHelper = OmniBrainDbHelper(context)
    private var server: io.ktor.server.engine.ApplicationEngine? = null

    fun start() {
        server = embeddedServer(Netty, port = port) {
            install(ContentNegotiation) {
                json()
            }
            configureRouting()
        }.start(wait = false)
    }

    fun stop() {
        server?.stop(1000, 2000)
    }

    private fun Application.configureRouting() {
        routing {
            get("/health") {
                call.respondText(
                    """{"status":"online","engine":"Ktor/Netty","port":$port}""",
                    io.ktor.http.ContentType.Application.Json
                )
            }

            post("/mcp") {
                val bodyText = call.receiveText()
                val jsonRpc = Json.parseToJsonElement(bodyText).jsonObject

                val method = jsonRpc["method"]?.jsonPrimitive?.content
                val id = jsonRpc["id"]

                when (method) {
                    "tools/list" -> {
                        val toolsResponse = buildJsonObject {
                            put("jsonrpc", "2.0")
                            put("id", id ?: buildJsonObject {})
                            put("result", buildJsonObject {
                                putJsonArray("tools") {
                                    add(buildJsonObject {
                                        put("name", "search_omnibrain_memory")
                                        put("description", "Search persisted session history and execution logs using FTS5.")
                                    })
                                    add(buildJsonObject {
                                        put("name", "get_device_health")
                                        put("description", "Fetch current Android battery level, storage availability, and network connection metrics.")
                                    })
                                }
                            })
                        }
                        call.respond(toolsResponse)
                    }
                    "tools/call" -> {
                        val params = jsonRpc["params"]?.jsonObject
                        val toolName = params?.get("name")?.jsonPrimitive?.content
                        val args = params?.get("arguments")?.jsonObject

                        val resultText = when (toolName) {
                            "search_omnibrain_memory" -> {
                                val query = args?.get("query")?.jsonPrimitive?.content ?: ""
                                val results = dbHelper.searchLogs(query)
                                dbHelper.logExecution("search_omnibrain_memory", args.toString(), results.joinToString("\n"))
                                results.joinToString("\n")
                            }
                            "get_device_health" -> {
                                val healthMetrics = fetchDeviceHealth()
                                dbHelper.logExecution("get_device_health", args.toString() ?: "{}", healthMetrics.toString())
                                healthMetrics.toString()
                            }
                            else -> "Error: Unknown tool '$toolName'"
                        }

                        val callResponse = buildJsonObject {
                            put("jsonrpc", "2.0")
                            put("id", id ?: buildJsonObject {})
                            put("result", buildJsonObject {
                                putJsonArray("content") {
                                    add(buildJsonObject {
                                        put("type", "text")
                                        put("text", resultText)
                                    })
                                }
                            })
                        }
                        call.respond(callResponse)
                    }
                    else -> {
                        call.respond(HttpStatusCode.BadRequest, "Unsupported method")
                    }
                }
            }
        }
    }

    private fun fetchDeviceHealth(): JsonObject {
        // Battery status
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) (level / scale.toFloat() * 100).toInt() else -1
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        // Internal Storage
        val stat = StatFs(Environment.getDataDirectory().path)
        val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
        val totalBytes = stat.blockCountLong * stat.blockSizeLong
        val availableGb = availableBytes / (1024 * 1024 * 1024)
        val totalGb = totalBytes / (1024 * 1024 * 1024)

        // Network Status
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork
        val capabilities = cm.getNetworkCapabilities(activeNetwork)
        val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: false
        val isCellular = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ?: false
        val isConnected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: false

        val networkType = when {
            isWifi -> "Wi-Fi"
            isCellular -> "Cellular"
            else -> if (isConnected) "Other" else "Disconnected"
        }

        return buildJsonObject {
            put("battery_percent", batteryPct)
            put("is_charging", isCharging)
            put("storage_available_gb", availableGb)
            put("storage_total_gb", totalGb)
            put("network_type", networkType)
            put("is_connected", isConnected)
        }
    }
}
