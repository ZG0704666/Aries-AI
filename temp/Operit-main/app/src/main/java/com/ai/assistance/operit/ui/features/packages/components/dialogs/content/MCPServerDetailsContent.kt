package com.ai.assistance.operit.ui.features.packages.components.dialogs.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.data.mcp.MCPLocalServer

/**
 * Content component for the MCP server details diaAppLogger.
 * 
 * @param server The MCP server to display details for
 * @param isInstalled Whether the server is installed
 * @param readmeContent README content if available
 * @param modifier Modifier for the component
 * @param mdFontSize Font size for markdown content
 */
@Composable
fun MCPServerDetailsContent(
    server: MCPLocalServer.PluginMetadata,
    isInstalled: Boolean,
    readmeContent: String? = null,
    modifier: Modifier = Modifier,
    mdFontSize: TextUnit = 14.sp
) {
    LazyColumn(
            modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
    ) {

        // 描述内容
        item {
            // Display full README content for installed plugins
            if (isInstalled && readmeContent != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ) {
                    // 在markdown库不可用时，简单显示原始文本
                    Text(
                        text = readmeContent,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = mdFontSize),
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                // Fallback to basic description
                Text(
                    text = server.description.takeIf { it.isNotBlank() } ?: "暂无描述",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = mdFontSize),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}
