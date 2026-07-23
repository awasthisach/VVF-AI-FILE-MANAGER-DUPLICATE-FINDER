package com.example.domain.model

data class LocalFile(
    val id: String,
    val name: String,
    val size: String,
    val mimeType: String,
    val category: String, // "Images", "Documents", "Audio", "Video"
    val lastModified: String,
    val classification: String = "Unclassified",
    val filePath: String? = null
)
