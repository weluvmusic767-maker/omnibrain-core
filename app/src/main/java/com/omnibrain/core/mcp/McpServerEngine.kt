package com.omnibrain.core.mcp

import android.content.ClipboardManager
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class McpServerEngine(
    private val context: Context,
    private val port: Int = 8080
) {
    private val dbHelper = OmniBrainDbHelper(context)
    private var server: io.ktor.server.engine.ApplicationEngine? = null

    fun start() {
        server = embeddedServer(Netty, port = port) {
            install(ContentNegotiation) { json() }
            configureRouting()
        }.start(wait = false)

        registerTunnelWithCloudflare()
    }

    fun stop() {
        server?.stop(1000, 2000)
    }

    private fun registerTunnelWithCloudflare() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val workerUrl = dbHelper.getConfig("cloudflare_worker_url") ?: return@launch
                val secret = dbHelper.getConfig("cloudflare_secret") ?: return@launch
                val currentTunnelUrl = dbHelper.getConfig("tunnel_url") ?: "http://localhost:$port"

                val url = URL(workerUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                val payload = buildJsonObject {
                    put("jsonrpc", "2.0")
                    put("id", 1)
                    put("method", "tools/call")
                    put("params", buildJsonObject {
                        put("name", "set_phone_url")
                        put("arguments", buildJsonObject {
                            put("url", currentTunnelUrl)
                            put("secret", secret)
                        })
                    })
                }.toString()

                conn.outputStream.use { it.write(payload.toByteArray()) }
                conn.responseCode
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun Application.configureRouting() {
        routing {
            get("/health") {
                val tunnel = dbHelper.getConfig("tunnel_url") ?: "none"
                call.respondText(
                    """{"status":"online","engine":"Ktor/Netty","port":$port,"tunnel_url":"$tunnel"}""",
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
                                        put("description", "Fetch current Android battery level, storage, and network state.")
                                    })
                                    add(buildJsonObject {
                                        put("name", "run_terminal_command")
                                        put("description", "Execute a local shell command on the device.")
                                    })
                                    add(buildJsonObject {
                                        put("name", "get_clipboard")
                                        put("description", "Read the current Android system clipboard content.")
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
                            "run_terminal_command" -> {
                                val cmd = args?.get("command")?.jsonPrimitive?.content ?: "echo 'No command provided'"
                                val output = executeShellCommand(cmd)
                                dbHelper.logExecution("run_terminal_command", args.toString(), output)
                                output
                            }
                            "get_clipboard" -> {
                                val clipText = getClipboardText()
                                dbHelper.logExecution("get_clipboard", "{}", clipText)
                                clipText
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
                    else -> call.respond(HttpStatusCode.BadRequest, "Unsupported method")
                }
            }
        }
    }

    private fun executeShellCommand(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            process.waitFor()
            output.toString().trim()
        } catch (e: Exception) {
            "Error executing shell command: ${e.message}"
        }
    }

    private fun getClipboardText(): String {
        return try {
            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = clipboardManager.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                clipData.getItemAt(0).text.toString()
            } else {
                "Clipboard is empty."
            }
        } catch (e: Exception) {
            "Error accessing clipboard: ${e.message}"
        }
    }

    private fun fetchDeviceHealth(): JsonObject {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) (level / scale.toFloat() * 100).toInt() else -1
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        val stat = StatFs(Environment.getDataDirectory().path)
        val availableGb = (stat.availableBlocksLong * stat.blockSizeLong) / (1024 * 1024 * 1024)
        val totalGb = (stat.blockCountLong * stat.blockSizeLong) / (1024 * 1024 * 1024)

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork
        val capabilities = cm.getNetworkCapabilities(activeNetwork)
        val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: false
        val isCellular = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ?: false
        val isConnected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: false

        return buildJsonObject {
            put("battery_percent", batteryPct)
            put("is_charging", isCharging)
            put("storage_available_gb", availableGb)
            put("storage_total_gb", totalGb)
            put("network_type", if (isWifi) "Wi-Fi" else if (isCellular) "Cellular" else "Disconnected")
            put("is_connected", isConnected)
        }
    }
}
