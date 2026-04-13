package com.ai.phoneagent.ui.components.markdown

import android.content.Context
import android.util.Log
import com.dokar.quickjs.QuickJs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

// ─────────────────────────────────────────────────────────────────────────────
//  Token model
// ─────────────────────────────────────────────────────────────────────────────

sealed class HighlightToken {
    /** Plain, unstyled text. */
    data class Plain(val text: String) : HighlightToken()

    /** Syntax token with a Prism.js-style type name. */
    data class Token(val type: String, val text: String) : HighlightToken()
}

// ─────────────────────────────────────────────────────────────────────────────
//  Highlighter  (quickjs-kt 1.x API — evaluate is now suspend)
//
//  Call Highlighter.init(context) once (e.g., in Application.onCreate).
//  After that, call Highlighter.highlight(code, lang) from any coroutine.
//
//  Asset required:  app/src/main/assets/highlight/prism.js
//  Download from:   https://prismjs.com/download.html  (select all languages)
// ─────────────────────────────────────────────────────────────────────────────

object Highlighter {

    private const val TAG = "Highlighter"

    // IO scope for initialization; Mutex serialises all evaluate() calls.
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mutex = Mutex()
    private val initDeferred = CompletableDeferred<Boolean>()

    private var quickJs: QuickJs? = null

    // ── Initialisation ────────────────────────────────────────────────────────

    /**
     * Load and evaluate Prism.js inside the QuickJs runtime.
     * Must be called once before [highlight]; safe to call on any thread.
     */
    fun init(context: Context) {
        scope.launch {
            try {
                val js = context.assets.open("highlight/prism.js")
                    .bufferedReader()
                    .use { it.readText() }
                val qjs = QuickJs.create(Dispatchers.Default)
                qjs.evaluate<Any?>(js)
                quickJs = qjs
                initDeferred.complete(true)
                Log.d(TAG, "Prism.js loaded successfully")
            } catch (e: Exception) {
                Log.w(TAG, "Prism.js unavailable – falling back to plain text: ${e.message}")
                initDeferred.complete(false)
            }
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Tokenise [code] using Prism.js for the given [language].
     *
     * Returns a list of [HighlightToken]s. Falls back to a single [HighlightToken.Plain]
     * if Prism is not ready or the language is unknown.
     */
    suspend fun highlight(code: String, language: String): List<HighlightToken> {
        if (code.isBlank()) return listOf(HighlightToken.Plain(code))
        if (!initDeferred.await()) return listOf(HighlightToken.Plain(code))

        return mutex.withLock {
            val qjs = quickJs ?: return@withLock listOf(HighlightToken.Plain(code))
            try {
                val codeEncoded = buildSafeJsString(code)

                val script = """
                    (function() {
                        var lang = Prism.languages['${language.lowercase()}'];
                        if (!lang) return null;
                        return JSON.stringify(Prism.tokenize($codeEncoded, lang));
                    })()
                """.trimIndent()

                val json = qjs.evaluate<String?>(script)
                    ?: return@withLock listOf(HighlightToken.Plain(code))

                parseTokenArray(JSONArray(json))
            } catch (e: Exception) {
                Log.w(TAG, "highlight() failed for lang='$language': ${e.message}")
                listOf(HighlightToken.Plain(code))
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildSafeJsString(code: String): String = buildString {
        append('"')
        code.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"'  -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
        append('"')
    }

    // ── JSON parsing ──────────────────────────────────────────────────────────

    private fun parseTokenArray(array: JSONArray): List<HighlightToken> {
        val result = mutableListOf<HighlightToken>()
        for (i in 0 until array.length()) {
            when (val item = array.get(i)) {
                is String -> if (item.isNotEmpty()) result += HighlightToken.Plain(item)
                is JSONObject -> {
                    val type = item.optString("type", "unknown")
                    val text = flattenContent(item.get("content"))
                    if (text.isNotEmpty()) result += HighlightToken.Token(type, text)
                }
                else -> {}
            }
        }
        return result
    }

    /** Recursively flatten a token content value into a plain string. */
    private fun flattenContent(content: Any): String = when (content) {
        is String     -> content
        is JSONArray  -> buildString { for (i in 0 until content.length()) append(flattenContent(content.get(i))) }
        is JSONObject -> flattenContent(content.opt("content") ?: "")
        else          -> content.toString()
    }
}
