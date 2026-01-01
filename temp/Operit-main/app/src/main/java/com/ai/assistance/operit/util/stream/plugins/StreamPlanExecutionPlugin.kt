package com.ai.assistance.operit.util.stream.plugins

import com.ai.assistance.operit.util.stream.*

/**
 * A stream processing plugin to identify and process <plan>...</plan> blocks.
 * This implementation uses the group capturing mechanism of the StreamKmpGraph.
 *
 * @param includeTagsInOutput If true, the XML tags themselves (`<plan>`, `</plan>`) will be included
 * in the output stream. If false, they will be filtered out, leaving only the content between the
 * tags.
 */
class StreamPlanExecutionPlugin(private val includeTagsInOutput: Boolean = true) : StreamPlugin {

    override var state: PluginState = PluginState.IDLE
        private set

    private val startTagMatcher: StreamKmpGraph = StreamKmpGraphBuilder().build(
        kmpPattern {
            literal("<plan")
            greedyStar { notChar('>') }
            char('>')
        }
    )

    private val endTagMatcher: StreamKmpGraph = StreamKmpGraphBuilder().build(
        kmpPattern {
            literal("</plan>")
        }
    )

    // Allow a new <plan> start immediately after a closing </plan>, even mid-line
    private var allowStartAfterEndTag: Boolean = false

    init {
        reset()
    }

    /**
     * Processes a single character for XML stream parsing and decides if it should be emitted. The
     * return value is used by `splitBy` as a filter.
     */
    override fun processChar(c: Char, atStartOfLine: Boolean): Boolean {
        if (state == PluginState.PROCESSING) {
            // We are inside a tag, looking for the end tag.
            val result = endTagMatcher.processChar(c)

            return when (result) {
                is StreamKmpMatchResult.Match -> {
                    // End tag fully matched. Reset state and filter this last character if needed.
                    StreamLogger.i("StreamPlanExecutionPlugin", "Found end tag. Switching to IDLE.")
                    // Enable one-time allowance for starting right after the end tag
                    allowStartAfterEndTag = true
                    reset()
                    includeTagsInOutput
                }
                is StreamKmpMatchResult.InProgress -> {
                    // We are in the middle of matching the end tag (e.g., '</', '</p', etc.).
                    // The emission of these characters depends on the flag.
                    includeTagsInOutput
                }
                is StreamKmpMatchResult.NoMatch -> {
                    // The character `c` did not match the next char of the end tag.
                    // This means it's regular content between tags.
                    true
                }
            }
        } else {
            if (state == PluginState.IDLE && !atStartOfLine) {
                if (!allowStartAfterEndTag) {
                    return true
                }
                // Allow adjacent plan after an end tag even if separated by spaces/tabs
                if (c == ' ' || c == '\t') {
                    return true
                }
            }
            // We are in IDLE or TRYING state, looking for a start tag.
            val previousState = state
            when (startTagMatcher.processChar(c)) {
                is StreamKmpMatchResult.Match -> {
                    StreamLogger.i(
                        "StreamPlanExecutionPlugin",
                        "Found start tag '<plan>'. Switching to PROCESSING."
                    )
                    state = PluginState.PROCESSING
                    startTagMatcher.reset()
                    // Consuming this as a new start clears the post-end allowance
                    allowStartAfterEndTag = false
                    return includeTagsInOutput
                }
                is StreamKmpMatchResult.InProgress -> {
                    state = PluginState.TRYING
                    // Keep the allowance during in-progress so subsequent characters can proceed
                    return includeTagsInOutput
                }
                is StreamKmpMatchResult.NoMatch -> {
                    // If we were trying and the match failed, we must reset to idle.
                    if (previousState == PluginState.TRYING) {
                        reset()
                    }
                    // Clear the allowance if we failed to start a new tag
                    allowStartAfterEndTag = false
                    // This is a default character, not part of a tag managed by this plugin.
                    return true
                }
            }
        }
    }

    /** Initializes the plugin to its default state. */
    override fun initPlugin(): Boolean {
        reset()
        return true
    }

    /** Destroys the plugin. No-op as listener is removed. */
    override fun destroy() {}

    /** Resets the plugin state. */
    override fun reset() {
        startTagMatcher.reset()
        endTagMatcher.reset()
        state = PluginState.IDLE
    }
}

