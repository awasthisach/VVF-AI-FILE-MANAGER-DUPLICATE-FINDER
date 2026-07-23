package com.example.data.api

import android.content.Context
import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class GeminiRepository(private val context: Context? = null) {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val apiService: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }

    /**
     * Resolves the active Gemini API Key.
     * Priority:
     * 1. User-configured key in SharedPreferences (local storage)
     * 2. BuildConfig key (if present and not placeholder)
     */
    fun getActiveApiKey(): String {
        context?.let { ctx ->
            val prefs = ctx.getSharedPreferences("vvf_smart_manager_prefs", Context.MODE_PRIVATE)
            val customKey = prefs.getString("custom_gemini_api_key", "")?.trim() ?: ""
            if (customKey.isNotEmpty()) {
                return customKey
            }
        }
        val buildKey = BuildConfig.GEMINI_API_KEY.trim()
        if (buildKey.isNotEmpty() && buildKey != "MY_GEMINI_API_KEY" && buildKey != "mock_key") {
            return buildKey
        }
        return ""
    }

    fun saveCustomApiKey(key: String): Boolean {
        context?.let { ctx ->
            val prefs = ctx.getSharedPreferences("vvf_smart_manager_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("custom_gemini_api_key", key.trim()).apply()
            return true
        }
        return false
    }

    fun clearCustomApiKey() {
        context?.let { ctx ->
            val prefs = ctx.getSharedPreferences("vvf_smart_manager_prefs", Context.MODE_PRIVATE)
            prefs.edit().remove("custom_gemini_api_key").apply()
        }
    }

    fun getSavedCustomApiKey(): String {
        context?.let { ctx ->
            val prefs = ctx.getSharedPreferences("vvf_smart_manager_prefs", Context.MODE_PRIVATE)
            return prefs.getString("custom_gemini_api_key", "")?.trim() ?: ""
        }
        return ""
    }

    fun isUsingCustomKey(): Boolean {
        return getSavedCustomApiKey().isNotEmpty()
    }

    fun hasValidApiKey(): Boolean {
        return getActiveApiKey().isNotEmpty()
    }

    /**
     * Summarizes the file contents using the Gemini API (gemini-3.5-flash model).
     * Returns a concise Hindi summary.
     */
    suspend fun summarizeFileContent(fileName: String, content: String): String = withContext(Dispatchers.IO) {
        val apiKey = getActiveApiKey()
        if (apiKey.isEmpty()) {
            return@withContext "त्रुटि: जेमिनी एपीआई की (Gemini API Key) सेट नहीं है। सुरक्षा कारणों से APK में की हार्डकोड नहीं की गई है। कृपया ऊपरी दाएँ कोने वाले 'API Key' बटन पर क्लिक करके अपनी खुद की Gemini API Key दर्ज करें।"
        }

        val prompt = """
            आप एक एक्सपर्ट डॉक्यूमेंट विश्लेषक हैं। कृपया फ़ाइल '$fileName' के निम्नलिखित कंटेंट का एक संक्षिप्त और जानकारीपूर्ण सारांश हिंदी भाषा (Hindi Language) में तैयार करें:
            
            कंटेंट:
            $content
            
            सारांश (अधिकतम 3-4 बिंदु):
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(GeminiPart(text = prompt))
                )
            )
        )

        try {
            val response = apiService.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text 
                ?: "त्रुटि: सारांश उत्पन्न करने में असमर्थ।"
        } catch (e: Exception) {
            e.printStackTrace()
            "त्रुटि: एपीआई कनेक्शन विफल रहा। विवरण: ${e.localizedMessage}"
        }
    }

    /**
     * Categorizes the file contents dynamically using the Gemini API.
     * Returns one of: "Images", "Documents", "Audio", "Video".
     */
    suspend fun categorizeFile(fileName: String, contentPreview: String): String = withContext(Dispatchers.IO) {
        val apiKey = getActiveApiKey()
        if (apiKey.isEmpty()) {
            return@withContext "Documents" // Default fallback
        }

        val prompt = """
            You are a file manager cataloger. Analyze the file name '$fileName' and content preview to categorize the file.
            Content Preview: $contentPreview
            
            Choose exactly one category from: "Documents", "Images", "Audio", "Video". 
            Reply with ONLY the category word (no punctuation, no explanation).
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(GeminiPart(text = prompt))
                )
            )
        )

        try {
            val response = apiService.generateContent(apiKey, request)
            val result = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim() ?: "Documents"
            val cleanResult = result.replace(Regex("[^a-zA-Z]"), "")
            if (cleanResult in listOf("Documents", "Images", "Audio", "Video")) {
                cleanResult
            } else {
                "Documents" // Safe fallback
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "Documents" // Default fallback
        }
    }

    /**
     * Classifies a file dynamically as 'Work', 'Personal', or 'Finance' using the Gemini API based on its metadata.
     */
    suspend fun classifyFile(fileName: String, size: String, mimeType: String, category: String): String = withContext(Dispatchers.IO) {
        val apiKey = getActiveApiKey()
        if (apiKey.isEmpty()) {
            // Simulated local classification if API key is not configured or during testing
            val name = fileName.lowercase()
            return@withContext when {
                name.contains("invoice") || name.contains("audit") || name.contains("report") || name.contains("finance") || name.contains("bill") || name.contains("salary") -> "Finance"
                name.contains("aadhaar") || name.contains("copy") || name.contains("personal") || name.contains("photo") || name.contains("anthem") || name.contains("family") -> "Personal"
                else -> "Work"
            }
        }

        val prompt = """
            You are an advanced file metadata classification engine. Your task is to analyze the metadata of a file and classify it into exactly one of three folders: "Work", "Personal", or "Finance".
            
            File Details:
            - Name: $fileName
            - Size: $size
            - Format/Mime: $mimeType
            - Category Group: $category
            
            Classification Guidelines:
            - "Finance" is for tax documents, invoices, audit reports, salary slips, bills, bank statements, financial charts.
            - "Personal" is for family photos, ID cards (Aadhaar, Passport), national anthems, personal music, private snapshots.
            - "Work" is for project specifications, startup pitches, source code, corporate logos, task lists, technical documentation.
            
            Rules:
            1. Respond with exactly one of these words: "Work", "Personal", "Finance"
            2. Do NOT include any other text, punctuation, explanation, or markdown formatting.
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(GeminiPart(text = prompt))
                )
            )
        )

        try {
            val response = apiService.generateContent(apiKey, request)
            val result = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim() ?: "Work"
            val cleanResult = result.replace(Regex("[^a-zA-Z]"), "")
            if (cleanResult == "Work" || cleanResult == "Personal" || cleanResult == "Finance") {
                cleanResult
            } else {
                val name = fileName.lowercase()
                when {
                    name.contains("invoice") || name.contains("audit") || name.contains("report") || name.contains("finance") || name.contains("bill") || name.contains("salary") -> "Finance"
                    name.contains("aadhaar") || name.contains("copy") || name.contains("personal") || name.contains("photo") || name.contains("anthem") || name.contains("family") -> "Personal"
                    else -> "Work"
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            val name = fileName.lowercase()
            when {
                name.contains("invoice") || name.contains("audit") || name.contains("report") || name.contains("finance") || name.contains("bill") || name.contains("salary") -> "Finance"
                name.contains("aadhaar") || name.contains("copy") || name.contains("personal") || name.contains("photo") || name.contains("anthem") || name.contains("family") -> "Personal"
                else -> "Work"
            }
        }
    }

    /**
     * Generates 3-5 descriptive hashtag tags for a file using Gemini API based on metadata and content snippets.
     */
    suspend fun generateDescriptiveTags(fileName: String, contentSnippet: String, category: String): List<String> = withContext(Dispatchers.IO) {
        val apiKey = getActiveApiKey()
        if (apiKey.isEmpty()) {
            val name = fileName.lowercase()
            val defaultTags = mutableListOf<String>()
            if (name.contains("pdf") || category == "Documents") defaultTags.add("#Document")
            if (name.contains("invoice") || name.contains("bill") || name.contains("audit")) defaultTags.add("#Finance")
            if (name.contains("project") || name.contains("code") || name.contains("demo")) defaultTags.add("#Work")
            if (name.contains("aadhaar") || name.contains("photo") || name.contains("vacation")) defaultTags.add("#Personal")
            if (defaultTags.isEmpty()) defaultTags.add("#File")
            return@withContext defaultTags
        }

        val prompt = """
            Analyze the following file details and generate 3-5 relevant, short descriptive tags (starting with #).
            
            File Name: $fileName
            Category: $category
            Content Preview: $contentSnippet
            
            Format output as a comma-separated list of hashtags, e.g.: #Invoice, #Tax2026, #Finance, #PDF
            Do NOT include explanations or intro text.
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(GeminiPart(text = prompt))
                )
            )
        )

        try {
            val response = apiService.generateContent(apiKey, request)
            val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim() ?: ""
            val tags = text.split(",").map { tag ->
                val t = tag.trim()
                if (t.startsWith("#")) t else "#$t"
            }.filter { it.length > 1 }
            if (tags.isNotEmpty()) tags else listOf("#$category", "#AutoTagged")
        } catch (e: Exception) {
            listOf("#$category", "#LocalFile")
        }
    }
}
