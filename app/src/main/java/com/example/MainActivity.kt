package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.text.TextStyle
import com.example.data.AppDatabase
import com.example.data.ScripEntity
import com.example.data.WatchlistEntity
import com.example.data.WatchlistRepository
import com.example.ui.theme.MyApplicationTheme
import com.example.utils.ScripExtractor
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ViewModel for managing Watchlist dashboard state
class MainViewModel(private val repository: WatchlistRepository) : ViewModel() {

    val watchlists: StateFlow<List<WatchlistEntity>> = repository.allWatchlists
        .stateIn(
            scope = kotlinx.coroutines.MainScope(),
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _selectedWatchlist = MutableStateFlow<WatchlistEntity?>(null)
    val selectedWatchlist: StateFlow<WatchlistEntity?> = _selectedWatchlist.asStateFlow()

    // Automatically fetch scrips whenever selectedWatchlist changes
    val activeScrips: StateFlow<List<ScripEntity>> = _selectedWatchlist
        .flatMapLatest { watchlist ->
            if (watchlist != null) {
                repository.getScripsForWatchlist(watchlist.id)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(
            scope = kotlinx.coroutines.MainScope(),
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun selectWatchlist(watchlist: WatchlistEntity) {
        _selectedWatchlist.value = watchlist
    }

    fun addWatchlist(name: String, onComplete: () -> Unit) {
        kotlinx.coroutines.MainScope().launch {
            val cleanName = name.trim()
            if (cleanName.isNotEmpty()) {
                val newId = repository.addWatchlist(cleanName)
                val newWatchlist = WatchlistEntity(id = newId, name = cleanName)
                _selectedWatchlist.value = newWatchlist
                onComplete()
            }
        }
    }

    fun deleteWatchlist(watchlist: WatchlistEntity) {
        kotlinx.coroutines.MainScope().launch {
            repository.deleteWatchlist(watchlist.id)
            // Reset selection if deleted active watchlist
            if (_selectedWatchlist.value?.id == watchlist.id) {
                val currentList = watchlists.value.filter { it.id != watchlist.id }
                _selectedWatchlist.value = currentList.firstOrNull()
            }
        }
    }

    fun deleteScrip(scripId: Int) {
        kotlinx.coroutines.MainScope().launch {
            repository.deleteScrip(scripId)
        }
    }

    fun addScrip(watchlistId: Int, scripName: String) {
        kotlinx.coroutines.MainScope().launch {
            repository.addScrip(watchlistId, scripName)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Create Repository
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = WatchlistRepository(database.watchlistDao())

        setContent {
            MyApplicationTheme {
                val viewModel: MainViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            @Suppress("UNCHECKED_CAST")
                            return MainViewModel(repository) as T
                        }
                    }
                )

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = {
                                Text(
                                    "Scrip Interceptor",
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                            )
                        )
                    }
                ) { innerPadding ->
                    DashboardScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val watchlists by viewModel.watchlists.collectAsStateWithLifecycle()
    val selectedWatchlist by viewModel.selectedWatchlist.collectAsStateWithLifecycle()
    val scrips by viewModel.activeScrips.collectAsStateWithLifecycle()

    // Dialog state for adding a new watchlist
    var showAddWatchlistDialog by remember { mutableStateOf(false) }
    var newWatchlistName by remember { mutableStateOf("") }

    // Dialog state for manual stock scrip addition
    var showManualAddScripDialog by remember { mutableStateOf(false) }
    var manualScripName by remember { mutableStateOf("") }

    // Sync selected watchlist on initial load
    LaunchedEffect(watchlists) {
        if (selectedWatchlist == null && watchlists.isNotEmpty()) {
            viewModel.selectWatchlist(watchlists.first())
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }



        // --- SECTION: WATCHLISTS CONTROLLER (CHIPS) ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "My Watchlists",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                IconButton(
                    onClick = { showAddWatchlistDialog = true },
                    modifier = Modifier.testTag("add_watchlist_fab")
                ) {
                    Icon(Icons.Default.AddCircle, contentDescription = "Create Watchlist", tint = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (watchlists.isEmpty()) {
                Text(
                    "No watchlists available. Create one to get started!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                val lazyRowState = rememberLazyListState()
                LaunchedEffect(selectedWatchlist) {
                    selectedWatchlist?.let { selected ->
                        val index = watchlists.indexOfFirst { it.id == selected.id }
                        if (index >= 0) {
                            lazyRowState.animateScrollToItem(index)
                        }
                    }
                }
                LazyRow(
                    state = lazyRowState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(watchlists, key = { it.id }) { watchlist ->
                        val isSelected = selectedWatchlist?.id == watchlist.id
                        InputChip(
                            selected = isSelected,
                            onClick = { viewModel.selectWatchlist(watchlist) },
                            label = { Text(watchlist.name) },
                            trailingIcon = {
                                // Let users delete custom watchlists, keeping "Default" safe
                                if (watchlist.name != "Default" && watchlist.name != "Long Term" && watchlist.name != "Daily Triggers") {
                                    Icon(
                                        Icons.Default.Cancel,
                                        contentDescription = "Delete watchlist",
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { viewModel.deleteWatchlist(watchlist) }
                                    )
                                }
                            },
                            colors = InputChipDefaults.inputChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.testTag("watchlist_chip_${watchlist.name}")
                        )
                    }
                }
            }
        }

        // --- SECTION: ACTIVE SCRIPS LIST ---
        item {
            selectedWatchlist?.let { watchlist ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${watchlist.name} Watchlist",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "List of active tracked NSE stocks",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = { showManualAddScripDialog = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("manual_add_scrip_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add scrip", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Stock", fontSize = 12.sp)
                    }
                }
            }
        }

        if (scrips.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Analytics,
                        contentDescription = "Empty Watchlist Icon",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "This Watchlist is Empty",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Try the simulator above or share an article from Moneycontrol to detect and add stock tickers automatically.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        } else {
            items(scrips, key = { it.id }) { scrip ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem()
                        .testTag("scrip_item_${scrip.scripName}"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ShowChart,
                                    contentDescription = "Stock Icon",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = scrip.scripName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val scripCategory = ScripExtractor.getScripCategory(scrip.scripName)
                                    val badgeBgColor = when (scripCategory) {
                                        "Derivative" -> MaterialTheme.colorScheme.tertiaryContainer
                                        "Commodity" -> Color(0xFFFFE082) // Light amber/gold for Commodity
                                        else -> MaterialTheme.colorScheme.secondaryContainer
                                    }
                                    val badgeTextColor = when (scripCategory) {
                                        "Derivative" -> MaterialTheme.colorScheme.onTertiaryContainer
                                        "Commodity" -> Color(0xFF5D4037) // Dark brown for readability on gold
                                        else -> MaterialTheme.colorScheme.onSecondaryContainer
                                    }
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                badgeBgColor,
                                                RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = scripCategory,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = badgeTextColor,
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            }
                        }

                        IconButton(
                            onClick = { viewModel.deleteScrip(scrip.id) },
                            modifier = Modifier.testTag("delete_scrip_button_${scrip.scripName}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete scrip",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // --- DIALOG: CREATE NEW WATCHLIST ---
    if (showAddWatchlistDialog) {
        AlertDialog(
            onDismissRequest = { showAddWatchlistDialog = false },
            title = { Text("Create Watchlist") },
            text = {
                Column {
                    Text(
                        "Enter a name for your new stock watchlist to keep them organized.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = newWatchlistName,
                        onValueChange = { newWatchlistName = it },
                        label = { Text("Watchlist Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_watchlist_name_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newWatchlistName.trim().isEmpty()) {
                            Toast.makeText(context, "Name cannot be empty!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        viewModel.addWatchlist(newWatchlistName) {
                            newWatchlistName = ""
                            showAddWatchlistDialog = false
                            Toast.makeText(context, "Watchlist created!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.testTag("confirm_create_watchlist_button")
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddWatchlistDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- DIALOG: MANUAL ADD SCRIP ---
    if (showManualAddScripDialog && selectedWatchlist != null) {
        val activeWatchlist = selectedWatchlist!!
        var searchQuery by remember { mutableStateOf("") }
        val filteredScrips = remember(searchQuery) {
            val q = searchQuery.uppercase().trim()
            val masterList = ScripExtractor.ALL_INDIAN_MARKET_SCRIPS.sorted()
            if (q.isEmpty()) {
                masterList
            } else {
                masterList.filter { it.contains(q) }
            }
        }

        AlertDialog(
            onDismissRequest = { 
                showManualAddScripDialog = false
                searchQuery = ""
                manualScripName = ""
            },
            title = { Text("Add Stock / Derivative / Commodity") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Search or enter a scrip name (Equity, Derivative, or Commodity):",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { 
                            searchQuery = it
                            // Auto-select custom typed if it is an exact match or custom
                            manualScripName = it.uppercase().trim()
                        },
                        label = { Text("Search or Type Scrip Code") },
                        placeholder = { Text("e.g. RELIANCE, NIFTY24DEC, GOLD") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_scrip_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Matching Scrips (Select one):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(4.dp)
                    ) {
                        val trimmedQuery = searchQuery.uppercase().trim()
                        if (trimmedQuery.isNotEmpty() && !ScripExtractor.ALL_INDIAN_MARKET_SCRIPS.contains(trimmedQuery)) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (manualScripName == trimmedQuery) 
                                                MaterialTheme.colorScheme.primaryContainer 
                                            else 
                                                Color.Transparent
                                        )
                                        .clickable { manualScripName = trimmedQuery }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add custom",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Add Custom: $trimmedQuery",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        items(filteredScrips) { stock ->
                            val isSelected = manualScripName == stock
                            val category = ScripExtractor.getScripCategory(stock)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (isSelected) 
                                            MaterialTheme.colorScheme.primaryContainer 
                                        else 
                                            Color.Transparent
                                    )
                                    .clickable { manualScripName = stock }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stock,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
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
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalName = manualScripName.trim().uppercase()
                        if (finalName.isEmpty()) {
                            Toast.makeText(context, "Please choose or type a scrip code!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        viewModel.addScrip(activeWatchlist.id, finalName)
                        manualScripName = ""
                        searchQuery = ""
                        showManualAddScripDialog = false
                        Toast.makeText(context, "Added scrip $finalName!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.testTag("confirm_manual_add_button")
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showManualAddScripDialog = false 
                        searchQuery = ""
                        manualScripName = ""
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }


}
