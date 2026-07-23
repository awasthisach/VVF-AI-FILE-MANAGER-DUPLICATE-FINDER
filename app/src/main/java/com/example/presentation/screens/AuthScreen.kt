package com.example.presentation.screens

import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
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
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.data.security.SecurityManager
import com.example.ui.theme.*

@Composable
fun AuthScreen(
    securityManager: SecurityManager,
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val isPinSet = remember { mutableStateOf(securityManager.isPinSet()) }
    
    // States for PIN Setup Flow
    val isSetupMode = remember { mutableStateOf(!isPinSet.value) }
    val setupStep = remember { mutableIntStateOf(1) } // 1: Enter PIN, 2: Confirm PIN
    val firstEnteredPin = remember { mutableStateOf("") }
    
    // States for Login Flow
    val currentInput = remember { mutableStateOf("") }
    val errorMessage = remember { mutableStateOf("") }

    // Check biometric availability
    val isBiometricAvailable = remember {
        val biometricManager = BiometricManager.from(context)
        biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }
    val isBiometricEnabled = remember { securityManager.isBiometricEnabled() }

    // Helper to trigger Biometric Prompt
    fun triggerBiometricAuth() {
        if (context !is FragmentActivity) return
        val executor = ContextCompat.getMainExecutor(context)
        val biometricPrompt = BiometricPrompt(
            context,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    Toast.makeText(context, "प्रमाणीकरण सफल!", Toast.LENGTH_SHORT).show()
                    onAuthSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // Suppress showing toast for cancels to avoid user fatigue
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        errorMessage.value = errString.toString()
                    }
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("VVF सुरक्षा")
            .setSubtitle("बायोमेट्रिक प्रमाणीकरण का उपयोग करें")
            .setNegativeButtonText("पिन दर्ज करें")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    // Auto-launch biometric prompt on start if set & enabled
    LaunchedEffect(isPinSet.value) {
        if (isPinSet.value && isBiometricAvailable && isBiometricEnabled) {
            triggerBiometricAuth()
        }
    }

    // PIN Pad Entry Actions
    fun handleNumPress(num: String) {
        errorMessage.value = ""
        if (currentInput.value.length < 4) {
            currentInput.value += num
        }
        
        // Auto process 4 digit PIN
        if (currentInput.value.length == 4) {
            val pin = currentInput.value
            currentInput.value = ""
            
            if (isSetupMode.value) {
                if (setupStep.intValue == 1) {
                    firstEnteredPin.value = pin
                    setupStep.intValue = 2
                } else {
                    if (pin == firstEnteredPin.value) {
                        val saved = securityManager.savePin(pin)
                        if (saved) {
                            securityManager.setBiometricEnabled(true) // Enable by default
                            isPinSet.value = true
                            isSetupMode.value = false
                            Toast.makeText(context, "सुरक्षा पिन सफलतापूर्वक सेट किया गया!", Toast.LENGTH_LONG).show()
                            onAuthSuccess()
                        } else {
                            errorMessage.value = "पिन सहेजने में विफल!"
                            setupStep.intValue = 1
                        }
                    } else {
                        errorMessage.value = "पिन मेल नहीं खाते! पुनः प्रयास करें।"
                        setupStep.intValue = 1
                    }
                }
            } else {
                if (securityManager.rulesEngine.isLockedOut()) {
                    val secs = securityManager.rulesEngine.getRemainingLockoutSeconds()
                    errorMessage.value = "अत्यधिक गलत प्रयास! $secs सेकंड सुरक्षा लॉक।"
                } else {
                    val isValid = securityManager.verifyPin(pin)
                    if (isValid) {
                        onAuthSuccess()
                    } else {
                        if (securityManager.rulesEngine.isLockedOut()) {
                            val secs = securityManager.rulesEngine.getRemainingLockoutSeconds()
                            errorMessage.value = "5 गलत प्रयास! $secs सेकंड के लिए सुरक्षा लॉक।"
                        } else {
                            errorMessage.value = "अमान्य पिन दर्ज किया गया!"
                        }
                    }
                }
            }
        }
    }

    fun handleBackspace() {
        if (currentInput.value.isNotEmpty()) {
            currentInput.value = currentInput.value.dropLast(1)
        }
    }

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
            // Visual Logo / Icon
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = "Shield Logo",
                tint = BhagwaOrange,
                modifier = Modifier
                    .size(80.dp)
                    .background(CosmicCard, CircleShape)
                    .padding(16.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Text Headings (Hindi Translation)
            Text(
                text = "VVF SMART MANAGER",
                color = BhagwaOrange,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            val subtitleText = when {
                isSetupMode.value && setupStep.intValue == 1 -> "नया 4-अंकीय सुरक्षा पिन सेट करें"
                isSetupMode.value && setupStep.intValue == 2 -> "पुष्टि करने के लिए पुनः पिन दर्ज करें"
                else -> "ऑफ़लाइन तिजोरी खोलने के लिए अपना पिन दर्ज करें"
            }

            Text(
                text = subtitleText,
                color = TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Display dots for input PIN
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(4) { index ->
                    val isFilled = index < currentInput.value.length
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(if (isFilled) BhagwaOrange else CosmicCard)
                            .align(Alignment.CenterVertically)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Error Display with Animation
            AnimatedVisibility(
                visible = errorMessage.value.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = errorMessage.value,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // Standard Numeric Custom Keypad
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val numRows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9")
                )

                numRows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        row.forEach { num ->
                            KeypadButton(text = num) { handleNumPress(num) }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Biometric Trigger
                    if (isPinSet.value && isBiometricAvailable) {
                        IconButton(
                            onClick = { triggerBiometricAuth() },
                            modifier = Modifier
                                .size(72.dp)
                                .background(CosmicCard, CircleShape)
                                .testTag("biometric_trigger_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = "Biometric Login",
                                tint = SoftGold,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(72.dp))
                    }

                    KeypadButton(text = "0") { handleNumPress("0") }

                    // Backspace Button
                    IconButton(
                        onClick = { handleBackspace() },
                        modifier = Modifier
                            .size(72.dp)
                            .background(CosmicCard, CircleShape)
                            .testTag("backspace_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                            contentDescription = "Backspace",
                            tint = TextPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun KeypadButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(CosmicCard)
            .clickable { onClick() }
            .testTag("keypad_$text"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
