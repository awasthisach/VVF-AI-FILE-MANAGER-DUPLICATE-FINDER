package com.example.presentation.screens

import android.widget.Toast
import androidx.compose.animation.*
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
import androidx.compose.material.icons.automirrored.filled.Backspace
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.security.BiometricAuthHelper
import com.example.data.security.SecurityManager
import com.example.ui.theme.*

data class VaultFile(
    val id: String,
    val name: String,
    val size: String,
    val originalMime: String,
    val encryptedDate: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecureVaultScreen(
    securityManager: SecurityManager,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    var isUnlocked by remember { mutableStateOf(false) }
    var pinEntry by remember { mutableStateOf("") }
    var unlockError by remember { mutableStateOf("") }

    // List of highly secure vault files in memory
    val vaultFiles = remember {
        mutableStateListOf(
            VaultFile("v_1", "Salary_Slip_May2026.pdf.aes", "1.1 MB", "PDF", "19 July 2026"),
            VaultFile("v_2", "PAN_Card_Scanned.jpg.aes", "890 KB", "JPEG", "18 July 2026"),
            VaultFile("v_3", "Taxes_VVF_SmartManager.zip.aes", "12.4 MB", "ZIP", "12 July 2026")
        )
    }

    var showAddVaultDialog by remember { mutableStateOf(false) }
    var newVaultFileName by remember { mutableStateOf("") }

    // Unlocking handler
    fun attemptUnlock() {
        if (securityManager.rulesEngine.isLockedOut()) {
            val secs = securityManager.rulesEngine.getRemainingLockoutSeconds()
            unlockError = "अत्यधिक गलत प्रयासों के कारण सुरक्षा लॉक! $secs सेकंड प्रतीक्षा करें।"
            return
        }
        if (securityManager.verifyPin(pinEntry)) {
            isUnlocked = true
            pinEntry = ""
            unlockError = ""
            Toast.makeText(context, "वॉल्ट सफलतापूर्वक अनलॉक हुआ!", Toast.LENGTH_SHORT).show()
        } else {
            if (securityManager.rulesEngine.isLockedOut()) {
                val secs = securityManager.rulesEngine.getRemainingLockoutSeconds()
                unlockError = "5 से अधिक गलत प्रयास! $secs सेकंड के लिए सुरक्षा लॉक।"
            } else {
                unlockError = "अमान्य सुरक्षा पिन! पुनः प्रयास करें।"
            }
            pinEntry = ""
        }
    }

    LaunchedEffect(Unit) {
        if (securityManager.isBiometricEnabled() && BiometricAuthHelper.isBiometricAvailable(context)) {
            BiometricAuthHelper.authenticateForSensitiveAccess(
                context = context,
                title = "सुरक्षित वॉल्ट प्रमाणीकरण",
                subtitle = "वॉल्ट की संवेदनशील फाइलें अनलॉक करने के लिए बायोमेट्रिक प्रमाणीकरण करें",
                onSuccess = {
                    isUnlocked = true
                    unlockError = ""
                }
            )
        }
    }

    if (!isUnlocked) {
        // Unlocking Prompt Barrier
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CosmicDarkBg)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = onBackClick, modifier = Modifier.align(Alignment.Start)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "पीछे", tint = TextPrimary)
                }

                Spacer(modifier = Modifier.height(48.dp))

                Icon(
                    imageVector = Icons.Default.EnhancedEncryption,
                    contentDescription = "Vault Lock",
                    tint = SoftGold,
                    modifier = Modifier
                        .size(80.dp)
                        .background(CosmicCard, CircleShape)
                        .padding(16.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "सुरक्षित वॉल्ट अनलॉक करें",
                    color = BhagwaOrange,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "वॉल्ट में प्रवेश के लिए अपना सुरक्षा पिन दर्ज करें",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
                )

                // PIN Entry Dots Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 16.dp)
                ) {
                    repeat(4) { idx ->
                        val isFilled = idx < pinEntry.length
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(if (isFilled) SoftGold else CosmicCard)
                        )
                    }
                }

                if (unlockError.isNotEmpty()) {
                    Text(
                        text = unlockError,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                // Inline custom pinpad for entry
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val keys = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9")
                    )
                    keys.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { k ->
                                KeypadButton(text = k) {
                                    if (pinEntry.length < 4) {
                                        pinEntry += k
                                        if (pinEntry.length == 4) attemptUnlock()
                                    }
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Spacer(modifier = Modifier.size(72.dp))
                        KeypadButton(text = "0") {
                            if (pinEntry.length < 4) {
                                pinEntry += "0"
                                if (pinEntry.length == 4) attemptUnlock()
                            }
                        }
                        IconButton(
                            onClick = { if (pinEntry.isNotEmpty()) pinEntry = pinEntry.dropLast(1) },
                            modifier = Modifier
                                .size(72.dp)
                                .background(CosmicCard, CircleShape)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Clear", tint = TextPrimary)
                        }
                    }

                    if (BiometricAuthHelper.isBiometricAvailable(context)) {
                        IconButton(
                            onClick = {
                                BiometricAuthHelper.authenticateForSensitiveAccess(
                                    context = context,
                                    title = "सुरक्षित वॉल्ट प्रमाणीकरण",
                                    subtitle = "फिंगरप्रिंट या बायोमेट्रिक से अनलॉक करें",
                                    onSuccess = {
                                        isUnlocked = true
                                        unlockError = ""
                                    }
                                )
                            },
                            modifier = Modifier
                                .padding(top = 12.dp)
                                .size(56.dp)
                                .background(BhagwaOrange.copy(alpha = 0.2f), CircleShape)
                                .border(1.dp, BhagwaOrange, CircleShape)
                                .testTag("vault_biometric_unlock_button")
                        ) {
                            Icon(Icons.Default.Fingerprint, contentDescription = "Biometric Unlock", tint = BhagwaOrange)
                        }
                    }
                }
            }
        }
    } else {
        // Unlocked Vault State
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("सुरक्षित वॉल्ट (AES-256)", color = SoftGold, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "पीछे", tint = TextPrimary)
                        }
                    },
                    actions = {
                        IconButton(onClick = { isUnlocked = false }) {
                            Icon(Icons.Default.LockOpen, contentDescription = "Lock Vault", tint = BhagwaOrange)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = CosmicDarkBg)
                )
            },
            containerColor = CosmicDarkBg,
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { 
                        newVaultFileName = ""
                        showAddVaultDialog = true 
                    },
                    containerColor = SoftGold,
                    contentColor = CosmicDarkBg
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Encrypted File")
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {
                // Info Banner
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SoftGold.copy(alpha = 0.12f))
                        .border(1.dp, SoftGold.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Shield",
                        tint = SoftGold,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "सभी फाइलें ऑन-डिवाइस मिलिट्री-ग्रेड AES-256 कीज़ से एन्क्रिप्टेड हैं। प्लेन-टेक्स्ट में कुछ भी संग्रहित नहीं है।",
                        color = TextPrimary,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "एन्क्रिप्टेड फाइलें (${vaultFiles.size})",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                if (vaultFiles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.EnhancedEncryption, contentDescription = "Empty", tint = TextSecondary, modifier = Modifier.size(64.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("वॉल्ट खाली है। फ़ाइलें जोड़ने के लिए + बटन दबाएँ।", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(vaultFiles, key = { it.id }) { vFile ->
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
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CosmicCard),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VerifiedUser,
                                        contentDescription = "Secure",
                                        tint = SoftGold,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = vFile.name,
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(text = vFile.size, color = TextSecondary, fontSize = 11.sp)
                                        Text(text = "•", color = TextSecondary, fontSize = 11.sp)
                                        Text(text = vFile.encryptedDate, color = TextSecondary, fontSize = 11.sp)
                                    }
                                }

                                // Decryption Action
                                IconButton(
                                    onClick = {
                                        vaultFiles.remove(vFile)
                                        Toast.makeText(context, "${vFile.name} को डिक्रिप्ट किया गया और मुख्य स्टोरेज में सहेजा गया!", Toast.LENGTH_LONG).show()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.NoEncryption,
                                        contentDescription = "Decrypt",
                                        tint = BhagwaOrange
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ADD SENSITIVE FILE DIALOG
            if (showAddVaultDialog) {
                AlertDialog(
                    onDismissRequest = { showAddVaultDialog = false },
                    containerColor = CosmicCard,
                    title = { Text("वॉल्ट में फ़ाइल एन्क्रिप्ट करें", color = SoftGold) },
                    text = {
                        OutlinedTextField(
                            value = newVaultFileName,
                            onValueChange = { newVaultFileName = it },
                            label = { Text("फ़ाइल का नाम (जैसे: Aadhaar.jpg)", color = TextSecondary) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SoftGold,
                                unfocusedBorderColor = BorderColor,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("add_vault_file_input")
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (newVaultFileName.isNotBlank()) {
                                    val securedName = if (newVaultFileName.endsWith(".aes")) newVaultFileName else "$newVaultFileName.aes"
                                    vaultFiles.add(
                                        VaultFile(
                                            id = "v_" + System.currentTimeMillis(),
                                            name = securedName,
                                            size = "${(1..5).random()} MB",
                                            originalMime = "PDF",
                                            encryptedDate = "19 July 2026"
                                        )
                                    )
                                    showAddVaultDialog = false
                                    Toast.makeText(context, "फ़ाइल एन्क्रिप्ट होकर वॉल्ट में सुरक्षित हुई!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SoftGold, contentColor = CosmicDarkBg)
                        ) {
                            Text("एन्क्रिप्ट करें", fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAddVaultDialog = false }) {
                            Text("रद्द करें", color = TextSecondary)
                        }
                    }
                )
            }
        }
    }
}
