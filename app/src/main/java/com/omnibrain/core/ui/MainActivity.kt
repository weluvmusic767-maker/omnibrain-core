package com.omnibrain.core.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
    private lateinit var dbHelper: OmniBrainDbHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate()
        dbHelper = OmniBrainDbHelper(this)

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
                        onStartService = {
                            McpForegroundService.start(this)
                            isServiceRunning = true
                        },
                        onStopService = {
                            McpForegroundService.stop(this)
                            isServiceRunning = false
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    isRunning: Boolean,
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
        // Title Header
        Text(
            text = "OmniBrain Core",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        // Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "SERVER METRICS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isRunning) "Status: ONLINE" else "Status: OFFLINE",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isRunning) Color(0xFF00E676) else Color(0xFFFF5252)
                    )
                    Text(
                        text = "Port: 8080",
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color.LightGray
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = if (isRunning) onStopService else onStartService,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRunning) Color(0xFFFF5252) else Color(0xFF6200EE)
                    )
                ) {
                    Text(if (isRunning) "Stop MCP Server" else "Start MCP Server")
                }
            }
        }

        // FTS5 Memory Query Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "MEMORY SEARCH (FTS5)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onQueryChange,
                    label = { Text("Enter search term...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Button(
                    onClick = onSearch,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Search")
                }
            }
        }

        // Search Results List
        Text(
            text = "Query Results (${searchResults.size})",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(searchResults.size) { index ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2C))
                ) {
                    Text(
                        text = searchResults[index],
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
