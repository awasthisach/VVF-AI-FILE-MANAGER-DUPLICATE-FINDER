package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "file_metadata")
data class FileMetadataEntity(
    @PrimaryKey val id: String,
    val encryptedName: String,
    val encryptedSize: String,
    val encryptedMimeType: String,
    val category: String, // images, documents, audio, video
    val lastModified: String,
    val classification: String = "Unclassified"
)
