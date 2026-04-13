package com.ai.phoneagent.ui.components.markdown

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.Expand
import kotlinx.coroutines.launch
import java.util.Base64

// ─────────────────────────────────────────────────────────────────────────────
//  Height cache – keyed on diagram source so the WebView doesn't jump on reuse
// ─────────────────────────────────────────────────────────────────────────────

private val mermaidHeightCache = HashMap<String, Int>()

// ─────────────────────────────────────────────────────────────────────────────
//  Mermaid composable
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Renders a Mermaid diagram using a WebView that loads Mermaid.js from CDN.
 *
 * The WebView calls back via [AndroidInterface]:
 *  - `updateHeight(px)` – adjusts the composable height
 *  - `exportImage(svgData)` – saves diagram as PNG to Downloads
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun Mermaid(code: String, modifier: Modifier = Modifier) {
    val isDark   = isSystemInDarkTheme()
    val context  = LocalContext.current
    val density  = LocalDensity.current
    val cs       = MaterialTheme.colorScheme
    val scope    = rememberCoroutineScope()

    val cachedHeightPx = mermaidHeightCache[code] ?: 300
    var heightPx by rememberSaveable(code) { mutableIntStateOf(cachedHeightPx) }
    var showPreview by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var exportSvgData by remember { mutableStateOf<String?>(null) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val html = remember(code, isDark, cs.primary.toArgb()) {
        buildMermaidHtml(code, isDark, cs)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Toolbar
        Row(
            modifier          = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text  = "mermaid",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { showPreview = true }, modifier = Modifier.size(28.dp)) {
                Icon(Lucide.Expand, "Preview diagram", modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(
                onClick  = { exportSvgData?.let { saveMermaidPng(context, it) } },
                enabled  = exportSvgData != null,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(Lucide.Download, "Export PNG", modifier = Modifier.size(16.dp),
                    tint = if (exportSvgData != null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            }
        }

        // WebView
        val heightDp = with(density) { heightPx.toDp() }.coerceAtLeast(80.dp)
        Box(
            modifier          = Modifier.fillMaxWidth().height(heightDp),
            contentAlignment  = Alignment.Center,
        ) {
            MermaidWebView(
                html     = html,
                modifier = Modifier.fillMaxSize(),
                onHeightChanged = { px ->
                    heightPx = px
                    mermaidHeightCache[code] = px
                    isLoading = false
                },
                onSvgExport = { svg ->
                    exportSvgData = svg
                },
            )
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(32.dp))
        }
    }

    // Preview bottom sheet
    if (showPreview) {
        ModalBottomSheet(
            onDismissRequest = { scope.launch { sheetState.hide(); showPreview = false } },
            sheetState       = sheetState,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Diagram preview", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { scope.launch { sheetState.hide(); showPreview = false } }) {
                        Text("Close")
                    }
                }
                Spacer(Modifier.height(8.dp))
                MermaidWebView(
                    html            = html,
                    modifier        = Modifier.fillMaxWidth().height(480.dp),
                    onHeightChanged = {},
                    onSvgExport     = {},
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Internal – WebView factory
// ─────────────────────────────────────────────────────────────────────────────

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MermaidWebView(
    html: String,
    modifier: Modifier = Modifier,
    onHeightChanged: (Int) -> Unit,
    onSvgExport: (String) -> Unit,
) {
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient  = WebViewClient()
                webChromeClient = WebChromeClient()
                addJavascriptInterface(
                    AndroidInterface(onHeightChanged, onSvgExport),
                    "AndroidInterface"
                )
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(
                "https://mermaid.js.org",
                html,
                "text/html",
                "UTF-8",
                null,
            )
        },
        modifier = modifier,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  JS Bridge
// ─────────────────────────────────────────────────────────────────────────────

private class AndroidInterface(
    private val onHeightChanged: (Int) -> Unit,
    private val onSvgExport: (String) -> Unit,
) {
    @JavascriptInterface
    fun updateHeight(height: Int) {
        onHeightChanged(height)
    }

    @JavascriptInterface
    fun exportImage(svgData: String) {
        onSvgExport(svgData)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  HTML template
// ─────────────────────────────────────────────────────────────────────────────

@Suppress("LongMethod")
private fun buildMermaidHtml(
    code: String,
    isDark: Boolean,
    cs: androidx.compose.material3.ColorScheme,
): String {
    fun Color.hex(): String = "#%06X".format(0xFFFFFF and toArgb())
    val theme     = if (isDark) "dark" else "default"
    val bgAlpha   = "rgba(0,0,0,0)"
    val fontColor = cs.onSurface.hex()
    val lineColor = cs.outline.hex()
    val nodeColor = cs.surfaceVariant.hex()
    val nodeBg    = cs.surface.hex()
    val primary   = cs.primary.hex()
    val secondary = cs.secondary.hex()

    // Escape backticks in diagram source
    val escaped = code.replace("\\", "\\\\").replace("`", "\\`")

    return """
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<style>
  html, body { margin:0; padding:0; background:$bgAlpha; }
  .mermaid  { max-width:100%; }
</style>
</head>
<body>
<div class="mermaid" id="diagram">${code.replace("<","&lt;").replace(">","&gt;")}</div>
<script src="https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.min.js"></script>
<script>
mermaid.initialize({
  startOnLoad: false,
  theme: '$theme',
  themeVariables: {
    background:        '$bgAlpha',
    mainBkg:           '$nodeBg',
    nodeBorder:        '$lineColor',
    clusterBkg:        '$nodeColor',
    lineColor:         '$lineColor',
    fontFamily:        'sans-serif',
    fontSize:          '14px',
    primaryColor:      '$primary',
    primaryBorderColor: '$lineColor',
    primaryTextColor:  '$fontColor',
    secondaryColor:    '$secondary',
    tertiaryColor:     '$nodeBg',
    edgeLabelBackground: '$nodeBg',
    titleColor:        '$fontColor',
  },
  securityLevel: 'loose',
});
async function render() {
  try {
    const { svg } = await mermaid.render('mermaid-svg', `$escaped`);
    document.getElementById('diagram').innerHTML = svg;
    AndroidInterface.exportImage(svg);
    setTimeout(function() {
      var h = document.body.scrollHeight;
      AndroidInterface.updateHeight(h);
    }, 100);
  } catch(e) {
    document.getElementById('diagram').innerText = 'Mermaid error: ' + e.message;
    AndroidInterface.updateHeight(100);
  }
}
render();
</script>
</body>
</html>
    """.trimIndent()
}

// ─────────────────────────────────────────────────────────────────────────────
//  SVG → PNG save helper
// ─────────────────────────────────────────────────────────────────────────────

private fun saveMermaidPng(context: Context, svgData: String) {
    try {
        // Encode SVG as a data URI and save via MediaStore
        val bytes    = svgData.toByteArray(Charsets.UTF_8)
        val b64      = Base64.getEncoder().encodeToString(bytes)
        val filename = "mermaid_${System.currentTimeMillis()}.svg"
        val cv = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, filename)
            put(MediaStore.Downloads.MIME_TYPE, "image/svg+xml")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
        uri?.let { context.contentResolver.openOutputStream(it)?.use { os -> os.write(bytes) } }
        Toast.makeText(context, "Saved to Downloads/$filename", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  HtmlWebView – generic WebView for HTML/SVG preview (used by CodeBlock)
// ─────────────────────────────────────────────────────────────────────────────

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HtmlWebView(html: String, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                webViewClient = WebViewClient()
            }
        },
        update  = { webView ->
            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        },
        modifier = modifier,
    )
}
