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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.BorderStroke
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
import kotlinx.coroutines.Dispatchers
import androidx.lifecycle.lifecycleScope
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.*

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

    fun deleteScripByName(watchlistId: Int, scripName: String) {
        kotlinx.coroutines.MainScope().launch {
            repository.deleteScripsByName(watchlistId, scripName)
        }
    }

    fun addScrip(watchlistId: Int, scripName: String) {
        kotlinx.coroutines.MainScope().launch {
            repository.addScrip(watchlistId, scripName)
        }
    }

    fun swapScrips(scrip1: ScripEntity, scrip2: ScripEntity) {
        kotlinx.coroutines.MainScope().launch {
            val t1 = scrip1.addedAt
            val t2 = scrip2.addedAt
            val newT1 = if (t1 == t2) t2 + 1 else t2
            val newT2 = if (t1 == t2) t1 - 1 else t1
            repository.updateScrip(scrip1.copy(addedAt = newT1))
            repository.updateScrip(scrip2.copy(addedAt = newT2))
        }
    }
}

data class GroupedScrip(
    val scripName: String,
    val primaryScrip: ScripEntity,
    val count: Int
)

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Pre-warm ScripExtractor maps in background for instantaneous scanning
        lifecycleScope.launch(Dispatchers.Default) {
            ScripExtractor.preWarm()
        }

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

                DashboardScreen(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
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

    val groupedScrips = remember(scrips) {
        scrips.groupBy { it.scripName }.map { (name, list) ->
            GroupedScrip(scripName = name, primaryScrip = list.first(), count = list.size)
        }
    }

    var isEditMode by remember { mutableStateOf(false) }

    // Dialog state for adding a new watchlist
    var showAddWatchlistDialog by remember { mutableStateOf(false) }
    var newWatchlistName by remember { mutableStateOf("") }

    // Dialog state for manual stock scrip addition
    var showManualAddScripDialog by remember { mutableStateOf(false) }
    var manualScripName by remember { mutableStateOf("") }

    // OCR states for screenshot scanning
    var showOcrDialog by remember { mutableStateOf(false) }
    var isOcrLoading by remember { mutableStateOf(false) }
    var ocrExtractedScrips by remember { mutableStateOf<List<String>>(emptyList()) }
    var ocrSelectedScrips by remember { mutableStateOf<Set<String>>(emptySet()) }

    val pickMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                isOcrLoading = true
                showOcrDialog = true
                ocrExtractedScrips = emptyList()
                ocrSelectedScrips = emptySet()
                
                val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(
                    com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS
                )
                try {
                    val inputImage = com.google.mlkit.vision.common.InputImage.fromFilePath(context, uri)
                    recognizer.process(inputImage)
                        .addOnSuccessListener { visionText ->
                            val text = visionText.text
                            val detected = ScripExtractor.extractScripsFromText(text)
                            ocrExtractedScrips = detected
                            ocrSelectedScrips = detected.toSet()
                            isOcrLoading = false
                        }
                        .addOnFailureListener { e ->
                            isOcrLoading = false
                            Toast.makeText(context, "Scanning failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            showOcrDialog = false
                        }
                } catch (e: Exception) {
                    isOcrLoading = false
                    Toast.makeText(context, "Error loading image: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    showOcrDialog = false
                }
            }
        }
    )

    // Sync selected watchlist on initial load
    LaunchedEffect(watchlists) {
        if (selectedWatchlist == null && watchlists.isNotEmpty()) {
            viewModel.selectWatchlist(watchlists.first())
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_app_logo),
                            contentDescription = "App Logo",
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Neo Watchlist Builder",
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                ),
                actions = {
                    IconButton(
                        onClick = {
                            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.testTag("scan_screenshot_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DocumentScanner,
                            contentDescription = "Scan Screenshot",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
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
                    onClick = { 
                        newWatchlistName = ScripExtractor.suggestWatchlistName(null)
                        showAddWatchlistDialog = true 
                    },
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
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onLongPress = {
                                        isEditMode = true
                                    }
                                )
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
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

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
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

                    if (isEditMode) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Mode",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Editing Mode Active",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Re-arrange with arrows or delete stocks",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                                TextButton(
                                    onClick = { isEditMode = false },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = "Done", modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Done", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
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
            itemsIndexed(groupedScrips, key = { _, it -> it.primaryScrip.id }) { index, grouped ->
                val scrip = grouped.primaryScrip
                val count = grouped.count
                val scripCategory = ScripExtractor.getScripCategory(scrip.scripName)
                val stockInfo = ScripExtractor.findStockBySymbol(scrip.scripName)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = {
                                    isEditMode = true
                                }
                            )
                        }
                        .testTag("scrip_item_${scrip.scripName}"),
                    shape = RoundedCornerShape(16.dp),
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
                            modifier = Modifier.weight(1.0f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Circular Avatar Logo with modern gradient background based on asset type
                            val avatarGradient = when (scripCategory) {
                                "Derivative" -> Brush.linearGradient(listOf(Color(0xFF0EA5E9), Color(0xFF2563EB))) // Sky-Blue
                                "Commodity" -> Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFD97706))) // Amber-Orange
                                else -> Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFFA855F7))) // Indigo-Purple
                            }
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(avatarGradient),
                                contentAlignment = Alignment.Center
                            ) {
                                val firstChar = scrip.scripName.firstOrNull()?.toString() ?: ""
                                Text(
                                    text = firstChar,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // Scrip name is the header, full stock name is not needed
                                Text(
                                    text = scrip.scripName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                // Row for Badges: Category, MTF Leverage, Research, Duplicate
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Asset class badge: Show only for Non-Equity
                                    if (scripCategory != "Equity") {
                                        val badgeBgColor = when (scripCategory) {
                                            "Derivative" -> MaterialTheme.colorScheme.tertiaryContainer
                                            "Commodity" -> Color(0xFFFFE082)
                                            else -> MaterialTheme.colorScheme.secondaryContainer
                                        }
                                        val badgeTextColor = when (scripCategory) {
                                            "Derivative" -> MaterialTheme.colorScheme.onTertiaryContainer
                                            "Commodity" -> Color(0xFF5D4037)
                                            else -> MaterialTheme.colorScheme.onSecondaryContainer
                                        }
                                        Box(
                                            modifier = Modifier
                                                .background(badgeBgColor, RoundedCornerShape(6.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = scripCategory,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = badgeTextColor,
                                                fontSize = 8.sp
                                            )
                                        }
                                    }

                                    // MTF Leverage Badge (Color-coded) - Don't show if value is 0 or 1
                                    if (scripCategory == "Equity") {
                                        val mtfValue = stockInfo?.mtfKotak ?: 0
                                        if (mtfValue > 1) {
                                            val mtfBgColor = when (mtfValue) {
                                                4 -> Color(0xFFDCFCE7) // Strong positive soft green
                                                3 -> Color(0xFFF0FDF4) // Light soft green
                                                2 -> Color(0xFFFEF3C7) // Warning soft yellow
                                                else -> Color(0xFFF3F4F6) // Neutral soft grey
                                            }
                                            val mtfTextColor = when (mtfValue) {
                                                4 -> Color(0xFF166534) // Deep green
                                                3 -> Color(0xFF15803D) // Green
                                                2 -> Color(0xFF92400E) // Deep amber
                                                else -> Color(0xFF4B5563) // Dark slate grey
                                            }
                                            val mtfText = "${mtfValue}X MTF Available"

                                            Box(
                                                modifier = Modifier
                                                    .background(mtfBgColor, RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = mtfText,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = mtfTextColor,
                                                    fontSize = 8.sp
                                                )
                                            }
                                        }
                                    }

                                    // Research Advisory Status Badge
                                    val researchValue = stockInfo?.researchKotak ?: 3
                                    if (researchValue == 1 || researchValue == 2) {
                                        val researchBgColor = if (researchValue == 1) Color(0xFFDBEAFE) else Color(0xFFFEF3C7)
                                        val researchTextColor = if (researchValue == 1) Color(0xFF1E40AF) else Color(0xFF92400E)
                                        val researchText = if (researchValue == 1) "BUY" else "HOLD"

                                        Box(
                                            modifier = Modifier
                                                .background(researchBgColor, RoundedCornerShape(6.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = researchText,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = researchTextColor,
                                                fontSize = 8.sp
                                            )
                                        }
                                    }

                                    // Duplicate count indicator
                                    if (count > 1) {
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    MaterialTheme.colorScheme.errorContainer,
                                                    RoundedCornerShape(6.dp)
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "added $count times",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                fontSize = 8.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Editing controls: Up/Down arrows and Delete (only visible in isEditMode)
                        if (isEditMode) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                // Move Up button
                                IconButton(
                                    onClick = {
                                        if (index > 0) {
                                            val previousScrip = groupedScrips[index - 1].primaryScrip
                                            viewModel.swapScrips(scrip, previousScrip)
                                        }
                                    },
                                    enabled = index > 0,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowUpward,
                                        contentDescription = "Move up",
                                        tint = if (index > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Move Down button
                                IconButton(
                                    onClick = {
                                        if (index < groupedScrips.size - 1) {
                                            val nextScrip = groupedScrips[index + 1].primaryScrip
                                            viewModel.swapScrips(scrip, nextScrip)
                                        }
                                    },
                                    enabled = index < groupedScrips.size - 1,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDownward,
                                        contentDescription = "Move down",
                                        tint = if (index < groupedScrips.size - 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Delete Button
                                IconButton(
                                    onClick = { viewModel.deleteScripByName(scrip.watchlistId, scrip.scripName) },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("delete_scrip_button_${scrip.scripName}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete scrip",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
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
                            val searchStockInfo = ScripExtractor.findStockBySymbol(stock)
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = stock,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (searchStockInfo != null) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "- ${searchStockInfo.companyName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
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

    if (showOcrDialog) {
        AlertDialog(
            onDismissRequest = { showOcrDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DocumentScanner,
                        contentDescription = "OCR Scan",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Screenshot Scanner", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isOcrLoading) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            val infiniteTransition = rememberInfiniteTransition(label = "logo_scale")
                            val scale by infiniteTransition.animateFloat(
                                initialValue = 0.9f,
                                targetValue = 1.1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1000, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "scale"
                            )
                            Image(
                                painter = painterResource(id = R.drawable.img_app_logo),
                                contentDescription = "Analyzing...",
                                modifier = Modifier
                                    .size(80.dp)
                                    .graphicsLayer(scaleX = scale, scaleY = scale)
                                    .clip(RoundedCornerShape(16.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 3.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Analyzing screenshot...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        if (ocrExtractedScrips.isEmpty()) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SearchOff,
                                    contentDescription = "No Stocks Found",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "No Indian market scrips detected in this screenshot.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            Column {
                                Text(
                                    text = "Select the scrips you want to add to your current active watchlist:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                                
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(ocrExtractedScrips) { scrip ->
                                        val isChecked = ocrSelectedScrips.contains(scrip)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    ocrSelectedScrips = if (isChecked) {
                                                        ocrSelectedScrips - scrip
                                                    } else {
                                                        ocrSelectedScrips + scrip
                                                    }
                                                }
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = isChecked,
                                                onCheckedChange = { checked ->
                                                    ocrSelectedScrips = if (checked == true) {
                                                        ocrSelectedScrips + scrip
                                                    } else {
                                                        ocrSelectedScrips - scrip
                                                    }
                                                }
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            val ocrStockInfo = ScripExtractor.findStockBySymbol(scrip)
                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Text(
                                                    text = scrip,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                if (ocrStockInfo != null) {
                                                    Text(
                                                        text = ocrStockInfo.companyName,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
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
                        }
                    }
                }
            },
            confirmButton = {
                if (!isOcrLoading && ocrExtractedScrips.isNotEmpty()) {
                    Button(
                        onClick = {
                            if (ocrSelectedScrips.isEmpty()) {
                                Toast.makeText(context, "Please select at least one scrip!", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            selectedWatchlist?.let { wl ->
                                ocrSelectedScrips.forEach { scrip ->
                                    viewModel.addScrip(wl.id, scrip)
                                }
                                Toast.makeText(context, "Added ${ocrSelectedScrips.size} scrips to ${wl.name}!", Toast.LENGTH_SHORT).show()
                            }
                            showOcrDialog = false
                        },
                        modifier = Modifier.testTag("ocr_confirm_button")
                    ) {
                        Text("Add Selected")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showOcrDialog = false }
                ) {
                    Text(if (!isOcrLoading && ocrExtractedScrips.isNotEmpty()) "Cancel" else "Dismiss")
                }
            }
        )
    }
}
}
