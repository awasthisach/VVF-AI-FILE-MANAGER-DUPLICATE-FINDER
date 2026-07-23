package com.example.data.search

import com.example.domain.model.LocalFile
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Advanced On-Device Layered Semantic Search Engine for VVF Smart Manager.
 * 
 * Multi-layer search architecture:
 * Level 1: Filename Exact & Substring Match (Highest priority)
 * Level 2: Metadata Match (Category, Classification, MimeType, Size)
 * Level 3: Multilingual Semantic Intent & Synonym Mapping (Hindi & English Query Concept Resolution)
 * Level 4: Fuzzy Text Similarity Matching (Typo tolerance via Levenshtein Distance & Token Overlap)
 */
class OnDeviceSemanticSearchEngine {

    data class SearchResult(
        val file: LocalFile,
        val score: Int,
        val matchType: String
    )

    private val semanticSynonymMap = mapOf(
        // Finance / Bills / Tax / Audit
        "finance" to listOf("audit", "report", "invoice", "tax", "salary", "bill", "statement", "docx", "pdf", "वित्त", "पैसे", "टैक्स", "बिल", "इनवॉइस", "वेतन", "खाता", "रिपोर्ट"),
        "वित्त" to listOf("audit", "report", "invoice", "tax", "salary", "bill", "docx", "pdf", "finance"),
        "पैसे" to listOf("audit", "invoice", "tax", "salary", "bill", "finance"),
        "टैक्स" to listOf("audit", "tax", "report", "finance"),
        "बिल" to listOf("bill", "invoice", "finance"),

        // Personal / Identity / Photos
        "personal" to listOf("aadhaar", "passport", "id", "photo", "image", "family", "jpg", "jpeg", "png", "mp3", "निजी", "आधार", "पहचान", "फोटो", "तस्वीर", "परिवार", "चित्र"),
        "निजी" to listOf("aadhaar", "passport", "id", "photo", "image", "jpg", "jpeg", "personal"),
        "आधार" to listOf("aadhaar", "card", "id", "personal", "jpg", "jpeg"),
        "फोटो" to listOf("photo", "image", "jpg", "jpeg", "png", "images"),
        "तस्वीर" to listOf("photo", "image", "jpg", "jpeg", "png", "images"),

        // Work / Projects / Office
        "work" to listOf("project", "startup", "pitch", "pdf", "doc", "code", "logo", "vvf", "काम", "प्रोजेक्ट", "कोड", "कार्यालय", "दस्तावेज"),
        "काम" to listOf("project", "startup", "pitch", "pdf", "doc", "vvf", "work"),
        "प्रोजेक्ट" to listOf("project", "startup", "pdf", "vvf", "work"),

        // Documents / PDF / Text
        "document" to listOf("pdf", "docx", "doc", "report", "audit", "project", "दस्तावेज", "कागज", "फाइल"),
        "documents" to listOf("pdf", "docx", "doc", "report", "audit", "project", "दस्तावेज", "कागज"),
        "दस्तावेज" to listOf("pdf", "docx", "doc", "report", "documents"),
        "कागज" to listOf("pdf", "docx", "doc", "documents"),

        // Audio / Songs / Music
        "audio" to listOf("mp3", "sanskrit", "anthem", "song", "music", "ऑडियो", "गाना", "संगीत", "गीत", "आवाज"),
        "ऑडियो" to listOf("mp3", "sanskrit", "anthem", "song", "music", "audio"),
        "गाना" to listOf("mp3", "sanskrit", "anthem", "song", "music", "audio"),
        "संगीत" to listOf("mp3", "sanskrit", "anthem", "song", "music", "audio"),

        // Video / Movies
        "video" to listOf("mp4", "pitch", "demo", "movie", "clip", "वीडियो", "फिल्म", "मूवी"),
        "वीडियो" to listOf("mp4", "pitch", "demo", "movie", "video"),
        "फिल्म" to listOf("mp4", "pitch", "demo", "movie", "video")
    )

    /**
     * Performs a layered semantic search over the given file list.
     * Returns matching files sorted by calculated semantic relevance score descending.
     */
    fun search(files: List<LocalFile>, rawQuery: String): List<LocalFile> {
        val query = rawQuery.trim().lowercase(Locale.ROOT)
        if (query.isBlank()) return files

        val queryTokens = query.split(Regex("\\s+")).filter { it.isNotBlank() }

        val scoredResults = files.mapNotNull { file ->
            var score = 0
            var primaryMatchReason = "No Match"

            val fileNameLower = file.name.lowercase(Locale.ROOT)
            val categoryLower = file.category.lowercase(Locale.ROOT)
            val classificationLower = file.classification.lowercase(Locale.ROOT)
            val mimeLower = file.mimeType.lowercase(Locale.ROOT)

            // LEVEL 1: Filename Exact & Substring Match
            if (fileNameLower == query) {
                score += 100
                primaryMatchReason = "सटीक फाइल नाम"
            } else if (fileNameLower.contains(query)) {
                score += 85
                primaryMatchReason = "फाइल नाम मिलान"
            }

            // LEVEL 2: Direct Metadata Field Match
            if (categoryLower.contains(query) || classificationLower.contains(query) || mimeLower.contains(query)) {
                score += 70
                if (primaryMatchReason == "No Match") primaryMatchReason = "श्रेणी / मेटाडेटा मिलान"
            }

            // LEVEL 3: Semantic Intent & Synonym Mapping
            for (token in queryTokens) {
                val synonyms = semanticSynonymMap[token] ?: emptyList()
                val isSemanticMatch = synonyms.any { synonym ->
                    fileNameLower.contains(synonym) || 
                    categoryLower.contains(synonym) || 
                    classificationLower.contains(synonym) ||
                    mimeLower.contains(synonym)
                }
                if (isSemanticMatch) {
                    score += 60
                    if (primaryMatchReason == "No Match") primaryMatchReason = "ऑन-डिवाइस सिमेंटिक कांसेप्ट"
                }
            }

            // LEVEL 4: Multi-Token Overlap & Fuzzy Similarity Match
            for (token in queryTokens) {
                if (token.length >= 3) {
                    val nameTokens = fileNameLower.split(Regex("[._\\-\\s]+"))
                    for (nToken in nameTokens) {
                        if (nToken.contains(token)) {
                            score += 40
                            if (primaryMatchReason == "No Match") primaryMatchReason = "आंशिक टोकन"
                        } else {
                            val similarity = calculateFuzzySimilarity(token, nToken)
                            if (similarity >= 0.75f) {
                                score += (30 * similarity).toInt()
                                if (primaryMatchReason == "No Match") primaryMatchReason = "फ़ज़ी स्पेलिंग मिलान"
                            }
                        }
                    }
                }
            }

            if (score > 0) {
                SearchResult(file, score, primaryMatchReason)
            } else {
                null
            }
        }

        return scoredResults
            .sortedWith(compareByDescending<SearchResult> { it.score }.thenBy { it.file.name })
            .map { it.file }
    }

    /**
     * Calculates Normalized Levenshtein Similarity between two text strings (0.0f to 1.0f).
     */
    private fun calculateFuzzySimilarity(s1: String, s2: String): Float {
        if (s1 == s2) return 1.0f
        if (s1.isEmpty() || s2.isEmpty()) return 0.0f

        val dist = levenshteinDistance(s1, s2)
        val maxLen = max(s1.length, s2.length)
        return 1.0f - (dist.toFloat() / maxLen.toFloat())
    }

    private fun levenshteinDistance(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }

        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j

        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = min(
                    min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[a.length][b.length]
    }
}
