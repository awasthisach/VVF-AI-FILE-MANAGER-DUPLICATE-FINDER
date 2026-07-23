package com.example.data.database

import com.example.data.security.MetadataEncryptionService
import com.example.domain.model.LocalFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FileMetadataRepository(
    private val fileMetadataDao: FileMetadataDao,
    private val encryptionService: MetadataEncryptionService
) {

    /**
     * Exposes all local files from the database.
     * Decrypts the sensitive metadata fields reactively.
     */
    val allFiles: Flow<List<LocalFile>> = fileMetadataDao.getAllFiles().map { entities ->
        entities.map { entity ->
            LocalFile(
                id = entity.id,
                name = encryptionService.decrypt(entity.encryptedName),
                size = encryptionService.decrypt(entity.encryptedSize),
                mimeType = encryptionService.decrypt(entity.encryptedMimeType),
                category = entity.category,
                lastModified = entity.lastModified,
                classification = entity.classification
            )
        }
    }

    /**
     * Queries the Room database directly to fetch files by category, decrypting them reactively.
     */
    fun getFilesByCategory(category: String): Flow<List<LocalFile>> {
        return fileMetadataDao.getFilesByCategory(category).map { entities ->
            entities.map { entity ->
                LocalFile(
                    id = entity.id,
                    name = encryptionService.decrypt(entity.encryptedName),
                    size = encryptionService.decrypt(entity.encryptedSize),
                    mimeType = encryptionService.decrypt(entity.encryptedMimeType),
                    category = entity.category,
                    lastModified = entity.lastModified,
                    classification = entity.classification
                )
            }
        }
    }

    /**
     * Inserts a new local file by encrypting its sensitive metadata fields before saving at rest.
     */
    suspend fun insertFile(file: LocalFile) {
        val entity = FileMetadataEntity(
            id = file.id,
            encryptedName = encryptionService.encrypt(file.name),
            encryptedSize = encryptionService.encrypt(file.size),
            encryptedMimeType = encryptionService.encrypt(file.mimeType),
            category = file.category,
            lastModified = file.lastModified,
            classification = file.classification
        )
        fileMetadataDao.insertFile(entity)
    }

    /**
     * Updates an existing file's metadata securely.
     */
    suspend fun updateFile(file: LocalFile) {
        val entity = FileMetadataEntity(
            id = file.id,
            encryptedName = encryptionService.encrypt(file.name),
            encryptedSize = encryptionService.encrypt(file.size),
            encryptedMimeType = encryptionService.encrypt(file.mimeType),
            category = file.category,
            lastModified = file.lastModified,
            classification = file.classification
        )
        fileMetadataDao.updateFile(entity)
    }

    /**
     * Deletes a file's metadata from the local database.
     */
    suspend fun deleteFile(id: String) {
        fileMetadataDao.deleteFile(id)
    }

    /**
     * Pre-populates the database with default files if it is currently empty.
     */
    suspend fun populateInitialDataIfEmpty() {
        if (fileMetadataDao.getCount() == 0) {
            val defaultFiles = listOf(
                LocalFile("f_1", "Project_VVF_SmartManager.pdf", "4.2 MB", "PDF", "Documents", "19 July 2026", "Work"),
                LocalFile("f_2", "Aadhaar_Card_Copy.jpg", "1.5 MB", "JPEG", "Images", "18 July 2026", "Personal"),
                LocalFile("f_3", "National_Anthem_Sanskrit.mp3", "8.9 MB", "MP3", "Audio", "15 July 2026", "Personal"),
                LocalFile("f_4", "Startup_Demo_Pitch.mp4", "45.1 MB", "MP4", "Video", "10 July 2026", "Work"),
                LocalFile("f_5", "Audit_Report_2026.docx", "840 KB", "DOCX", "Documents", "08 July 2026", "Finance"),
                LocalFile("f_6", "Golden_Leaf_Logo_VVF.png", "2.1 MB", "PNG", "Images", "19 July 2026", "Work")
            )
            for (file in defaultFiles) {
                insertFile(file)
            }
        }
    }
}
