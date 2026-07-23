package com.example.presentation.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.plugin.GoogleDrivePlugin
import com.example.domain.plugin.CloudFile
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudSyncScreen(
    plugin: GoogleDrivePlugin,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var isLoggedIn by remember { mutableStateOf(plugin.isLoggedIn(context)) }
    var userEmail by remember { 
        mutableStateOf(
            if (isLoggedIn) context.getSharedPreferences("vvf_gdrive_state", android.content.Context.MODE_PRIVATE).getString("user_email", "user.vvf.smart@gmail.com") 
            else ""
        ) 
    }
    
    var cloudFiles by remember { mutableStateOf<List<CloudFile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    // Offline sync queue items
    val offlineQueue = remember {
        mutableStateListOf(
            CloudFile("q_1", "secure_vault_backup.zip", 24100000, "zip", 1781898000000, isPendingSync = true),
            CloudFile("q_2", "Project_VVF_SmartManager.pdf", 4200000, "pdf", 1781898100000, isDownloaded = true),
            CloudFile("q_3", "Aadhaar_Card_Copy.jpg", 1500000, "jpeg", 1781898200000, isPendingSync = false, isDownloaded = false) // Simulated failure
        )
    }

    var showAccountChooserModal by remember { mutableStateOf(false) }
    var availableAccounts by remember { mutableStateOf<List<String>>(emptyList()) }

    // Load cloud files once logged in
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            isLoading = true
            val result = plugin.listFiles(context)
            if (result.isSuccess) {
                cloudFiles = result.getOrNull() ?: emptyList()
            }
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("गूगल ड्राइव क्लाउड सिंक", color = BhagwaOrange, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "पीछे", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CosmicDarkBg)
            )
        },
        containerColor = CosmicDarkBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // Authentication Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CosmicCard),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Google Drive Brand icon style
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(BhagwaOrange.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudQueue,
                                contentDescription = "Drive",
                                tint = BhagwaOrange,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isLoggedIn) "गूगल ड्राइव कनेक्टेड" else "क्लाउड स्टोरेज डिस्कनेक्टेड",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = if (isLoggedIn) userEmail.toString() else "Google Account से सीधे कनेक्ट करें",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isLoggedIn) {
                        Button(
                            onClick = {
                                scope.launch {
                                    plugin.logout(context)
                                    isLoggedIn = false
                                    userEmail = ""
                                    cloudFiles = emptyList()
                                    Toast.makeText(context, "लॉगआउट सफल!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = CosmicSurface),
                            border = BorderStroke(1.dp, BorderColor),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Disconnect")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("साइन आउट करें", color = Color.White)
                        }
                    } else {
                        Button(
                            onClick = {
                                availableAccounts = plugin.getDeviceGoogleAccounts(context)
                                showAccountChooserModal = true
                            },
                            modifier = Modifier.fillMaxWidth().testTag("google_login_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = BhagwaOrange),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.CloudCircle, contentDescription = "Google", tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Google Account से लॉगइन करें", color = Color.White)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Tab-like Section Titles
            Text(
                text = "ऑफ़लाइन सिंक कतार (Offline Sync Queue)",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = SoftGold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Queue items showing incremental sync and failure recovery states
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(offlineQueue) { qItem ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CosmicCard, RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (qItem.isPendingSync) Icons.Default.Sync else if (qItem.isDownloaded) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = "status",
                                tint = if (qItem.isPendingSync) BhagwaOrange else if (qItem.isDownloaded) EmeraldGreen else Color.Red,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = qItem.name,
                                color = TextPrimary,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (qItem.isPendingSync) "कतार में (Pending)" else if (qItem.isDownloaded) "सफल (Synced)" else "विफल (Failed - Retry)",
                                color = if (qItem.isPendingSync) SoftGold else if (qItem.isDownloaded) EmeraldGreen else Color.Red,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Cloud Files Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "गूगल ड्राइव फाइलें",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )
                if (isLoggedIn) {
                    IconButton(onClick = {
                        scope.launch {
                            isLoading = true
                            val res = plugin.listFiles(context)
                            if (res.isSuccess) {
                                cloudFiles = res.getOrDefault(emptyList())
                            }
                            isLoading = false
                        }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = SoftGold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (!isLoggedIn) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "गूगल ड्राइव फ़ाइलें देखने और सिंक करने के लिए कृपया लॉगिन करें।",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            } else if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = BhagwaOrange)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(cloudFiles) { cFile ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(CosmicSurface)
                                .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(CosmicCard),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                                    contentDescription = "File",
                                    tint = SkyCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = cFile.name,
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${cFile.size / 1024} KB",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }

                            IconButton(
                                onClick = {
                                    scope.launch {
                                        val downloadRes = plugin.downloadFile(context, cFile.id, "")
                                        if (downloadRes.isSuccess) {
                                            Toast.makeText(context, "${cFile.name} ऑफ़लाइन डाउनलोड किया गया!", Toast.LENGTH_SHORT).show()
                                            // Refresh local listing
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = "Download",
                                    tint = SoftGold
                                )
                            }
                        }
                    }
                }
            }
        }

        // GOOGLE ACCOUNT CHOOSER MODAL (Native Device Accounts)
        if (showAccountChooserModal) {
            AlertDialog(
                onDismissRequest = { showAccountChooserModal = false },
                containerColor = CosmicCard,
                icon = {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Google Account",
                        tint = SoftGold,
                        modifier = Modifier.size(40.dp)
                    )
                },
                title = {
                    Text(
                        text = "गूगल अकाउंट चुनें (Select Google Account)",
                        color = BhagwaOrange,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "VVF Smart Manager आपके डिवाइस के Google Account से सुरक्षित रूप से कनेक्ट होगा:",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )

                        availableAccounts.forEach { emailAcc ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(CosmicSurface)
                                    .border(1.dp, BorderColor, RoundedCornerShape(10.dp))
                                    .clickable {
                                        scope.launch {
                                            isLoading = true
                                            showAccountChooserModal = false
                                            val res = plugin.loginWithAccount(context, emailAcc, "Google Device User")
                                            if (res.isSuccess) {
                                                isLoggedIn = true
                                                userEmail = emailAcc
                                                Toast.makeText(context, "$emailAcc से लॉगइन सफल!", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "लॉगइन विफल", Toast.LENGTH_SHORT).show()
                                            }
                                            isLoading = false
                                        }
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(BhagwaOrange.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = emailAcc.take(1).uppercase(),
                                        color = BhagwaOrange,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = emailAcc,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Google Account (Device)",
                                        color = EmeraldGreen,
                                        fontSize = 10.sp
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Select",
                                    tint = SoftGold,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showAccountChooserModal = false }) {
                        Text("रद्द करें", color = TextSecondary)
                    }
                }
            )
        }
    }
}
