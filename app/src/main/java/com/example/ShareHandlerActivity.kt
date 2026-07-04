package com.example

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.lifecycleScope
import com.example.data.AppDatabase
import com.example.data.WatchlistEntity
import com.example.data.WatchlistRepository
import com.example.ui.theme.MyApplicationTheme
import com.example.utils.ScripExtractor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ShareHandlerActivity : ComponentActivity() {

    private lateinit var repository: WatchlistRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Repository
        val database = AppDatabase.getDatabase(applicationContext)
        repository = WatchlistRepository(database.watchlistDao())

        // Extract and validate share payload
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
        val url = ScripExtractor.extractUrl(sharedText)

        if (url == null) {
            Toast.makeText(this, "No valid link found in shared content!", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        if (!ScripExtractor.isValidMoneycontrolUrl(url)) {
            Toast.makeText(this, "Invalid source! Only moneycontrol.com links are supported.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContent {
            MyApplicationTheme {
                ShareOverlayScreen(
                    url = url,
                    onDismiss = { finish() },
                    onAddComplete = { watchlistName, scripsAdded ->
                        Toast.makeText(
                            this,
                            "Added ${scripsAdded.size} scrips to '$watchlistName' watchlist!",
                            Toast.LENGTH_LONG
                        ).show()
                        finish()
                    }
                )
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ShareOverlayScreen(
        url: String,
        onDismiss: () -> Unit,
        onAddComplete: (watchlistName: String, scrips: List<String>) -> Unit
    ) {
        var isLoading by remember { mutableStateOf(true) }
        var extractedScrips by remember { mutableStateOf<List<String>>(emptyList()) }
        var selectedScrips by remember { mutableStateOf<Set<String>>(emptySet()) }
        
        var watchlists by remember { mutableStateOf<List<WatchlistEntity>>(emptyList()) }
        var selectedWatchlist by remember { mutableStateOf<WatchlistEntity?>(null) }
        var isCreatingNewWatchlist by remember { mutableStateOf(false) }
        var newWatchlistName by remember { mutableStateOf("") }

        // Load data and scrape URL
        LaunchedEffect(url) {
            // Load watchlists
            watchlists = repository.allWatchlists.first()
            if (watchlists.isNotEmpty()) {
                selectedWatchlist = watchlists.first()
            }

            // Scrape scrips
            extractedScrips = ScripExtractor.extractScripsFromUrl(url)
            selectedScrips = extractedScrips.toSet() // Pre-select all extracted scrips
            isLoading = false
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Bottom card mimicking BottomSheet
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.82f).dp)
                    .padding(top = 48.dp) // Leave status bar space
                    .clickable(enabled = false) {}, // Prevent dismiss click propagation
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(24.dp)
                    ) {
                        // Drag Handle decoration
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(4.dp)
                                .background(
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    RoundedCornerShape(2.dp)
                                )
                                .align(Alignment.CenterHorizontally)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Analytics,
                                    contentDescription = "Analytics",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Scrip Detector",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "URL: $url",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        if (isLoading) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Analyzing article content...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            if (extractedScrips.isEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SentimentDissatisfied,
                                        contentDescription = "No scrips found",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "No Indian market scrips (stocks, derivatives, or commodities) found in this article.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Button(
                                        onClick = onDismiss,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Close")
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    Text(
                                        text = "Detected Scrips (${extractedScrips.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Scrips Checklist
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                                RoundedCornerShape(12.dp)
                                            )
                                            .padding(8.dp)
                                    ) {
                                        extractedScrips.forEach { scrip ->
                                            val isChecked = selectedScrips.contains(scrip)
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        selectedScrips = if (isChecked) {
                                                            selectedScrips - scrip
                                                        } else {
                                                            selectedScrips + scrip
                                                        }
                                                    }
                                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Checkbox(
                                                    checked = isChecked,
                                                    onCheckedChange = { checked ->
                                                        selectedScrips = if (checked == true) {
                                                            selectedScrips + scrip
                                                        } else {
                                                            selectedScrips - scrip
                                                        }
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Row(
                                                    modifier = Modifier.weight(1f),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = scrip,
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    val category = ScripExtractor.getScripCategory(scrip)
                                                    Box(
                                                        modifier = Modifier
                                                            .background(
                                                                when (category) {
                                                                    "Derivative" -> MaterialTheme.colorScheme.tertiaryContainer
                                                                    "Commodity" -> Color(0xFFFFE082)
                                                                    else -> MaterialTheme.colorScheme.secondaryContainer
                                                                },
                                                                RoundedCornerShape(4.dp)
                                                            )
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = category,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = when (category) {
                                                                "Derivative" -> MaterialTheme.colorScheme.onTertiaryContainer
                                                                "Commodity" -> Color(0xFF5D4037)
                                                                else -> MaterialTheme.colorScheme.onSecondaryContainer
                                                            },
                                                            fontSize = 8.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(20.dp))

                                    // Watchlist Selector
                                    Text(
                                        text = "Target Watchlist",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    if (isCreatingNewWatchlist) {
                                        OutlinedTextField(
                                            value = newWatchlistName,
                                            onValueChange = { newWatchlistName = it },
                                            label = { Text("New Watchlist Name") },
                                            placeholder = { Text("Enter watchlist name") },
                                            singleLine = true,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("new_watchlist_input"),
                                            trailingIcon = {
                                                IconButton(onClick = { isCreatingNewWatchlist = false }) {
                                                    Icon(Icons.Default.Cancel, contentDescription = "Cancel New Watchlist")
                                                }
                                            }
                                        )
                                    } else {
                                        // Dropdown-style Selector for existing watchlists
                                        var isDropdownExpanded by remember { mutableStateOf(false) }
                                        Box(modifier = Modifier.fillMaxWidth()) {
                                            OutlinedCard(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { isDropdownExpanded = true }
                                                    .testTag("watchlist_selector_card"),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(16.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = selectedWatchlist?.name ?: "Select Watchlist",
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                    Icon(
                                                        imageVector = Icons.Default.ArrowDropDown,
                                                        contentDescription = "Expand Watchlists"
                                                    )
                                                }
                                            }

                                            DropdownMenu(
                                                expanded = isDropdownExpanded,
                                                onDismissRequest = { isDropdownExpanded = false },
                                                modifier = Modifier.fillMaxWidth(0.85f)
                                            ) {
                                                watchlists.forEach { watchlist ->
                                                    DropdownMenuItem(
                                                        text = { Text(watchlist.name) },
                                                        onClick = {
                                                            selectedWatchlist = watchlist
                                                            isDropdownExpanded = false
                                                        }
                                                    )
                                                }
                                                HorizontalDivider()
                                                DropdownMenuItem(
                                                    text = { 
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Icon(Icons.Default.Add, contentDescription = "Create New")
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            Text("Create New Watchlist", color = MaterialTheme.colorScheme.primary)
                                                        }
                                                    },
                                                    onClick = {
                                                        isCreatingNewWatchlist = true
                                                        isDropdownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                // Save / Dismiss Buttons
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = onDismiss,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Dismiss")
                                    }

                                    Button(
                                        onClick = {
                                            if (selectedScrips.isEmpty()) {
                                                Toast.makeText(
                                                    this@ShareHandlerActivity,
                                                    "Please select at least one scrip!",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                return@Button
                                            }

                                            lifecycleScope.launch {
                                                val watchlistId: Int
                                                val watchlistName: String

                                                if (isCreatingNewWatchlist) {
                                                    val cleanName = newWatchlistName.trim()
                                                    if (cleanName.isEmpty()) {
                                                        Toast.makeText(
                                                            this@ShareHandlerActivity,
                                                            "Watchlist name cannot be empty!",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                        return@launch
                                                    }
                                                    watchlistId = repository.addWatchlist(cleanName)
                                                    watchlistName = cleanName
                                                } else {
                                                    val selected = selectedWatchlist
                                                    if (selected == null) {
                                                        Toast.makeText(
                                                            this@ShareHandlerActivity,
                                                            "Please select a target watchlist!",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                        return@launch
                                                    }
                                                    watchlistId = selected.id
                                                    watchlistName = selected.name
                                                }

                                                // Save selected scrips
                                                selectedScrips.forEach { scrip ->
                                                    repository.addScrip(watchlistId, scrip)
                                                }

                                                onAddComplete(watchlistName, selectedScrips.toList())
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1.5f)
                                            .testTag("save_to_watchlist_button")
                                    ) {
                                        Icon(Icons.Default.PlaylistAdd, contentDescription = "Add")
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Save Scrips")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
