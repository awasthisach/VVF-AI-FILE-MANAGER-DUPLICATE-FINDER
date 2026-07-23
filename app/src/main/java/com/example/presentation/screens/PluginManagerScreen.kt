package com.example.presentation.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

data class AppPlugin(
    val id: String,
    val name: String,
    val description: String,
    val icon: ImageVector,
    val category: String, // "AI", "Cloud", "Utility"
    var isEnabled: Boolean,
    val size: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginManagerScreen(
    onNavigateToCloud: () -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current

    // In-memory list representing on-demand plugins
    val pluginsList = remember {
        mutableStateListOf(
            AppPlugin("pl_1", "Google Drive Core Sync", "गूगल क्लाउड सिंक और ऑटोमेटिक बैकअप इनेबलर।", Icons.Default.CloudSync, "Cloud", true, "बिल्ट-इन (Core)"),
            AppPlugin("pl_2", "OCR Text Recognizer", "ML Kit द्वारा इमेज से टेक्स्ट और पीडीएफ पीडीएफ रीडर।", Icons.Default.DocumentScanner, "Utility", false, "2.4 MB (Downloadable)"),
            AppPlugin("pl_3", "TFLite Semantic Search", "ऑन-डिवाइस नेचुरल लैंग्वेज सर्च और स्मार्ट श्रेणियां।", Icons.Default.Psychology, "AI", false, "12.8 MB (Downloadable)"),
            AppPlugin("pl_4", "Dropbox Sync Integration", "ड्रॉपबॉक्स पर्सनल/प्रोफेशनल अकाउंट सिंक।", Icons.Default.Cloud, "Cloud", false, "1.1 MB (Downloadable)"),
            AppPlugin("pl_5", "Nextcloud & NAS Server", "निजी होम सर्वर (FTP, SMB, WebDAV) सपोर्ट।", Icons.Default.Dns, "Cloud", false, "3.5 MB (Downloadable)")
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("प्लगइन मैनेजर", color = BhagwaOrange, fontWeight = FontWeight.Bold) },
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
            // Explanatory Banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(BhagwaOrange.copy(alpha = 0.12f))
                    .border(1.dp, BhagwaOrange.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.OfflineBolt,
                    contentDescription = "On-Demand",
                    tint = BhagwaOrange,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "VVF Smart Manager प्लगइन आर्किटेक्चर का उपयोग करता है। सभी उन्नत AI व क्लाउड मॉड्यूल 'ऑन-डिमांड' हैं ताकि मूल ऐप साइज 10MB से कम रहे।",
                    color = TextPrimary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "उपलब्ध प्लगइन्स (${pluginsList.size})",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(pluginsList, key = { it.id }) { plugin ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CosmicSurface),
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CosmicCard),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = plugin.icon,
                                        contentDescription = plugin.name,
                                        tint = if (plugin.isEnabled) BhagwaOrange else TextSecondary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = plugin.name,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(BorderColor)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = plugin.category,
                                                color = SoftGold,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Text(
                                        text = plugin.description,
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "साइज़: ${plugin.size}",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )

                                if (plugin.id == "pl_1") {
                                    // Google Drive link configuration shortcut
                                    TextButton(
                                        onClick = onNavigateToCloud,
                                        colors = ButtonDefaults.textButtonColors(contentColor = BhagwaOrange)
                                    ) {
                                        Text("सिंक सेटिंग्स", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Icon(Icons.Default.ChevronRight, contentDescription = "Sync", modifier = Modifier.size(16.dp))
                                    }
                                } else {
                                    Switch(
                                        checked = plugin.isEnabled,
                                        onCheckedChange = { isChecked ->
                                            val index = pluginsList.indexOfFirst { it.id == plugin.id }
                                            if (index != -1) {
                                                pluginsList[index] = plugin.copy(isEnabled = isChecked)
                                                val status = if (isChecked) "इनेबल" else "डिसेबल"
                                                Toast.makeText(context, "${plugin.name} $status किया गया!", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = BhagwaOrange,
                                            checkedTrackColor = BhagwaOrange.copy(alpha = 0.4f),
                                            uncheckedThumbColor = TextSecondary,
                                            uncheckedTrackColor = CosmicCard
                                        ),
                                        modifier = Modifier.testTag("switch_${plugin.id}")
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
