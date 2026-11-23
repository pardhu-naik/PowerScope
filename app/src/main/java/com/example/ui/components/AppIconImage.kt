package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.data.repository.PowerRepository

@Composable
fun AppIconImage(
    packageName: String,
    repository: PowerRepository,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, packageName) {
        value = repository.getAppIcon(packageName)?.let { drawable ->
            runCatching { drawable.toBitmap(width = 96, height = 96).asImageBitmap() }.getOrNull()
        }
    }

    val icon = bitmap
    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = packageName,
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(10.dp))
        )
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Icon(
                imageVector = Icons.Rounded.Android,
                contentDescription = packageName,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
