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
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class DuplicateGroup(
    val id: String,
    val masterFile: String,
    val size: String,
    val duplicateFiles: List<String>,
    val similarityScore: Int // Percentage
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicateCleanerScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var similarityThreshold by remember { mutableFloatStateOf(85f) }
    var isScanning by remember { mutableStateOf(false) }
    var progressPercent by remember { mutableFloatStateOf(0f) }
    var hasScanned by remember { mutableStateOf(false) }
    var duplicateToDelete by remember { mutableStateOf<Pair<DuplicateGroup, String>?>(null) }

    // Hardcoded initial list of duplicates representing real scanned file states
    val allDuplicateGroups = remember {
        mutableStateListOf(
            DuplicateGroup("g_1", "Golden_Leaf_Logo_VVF.png", "2.1 MB", listOf("VVF_Logo_Latest.png", "VVF_Logo_Backup.png"), 95),
            DuplicateGroup("g_2", "Project_Proposal_2026.pdf", "4.2 MB", listOf("Project_Proposal_Final.pdf"), 90),
            DuplicateGroup("g_3", "Family_Pic.jpg", "3.4 MB", listOf("Family_Pic_Compressed.jpg"), 75),
            DuplicateGroup("g_4", "Meeting_Minutes.docx", "890 KB", listOf("Meeting_Notes.docx"), 72)
        )
    }

    // Filter duplicates dynamically based on the visual similarity slider (Level 3-4 Threshold)
    val filteredGroups = allDuplicateGroups.filter { group ->
        group.similarityScore >= similarityThreshold.toInt()
    }

    fun startScan() {
        scope.launch {
            isScanning = true
            progressPercent = 0f
            while (progressPercent < 1.0f) {
                delay(150)
                progressPercent += 0.1f
            }
            isScanning = false
            hasScanned = true
            Toast.makeText(context, "स्कैन पूर्ण हुआ! सिमिलरिटी थ्रेशोल्ड लागू किया गया।", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("स्मार्ट डुप्लीकेट क्लीनर", color = BhagwaOrange, fontWeight = FontWeight.Bold) },
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
            // Slider Parameter Controller Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CosmicCard),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "सिमिलरिटी स्लाइडर (Level 3-4 AI Scan)",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "विजुअल और सिमेंटिक समानता थ्रेशोल्ड निर्धारित करें",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "70% (ढीला/Loose)",
                            color = SkyCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${similarityThreshold.toInt()}% समानता (Similarity)",
                            color = BhagwaOrange,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "95% (सटीक/Near-Exact)",
                            color = SoftGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Slider(
                        value = similarityThreshold,
                        onValueChange = { similarityThreshold = it },
                        valueRange = 70f..95f,
                        colors = SliderDefaults.colors(
                            thumbColor = BhagwaOrange,
                            activeTrackColor = BhagwaOrange,
                            inactiveTrackColor = BorderColor
                        ),
                        modifier = Modifier.testTag("similarity_slider")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { startScan() },
                        enabled = !isScanning,
                        modifier = Modifier.fillMaxWidth().testTag("scan_duplicates_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = BhagwaOrange),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ManageSearch, contentDescription = "Scan")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isScanning) "स्कैनिंग जारी है..." else "डुप्लीकेट्स के लिए स्कैन करें", color = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Loading / Scanning progress bar
            if (isScanning) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        progress = { progressPercent },
                        color = BhagwaOrange,
                        trackColor = BorderColor,
                        strokeWidth = 6.dp,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "फ़ाइलें स्कैन की जा रही हैं... ${(progressPercent * 100).toInt()}%",
                        color = TextPrimary,
                        fontSize = 13.sp
                    )
                }
            } else if (!hasScanned) {
                // Initial State prior to scan
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterNone,
                            contentDescription = "Duplicate Icon",
                            tint = TextSecondary,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "स्कैन बटन दबाकर डुप्लीकेट और मिलती-जुलती (Similar) फ़ाइलों को खोजें और साफ़ करें।",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }
            } else {
                // Scan Results
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "स्कैन परिणाम (${filteredGroups.size} समूह मिले)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                    
                    Text(
                        text = "सिमिलरिटी >= ${similarityThreshold.toInt()}%",
                        fontSize = 11.sp,
                        color = SoftGold,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (filteredGroups.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "इस थ्रेशोल्ड स्तर पर कोई डुप्लीकेट नहीं मिला। कृपया सिमिलरिटी स्तर घटाकर देखें।",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(filteredGroups, key = { it.id }) { group ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                                border = BorderStroke(1.dp, BorderColor)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    // Group Header
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Folder,
                                                contentDescription = "Group",
                                                tint = SoftGold,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "मूल: ${group.masterFile}",
                                                color = TextPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.widthIn(max = 160.dp)
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(if (group.similarityScore > 90) BhagwaOrange.copy(alpha = 0.2f) else SkyCyan.copy(alpha = 0.2f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "${group.similarityScore}% समान",
                                                color = if (group.similarityScore > 90) BhagwaOrange else SkyCyan,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    HorizontalDivider(color = BorderColor, modifier = Modifier.padding(vertical = 8.dp))

                                    // Duplicate files to delete
                                    group.duplicateFiles.forEach { duplicate ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.ContentCopy,
                                                    contentDescription = "Duplicate",
                                                    tint = TextSecondary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = duplicate,
                                                    color = TextSecondary,
                                                    fontSize = 12.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.widthIn(max = 200.dp)
                                                )
                                            }

                                            IconButton(
                                                onClick = {
                                                    duplicateToDelete = Pair(group, duplicate)
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // DUPLICATE DELETE CONFIRMATION DIALOG MODAL
    duplicateToDelete?.let { (group, duplicate) ->
        AlertDialog(
            onDismissRequest = { duplicateToDelete = null },
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
                    text = "डुप्लीकेट फ़ाइल स्थायी रूप से हटाएं?",
                    color = BhagwaOrange,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "क्या आप डुप्लीकेट फ़ाइल '$duplicate' को लोकल स्टोरेज से स्थायी रूप से हटाना चाहते हैं?",
                    color = TextPrimary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val index = allDuplicateGroups.indexOf(group)
                        if (index != -1) {
                            val updatedList = group.duplicateFiles.toMutableList()
                            updatedList.remove(duplicate)
                            if (updatedList.isEmpty()) {
                                allDuplicateGroups.removeAt(index)
                            } else {
                                allDuplicateGroups[index] = group.copy(duplicateFiles = updatedList)
                            }
                            Toast.makeText(context, "'$duplicate' स्थायी रूप से हटाया गया!", Toast.LENGTH_SHORT).show()
                        }
                        duplicateToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("हाँ, हटाएं", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { duplicateToDelete = null }) {
                    Text("रद्द करें", color = TextSecondary)
                }
            }
        )
    }
}
