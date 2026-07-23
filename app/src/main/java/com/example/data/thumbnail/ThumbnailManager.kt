package com.example.data.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ThumbnailManager {

    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = (maxMemory / 8).coerceAtLeast(1024)
    private val thumbnailCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    /**
     * Retrieves or generates a bitmap thumbnail for a file (PDF or Image).
     */
    suspend fun getThumbnail(
        context: Context,
        fileId: String,
        fileName: String,
        filePath: String?,
        mimeType: String
    ): Bitmap = withContext(Dispatchers.IO) {
        val cacheKey = "$fileId-$filePath-$fileName"
        thumbnailCache.get(cacheKey)?.let { return@withContext it }

        var bitmap: Bitmap? = null

        // 1. Attempt to generate thumbnail from actual file path or URI if present
        if (!filePath.isNullOrEmpty()) {
            val file = File(filePath)
            if (file.exists()) {
                if (isPdf(fileName, mimeType)) {
                    bitmap = generatePdfThumbnailFromFile(file)
                } else if (isImage(fileName, mimeType)) {
                    bitmap = generateImageThumbnailFromFile(file)
                }
            } else if (filePath.startsWith("content://")) {
                val uri = Uri.parse(filePath)
                if (isPdf(fileName, mimeType)) {
                    bitmap = generatePdfThumbnailFromUri(context, uri)
                } else if (isImage(fileName, mimeType)) {
                    bitmap = generateImageThumbnailFromUri(context, uri)
                }
            }
        }

        // 2. If no physical file or rendering returned null, generate a high-quality stylized visual preview thumbnail
        if (bitmap == null) {
            bitmap = generateStylizedThumbnail(fileName, mimeType)
        }

        thumbnailCache.put(cacheKey, bitmap)
        return@withContext bitmap
    }

    fun isPdf(fileName: String, mimeType: String): Boolean {
        return mimeType.lowercase().contains("pdf") || fileName.lowercase().endsWith(".pdf")
    }

    fun isImage(fileName: String, mimeType: String): Boolean {
        val name = fileName.lowercase()
        val mime = mimeType.lowercase()
        return mime.startsWith("image/") || mime.contains("jpeg") || mime.contains("png") ||
                name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") ||
                name.endsWith(".webp") || name.endsWith(".gif")
    }

    private fun generatePdfThumbnailFromFile(file: File): Bitmap? {
        return try {
            val fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val pdfRenderer = PdfRenderer(fileDescriptor)
            if (pdfRenderer.pageCount > 0) {
                val page = pdfRenderer.openPage(0)
                val targetWidth = 160
                val targetHeight = (160f * (page.height.toFloat() / page.width.toFloat())).toInt().coerceIn(120, 220)
                val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)

                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                pdfRenderer.close()
                fileDescriptor.close()
                bitmap
            } else {
                pdfRenderer.close()
                fileDescriptor.close()
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun generatePdfThumbnailFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
            val pdfRenderer = PdfRenderer(pfd)
            if (pdfRenderer.pageCount > 0) {
                val page = pdfRenderer.openPage(0)
                val targetWidth = 160
                val targetHeight = (160f * (page.height.toFloat() / page.width.toFloat())).toInt().coerceIn(120, 220)
                val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)

                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                pdfRenderer.close()
                pfd.close()
                bitmap
            } else {
                pdfRenderer.close()
                pfd.close()
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun generateImageThumbnailFromFile(file: File): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
            options.inSampleSize = calculateInSampleSize(options, 200, 200)
            options.inJustDecodeBounds = false
            BitmapFactory.decodeFile(file.absolutePath, options)
        } catch (e: Exception) {
            null
        }
    }

    private fun generateImageThumbnailFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            val newStream = context.contentResolver.openInputStream(uri) ?: return null
            options.inSampleSize = calculateInSampleSize(options, 200, 200)
            options.inJustDecodeBounds = false
            val bitmap = BitmapFactory.decodeStream(newStream, null, options)
            newStream.close()
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /**
     * Generates a stylized bitmap preview thumbnail for files when direct file rendering is unavailable.
     */
    private fun generateStylizedThumbnail(fileName: String, mimeType: String): Bitmap {
        val width = 160
        val height = 160
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val isPdfFile = isPdf(fileName, mimeType)
        val isImgFile = isImage(fileName, mimeType)

        val bgColor = when {
            isPdfFile -> Color.parseColor("#1A1816")
            isImgFile -> Color.parseColor("#0F1A21")
            else -> Color.parseColor("#181A20")
        }
        canvas.drawColor(bgColor)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        if (isPdfFile) {
            // Draw paper background
            paint.color = Color.parseColor("#FAFAFC")
            val paperRect = RectF(24f, 16f, 136f, 144f)
            canvas.drawRoundRect(paperRect, 8f, 8f, paint)

            // Red PDF Banner header
            paint.color = Color.parseColor("#E53935")
            val bannerRect = RectF(24f, 16f, 136f, 44f)
            canvas.drawRoundRect(bannerRect, 8f, 8f, paint)
            canvas.drawRect(RectF(24f, 32f, 136f, 44f), paint)

            // Draw "PDF" label on banner
            paint.color = Color.WHITE
            paint.textSize = 15f
            paint.isFakeBoldText = true
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("PDF", 80f, 36f, paint)

            // Draw document content preview lines
            paint.color = Color.parseColor("#90A4AE")
            paint.strokeWidth = 3.5f
            val lineYPositions = floatArrayOf(58f, 72f, 86f, 100f, 114f, 128f)
            for (i in lineYPositions.indices) {
                val y = lineYPositions[i]
                val endX = if (i % 2 == 1) 105f else 125f
                canvas.drawLine(36f, y, endX, y, paint)
            }
        } else if (isImgFile) {
            // Stylized photo thumbnail canvas preview
            paint.color = Color.parseColor("#0F3846")
            canvas.drawRect(0f, 0f, 160f, 160f, paint)

            // Sunset circle
            paint.color = Color.parseColor("#FF9800")
            canvas.drawCircle(80f, 65f, 30f, paint)

            // Mountain shapes
            paint.color = Color.parseColor("#132A36")
            val path1 = Path().apply {
                moveTo(0f, 160f)
                lineTo(45f, 95f)
                lineTo(90f, 160f)
                close()
            }
            canvas.drawPath(path1, paint)

            val path2 = Path().apply {
                moveTo(50f, 160f)
                lineTo(115f, 80f)
                lineTo(160f, 160f)
                close()
            }
            paint.color = Color.parseColor("#1B3C4D")
            canvas.drawPath(path2, paint)

            // IMG Badge tag
            paint.color = Color.parseColor("#00E5FF")
            val badgeRect = RectF(96f, 12f, 148f, 34f)
            canvas.drawRoundRect(badgeRect, 6f, 6f, paint)

            paint.color = Color.BLACK
            paint.textSize = 11f
            paint.isFakeBoldText = true
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("IMG", 122f, 28f, paint)
        } else {
            // Generic file fallback
            paint.color = Color.parseColor("#37474F")
            canvas.drawRoundRect(RectF(30f, 20f, 130f, 140f), 12f, 12f, paint)

            paint.color = Color.parseColor("#ECEFF1")
            paint.textSize = 16f
            paint.isFakeBoldText = true
            paint.textAlign = Paint.Align.CENTER
            val ext = fileName.substringAfterLast('.', "FILE").uppercase().take(4)
            canvas.drawText(ext, 80f, 86f, paint)
        }

        return bitmap
    }
}
