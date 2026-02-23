/*
 * Aries AI - Android UI Automation Framework
 * Copyright (C) 2025-2026 ZG0704666
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.ai.phoneagent.core.utils

import java.io.StringReader
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * 将 Shizuku uiautomator 原始 XML 精简为稳定的结构化摘要，降低噪声并控制 token 成本。
 */
object ShizukuUiTreeSummarizer {

    private val boundsRegex = Regex("""\[(\d+),(\d+)]\[(\d+),(\d+)]""")

    private data class UiNode(
            val order: Int,
            val depth: Int,
            val className: String,
            val text: String,
            val contentDesc: String,
            val resourceId: String,
            val packageName: String,
            val bounds: String,
            val clickable: Boolean,
            val editable: Boolean,
            val focused: Boolean,
            val scrollable: Boolean,
    )

    fun summarize(
            rawXml: String,
            maxNodes: Int,
            maxChars: Int,
            detail: String = "summary",
    ): String {
        val normalizedXml = rawXml.trim()
        if (normalizedXml.isBlank()) return "[Shizuku UI层级为空]"

        val parsed = parseNodes(normalizedXml)
        if (parsed.isEmpty()) return ActionUtils.truncateUiTree(normalizedXml, maxChars)

        val selected = selectNodes(parsed, maxNodes.coerceAtLeast(1))
        val summary = buildSummaryXml(selected, parsed.size, detail)
        return if (summary.length <= maxChars) {
            summary
        } else {
            ActionUtils.truncateUiTree(summary, maxChars)
        }
    }

    fun extractPackageName(summaryOrRawUiDump: String): String? {
        val rootMatch = Regex("""<ui_hierarchy[^>]*\spackage="([^"]+)"""")
                .find(summaryOrRawUiDump)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
                .orEmpty()
        if (rootMatch.isNotBlank()) return rootMatch

        return Regex("""<node[^>]*\spackage="([^"]+)"""")
                .find(summaryOrRawUiDump)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
    }

    private fun parseNodes(rawXml: String): List<UiNode> {
        return runCatching {
                    val parser =
                            XmlPullParserFactory.newInstance().newPullParser().apply {
                                setInput(StringReader(rawXml))
                            }
                    val nodes = mutableListOf<UiNode>()
                    var order = 0
                    var eventType = parser.eventType
                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        if (eventType == XmlPullParser.START_TAG && parser.name == "node") {
                            val className = parser.getAttributeValue(null, "class").orEmpty().trim()
                            val text = parser.getAttributeValue(null, "text").orEmpty().trim()
                            val contentDesc =
                                    parser.getAttributeValue(null, "content-desc").orEmpty().trim()
                            val resourceId =
                                    parser.getAttributeValue(null, "resource-id").orEmpty().trim()
                            val packageName =
                                    parser.getAttributeValue(null, "package").orEmpty().trim()
                            val bounds = parser.getAttributeValue(null, "bounds").orEmpty().trim()
                            val clickable = parseBool(parser.getAttributeValue(null, "clickable"))
                            val editable = parseBool(parser.getAttributeValue(null, "editable"))
                            val focused = parseBool(parser.getAttributeValue(null, "focused"))
                            val scrollable = parseBool(parser.getAttributeValue(null, "scrollable"))

                            nodes +=
                                    UiNode(
                                            order = order++,
                                            depth = parser.depth,
                                            className = className,
                                            text = text,
                                            contentDesc = contentDesc,
                                            resourceId = resourceId,
                                            packageName = packageName,
                                            bounds = bounds,
                                            clickable = clickable,
                                            editable = editable,
                                            focused = focused,
                                            scrollable = scrollable,
                                    )
                        }
                        eventType = parser.next()
                    }
                    nodes
                }
                .getOrDefault(emptyList())
    }

    private fun parseBool(value: String?): Boolean {
        return value.equals("true", ignoreCase = true)
    }

    private fun selectNodes(nodes: List<UiNode>, maxNodes: Int): List<UiNode> {
        if (nodes.size <= maxNodes) return nodes

        val picked = linkedSetOf<Int>()
        picked += nodes.first().order

        val prioritized =
                nodes.filter { shouldKeep(it) }
                        .sortedByDescending { score(it) }
                        .map { it.order }
        for (order in prioritized) {
            if (picked.size >= maxNodes) break
            picked += order
        }

        if (picked.size < maxNodes) {
            for (node in nodes) {
                if (picked.size >= maxNodes) break
                picked += node.order
            }
        }

        val pickedSet = picked.toSet()
        return nodes.filter { it.order in pickedSet }.take(maxNodes)
    }

    private fun shouldKeep(node: UiNode): Boolean {
        if (node.editable || node.focused || node.clickable || node.scrollable) return true
        if (node.text.isNotBlank() || node.contentDesc.isNotBlank() || node.resourceId.isNotBlank()) {
            return true
        }
        return isKeyClass(node.className) || node.depth <= 2
    }

    private fun score(node: UiNode): Int {
        var score = 0
        if (node.editable) score += 12
        if (node.focused) score += 8
        if (node.clickable) score += 6
        if (node.scrollable) score += 4
        if (node.text.isNotBlank()) score += 5
        if (node.contentDesc.isNotBlank()) score += 4
        if (node.resourceId.isNotBlank()) score += 3
        if (isKeyClass(node.className)) score += 2
        return score
    }

    private fun isKeyClass(className: String): Boolean {
        if (className.isBlank()) return false
        val keyClasses =
                listOf(
                        "EditText",
                        "Button",
                        "TextView",
                        "ImageView",
                        "RecyclerView",
                        "ListView",
                        "ScrollView",
                        "WebView",
                        "ViewPager",
                        "TabLayout",
                )
        return keyClasses.any { className.contains(it, ignoreCase = true) }
    }

    private fun buildSummaryXml(nodes: List<UiNode>, totalNodes: Int, detail: String): String {
        val packageName = nodes.firstOrNull { it.packageName.isNotBlank() }?.packageName.orEmpty()
        val sb = StringBuilder()
        sb.append("<ui_hierarchy source=\"shizuku-summary\"")
        if (packageName.isNotBlank()) {
            sb.append(" package=\"").append(escape(packageName)).append('"')
        }
        sb.append(" total_nodes=\"").append(totalNodes).append('"')
        sb.append(" selected_nodes=\"").append(nodes.size).append('"')
        sb.append(" detail=\"").append(escape(detail)).append('"')
        sb.append(">")

        nodes.forEachIndexed { index, node ->
            sb.append("\n  <node")
            sb.append(" idx=\"").append(index + 1).append('"')
            sb.append(" depth=\"").append(node.depth).append('"')
            appendAttr(sb, "class", node.className)
            appendAttr(sb, "text", node.text)
            appendAttr(sb, "content_desc", node.contentDesc)
            appendAttr(sb, "resource_id", node.resourceId)
            appendAttr(sb, "bounds", node.bounds)
            appendAttr(sb, "center", centerFromBounds(node.bounds))
            appendAttr(sb, "clickable", node.clickable.toString())
            appendAttr(sb, "editable", node.editable.toString())
            appendAttr(sb, "focused", node.focused.toString())
            if (detail.equals("full", ignoreCase = true)) {
                appendAttr(sb, "scrollable", node.scrollable.toString())
            }
            sb.append(" />")
        }

        if (nodes.size < totalNodes) {
            sb.append("\n  <!-- truncated, kept=").append(nodes.size).append("/").append(totalNodes).append(" -->")
        }
        sb.append("\n</ui_hierarchy>")
        return sb.toString()
    }

    private fun appendAttr(sb: StringBuilder, key: String, value: String) {
        if (value.isBlank()) return
        sb.append(" ").append(key).append("=\"").append(escape(value)).append('"')
    }

    private fun centerFromBounds(bounds: String): String {
        val match = boundsRegex.find(bounds) ?: return ""
        val l = match.groupValues[1].toIntOrNull() ?: return ""
        val t = match.groupValues[2].toIntOrNull() ?: return ""
        val r = match.groupValues[3].toIntOrNull() ?: return ""
        val b = match.groupValues[4].toIntOrNull() ?: return ""
        val cx = (l + r) / 2
        val cy = (t + b) / 2
        return "[$cx,$cy]"
    }

    private fun escape(raw: String): String {
        if (raw.isEmpty()) return raw
        return buildString(raw.length) {
            raw.forEach { ch ->
                when (ch) {
                    '&' -> append("&amp;")
                    '"' -> append("&quot;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    else -> append(ch)
                }
            }
        }
    }
}
