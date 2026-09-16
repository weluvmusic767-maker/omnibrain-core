package com.omnibrain.core.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnibrain.core.db.OmniBrainDbHelper
import com.omnibrain.core.service.McpForegroundService

class MainActivity : ComponentActivity() {

    private var isServiceRunning by mutableStateOf(false)
    private var searchQuery by mutableStateOf("")
    private var searchResults by mutableStateOf<List<String>>(emptyList())
    private var totalLogCount by mutableStateOf(0L)
    private var tunnelUrl by mutableStateOf("Offline")
    private lateinit var dbHelper: OmniBrainDbHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate()
        dbHelper = OmniBrainDbHelper(this)
        refreshStats()

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF6200EE),
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DashboardScreen(
                        isRunning = isServiceRunning,
                        logCount = totalLogCount,
                        tunnelUrl = tunnelUrl,
                        onStartService = {
                            McpForegroundService.start(this)
                            isServiceRunning = true
                            refreshStats()
                        },
                        onStopService = {
                            McpForegroundService.stop(this)
                            isServiceRunning = false
                            refreshStats()
                        },
                        searchQuery = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onSearch = {
                            if (searchQuery.isNotBlank()) {
                                searchResults = dbHelper.searchLogs(searchQuery)
                            }
                        },
                        searchResults = searchResults
                    )
                }
            }
        }
    }

    private fun refreshStats() {
        totalLogCount = dbHelper.getTotalLogCount()
        tunnelUrl = dbHelper.getConfig("tunnel_url") ?: "http://localhost:8080"
    }
}

@Composable
fun DashboardScreen(
    isRunning: Boolean,
    logCount: Long,
    tunnelUrl: String,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    searchResults: List<String>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "OmniBrain Dashboard",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        // Metrics & Control Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "SERVER CONTROL & STATS", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isRunning) "● Ktor Online" else "○ Ktor Stopped",
                        color = if (isRunning) Color(0xFF00E676) else Color(0xFFFF5252),
                        fontWeight = FontWeight.Bold
                    )
                    Text(text = "Logs: $logCount", color = Color.LightGray, fontFamily = FontFamily.Monospace)
                }

                Text(
                    text = "Endpoint: $tunnelUrl",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = if (isRunning) onStopService else onStartService,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRunning) Color(0xFFFF5252) else Color(0xFF6200EE)
                    )
                ) {
                    Text(if (isRunning) "Stop MCP Engine" else "Start MCP Engine")
                }
            }
        }

        // Search Interface Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "FTS5 MEMORY SEARCH", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onQueryChange,
                    label = { Text("Search logs or tool results...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Button(
                    onClick = onSearch,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Search FTS5")
                }
            }
        }

        // Search Results List
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(searchResults.size) { idx ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2C))
                ) {
                    Text(
                        text = searchResults[idx],
                        modifier = Modifier.padding(12.dp),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White
                    )
                }
            }
        }
    }
}
