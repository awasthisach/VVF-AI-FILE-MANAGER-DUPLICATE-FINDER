package com.example

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.example.data.api.GeminiRepository
import com.example.data.database.AppDatabase
import com.example.data.database.FileMetadataRepository
import com.example.data.plugin.GoogleDrivePlugin
import com.example.data.security.MetadataEncryptionService
import com.example.data.security.SecurityManager
import com.example.presentation.screens.*
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

enum class AppScreen {
    AUTH,
    DASHBOARD,
    SECURE_VAULT,
    CLOUD_SYNC,
    DUPLICATES,
    PLUGINS
}

class MainActivity : FragmentActivity() {

    private lateinit var securityManager: SecurityManager
    private lateinit var googleDrivePlugin: GoogleDrivePlugin
    private lateinit var fileRepository: FileMetadataRepository
    private lateinit var geminiRepository: GeminiRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Core Offline Security & Cloud plugins
        securityManager = SecurityManager(applicationContext)
        googleDrivePlugin = GoogleDrivePlugin()
        
        // Initialize database, encryption, and repositories
        val database = AppDatabase.getDatabase(applicationContext)
        val encryptionService = MetadataEncryptionService(applicationContext)
        fileRepository = FileMetadataRepository(database.fileMetadataDao(), encryptionService)
        geminiRepository = GeminiRepository(applicationContext)
        
        // Populate offline-first files if database is empty
        lifecycleScope.launch {
            fileRepository.populateInitialDataIfEmpty()
        }
        
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.ui.graphics.Color(0xFF090E17) // CosmicDarkBg direct reference
                ) {
                    var currentScreen by remember { mutableStateOf(AppScreen.AUTH) }

                    Crossfade(targetState = currentScreen, label = "ScreenTransition") { screen ->
                        when (screen) {
                            AppScreen.AUTH -> {
                                AuthScreen(
                                    securityManager = securityManager,
                                    onAuthSuccess = { currentScreen = AppScreen.DASHBOARD }
                                )
                            }
                            AppScreen.DASHBOARD -> {
                                DashboardScreen(
                                    repository = fileRepository,
                                    geminiRepository = geminiRepository,
                                    onNavigateToVault = { currentScreen = AppScreen.SECURE_VAULT },
                                    onNavigateToDuplicates = { currentScreen = AppScreen.DUPLICATES },
                                    onNavigateToCloud = { currentScreen = AppScreen.CLOUD_SYNC },
                                    onNavigateToPlugins = { currentScreen = AppScreen.PLUGINS }
                                )
                            }
                            AppScreen.SECURE_VAULT -> {
                                SecureVaultScreen(
                                    securityManager = securityManager,
                                    onBackClick = { currentScreen = AppScreen.DASHBOARD }
                                )
                            }
                            AppScreen.CLOUD_SYNC -> {
                                CloudSyncScreen(
                                    plugin = googleDrivePlugin,
                                    onBackClick = { currentScreen = AppScreen.DASHBOARD }
                                )
                            }
                            AppScreen.DUPLICATES -> {
                                DuplicateCleanerScreen(
                                    onBackClick = { currentScreen = AppScreen.DASHBOARD }
                                )
                            }
                            AppScreen.PLUGINS -> {
                                PluginManagerScreen(
                                    onNavigateToCloud = { currentScreen = AppScreen.CLOUD_SYNC },
                                    onBackClick = { currentScreen = AppScreen.DASHBOARD }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

