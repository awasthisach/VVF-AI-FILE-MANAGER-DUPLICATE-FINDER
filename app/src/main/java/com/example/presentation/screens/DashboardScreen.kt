package com.example.presentation.screens

import android.Manifest

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.api.GeminiRepository
import com.example.data.database.FileMetadataRepository
import com.example.data.search.OnDeviceSemanticSearchEngine
import com.example.data.security.BiometricAuthHelper
import com.example.data.security.SecurityManager
import com.example.domain.model.LocalFile
import com.example.presentation.components.FileThumbnailView
import com.example.ui.theme.*
import kotlinx.coroutines.launch

enum class DashboardView {
    MY_FILES,
    RECENT,
    AI_ANALYSIS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    repository: FileMetadataRepository,
    geminiRepository: GeminiRepository,
    onNavigateToVault: () -> Unit,
    onNavigateToDuplicates: () -> Unit,
    onNavigateToCloud: () -> Unit,
    onNavigateToPlugins: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val securityManager = remember { SecurityManager(context) }
    
    // Sidebar Navigation & Dashboard Views State
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var currentView by remember { mutableStateOf(DashboardView.MY_FILES) }
    var isBatchAutoTagging by remember { mutableStateOf(false) }
    var selectedAiTagFilter by remember { mutableStateOf<String?>(null) }

    var selectedCategory by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }

    // Read from Room DB reactively based on chosen category tab (direct DB query)
    val localFilesList by remember(selectedCategory) {
        if (selectedCategory == "All") {
            repository.allFiles
        } else {
            repository.getFilesByCategory(selectedCategory)
        }
    }.collectAsState(initial = emptyList())

    // Collect all unique AI hashtags across files for the AI Tag Cloud
    val allAiTags = remember(localFilesList) {
        localFilesList.flatMap { file ->
            file.classification.split(" ", ",", "\n")
                .map { it.trim() }
                .filter { it.startsWith("#") && it.length > 1 }
        }.distinct()
    }
    
    // State for CRUD Dialogs
    var showAddDialog by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<LocalFile?>(null) }
    var showDeleteConfirmDialogForFile by remember { mutableStateOf<LocalFile?>(null) }
    var showFileDetailModalForFile by remember { mutableStateOf<LocalFile?>(null) }
    var showSecurityAuditModal by remember { mutableStateOf(false) }

    // State for SD Card & Storage Scan
    var isScanningSdCard by remember { mutableStateOf(false) }
    var showPermissionRationaleModal by remember { mutableStateOf(false) }
    var hasStoragePermission by remember { mutableStateOf(checkStoragePermissions(context)) }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            hasStoragePermission = true
            Toast.makeText(context, "अनुमति स्वीकृत! स्टोरेज व SD कार्ड स्कैन जारी...", Toast.LENGTH_SHORT).show()
            coroutineScope.launch {
                isScanningSdCard = true
                val count = scanAndIndexStorageFiles(context, repository)
                isScanningSdCard = false
                Toast.makeText(context, "$count नयी फ़ाइलें स्कैन व इंडेक्स हुईं!", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(context, "अनुमति अस्वीकृत! स्टोरेज परमिशन जरूरी है।", Toast.LENGTH_LONG).show()
        }
    }

    // Auto-run background storage scan if permission is present
    LaunchedEffect(hasStoragePermission) {
        if (hasStoragePermission && localFilesList.isEmpty()) {
            coroutineScope.launch {
                isScanningSdCard = true
                scanAndIndexStorageFiles(context, repository)
                isScanningSdCard = false
            }
        }
    }
    
    // State for Gemini AI Summarization
    var showSummaryDialogForFile by remember { mutableStateOf<LocalFile?>(null) }
    var summaryText by remember { mutableStateOf("") }
    var isSummarizing by remember { mutableStateOf(false) }

    // Sort states - supports organizing by Name, Date Modified, Size, and File Type
    var sortBy by remember { mutableStateOf("Date Modified") } // "Name", "Date Modified", "Size", "File Type"
    var isAscending by remember { mutableStateOf(false) }

    // State for Dynamic AI Classification & Categorization
    var isClassifying by remember { mutableStateOf(false) }
    var classifiedFileName by remember { mutableStateOf("") }
    var classifiedResultTag by remember { mutableStateOf("") }
    var showClassificationResultDialog by remember { mutableStateOf(false) }
    
    // Input form states for Upload / Add File with Gemini Auto-Categorization
    var newFileName by remember { mutableStateOf("") }
    var newFileContentPreview by remember { mutableStateOf("") }
    var newFileCategory by remember { mutableStateOf("Documents") }
    var isAiAutoCategorizeEnabled by remember { mutableStateOf(true) }
    var isAddingFileProgress by remember { mutableStateOf(false) }
    var renameInputName by remember { mutableStateOf("") }

    val searchEngine = remember { OnDeviceSemanticSearchEngine() }

    // On-device multi-layered semantic search engine on reactively loaded Room records
    val filteredFiles = remember(localFilesList, searchQuery, sortBy, isAscending) {
        val list = if (searchQuery.isBlank()) {
            localFilesList
        } else {
            searchEngine.search(localFilesList, searchQuery)
        }
        val sorted = when (sortBy) {
            "Name" -> list.sortedBy { it.name.lowercase() }
            "Size" -> list.sortedBy { parseSize(it.size) }
            "Date Modified", "Date Created" -> list.sortedBy { it.lastModified }
            "File Type" -> list.sortedBy { it.mimeType }
            else -> list
        }
        if (isAscending) sorted else sorted.reversed()
    }

    // Biometric gatekeeper callback for opening files with sensitive content
    val handleSensitiveFileAccess: (LocalFile) -> Unit = { file ->
        if (securityManager.isBiometricEnabled() || isSensitiveFile(file)) {
            BiometricAuthHelper.authenticateForSensitiveAccess(
                context = context,
                title = "🔒 " + file.name,
                subtitle = "संवेदनशील फ़ाइल जानकारी देखने के लिए बायोमेट्रिक प्रमाणीकरण आवश्यक है",
                onSuccess = {
                    showFileDetailModalForFile = file
                },
                onError = { err ->
                    Toast.makeText(context, "प्रमाणीकरण अस्वीकृत: $err", Toast.LENGTH_SHORT).show()
                }
            )
        } else {
            showFileDetailModalForFile = file
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = CosmicCard,
                drawerContentColor = TextPrimary
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CosmicSurface)
                        .padding(20.dp)
                ) {
                    Text(
                        text = "VVF Smart Manager",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = BhagwaOrange
                    )
                    Text(
                        text = "नेविगेशन साइडबार व एआई कंट्रोल",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
                HorizontalDivider(color = BorderColor)
                Spacer(modifier = Modifier.height(12.dp))

                NavigationDrawerItem(
                    label = { Text("मेरी फ़ाइलें (My Files)", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                    selected = currentView == DashboardView.MY_FILES,
                    onClick = {
                        currentView = DashboardView.MY_FILES
                        coroutineScope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Default.Folder, contentDescription = null, tint = BhagwaOrange) },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = BhagwaOrange.copy(alpha = 0.15f),
                        selectedTextColor = BhagwaOrange
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp).testTag("drawer_my_files")
                )

                NavigationDrawerItem(
                    label = { Text("हाल की फ़ाइलें (Recent)", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                    selected = currentView == DashboardView.RECENT,
                    onClick = {
                        currentView = DashboardView.RECENT
                        coroutineScope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Default.History, contentDescription = null, tint = SoftGold) },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = SoftGold.copy(alpha = 0.15f),
                        selectedTextColor = SoftGold
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp).testTag("drawer_recent")
                )

                NavigationDrawerItem(
                    label = { Text("Gemini AI विश्लेषण (AI Analysis)", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                    selected = currentView == DashboardView.AI_ANALYSIS,
                    onClick = {
                        currentView = DashboardView.AI_ANALYSIS
                        coroutineScope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = SkyCyan) },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = SkyCyan.copy(alpha = 0.15f),
                        selectedTextColor = SkyCyan
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp).testTag("drawer_ai_analysis")
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = BorderColor)

                Text(
                    text = "सुरक्षा व सिस्टम मॉड्यूल",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )

                NavigationDrawerItem(
                    label = { Text("सुरक्षित वॉल्ट", fontSize = 13.sp) },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        onNavigateToVault()
                    },
                    icon = { Icon(Icons.Default.EnhancedEncryption, contentDescription = null, tint = SoftGold) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                NavigationDrawerItem(
                    label = { Text("गूगल ड्राइव सिंक", fontSize = 13.sp) },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        onNavigateToCloud()
                    },
                    icon = { Icon(Icons.Default.CloudQueue, contentDescription = null, tint = BhagwaOrange) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                NavigationDrawerItem(
                    label = { Text("डुप्लीकेट क्लीनर", fontSize = 13.sp) },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        onNavigateToDuplicates()
                    },
                    icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = SkyCyan) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                NavigationDrawerItem(
                    label = { Text("प्लगइन मैनेजर", fontSize = 13.sp) },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        onNavigateToPlugins()
                    },
                    icon = { Icon(Icons.Default.Extension, contentDescription = null, tint = TextSecondary) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                NavigationDrawerItem(
                    label = { Text("Gemini API Key (सुरक्षा)", fontSize = 13.sp) },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        showApiKeyDialog = true
                    },
                    icon = { Icon(Icons.Default.Key, contentDescription = null, tint = BhagwaOrange) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = CosmicDarkBg,
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { 
                        newFileName = ""
                        newFileContentPreview = ""
                        newFileCategory = "Documents"
                        isAiAutoCategorizeEnabled = true
                        showAddDialog = true 
                    },
                    containerColor = BhagwaOrange,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("add_file_fab")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "नयी फ़ाइल जोड़ें")
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // App Header Bar with Sidebar Hamburger Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { coroutineScope.launch { drawerState.open() } },
                            modifier = Modifier
                                .background(CosmicCard, CircleShape)
                                .testTag("sidebar_hamburger_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "नेविगेशन साइडबार खोलें",
                                tint = BhagwaOrange
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = "VVF Smart Manager",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = BhagwaOrange
                            )
                            Text(
                                text = when(currentView) {
                                    DashboardView.MY_FILES -> "मेरी फ़ाइलें (My Files)"
                                    DashboardView.RECENT -> "हाल ही में संपादित फ़ाइलें"
                                    DashboardView.AI_ANALYSIS -> "Gemini AI विश्लेषण व ऑटो-टैगिंग"
                                },
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { showApiKeyDialog = true },
                            modifier = Modifier.background(CosmicCard, CircleShape).testTag("api_key_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = "Gemini API Key सेटिंग्स",
                                tint = if (geminiRepository.isUsingCustomKey()) SkyCyan else BhagwaOrange
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = onNavigateToPlugins,
                            modifier = Modifier.background(CosmicCard, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Extension,
                                contentDescription = "प्लगइन्स",
                                tint = SoftGold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Top View Selector Pills/Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(CosmicSurface)
                        .border(1.dp, BorderColor, RoundedCornerShape(10.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    DashboardView.values().forEach { view ->
                        val isSelected = currentView == view
                        val label = when(view) {
                            DashboardView.MY_FILES -> "मेरी फ़ाइलें"
                            DashboardView.RECENT -> "हाल ही में"
                            DashboardView.AI_ANALYSIS -> "AI विश्लेषण"
                        }
                        val icon = when(view) {
                            DashboardView.MY_FILES -> Icons.Default.Folder
                            DashboardView.RECENT -> Icons.Default.History
                            DashboardView.AI_ANALYSIS -> Icons.Default.AutoAwesome
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) BhagwaOrange else Color.Transparent)
                                .clickable { currentView = view }
                                .padding(vertical = 8.dp)
                                .testTag("top_view_tab_${view.name}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = if (isSelected) Color.White else TextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else TextSecondary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

            // CLEAR PERMISSION REQUEST BANNER (File System Access API & Storage Permissions)
            if (!hasStoragePermission) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .testTag("storage_permission_banner"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CosmicCard),
                    border = BorderStroke(1.dp, SoftGold.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(BhagwaOrange.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderSpecial,
                                contentDescription = "Permission",
                                tint = BhagwaOrange,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "फ़ाइल एक्सेस अनुमति आवश्यक (Storage Permission)",
                                color = BhagwaOrange,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "ऑफ़लाइन फ़ाइलों को ऑटो-कैटेगराइज़ करने के लिए स्टोरेज एक्सेस दें।",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                        Button(
                            onClick = { permissionLauncher.launch(getRequiredStoragePermissions()) },
                            colors = ButtonDefaults.buttonColors(containerColor = BhagwaOrange),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("अनुमति दें", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            when (currentView) {
                    DashboardView.MY_FILES -> {
                        // Real-time Summary Cards Section at Top (Total Files, Total Storage Used, AI Category Counts)
                        SummaryCardsSection(files = localFilesList)

                        Spacer(modifier = Modifier.height(12.dp))

                        // Quick Access Security Panel (Secure Vault & Duplicate Cleaner)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SecurityQuickCard(
                                title = "सुरक्षित वॉल्ट",
                                subtitle = "निजी फाइलों को एन्क्रिप्ट करें",
                                icon = Icons.Default.EnhancedEncryption,
                                color = SoftGold,
                                modifier = Modifier.weight(1f),
                                onClick = onNavigateToVault
                            )
                            SecurityQuickCard(
                                title = "डुप्लीकेट क्लीनर",
                                subtitle = "सिमिलरिटी स्लाइडर से खोजें",
                                icon = Icons.Default.DeleteSweep,
                                color = BhagwaOrange,
                                modifier = Modifier.weight(1f),
                                onClick = onNavigateToDuplicates
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Quick Access System Storage Scanner & Google Drive Sync Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SecurityQuickCard(
                                title = if (isScanningSdCard) "स्कैनिंग जारी..." else "SD कार्ड/स्टोरेज स्कैन",
                                subtitle = "स्टोरेज फाइलें ढूंढें व इंडेक्स करें",
                                icon = Icons.Default.SdCard,
                                color = SkyCyan,
                                modifier = Modifier.weight(1f).testTag("sd_card_scan_card"),
                                onClick = {
                                    val hasPermission = checkStoragePermissions(context)
                                    if (hasPermission) {
                                        coroutineScope.launch {
                                            isScanningSdCard = true
                                            val count = scanAndIndexStorageFiles(context, repository)
                                            isScanningSdCard = false
                                            Toast.makeText(context, "$count नयी फ़ाइलें स्कैन व इंडेक्स हुईं!", Toast.LENGTH_LONG).show()
                                        }
                                    } else {
                                        showPermissionRationaleModal = true
                                    }
                                }
                            )
                            SecurityQuickCard(
                                title = "गूगल ड्राइव सिंक",
                                subtitle = "गूगल अकाउंट कनेक्ट करें",
                                icon = Icons.Default.CloudQueue,
                                color = BhagwaOrange,
                                modifier = Modifier.weight(1f).testTag("google_drive_card"),
                                onClick = onNavigateToCloud
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Real-time Security Health Audit Status Banner
                        val auditReport = remember(securityManager) {
                            securityManager.rulesEngine.AuditSecurityHealth(
                                isPinSet = securityManager.isPinSet(),
                                isBiometricEnabled = securityManager.isBiometricEnabled()
                            )
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showSecurityAuditModal = true }
                                .testTag("security_audit_banner"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = CosmicCard),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SoftGold.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(BhagwaOrange.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Shield,
                                            contentDescription = "Security Audit",
                                            tint = SoftGold,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(
                                                text = "सुरक्षा हेल्थ स्कोर: ${auditReport.healthScore}%",
                                                color = SoftGold,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = "• एआई ऑडिट एक्टिव",
                                                color = SkyCyan,
                                                fontSize = 10.sp
                                            )
                                        }
                                        Text(
                                            text = "AES-256 GCM • एंटी-ब्रूट-फोर्स • इनपुट सेनेटाइजर",
                                            color = TextSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "Details",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Real-time Search Field
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("ऑन-डिवाइस सिमेंटिक खोज (जैसे: 'बिल', 'फोटो', 'ऑडियो')...", color = TextSecondary) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = SoftGold) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear Search", tint = TextSecondary)
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("file_search_input"),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BhagwaOrange,
                                unfocusedBorderColor = BorderColor,
                                focusedContainerColor = CosmicCard,
                                unfocusedContainerColor = CosmicCard,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Horizontal Scrollable Categories Filter Tabs
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val categories = listOf("All", "Images", "Documents", "Audio", "Video")
                            categories.forEach { category ->
                                val isSelected = selectedCategory == category
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(if (isSelected) BhagwaOrange else CosmicCard)
                                        .border(1.dp, if (isSelected) BhagwaOrange else BorderColor, RoundedCornerShape(20.dp))
                                        .clickable { selectedCategory = category }
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                        .testTag("category_tab_$category"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = category,
                                        color = if (isSelected) Color.White else TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // File Listing with sorting controls
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (searchQuery.isBlank()) "फ़ाइल सूची (${filteredFiles.size})" else "खोज परिणाम (${filteredFiles.size}/${localFilesList.size})",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary
                            )
                            
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FilterList,
                                    contentDescription = "Sort",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                listOf("Name", "Date Modified", "Size", "File Type").forEach { option ->
                                    val isSelected = sortBy == option
                                    val displayName = when (option) {
                                        "Name" -> "नाम"
                                        "Date Modified" -> "दिनांक"
                                        "Size" -> "आकार"
                                        "File Type" -> "प्रकार"
                                        else -> option
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSelected) BhagwaOrange.copy(alpha = 0.2f) else Color.Transparent)
                                            .border(1.dp, if (isSelected) BhagwaOrange else Color.Transparent, RoundedCornerShape(6.dp))
                                            .clickable { sortBy = option }
                                            .padding(horizontal = 6.dp, vertical = 4.dp)
                                            .testTag("sort_chip_$option"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = displayName,
                                            color = if (isSelected) BhagwaOrange else TextSecondary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { isAscending = !isAscending },
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(CosmicCard, CircleShape)
                                        .testTag("sort_direction_toggle")
                                ) {
                                    Icon(
                                        imageVector = if (isAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                        contentDescription = if (isAscending) "A-Z/Smallest/Oldest" else "Z-A/Largest/Newest",
                                        tint = BhagwaOrange,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        if (filteredFiles.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = "Empty", tint = TextSecondary, modifier = Modifier.size(64.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("कोई फ़ाइल नहीं मिली", color = TextSecondary, fontSize = 14.sp)
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(filteredFiles, key = { it.id }) { file ->
                                    FileItemRow(
                                        file = file,
                                        onFileClick = { handleSensitiveFileAccess(file) },
                                        onRenameClick = {
                                            renameInputName = file.name
                                            showRenameDialog = file
                                        },
                                        onDeleteClick = { showDeleteConfirmDialogForFile = file },
                                        onSummarizeClick = {
                                            showSummaryDialogForFile = file
                                            summaryText = ""
                                            isSummarizing = true
                                            coroutineScope.launch {
                                                val simulatedContent = getSimulatedContentForFile(file.name, file.category)
                                                val summary = geminiRepository.summarizeFileContent(file.name, simulatedContent)
                                                summaryText = summary
                                                isSummarizing = false
                                            }
                                        },
                                        onClassifyClick = {
                                            isClassifying = true
                                            classifiedFileName = file.name
                                            classifiedResultTag = ""
                                            showClassificationResultDialog = true
                                            coroutineScope.launch {
                                                val tags = geminiRepository.generateDescriptiveTags(
                                                    fileName = file.name,
                                                    contentSnippet = getSimulatedContentForFile(file.name, file.category),
                                                    category = file.category
                                                )
                                                val tagResult = tags.joinToString(" ")
                                                repository.updateFile(file.copy(classification = tagResult))
                                                classifiedResultTag = tagResult
                                                isClassifying = false
                                                Toast.makeText(context, "AI टैग्स जनरेट हुए: $tagResult", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    DashboardView.RECENT -> {
                        // Recent Files View
                        val recentFilesList = remember(localFilesList) {
                            localFilesList.sortedByDescending { it.lastModified }
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = CosmicCard),
                            border = BorderStroke(1.dp, SoftGold.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(SoftGold.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.History, contentDescription = null, tint = SoftGold)
                                }
                                Column {
                                    Text("हाल ही में जोड़ी गई / संपादित फ़ाइलें", color = SoftGold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("नवीनतम संशोधन तिथि के आधार पर क्रमित फ़ाइल सूची", color = TextSecondary, fontSize = 11.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (recentFilesList.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("हाल में कोई फ़ाइल उपलब्ध नहीं है", color = TextSecondary)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(recentFilesList, key = { it.id }) { file ->
                                    FileItemRow(
                                        file = file,
                                        onFileClick = { handleSensitiveFileAccess(file) },
                                        onRenameClick = {
                                            renameInputName = file.name
                                            showRenameDialog = file
                                        },
                                        onDeleteClick = { showDeleteConfirmDialogForFile = file },
                                        onSummarizeClick = {
                                            showSummaryDialogForFile = file
                                            summaryText = ""
                                            isSummarizing = true
                                            coroutineScope.launch {
                                                val simulatedContent = getSimulatedContentForFile(file.name, file.category)
                                                val summary = geminiRepository.summarizeFileContent(file.name, simulatedContent)
                                                summaryText = summary
                                                isSummarizing = false
                                            }
                                        },
                                        onClassifyClick = {
                                            coroutineScope.launch {
                                                val tags = geminiRepository.generateDescriptiveTags(
                                                    fileName = file.name,
                                                    contentSnippet = getSimulatedContentForFile(file.name, file.category),
                                                    category = file.category
                                                )
                                                val tagResult = tags.joinToString(" ")
                                                repository.updateFile(file.copy(classification = tagResult))
                                                Toast.makeText(context, "AI टैग्स अद्यतन: $tagResult", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    DashboardView.AI_ANALYSIS -> {
                        // Gemini AI Analysis & Auto-Tagging View
                        val displayAiFiles = remember(localFilesList, selectedAiTagFilter) {
                            if (selectedAiTagFilter == null) {
                                localFilesList
                            } else {
                                localFilesList.filter { file ->
                                    file.classification.contains(selectedAiTagFilter!!, ignoreCase = true)
                                }
                            }
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = CosmicCard),
                            border = BorderStroke(1.dp, SkyCyan.copy(alpha = 0.6f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(SkyCyan.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = "AI Auto-Tagging",
                                            tint = SkyCyan,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Gemini AI स्मार्ट ऑटो-टैगिंग इंजन",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = SkyCyan
                                        )
                                        Text(
                                            text = "मेटाडेटा व कंटेंट स्निपेट का विश्लेषण करके स्वतः 3-5 विवरणशील हैशटैग जनरेट करें",
                                            fontSize = 11.sp,
                                            color = TextSecondary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                if (isBatchAutoTagging) {
                                    LinearProgressIndicator(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = BhagwaOrange,
                                        trackColor = CosmicSurface
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "जेमिनी एआई ऑटो-टैगिंग व विश्लेषण जारी है...",
                                        fontSize = 11.sp,
                                        color = SoftGold,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    Button(
                                        onClick = {
                                            coroutineScope.launch {
                                                isBatchAutoTagging = true
                                                Toast.makeText(context, "बैच जेमिनी एआई ऑटो-टैगिंग शुरू...", Toast.LENGTH_SHORT).show()
                                                var count = 0
                                                localFilesList.forEach { file ->
                                                    val tags = geminiRepository.generateDescriptiveTags(
                                                        fileName = file.name,
                                                        contentSnippet = getSimulatedContentForFile(file.name, file.category),
                                                        category = file.category
                                                    )
                                                    val tagStr = tags.joinToString(" ")
                                                    repository.updateFile(file.copy(classification = tagStr))
                                                    count++
                                                }
                                                isBatchAutoTagging = false
                                                Toast.makeText(context, "$count फ़ाइलों का जेमिनी एआई ऑटो-टैगिंग पूर्ण!", Toast.LENGTH_LONG).show()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().testTag("batch_ai_tagging_button"),
                                        colors = ButtonDefaults.buttonColors(containerColor = SkyCyan),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = CosmicDarkBg, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("सभी फ़ाइलों का AI ऑटो-टैगिंग चलाएं", color = CosmicDarkBg, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Hashtags Tag Cloud
                        Text("Gemini AI हैशटैग क्लस्टर (फिल्टर करने के लिए टैप करें):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SoftGold)
                        Spacer(modifier = Modifier.height(6.dp))

                        if (allAiTags.isNotEmpty()) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                item {
                                    FilterChip(
                                        selected = selectedAiTagFilter == null,
                                        onClick = { selectedAiTagFilter = null },
                                        label = { Text("सभी फ़ाइलें (${localFilesList.size})", fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = BhagwaOrange,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                                items(allAiTags) { tag ->
                                    val isSelected = selectedAiTagFilter == tag
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            selectedAiTagFilter = if (isSelected) null else tag
                                        },
                                        label = { Text(tag, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = SoftGold,
                                            selectedLabelColor = CosmicDarkBg,
                                            containerColor = CosmicSurface,
                                            labelColor = TextPrimary
                                        )
                                    )
                                }
                            }
                        } else {
                            Text("अभी कोई एआई हैशटैग जनरेट नहीं हुआ है। ऊपर दिए बटन से ऑटो-टैगिंग चलाएं।", fontSize = 11.sp, color = TextSecondary)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (selectedAiTagFilter == null) "एआई वर्गीकृत फ़ाइलें (${displayAiFiles.size})" else "टैग '$selectedAiTagFilter' फ़ाइलें (${displayAiFiles.size})",
                                color = TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(displayAiFiles, key = { it.id }) { file ->
                                FileItemRow(
                                    file = file,
                                    onFileClick = { handleSensitiveFileAccess(file) },
                                    onRenameClick = {
                                        renameInputName = file.name
                                        showRenameDialog = file
                                    },
                                    onDeleteClick = { showDeleteConfirmDialogForFile = file },
                                    onSummarizeClick = {
                                        showSummaryDialogForFile = file
                                        summaryText = ""
                                        isSummarizing = true
                                        coroutineScope.launch {
                                            val simulatedContent = getSimulatedContentForFile(file.name, file.category)
                                            val summary = geminiRepository.summarizeFileContent(file.name, simulatedContent)
                                            summaryText = summary
                                            isSummarizing = false
                                        }
                                    },
                                    onClassifyClick = {
                                        coroutineScope.launch {
                                            val tags = geminiRepository.generateDescriptiveTags(
                                                fileName = file.name,
                                                contentSnippet = getSimulatedContentForFile(file.name, file.category),
                                                category = file.category
                                            )
                                            val tagResult = tags.joinToString(" ")
                                            repository.updateFile(file.copy(classification = tagResult))
                                            Toast.makeText(context, "AI टैग्स: $tagResult", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                        }
                    }
                    }
                }
            }
        }

        // 1. ADD / UPLOAD FILE DIALOG WITH GEMINI AUTO-CATEGORIZATION
        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { 
                    if (!isAddingFileProgress) {
                        showAddDialog = false 
                    }
                },
                containerColor = CosmicCard,
                title = { Text("नयी फ़ाइल अपलोड करें", color = BhagwaOrange) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = newFileName,
                            onValueChange = { newFileName = it },
                            label = { Text("फ़ाइल का नाम (e.g. Project_Invoice.pdf)", color = TextSecondary) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BhagwaOrange,
                                unfocusedBorderColor = BorderColor,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("add_file_name_input")
                        )

                        OutlinedTextField(
                            value = newFileContentPreview,
                            onValueChange = { newFileContentPreview = it },
                            label = { Text("कंटेंट विवरण (Gemini AI कैटेगराइज़ेशन हेतु)", color = TextSecondary) },
                            singleLine = false,
                            maxLines = 3,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SoftGold,
                                unfocusedBorderColor = BorderColor,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("add_file_content_input")
                        )

                        // Toggle Gemini Auto-Categorization
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(CosmicSurface)
                                .clickable { isAiAutoCategorizeEnabled = !isAiAutoCategorizeEnabled }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "AI",
                                    tint = SoftGold,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Gemini AI ऑटो-कैटेगराइज़", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                            Switch(
                                checked = isAiAutoCategorizeEnabled,
                                onCheckedChange = { isAiAutoCategorizeEnabled = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = BhagwaOrange,
                                    checkedTrackColor = BhagwaOrange.copy(alpha = 0.3f)
                                )
                            )
                        }

                        if (!isAiAutoCategorizeEnabled) {
                            Text("मैन्युअल श्रेणी चुनें (Category):", color = TextSecondary, fontSize = 13.sp)
                            val catOptions = listOf("Documents", "Images", "Audio", "Video")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                catOptions.forEach { cat ->
                                    val isSelected = newFileCategory == cat
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) BhagwaOrange else CosmicSurface)
                                            .clickable { newFileCategory = cat }
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Text(cat, color = Color.White, fontSize = 11.sp)
                                    }
                                }
                            }
                        }

                        if (isAddingFileProgress) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                CircularProgressIndicator(color = SoftGold, modifier = Modifier.size(20.dp))
                                Text("Gemini API फ़ाइल कैटेगराइज़ कर रहा है...", color = SoftGold, fontSize = 12.sp)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newFileName.isNotBlank()) {
                                isAddingFileProgress = true
                                coroutineScope.launch {
                                    val fileId = "f_" + System.currentTimeMillis()
                                    val randSize = "${(1..12).random()}.${(0..9).random()} MB"

                                    var finalCategory = newFileCategory
                                    var finalClassification = "Unclassified"

                                    if (isAiAutoCategorizeEnabled) {
                                        // Invoke Gemini API to analyze metadata and content preview for category and classification
                                        finalCategory = geminiRepository.categorizeFile(
                                            fileName = newFileName,
                                            contentPreview = newFileContentPreview.ifBlank { newFileName }
                                        )
                                        
                                        val mime = when (finalCategory) {
                                            "Images" -> "PNG"
                                            "Audio" -> "MP3"
                                            "Video" -> "MP4"
                                            else -> "PDF"
                                        }

                                        finalClassification = geminiRepository.classifyFile(
                                            fileName = newFileName,
                                            size = randSize,
                                            mimeType = mime,
                                            category = finalCategory
                                        )
                                    }

                                    val mime = when (finalCategory) {
                                        "Images" -> "PNG"
                                        "Audio" -> "MP3"
                                        "Video" -> "MP4"
                                        else -> "PDF"
                                    }

                                    repository.insertFile(
                                        LocalFile(
                                            id = fileId,
                                            name = newFileName,
                                            size = randSize,
                                            mimeType = mime,
                                            category = finalCategory,
                                            classification = finalClassification,
                                            lastModified = "21 July 2026"
                                        )
                                    )

                                    isAddingFileProgress = false
                                    showAddDialog = false
                                    Toast.makeText(
                                        context, 
                                        "फ़ाइल ऑटो-कैटेगराइज़ की गई: [$finalCategory / $finalClassification]", 
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BhagwaOrange),
                        enabled = !isAddingFileProgress
                    ) {
                        Text("अपलोड व कैटेगराइज़ करें")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showAddDialog = false },
                        enabled = !isAddingFileProgress
                    ) {
                        Text("रद्द करें", color = TextSecondary)
                    }
                }
            )
        }

        // 2. RENAME FILE DIALOG
        showRenameDialog?.let { fileToRename ->
            AlertDialog(
                onDismissRequest = { showRenameDialog = null },
                containerColor = CosmicCard,
                title = { Text("फ़ाइल का नाम बदलें", color = BhagwaOrange) },
                text = {
                    OutlinedTextField(
                        value = renameInputName,
                        onValueChange = { renameInputName = it },
                        label = { Text("नया नाम दर्ज करें", color = TextSecondary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BhagwaOrange,
                            unfocusedBorderColor = BorderColor,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("rename_file_input")
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (renameInputName.isNotBlank()) {
                                coroutineScope.launch {
                                    repository.updateFile(fileToRename.copy(name = renameInputName))
                                }
                                showRenameDialog = null
                                Toast.makeText(context, "नाम सफलतापूर्वक बदला गया!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BhagwaOrange)
                    ) {
                        Text("अपडेट करें")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialog = null }) {
                        Text("रद्द करें", color = TextSecondary)
                    }
                }
            )
        }

        // 3. GEMINI SUMMARY DIALOG
        showSummaryDialogForFile?.let { file ->
            AlertDialog(
                onDismissRequest = { 
                    if (!isSummarizing) {
                        showSummaryDialogForFile = null 
                    }
                },
                containerColor = CosmicCard,
                icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "AI", tint = SoftGold, modifier = Modifier.size(36.dp)) },
                title = { Text(file.name, color = BhagwaOrange, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (isSummarizing) {
                            CircularProgressIndicator(color = BhagwaOrange, modifier = Modifier.padding(16.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("जेमिनी एआई द्वारा विश्लेषण जारी है...", color = TextSecondary, fontSize = 13.sp)
                        } else {
                            Text(
                                text = summaryText,
                                color = TextPrimary,
                                fontSize = 14.sp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showSummaryDialogForFile = null },
                        colors = ButtonDefaults.buttonColors(containerColor = BhagwaOrange),
                        enabled = !isSummarizing
                    ) {
                        Text("ठीक है", color = Color.White)
                    }
                }
            )
        }

        // 4. GEMINI CLASSIFICATION DIALOG
        if (showClassificationResultDialog) {
            AlertDialog(
                onDismissRequest = { 
                    if (!isClassifying) {
                        showClassificationResultDialog = false 
                    }
                },
                containerColor = CosmicCard,
                icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "AI", tint = SoftGold, modifier = Modifier.size(36.dp)) },
                title = { Text("AI वर्गीकरण परिणाम (Classification)", color = BhagwaOrange, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(classifiedFileName, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        if (isClassifying) {
                            CircularProgressIndicator(color = BhagwaOrange, modifier = Modifier.padding(16.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("जेमिनी एआई द्वारा मेटाडेटा विश्लेषण जारी है...", color = TextSecondary, fontSize = 13.sp)
                        } else {
                            val tagColor = when (classifiedResultTag) {
                                "Work" -> SkyCyan
                                "Personal" -> EmeraldGreen
                                "Finance" -> SoftGold
                                else -> TextSecondary
                            }
                            Text("श्रेणी (Folder Tag):", color = TextSecondary, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(tagColor.copy(alpha = 0.2f))
                                    .border(1.dp, tagColor, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = classifiedResultTag,
                                    color = tagColor,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "इस फ़ाइल को स्वचालित रूप से '$classifiedResultTag' फ़ोल्डर टैग के अंतर्गत सुरक्षित कर दिया गया है।",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showClassificationResultDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = BhagwaOrange),
                        enabled = !isClassifying
                    ) {
                        Text("ठीक है", color = Color.White)
                    }
                }
            )
        }

        // 5. PERMANENT DELETE CONFIRMATION DIALOG MODAL
        showDeleteConfirmDialogForFile?.let { fileToDelete ->
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialogForFile = null },
                containerColor = CosmicCard,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = BhagwaOrange,
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = {
                    Text(
                        text = "स्थायी रूप से हटाएं?",
                        color = BhagwaOrange,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "'${fileToDelete.name}' को डेटाबेस व लोकल स्टोरेज से स्थायी रूप से हटा दिया जाएगा।",
                            color = TextPrimary,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "क्या आप आश्वस्त हैं? इस क्रिया के बाद फ़ाइल डेटा वापस प्राप्त नहीं किया जा सकता।",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                repository.deleteFile(fileToDelete.id)
                            }
                            val deletedName = fileToDelete.name
                            showDeleteConfirmDialogForFile = null
                            Toast.makeText(context, "'$deletedName' को स्थायी रूप से हटा दिया गया!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("confirm_delete_button")
                    ) {
                        Text("हाँ, स्थायी रूप से हटाएं", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showDeleteConfirmDialogForFile = null },
                        modifier = Modifier.testTag("cancel_delete_button")
                    ) {
                        Text("रद्द करें", color = TextSecondary)
                    }
                }
            )
        }

        // 6. FULL SYSTEM SECURITY HEALTH AUDIT REPORT MODAL
        if (showSecurityAuditModal) {
            val report = securityManager.rulesEngine.AuditSecurityHealth(
                isPinSet = securityManager.isPinSet(),
                isBiometricEnabled = securityManager.isBiometricEnabled()
            )

            AlertDialog(
                onDismissRequest = { showSecurityAuditModal = false },
                containerColor = CosmicCard,
                icon = {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = "Audit",
                        tint = SoftGold,
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = {
                    Text(
                        text = "VVF स्मार्ट मैनेजर - सुरक्षा ऑडिट",
                        color = BhagwaOrange,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("सुरक्षा हेल्थ स्कोर:", color = TextPrimary, fontSize = 14.sp)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(EmeraldGreen.copy(alpha = 0.2f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${report.healthScore}% - सुरक्षित",
                                    color = EmeraldGreen,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        HorizontalDivider(color = BorderColor)

                        Text("सक्रिय सुरक्षा नियम:", color = SoftGold, fontWeight = FontWeight.Bold, fontSize = 13.sp)

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(16.dp))
                                Text("AES-256 GCM एन्क्रिप्शन (डाटा ऐट रेस्ट)", color = TextPrimary, fontSize = 12.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(16.dp))
                                Text("एन्टी-ब्रूट-फ़ोर्स सुरक्षा (5 प्रयास सीमा)", color = TextPrimary, fontSize = 12.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(16.dp))
                                Text("इनपुट न्यूट्रलाइजेशन (पाथ / SQL इंजेक्शन सुरक्षा)", color = TextPrimary, fontSize = 12.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(if (report.isPinSet) Icons.Default.CheckCircle else Icons.Default.Warning, contentDescription = null, tint = if (report.isPinSet) EmeraldGreen else BhagwaOrange, modifier = Modifier.size(16.dp))
                                Text("मास्टर सुरक्षा पिन: ${if (report.isPinSet) "सक्रिय" else "अधूरी"}", color = TextPrimary, fontSize = 12.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(if (report.isBiometricEnabled) Icons.Default.CheckCircle else Icons.Default.Warning, contentDescription = null, tint = if (report.isBiometricEnabled) EmeraldGreen else BhagwaOrange, modifier = Modifier.size(16.dp))
                                Text("बायोमेट्रिक प्रमाणीकरण: ${if (report.isBiometricEnabled) "सक्रिय" else "अधूरी"}", color = TextPrimary, fontSize = 12.sp)
                            }
                        }

                        HorizontalDivider(color = BorderColor)

                        Text("सिफारिशें:", color = SkyCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        for (rec in report.recommendations) {
                            Text("• $rec", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showSecurityAuditModal = false },
                        colors = ButtonDefaults.buttonColors(containerColor = BhagwaOrange)
                    ) {
                        Text("बंद करें", color = Color.White)
                    }
                }
            )
        }

        // 7. STORAGE / SD CARD PERMISSION RATIONALE DIALOG
        if (showPermissionRationaleModal) {
            AlertDialog(
                onDismissRequest = { showPermissionRationaleModal = false },
                containerColor = CosmicCard,
                icon = {
                    Icon(
                        imageVector = Icons.Default.FolderSpecial,
                        contentDescription = "Permission",
                        tint = SoftGold,
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = {
                    Text(
                        text = "स्टोरेज व SD कार्ड अनुमति आवश्यक",
                        color = BhagwaOrange,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "VVF Smart Manager को आपके फोन और SD कार्ड में मौजूद दस्तावेजों, फोटो और मीडिया फाइलों को स्कैन व इंडेक्स करने के लिए स्टोरेज परमिशन की आवश्यकता है।",
                            color = TextPrimary,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "• सभी फाइलें केवल स्थानीय रूप से आपके फोन में प्रोसेस की जाती हैं।\n• कोई भी फाइल बिना आपकी अनुमति के किसी सर्वर पर अपलोड नहीं की जाती।",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showPermissionRationaleModal = false
                            val perms = if (android.os.Build.VERSION.SDK_INT >= 33) {
                                arrayOf(
                                    android.Manifest.permission.READ_MEDIA_IMAGES,
                                    android.Manifest.permission.READ_MEDIA_VIDEO,
                                    android.Manifest.permission.READ_MEDIA_AUDIO,
                                    android.Manifest.permission.POST_NOTIFICATIONS
                                )
                            } else {
                                arrayOf(
                                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                                )
                            }
                            permissionLauncher.launch(perms)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BhagwaOrange)
                    ) {
                        Text("अनुमति दें", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPermissionRationaleModal = false }) {
                        Text("रद्द करें", color = TextSecondary)
                    }
                }
            )
        }

        // 7. FILE DETAIL & INTERACTIVE FILE EXPLORER MODAL
        if (showFileDetailModalForFile != null) {
            val detFile = showFileDetailModalForFile!!
            AlertDialog(
                onDismissRequest = { showFileDetailModalForFile = null },
                containerColor = CosmicCard,
                icon = {
                    Icon(
                        imageVector = when (detFile.category) {
                            "Images" -> Icons.Default.Image
                            "Audio" -> Icons.Default.AudioFile
                            "Video" -> Icons.Default.VideoFile
                            else -> Icons.Default.Description
                        },
                        contentDescription = detFile.category,
                        tint = BhagwaOrange,
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = {
                    Text(
                        text = detFile.name,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // High-res Thumbnail Preview
                        FileThumbnailView(
                            file = detFile,
                            size = 120.dp,
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                            border = BorderStroke(1.dp, BorderColor)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("फ़ाइल आकार (Size):", color = TextSecondary, fontSize = 12.sp)
                                    Text(detFile.size, color = BhagwaOrange, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("श्रेणी (Category):", color = TextSecondary, fontSize = 12.sp)
                                    Text(detFile.category, color = SoftGold, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("AI टैग (Tag):", color = TextSecondary, fontSize = 12.sp)
                                    Text(if (detFile.classification.isBlank()) "Unclassified" else detFile.classification, color = SkyCyan, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("MIME प्रकार:", color = TextSecondary, fontSize = 12.sp)
                                    Text(detFile.mimeType, color = TextPrimary, fontSize = 11.sp)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("अंतिम परिवर्तन:", color = TextSecondary, fontSize = 12.sp)
                                    Text(detFile.lastModified, color = TextPrimary, fontSize = 11.sp)
                                }
                            }
                        }

                        // Interactive Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    openLocalFile(context, detFile)
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = BhagwaOrange),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("खोलें", fontSize = 12.sp)
                            }

                            Button(
                                onClick = {
                                    shareLocalFile(context, detFile)
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = SoftGold),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Share", tint = CosmicDarkBg, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("शेयर करें", fontSize = 12.sp, color = CosmicDarkBg, fontWeight = FontWeight.Bold)
                            }
                        }

                        // AI Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val f = detFile
                                    showFileDetailModalForFile = null
                                    showSummaryDialogForFile = f
                                    summaryText = ""
                                    isSummarizing = true
                                    coroutineScope.launch {
                                        val simulatedContent = getSimulatedContentForFile(f.name, f.category)
                                        val summary = geminiRepository.summarizeFileContent(f.name, simulatedContent)
                                        summaryText = summary
                                        isSummarizing = false
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                border = BorderStroke(1.dp, SoftGold),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = "Summarize", tint = SoftGold, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("AI सारांश", fontSize = 11.sp, color = SoftGold)
                            }

                            OutlinedButton(
                                onClick = {
                                    val f = detFile
                                    showFileDetailModalForFile = null
                                    showDeleteConfirmDialogForFile = f
                                },
                                modifier = Modifier.weight(1f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("हटाएं", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showFileDetailModalForFile = null }) {
                        Text("बंद करें", color = TextSecondary)
                    }
                }
            )
        }

        if (showApiKeyDialog) {
            GeminiApiKeyDialog(
                geminiRepository = geminiRepository,
                onDismiss = { showApiKeyDialog = false }
            )
        }
    }



@Composable
fun GeminiApiKeyDialog(
    geminiRepository: GeminiRepository,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var apiKeyInput by remember { mutableStateOf(geminiRepository.getSavedCustomApiKey()) }
    var isKeyVisible by remember { mutableStateOf(false) }
    var isKeySaved by remember { mutableStateOf(geminiRepository.isUsingCustomKey()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CosmicCard,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = BhagwaOrange,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Gemini API Key सेटिंग्स",
                    color = BhagwaOrange,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column {
                Card(
                    colors = CardDefaults.cardColors(containerColor = BhagwaOrange.copy(alpha = 0.12f)),
                    border = BorderStroke(1.dp, BhagwaOrange.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "🛡️ सुरक्षा चेतावनी (Security Notice):",
                            color = BhagwaOrange,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "APK को अन्य टेस्टर्स या यूज़र्स को देते समय अपनी निजी API Key को ऐप में हार्डकोड न छोड़ें। यहाँ दर्ज की गई Key केवल आपके स्थानीय डिवाइस पर सुरक्षित रहेगी।",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }

                Text(
                    text = if (isKeySaved) "✅ कस्टम API Key सक्रिय है" else "⚠️ वर्तमान में कोई कस्टम Key सेट नहीं है",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isKeySaved) SkyCyan else SoftGold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    label = { Text("Gemini API Key (AIzaSy...)", fontSize = 12.sp) },
                    placeholder = { Text("Google AI Studio से प्राप्त Key डालें") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BhagwaOrange,
                        unfocusedBorderColor = BorderColor,
                        focusedLabelColor = BhagwaOrange,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    trailingIcon = {
                        IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                            Icon(
                                imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle Visibility",
                                tint = TextSecondary
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("gemini_api_key_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "निःशुल्क API Key प्राप्त करने के लिए aistudio.google.com पर जाएँ।",
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (apiKeyInput.trim().isNotEmpty()) {
                        geminiRepository.saveCustomApiKey(apiKeyInput.trim())
                        isKeySaved = true
                        Toast.makeText(context, "API Key सफलता से सेव की गई!", Toast.LENGTH_SHORT).show()
                    } else {
                        geminiRepository.clearCustomApiKey()
                        isKeySaved = false
                        Toast.makeText(context, "API Key हटा दी गई।", Toast.LENGTH_SHORT).show()
                    }
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = BhagwaOrange),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("सहेजें (Save Key)", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                if (isKeySaved) {
                    TextButton(
                        onClick = {
                            geminiRepository.clearCustomApiKey()
                            apiKeyInput = ""
                            isKeySaved = false
                            Toast.makeText(context, "API Key साफ़ कर दी गई।", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("हटाएं (Clear)", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("रद्द करें", color = TextSecondary, fontSize = 12.sp)
                }
            }
        }
    )
}

// Summary Cards Section Component - displaying Total Files, Total Storage Used, and AI Category Breakdown
@Composable
fun SummaryCardsSection(files: List<LocalFile>) {
    val totalFiles = files.size
    val totalStorageStr = calculateTotalStorageFormatted(files)

    val docsCount = files.count { it.category.equals("Documents", ignoreCase = true) }
    val imgsCount = files.count { it.category.equals("Images", ignoreCase = true) }
    val audioCount = files.count { it.category.equals("Audio", ignoreCase = true) }
    val videoCount = files.count { it.category.equals("Video", ignoreCase = true) }

    val workCount = files.count { it.classification.equals("Work", ignoreCase = true) }
    val personalCount = files.count { it.classification.equals("Personal", ignoreCase = true) }
    val financeCount = files.count { it.classification.equals("Finance", ignoreCase = true) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // First Row: Total Files & Total Storage Used Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Card 1: Total Files
            Card(
                modifier = Modifier
                    .weight(1f)
                    .testTag("summary_total_files_card"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CosmicCard)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("कुल फ़ाइलें", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        Icon(Icons.Default.Folder, contentDescription = "Total Files", tint = SoftGold, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("$totalFiles", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("ऑफ़लाइन डेटाबेस", color = TextSecondary, fontSize = 10.sp)
                }
            }

            // Card 2: Total Storage Used
            Card(
                modifier = Modifier
                    .weight(1f)
                    .testTag("summary_storage_used_card"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CosmicCard)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("प्रयुक्त स्टोरेज", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        Icon(Icons.Default.Storage, contentDescription = "Storage Used", tint = BhagwaOrange, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(totalStorageStr, color = BhagwaOrange, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("एग्रिगेटेड साइज़", color = TextSecondary, fontSize = 10.sp)
                }
            }
        }

        // Second Row: AI Category Breakdown Cards
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("summary_ai_categories_card"),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = CosmicCard)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "AI Categories", tint = SoftGold, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("AI डिटेक्डेड कैटेगरीज व काउंट्स", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("Gemini AI", color = SoftGold, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item { CategoryPillCard(label = "Documents", count = docsCount, color = SoftGold, icon = Icons.Default.Description) }
                    item { CategoryPillCard(label = "Images", count = imgsCount, color = SkyCyan, icon = Icons.Default.Image) }
                    item { CategoryPillCard(label = "Audio", count = audioCount, color = EmeraldGreen, icon = Icons.Default.AudioFile) }
                    item { CategoryPillCard(label = "Video", count = videoCount, color = BhagwaOrange, icon = Icons.Default.VideoFile) }
                    if (workCount > 0) item { CategoryPillCard(label = "Work Tag", count = workCount, color = SkyCyan, icon = Icons.Default.Work) }
                    if (personalCount > 0) item { CategoryPillCard(label = "Personal Tag", count = personalCount, color = EmeraldGreen, icon = Icons.Default.Person) }
                    if (financeCount > 0) item { CategoryPillCard(label = "Finance Tag", count = financeCount, color = SoftGold, icon = Icons.Default.AttachMoney) }
                }
            }
        }
    }
}

@Composable
fun CategoryPillCard(label: String, count: Int, color: Color, icon: ImageVector) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(CosmicSurface)
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = color, modifier = Modifier.size(14.dp))
            Text(text = label, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(color)
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text(text = "$count", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// Helpers to aggregate and parse size strings accurately
fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format("%.2f GB", gb)
        mb >= 1.0 -> String.format("%.1f MB", mb)
        kb >= 1.0 -> String.format("%.0f KB", kb)
        else -> "$bytes B"
    }
}

fun parseSizeBytes(sizeStr: String): Long {
    return try {
        val clean = sizeStr.uppercase().trim()
        val value = clean.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0
        when {
            clean.contains("GB") -> (value * 1024.0 * 1024.0 * 1024.0).toLong()
            clean.contains("MB") -> (value * 1024.0 * 1024.0).toLong()
            clean.contains("KB") -> (value * 1024.0).toLong()
            clean.contains("B") -> value.toLong()
            else -> value.toLong() // Raw bytes if no unit
        }
    } catch (e: Exception) {
        0L
    }
}

fun calculateTotalStorageFormatted(files: List<LocalFile>): String {
    val totalBytes = files.sumOf { parseSizeBytes(it.size) }
    return formatFileSize(totalBytes)
}

fun parseSize(sizeStr: String): Double {
    return parseSizeBytes(sizeStr) / (1024.0 * 1024.0) // Returns MB for sorting
}

fun isSensitiveFile(file: LocalFile): Boolean {
    val cat = file.category.lowercase()
    val name = file.name.lowercase()
    val tag = file.classification.lowercase()
    return cat == "finance" || cat == "personal" ||
           tag.contains("finance") || tag.contains("personal") || tag.contains("sensitive") || tag.contains("confidential") ||
           name.contains("aadhaar") || name.contains("passport") || name.contains("tax") ||
           name.contains("invoice") || name.contains("salary") || name.contains("bank") || name.contains("audit")
}

@Composable
fun SecurityQuickCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(CosmicCard)
            .border(1.dp, BorderColor, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Column {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = TextSecondary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

fun openLocalFile(context: android.content.Context, file: LocalFile) {
    try {
        val searchDirs = listOfNotNull(
            android.os.Environment.getExternalStorageDirectory(),
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS),
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES),
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC),
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES)
        )
        var realFile: java.io.File? = null
        for (dir in searchDirs) {
            val candidate = java.io.File(dir, file.name)
            if (candidate.exists()) {
                realFile = candidate
                break
            }
        }
        if (realFile != null) {
            val fileUri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                realFile
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(fileUri, file.mimeType.ifBlank { "*/*" })
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } else {
            Toast.makeText(
                context,
                "फ़ाइल व्यूअर: ${file.name}\nआकार: ${file.size} • श्रेणी: ${file.category}",
                Toast.LENGTH_LONG
            ).show()
        }
    } catch (e: Exception) {
        Toast.makeText(
            context,
            "${file.name} खोलने के लिए उपयुक्त ऐप उपलब्ध नहीं है",
            Toast.LENGTH_SHORT
        ).show()
    }
}

fun shareLocalFile(context: android.content.Context, file: LocalFile) {
    try {
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = file.mimeType.ifBlank { "text/plain" }
            putExtra(android.content.Intent.EXTRA_SUBJECT, file.name)
            putExtra(android.content.Intent.EXTRA_TEXT, "VVF Smart Manager शेयरिंग: ${file.name} (${file.size})")
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "फ़ाइल शेयर करें"))
    } catch (e: Exception) {
        Toast.makeText(context, "शेयरिंग विफल", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun FileItemRow(
    file: LocalFile,
    onFileClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onSummarizeClick: () -> Unit,
    onClassifyClick: () -> Unit
) {
    var expandedMenu by remember { mutableStateOf(false) }

    val icon = when (file.category) {
        "Images" -> Icons.Default.Image
        "Audio" -> Icons.Default.AudioFile
        "Video" -> Icons.Default.VideoFile
        else -> Icons.Default.Description
    }

    val iconColor = when (file.category) {
        "Images" -> SkyCyan
        "Audio" -> EmeraldGreen
        "Video" -> BhagwaOrange
        else -> SoftGold
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CosmicSurface)
            .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
            .clickable { onFileClick() }
            .padding(12.dp)
            .testTag("file_row_${file.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FileThumbnailView(
            file = file,
            size = 48.dp,
            onClick = onFileClick
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = file.size, color = TextSecondary, fontSize = 11.sp)
                Text(text = "•", color = TextSecondary, fontSize = 11.sp)
                
                // Gemini Detected Category Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(iconColor.copy(alpha = 0.15f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = file.category,
                        color = iconColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (file.classification.isNotBlank() && file.classification != "Unclassified") {
                    val tagColor = when (file.classification) {
                        "Work" -> SkyCyan
                        "Personal" -> EmeraldGreen
                        "Finance" -> SoftGold
                        else -> TextSecondary
                    }
                    Text(text = "•", color = TextSecondary, fontSize = 11.sp)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(tagColor.copy(alpha = 0.15f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = file.classification,
                            color = tagColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (isSensitiveFile(file)) {
                    Text(text = "•", color = TextSecondary, fontSize = 11.sp)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(BhagwaOrange.copy(alpha = 0.18f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = "Sensitive", tint = BhagwaOrange, modifier = Modifier.size(10.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "संवेदनशील",
                                color = BhagwaOrange,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Box {
            IconButton(onClick = { expandedMenu = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = TextSecondary
                )
            }

            DropdownMenu(
                expanded = expandedMenu,
                onDismissRequest = { expandedMenu = false },
                containerColor = CosmicCard,
                modifier = Modifier.border(1.dp, BorderColor, RoundedCornerShape(8.dp))
            ) {
                DropdownMenuItem(
                    text = { Text("AI सारांश (Summarize)", color = SoftGold) },
                    onClick = {
                        expandedMenu = false
                        onSummarizeClick()
                    },
                    leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = "Summarize", tint = SoftGold) }
                )
                DropdownMenuItem(
                    text = { Text("AI कैटेगराइज़/क्लासिफाई", color = SoftGold) },
                    onClick = {
                        expandedMenu = false
                        onClassifyClick()
                    },
                    leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = "Classify", tint = SoftGold) }
                )
                DropdownMenuItem(
                    text = { Text("नाम बदलें (Rename)", color = TextPrimary) },
                    onClick = {
                        expandedMenu = false
                        onRenameClick()
                    },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = "Rename", tint = SoftGold) }
                )
                DropdownMenuItem(
                    text = { Text("हटाएं (Delete)", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        expandedMenu = false
                        onDeleteClick()
                    },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error) }
                )
            }
        }
    }
}

// Simulated content helper for Gemini analysis of virtual files
fun getSimulatedContentForFile(fileName: String, category: String): String {
    return when (category) {
        "Documents" -> {
            if (fileName.contains("Project", ignoreCase = true)) {
                "यह VVF Smart Manager प्रोजेक्ट का टेक्निकल स्पेसिफिकेशन डॉक्यूमेंट है। इसके मुख्य फीचर्स में AES-256 फाइल मेटाडेटा एन्क्रिप्शन, रूम ऑफलाइन-फर्स्ट डेटाबेस, और डुप्लीकेट फाइल डिटेक्टर शामिल हैं। इसे पूर्णतः सुरक्षित और प्राइवेसी-फर्स्ट डिज़ाइन किया गया है।"
            } else {
                "यह एक सुरक्षित ऑडिट रिपोर्ट है जो VVF प्लेटफॉर्म के ऑफलाइन आर्किटेक्चर की पुष्टि करती है। इसमें सुरक्षा मानकों, डेटा लीक की रोकथाम और केस्टोर आधारित क्रिप्टोग्राफी का विस्तृत विश्लेषण शामिल है।"
            }
        }
        "Images" -> {
            "Aadhaar Card Copy. ID Number: XXXX-XXXX-1234. Name: Sach Awasthi. Address: New Delhi, India. Validated by UIDAI. Metadata contains GPS coordinates and device hash."
        }
        "Audio" -> {
            "Audio transcript: संस्कृत में जन गण मन राष्ट्रीय गान। भारत भाग्य विधाता। पंजाब सिंधु गुजरात मराठा द्राविड़ उत्कल बंग।"
        }
        "Video" -> {
            "Video description: स्टार्टअप पिच डेमोंसट्रेशन वीडियो क्लिप। इस वीडियो में ऑफलाइन-फर्स्ट फाइल मैनेजर, गूगल ड्राइव बैकअप प्लगइन और एआई आधारित सारांश टूल का लाइव डेमो दिया गया है।"
        }
        else -> "यह एक सुरक्षित डिवाइस फाइल है। गोपनीयता बनाए रखने के लिए केवल मेटाडेटा अनुक्रमित किया गया है।"
    }
}

// Runtime Storage Permission Check Helper
fun checkStoragePermissions(context: android.content.Context): Boolean {
    return if (android.os.Build.VERSION.SDK_INT >= 33) {
        androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.READ_MEDIA_IMAGES
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
        androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.READ_MEDIA_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    } else {
        androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.READ_EXTERNAL_STORAGE
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}

fun getRequiredStoragePermissions(): Array<String> {
    return if (android.os.Build.VERSION.SDK_INT >= 33) {
        arrayOf(
            android.Manifest.permission.READ_MEDIA_IMAGES,
            android.Manifest.permission.READ_MEDIA_AUDIO,
            android.Manifest.permission.READ_MEDIA_VIDEO
        )
    } else {
        arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

// Real Storage & SD Card File Scanner Helper
suspend fun scanAndIndexStorageFiles(
    context: android.content.Context,
    repository: FileMetadataRepository
): Int {
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        var scannedCount = 0
        try {
            val rootDirs = listOfNotNull(
                android.os.Environment.getExternalStorageDirectory(),
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS),
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
            )

            val foundFiles = mutableListOf<java.io.File>()
            for (dir in rootDirs) {
                if (dir.exists() && dir.isDirectory) {
                    dir.listFiles()?.take(50)?.forEach { file ->
                        if (file.isFile && !file.name.startsWith(".")) {
                            foundFiles.add(file)
                        }
                    }
                }
            }

            val dateFormat = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())

            for (file in foundFiles) {
                val name = file.name
                val ext = file.extension.lowercase()
                val category = when (ext) {
                    "pdf", "doc", "docx", "txt" -> "Documents"
                    "jpg", "jpeg", "png", "webp" -> "Images"
                    "mp3", "wav", "m4a" -> "Audio"
                    "mp4", "mkv" -> "Video"
                    "zip", "rar" -> "Archives"
                    else -> "Documents"
                }

                val localFile = LocalFile(
                    id = "sd_" + file.name.hashCode().toString(),
                    name = name,
                    size = formatFileSize(file.length()),
                    mimeType = when(ext) {
                        "pdf" -> "application/pdf"
                        "jpg", "jpeg", "png", "webp" -> "image/$ext"
                        "txt" -> "text/plain"
                        "mp3", "wav" -> "audio/$ext"
                        "mp4", "mkv" -> "video/$ext"
                        else -> "application/octet-stream"
                    },
                    category = category,
                    lastModified = dateFormat.format(java.util.Date(file.lastModified())),
                    filePath = file.absolutePath
                )
                repository.insertFile(localFile)
                scannedCount++
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext scannedCount
    }
}

