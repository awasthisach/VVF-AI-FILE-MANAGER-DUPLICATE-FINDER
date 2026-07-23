package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FileMetadataDao {

    @Query("SELECT * FROM file_metadata ORDER BY lastModified DESC")
    fun getAllFiles(): Flow<List<FileMetadataEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: FileMetadataEntity)

    @Update
    suspend fun updateFile(file: FileMetadataEntity)

    @Query("SELECT * FROM file_metadata WHERE id = :id")
    suspend fun getFileById(id: String): FileMetadataEntity?

    @Query("SELECT * FROM file_metadata WHERE category = :category ORDER BY lastModified DESC")
    fun getFilesByCategory(category: String): Flow<List<FileMetadataEntity>>

    @Query("DELETE FROM file_metadata WHERE id = :id")
    suspend fun deleteFile(id: String)
    
    @Query("SELECT COUNT(*) FROM file_metadata")
    suspend fun getCount(): Int
}
