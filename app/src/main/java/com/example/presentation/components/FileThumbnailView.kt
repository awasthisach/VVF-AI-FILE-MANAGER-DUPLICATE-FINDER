package com.example.presentation.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.thumbnail.ThumbnailManager
import com.example.domain.model.LocalFile
import com.example.ui.theme.BhagwaOrange
import com.example.ui.theme.BorderColor
import com.example.ui.theme.CosmicCard
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.SkyCyan
import com.example.ui.theme.SoftGold

@Composable
fun FileThumbnailView(
    file: LocalFile,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var bitmap by remember(file.id, file.name, file.filePath) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(file.id, file.name, file.filePath) { mutableStateOf(true) }

    LaunchedEffect(file.id, file.name, file.filePath) {
        isLoading = true
        bitmap = ThumbnailManager.getThumbnail(
            context = context,
            fileId = file.id,
            fileName = file.name,
            filePath = file.filePath,
            mimeType = file.mimeType
        )
        isLoading = false
    }

    val icon = when (file.category) {
        "Images" -> Icons.Default.Image
        "Audio" -> Icons.Default.AudioFile
        "Video" -> Icons.Default.VideoFile
        else -> Icons.Default.Description
    }

    val iconColor = when (file.category) {
        "Images" -> SkyCyan
        "Audio" -> EmeraldGreen
        "Video" -> BhagwaOrange
        else -> SoftGold
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(CosmicCard)
            .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .testTag("file_thumbnail_${file.id}"),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(size / 3),
                color = iconColor,
                strokeWidth = 2.dp
            )
        } else if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = file.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = file.category,
                tint = iconColor,
                modifier = Modifier.size(size * 0.5f)
            )
        }
    }
}
