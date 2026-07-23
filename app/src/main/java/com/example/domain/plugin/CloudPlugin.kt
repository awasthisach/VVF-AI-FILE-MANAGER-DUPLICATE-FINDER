package com.example.domain.plugin

import android.content.Context

data class CloudFile(
    val id: String,
    val name: String,
    val size: Long,
    val mimeType: String,
    val lastModified: Long,
    val isDirectory: Boolean = false,
    val isDownloaded: Boolean = false,
    val isPendingSync: Boolean = false
)

interface CloudPlugin {
    val id: String
    val name: String
    val iconName: String // Matches vector names
    val isCore: Boolean
    val isInstalled: Boolean
    
    fun isLoggedIn(context: Context): Boolean
    
    suspend fun login(context: Context): Result<String> // Returns user email or unique identifier
    
    suspend fun logout(context: Context): Result<Unit>
    
    suspend fun listFiles(context: Context, directoryId: String? = null): Result<List<CloudFile>>
    
    suspend fun uploadFile(context: Context, localPath: String, remoteDirId: String? = null): Result<CloudFile>
    
    suspend fun downloadFile(context: Context, fileId: String, localPath: String): Result<Unit>
}
