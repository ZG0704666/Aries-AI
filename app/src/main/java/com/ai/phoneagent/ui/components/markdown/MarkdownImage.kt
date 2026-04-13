package com.ai.phoneagent.ui.components.markdown

import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.X
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
//  ZoomableAsyncImage
// ─────────────────────────────────────────────────────────────────────────────

/**
 * An [AsyncImage] (Coil 3) that:
 *  - Shows a shimmer placeholder while loading
 *  - Supports pinch-to-zoom + pan gestures after load
 *  - Opens a full-screen [ImagePreviewDialog] on tap
 */
@Composable
fun ZoomableAsyncImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    var showPreview by remember { mutableStateOf(false) }
    val context = LocalContext.current

    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context)
            .data(url)
            .build(),
        contentDescription = contentDescription,
        contentScale       = ContentScale.FillWidth,
        loading            = { ShimmerBox(modifier = modifier.fillMaxWidth().height(180.dp)) },
        error              = {
            Box(
                modifier          = modifier.fillMaxWidth().height(80.dp)
                    .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.medium),
                contentAlignment  = Alignment.Center,
            ) {
                Text("Failed to load image", color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall)
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .clickable { showPreview = true },
    )

    if (showPreview) {
        ImagePreviewDialog(url = url, onDismiss = { showPreview = false })
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  ImagePreviewDialog – full-screen with zoom + download
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ImagePreviewDialog(url: String, onDismiss: () -> Unit) {
    var scale     by remember { mutableFloatStateOf(1f) }
    var offset    by remember { mutableStateOf(Offset.Zero) }
    val context   = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color    = Color.Black.copy(alpha = 0.95f),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Zoomable image
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(url)
                        .build(),
                    contentDescription = null,
                    contentScale       = ContentScale.Fit,
                    modifier           = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX        = scale,
                            scaleY        = scale,
                            translationX  = offset.x,
                            translationY  = offset.y,
                        )
                        .pointerInput(Unit) {
                            forEachGesture {
                                awaitPointerEventScope {
                                    awaitFirstDown(requireUnconsumed = false)
                                    do {
                                        val event = awaitPointerEvent()
                                        val zoom  = event.calculateZoom()
                                        val pan   = event.calculatePan()
                                        scale  = (scale * zoom).coerceIn(0.5f, 8f)
                                        offset = Offset(
                                            offset.x + pan.x * scale,
                                            offset.y + pan.y * scale,
                                        )
                                    } while (event.changes.any { it.pressed })
                                }
                            }
                        },
                )

                // Toolbar
                Row(
                    modifier          = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(onClick = { downloadImage(context, url) }) {
                        Icon(Lucide.Download, "Download", tint = Color.White,
                            modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = onDismiss) {
                        Icon(Lucide.X, "Close", tint = Color.White,
                            modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Shimmer placeholder
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ShimmerBox(modifier: Modifier = Modifier) {
    val isDark  = isSystemInDarkTheme()
    val base    = if (isDark) Color(0xFF3A3A3A) else Color(0xFFE0E0E0)
    val shimmer = if (isDark) Color(0xFF4A4A4A) else Color(0xFFF5F5F5)

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateX by transition.animateFloat(
        initialValue = -400f,
        targetValue  = 400f,
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerX",
    )

    Box(
        modifier = modifier.background(
            brush = Brush.linearGradient(
                colors = listOf(base, shimmer, base),
                start  = Offset(translateX, 0f),
                end    = Offset(translateX + 400f, 400f),
            ),
            shape = MaterialTheme.shapes.medium,
        )
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Download helper
// ─────────────────────────────────────────────────────────────────────────────

private fun downloadImage(context: android.content.Context, url: String) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val conn   = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.connect()
            val ext    = when (conn.contentType?.lowercase()) {
                "image/png"  -> "png"
                "image/gif"  -> "gif"
                "image/webp" -> "webp"
                else         -> "jpg"
            }
            val bytes  = conn.inputStream.readBytes()
            conn.disconnect()

            val filename = "image_${System.currentTimeMillis()}.$ext"
            val cv = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, filename)
                put(MediaStore.Downloads.MIME_TYPE, "image/$ext")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
            uri?.let { context.contentResolver.openOutputStream(it)?.use { os -> os.write(bytes) } }

            CoroutineScope(Dispatchers.Main).launch {
                Toast.makeText(context, "Saved to Downloads/$filename", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            CoroutineScope(Dispatchers.Main).launch {
                Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
