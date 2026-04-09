/*
 * Aries AI - Android UI Automation Framework
 * Copyright (C) 2025-2026 ZG0704666
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.ai.phoneagent.helper

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat
import com.ai.phoneagent.R

/**
 * Aries AI WebView-based Markdown Renderer
 *
 * Manages WebView instances that render full-featured Markdown including:
 * - KaTeX math formulas ($...$ and $$...$$)
 * - Mermaid diagrams
 * - Syntax-highlighted code blocks (highlight.js)
 * - Tables, task lists, strikethrough, blockquotes
 */
@SuppressLint("SetJavaScriptEnabled")
class MarkdownWebViewManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Pre-built HTML template with M3T colors injected */
    private val htmlTemplate: String by lazy { buildTemplate() }

    private fun buildTemplate(): String {
        val raw = appContext.assets.open("html/aries_markdown.html")
            .bufferedReader().use { it.readText() }

        val isDark = (appContext.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        fun colorHex(resId: Int): String {
            val color = ContextCompat.getColor(appContext, resId)
            return String.format("#%06X", 0xFFFFFF and color)
        }

        // Surface variant with 40% alpha for alternating table rows / blockquote bg
        val surfaceVariant = ContextCompat.getColor(appContext, R.color.m3t_surface_variant)
        val surfaceVariantAlpha = String.format(
            "rgba(%d,%d,%d,0.4)",
            Color.red(surfaceVariant), Color.green(surfaceVariant), Color.blue(surfaceVariant)
        )

        val mermaidTheme = if (isDark) "dark" else "default"

        return raw
            .replace("{{ON_SURFACE_COLOR}}", colorHex(R.color.m3t_on_surface))
            .replace("{{ON_SURFACE_VARIANT_COLOR}}", colorHex(R.color.m3t_on_surface_variant))
            .replace("{{SURFACE_VARIANT_COLOR}}", colorHex(R.color.m3t_surface_variant))
            .replace("{{SURFACE_VARIANT_COLOR_ALPHA}}", surfaceVariantAlpha)
            .replace("{{PRIMARY_COLOR}}", colorHex(R.color.m3t_primary))
            .replace("{{OUTLINE_VARIANT_COLOR}}", colorHex(R.color.m3t_outline_variant))
            .replace("{{MERMAID_THEME}}", mermaidTheme)
    }

    /**
     * Create and configure a WebView for Markdown rendering.
     * The caller is responsible for adding it to the view hierarchy.
     *
     * @param onHeightChanged called (on main thread) each time content height changes
     * @param onReady called when the page is fully loaded and scripts are initialized
     */
    fun createWebView(
        context: Context,
        onHeightChanged: (Int) -> Unit,
        onReady: () -> Unit = {}
    ): WebView {
        val webView = WebView(context)
        configureWebView(webView)

        val bridge = AriesBridge(webView, onHeightChanged, onReady, mainHandler)
        webView.addJavascriptInterface(bridge, "AriesBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Log.w(TAG, "Cannot open URL: $url", e)
                    }
                    return true
                }
                return false
            }

            override fun onPageFinished(view: WebView, url: String) {
                // Page loaded — bridge.onReady() will be called from JS
            }
        }

        // Load the pre-built template HTML
        webView.loadDataWithBaseURL(
            "file:///android_asset/html/",
            htmlTemplate,
            "text/html",
            "UTF-8",
            null
        )

        return webView
    }

    /**
     * Render Markdown content into the given WebView (full replace).
     * Must be called after the WebView is ready (onReady callback fired).
     */
    fun renderContent(webView: WebView, markdown: String) {
        val base64 = Base64.encodeToString(
            markdown.toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP
        )
        webView.evaluateJavascript("renderMarkdown('$base64')", null)
    }

    /**
     * Append a streaming delta chunk to the WebView.
     * The JS side accumulates and debounce-renders.
     */
    fun appendStreamDelta(webView: WebView, delta: String) {
        if (delta.isEmpty()) return
        val base64 = Base64.encodeToString(
            delta.toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP
        )
        webView.evaluateJavascript("appendStreamDelta('$base64')", null)
    }

    /**
     * Notify the WebView that streaming is complete.
     * Triggers a final render with mermaid support.
     */
    fun finishStream(webView: WebView) {
        webView.evaluateJavascript("finishStream()", null)
    }

    /**
     * Reset the WebView streaming state for a new message.
     */
    fun resetStream(webView: WebView) {
        webView.evaluateJavascript("resetStream()", null)
    }

    /**
     * Safely destroy a WebView: remove from parent, stop loading, destroy.
     */
    fun destroyWebView(webView: WebView) {
        webView.stopLoading()
        webView.clearHistory()
        webView.destroy()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(webView: WebView) {
        webView.setBackgroundColor(Color.TRANSPARENT)
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        webView.isNestedScrollingEnabled = false

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = true
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            loadsImagesAutomatically = true
            defaultTextEncodingName = "UTF-8"
        }
    }

    // ------------------------------------------------------------------
    // JS → Android Bridge
    // ------------------------------------------------------------------

    private inner class AriesBridge(
        private val webView: WebView,
        private val onHeightChanged: (Int) -> Unit,
        private val onReady: () -> Unit,
        private val handler: Handler
    ) {
        @JavascriptInterface
        fun onHeightChanged(heightPx: Int) {
            handler.post {
                if (heightPx > 0) {
                    val density = webView.resources.displayMetrics.density
                    val heightDp = (heightPx * density + 0.5f).toInt()
                    onHeightChanged(heightDp)
                }
            }
        }

        @JavascriptInterface
        fun onLinkClicked(url: String) {
            handler.post {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    appContext.startActivity(intent)
                } catch (e: Exception) {
                    Log.w(TAG, "Cannot open link: $url", e)
                }
            }
        }

        @JavascriptInterface
        fun onCodeCopy(code: String) {
            handler.post {
                val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("code", code)
                clipboard.setPrimaryClip(clip)
            }
        }

        @JavascriptInterface
        fun onReady() {
            handler.post { onReady() }
        }
    }

    // ------------------------------------------------------------------
    companion object {
        private const val TAG = "MarkdownWebViewMgr"

        @Volatile
        private var instance: MarkdownWebViewManager? = null

        fun getInstance(context: Context): MarkdownWebViewManager {
            return instance ?: synchronized(this) {
                instance ?: MarkdownWebViewManager(context.applicationContext).also { instance = it }
            }
        }

        /**
         * Detect whether given markdown content benefits from WebView rendering.
         * Simple plain-text replies stay as lightweight TextViews.
         */
        fun shouldUseWebView(content: String): Boolean {
            if (content.length < 30) return false
            // Math formulas
            if (content.contains("$$") || content.contains("\\(") || content.contains("\\[")) return true
            // Code fences
            if (content.contains("```")) return true
            // Tables (at least two rows with pipes)
            if (content.lines().count { it.contains('|') } >= 2) return true
            // Mermaid (already covered by ``` but explicit)
            if (content.contains("mermaid")) return true
            return false
        }
    }
}
