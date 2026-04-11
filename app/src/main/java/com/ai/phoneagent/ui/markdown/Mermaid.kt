package com.ai.phoneagent.ui.markdown

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.util.concurrent.ConcurrentHashMap

private enum class MermaidTheme(val value: String) {
    DEFAULT("default"),
    DARK("dark")
}

private val heightCache = ConcurrentHashMap<String, Int>()

/**
 * Mermaid 图表渲染组件
 *
 * 使用 WebView + mermaid.js CDN 渲染。如果 CDN 不可用会显示原始代码作为 fallback。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun Mermaid(
    code: String,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.surface.luminance() < 0.5f
    val mermaidTheme = if (isDark) MermaidTheme.DARK else MermaidTheme.DEFAULT
    val density = LocalDensity.current

    val primaryColor = colorScheme.primaryContainer.toCssHex()
    val onPrimaryColor = colorScheme.onPrimaryContainer.toCssHex()
    val secondaryColor = colorScheme.secondaryContainer.toCssHex()
    val onSecondaryColor = colorScheme.onSecondaryContainer.toCssHex()
    val surfaceColor = colorScheme.surface.toCssHex()
    val onSurfaceColor = colorScheme.onSurface.toCssHex()
    val outlineColor = colorScheme.outline.toCssHex()
    val backgroundColor = colorScheme.surfaceContainerLow.toCssHex()

    // 高度状态 (dp)
    var contentHeightDp by remember {
        mutableIntStateOf(heightCache[code] ?: 150)
    }
    var renderFailed by remember { mutableStateOf(false) }

    val html = remember(code, isDark) {
        buildMermaidHtml(
            code = code,
            theme = mermaidTheme,
            primaryColor = primaryColor,
            onPrimaryColor = onPrimaryColor,
            secondaryColor = secondaryColor,
            onSecondaryColor = onSecondaryColor,
            surfaceColor = surfaceColor,
            onSurfaceColor = onSurfaceColor,
            outlineColor = outlineColor,
            backgroundColor = backgroundColor,
        )
    }

    if (renderFailed) {
        // Fallback: 原始代码文本
        Text(
            text = code,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        )
        return
    }

    val heightDp = contentHeightDp.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp)
            .animateContentSize(),
    ) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)

                    addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun updateHeight(height: Int) {
                                if (height > 0) {
                                    val dpVal = (height * density.density).toInt()
                                    heightCache[code] = dpVal
                                    contentHeightDp = dpVal
                                }
                            }

                            @JavascriptInterface
                            fun onRenderError(msg: String) {
                                renderFailed = true
                            }
                        },
                        "AndroidInterface"
                    )

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            view?.evaluateJavascript(
                                "setTimeout(function() { calculateAndSendHeight(); }, 800);",
                                null
                            )
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            errorCode: Int,
                            description: String?,
                            failingUrl: String?
                        ) {
                            // CDN 加载失败 → fallback
                            if (failingUrl?.contains("mermaid") == true) {
                                renderFailed = true
                            }
                        }
                    }

                    loadDataWithBaseURL(
                        "https://cdn.jsdelivr.net/",
                        html,
                        "text/html",
                        "UTF-8",
                        null
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 80.dp, max = heightDp.coerceAtLeast(80.dp)),
        )
    }
}

private fun buildMermaidHtml(
    code: String,
    theme: MermaidTheme,
    primaryColor: String,
    onPrimaryColor: String,
    secondaryColor: String,
    onSecondaryColor: String,
    surfaceColor: String,
    onSurfaceColor: String,
    outlineColor: String,
    backgroundColor: String,
): String {
    val escapedCode = code.escapeHtml()
    return """
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=5.0">
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            background-color: $backgroundColor;
            padding: 8px;
        }
        .mermaid {
            width: 100%;
        }
        .mermaid svg {
            max-width: 100%;
            height: auto !important;
        }
        .error-msg {
            color: $onSurfaceColor;
            font-family: monospace;
            font-size: 12px;
            white-space: pre-wrap;
        }
    </style>
</head>
<body>
    <div id="diagram"><pre class="mermaid">$escapedCode</pre></div>
    <script src="https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.min.js"></script>
    <script>
        var renderDone = false;

        function calculateAndSendHeight() {
            try {
                var el = document.getElementById('diagram');
                var svg = el ? el.querySelector('svg') : null;
                var h;
                if (svg) {
                    var box = svg.getBoundingClientRect();
                    h = Math.ceil(box.height) + 24;
                } else {
                    h = Math.max(
                        document.body.scrollHeight,
                        document.body.offsetHeight,
                        document.documentElement.scrollHeight
                    );
                }
                if (h > 0) AndroidInterface.updateHeight(h);
            } catch(e) {}
        }

        try {
            mermaid.initialize({
                startOnLoad: false,
                theme: '${theme.value}',
                securityLevel: 'loose',
                themeVariables: {
                    primaryColor: '$primaryColor',
                    primaryTextColor: '$onPrimaryColor',
                    primaryBorderColor: '$outlineColor',
                    secondaryColor: '$secondaryColor',
                    secondaryTextColor: '$onSecondaryColor',
                    secondaryBorderColor: '$outlineColor',
                    tertiaryColor: '$surfaceColor',
                    lineColor: '$outlineColor',
                    textColor: '$onSurfaceColor',
                    mainBkg: '$primaryColor',
                    secondBkg: '$secondaryColor',
                    background: '$backgroundColor',
                    nodeBkg: '$surfaceColor',
                    nodeBorder: '$outlineColor',
                    clusterBkg: '$surfaceColor',
                    clusterBorder: '$outlineColor'
                }
            });

            mermaid.run({ querySelector: '.mermaid' }).then(function() {
                renderDone = true;
                setTimeout(calculateAndSendHeight, 300);
            }).catch(function(err) {
                console.error('Mermaid error:', err);
                AndroidInterface.onRenderError(err.toString());
            });
        } catch(e) {
            AndroidInterface.onRenderError(e.toString());
        }

        window.addEventListener('resize', function() {
            if (renderDone) calculateAndSendHeight();
        });

        // Fallback: 5s 后若仍未渲染完成则报错
        setTimeout(function() {
            if (!renderDone) {
                AndroidInterface.onRenderError('timeout');
            }
        }, 8000);
    </script>
</body>
</html>
    """.trimIndent()
}

private fun androidx.compose.ui.graphics.Color.toCssHex(): String {
    val r = (red * 255).toInt()
    val g = (green * 255).toInt()
    val b = (blue * 255).toInt()
    return String.format("#%02X%02X%02X", r, g, b)
}

private fun String.escapeHtml(): String {
    return this
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}

private fun androidx.compose.ui.graphics.Color.luminance(): Float {
    return 0.2126f * red + 0.7152f * green + 0.0722f * blue
}
