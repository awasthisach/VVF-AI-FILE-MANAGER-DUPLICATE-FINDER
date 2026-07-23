package com.example.data.api

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GeminiRepositoryTest {

    private val geminiRepository = GeminiRepository()

    @Test
    fun testSimulatedClassificationForFinance() = runTest {
        // Since API key is empty/placeholder, it should fall back to simulated local classification
        val result = geminiRepository.classifyFile(
            fileName = "Project_Invoice_July.pdf",
            size = "150 KB",
            mimeType = "PDF",
            category = "Documents"
        )
        assertEquals("Finance", result)
    }

    @Test
    fun testSimulatedClassificationForPersonal() = runTest {
        val result = geminiRepository.classifyFile(
            fileName = "My_Aadhaar_Card_Copy.jpg",
            size = "1.2 MB",
            mimeType = "JPEG",
            category = "Images"
        )
        assertEquals("Personal", result)
    }

    @Test
    fun testSimulatedClassificationForWork() = runTest {
        val result = geminiRepository.classifyFile(
            fileName = "App_Development_Plan.docx",
            size = "800 KB",
            mimeType = "DOCX",
            category = "Documents"
        )
        assertEquals("Work", result)
    }
}
