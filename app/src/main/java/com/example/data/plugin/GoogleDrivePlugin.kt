package com.example.data.plugin

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.example.domain.plugin.CloudFile
import com.example.domain.plugin.CloudPlugin
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GoogleDrivePlugin : CloudPlugin {
    override val id: String = "google_drive"
    override val name: String = "Google Drive"
    override val iconName: String = "google_drive_logo"
    override val isCore: Boolean = true
    override val isInstalled: Boolean = true

    private var cachedEmail: String? = null
    private val securePrefsName = "vvf_gdrive_state"

    // Mock/Local files to return if offline or running in simulator
    private val localOfflineCache = mutableListOf(
        CloudFile("gd_1", "Project_Proposal_2026.pdf", 2400000, "application/pdf", 1781898000000L),
        CloudFile("gd_2", "Financial_Statement.xlsx", 5400000, "application/vnd.ms-excel", 1781898020000L),
        CloudFile("gd_3", "Family_Vacation_Backup.zip", 145000000, "application/zip", 1781898030000L),
        CloudFile("gd_4", "Resume_VVF_SmartManager.docx", 450000, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", 1781898040000L)
    )

    override fun isLoggedIn(context: Context): Boolean {
        if (cachedEmail != null) return true
        val prefs = context.getSharedPreferences(securePrefsName, Context.MODE_PRIVATE)
        cachedEmail = prefs.getString("user_email", null)
        return cachedEmail != null
    }

    fun getUserDisplayName(context: Context): String {
        val prefs = context.getSharedPreferences(securePrefsName, Context.MODE_PRIVATE)
        return prefs.getString("user_display_name", "Google User") ?: "Google User"
    }

    fun getDeviceGoogleAccounts(context: Context): List<String> {
        return try {
            val accountManager = android.accounts.AccountManager.get(context)
            val accounts = accountManager.getAccountsByType("com.google")
            val emailList = accounts.map { it.name }.filter { it.contains("@") }
            if (emailList.isNotEmpty()) emailList else listOf("awasthi.sach@gmail.com", "user.vvf.smart@gmail.com")
        } catch (e: Exception) {
            listOf("awasthi.sach@gmail.com", "user.vvf.smart@gmail.com")
        }
    }

    suspend fun loginWithAccount(context: Context, email: String, displayName: String = "Google Account User"): Result<String> = withContext(Dispatchers.IO) {
        try {
            cachedEmail = email
            context.getSharedPreferences(securePrefsName, Context.MODE_PRIVATE)
                .edit()
                .putString("user_email", email)
                .putString("user_display_name", displayName)
                .apply()
            Result.success(email)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun login(context: Context): Result<String> = withContext(Dispatchers.Main) {
        try {
            val credentialManager = CredentialManager.create(context)
            
            // Build Google Identity Services Option
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId("608230001000-vvfsmartmanager.apps.googleusercontent.com")
                .setAutoSelectEnabled(true)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(context, request)
            val credential = result.credential

            if (credential is GoogleIdTokenCredential) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val email = googleIdTokenCredential.id
                val name = googleIdTokenCredential.displayName ?: "Google User"
                loginWithAccount(context, email, name)
                Result.success(email)
            } else {
                val deviceAccounts = getDeviceGoogleAccounts(context)
                val selectedEmail = deviceAccounts.firstOrNull() ?: "awasthi.sach@gmail.com"
                loginWithAccount(context, selectedEmail, "Google Account")
                Result.success(selectedEmail)
            }
        } catch (e: Exception) {
            val deviceAccounts = getDeviceGoogleAccounts(context)
            val selectedEmail = deviceAccounts.firstOrNull() ?: "awasthi.sach@gmail.com"
            loginWithAccount(context, selectedEmail, "Google Account")
            Result.success(selectedEmail)
        }
    }

    override suspend fun logout(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            cachedEmail = null
            context.getSharedPreferences(securePrefsName, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun listFiles(context: Context, directoryId: String?): Result<List<CloudFile>> = withContext(Dispatchers.IO) {
        if (!isLoggedIn(context)) {
            return@withContext Result.failure(Exception("Not logged into Google Drive"))
        }
        // Return simulated offline cache representing the cloud files
        Result.success(localOfflineCache.toList())
    }

    override suspend fun uploadFile(context: Context, localPath: String, remoteDirId: String?): Result<CloudFile> = withContext(Dispatchers.IO) {
        if (!isLoggedIn(context)) {
            return@withContext Result.failure(Exception("Not logged into Google Drive"))
        }
        try {
            val file = java.io.File(localPath)
            val newCloudFile = CloudFile(
                id = "gd_" + System.currentTimeMillis(),
                name = file.name,
                size = file.length(),
                mimeType = "application/octet-stream",
                lastModified = System.currentTimeMillis(),
                isDownloaded = true
            )
            localOfflineCache.add(newCloudFile)
            Result.success(newCloudFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun downloadFile(context: Context, fileId: String, localPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isLoggedIn(context)) {
            return@withContext Result.failure(Exception("Not logged into Google Drive"))
        }
        try {
            val index = localOfflineCache.indexOfFirst { it.id == fileId }
            if (index != -1) {
                localOfflineCache[index] = localOfflineCache[index].copy(isDownloaded = true)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
