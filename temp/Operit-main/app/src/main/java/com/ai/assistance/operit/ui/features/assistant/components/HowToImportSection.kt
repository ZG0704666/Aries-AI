package com.ai.assistance.operit.ui.features.assistant.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R

@Composable
fun HowToImportSection() {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val newEditorUrl = "https://www.loongbones.com/"
    val oldEditorUrl = "https://www.egret.uk/download/"

    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp, start = 4.dp, top = 12.dp)) {
        Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                    stringResource(R.string.how_to_import),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
            )
            Icon(
                    imageVector =
                    if (expanded) Icons.Default.ExpandLess
                    else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) stringResource(R.string.collapse) else stringResource(R.string.expand)
            )
        }
    }

    if (expanded) {
        Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                val primaryColor = MaterialTheme.colorScheme.primary
                val annotatedString = buildAnnotatedString {
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(stringResource(R.string.about_avatar_formats))
                        append("\n\n")
                    }
                    
                    withStyle(style = SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold)) {
                        append(stringResource(R.string.making_models))
                    }
                    append("\n")
                    append(stringResource(R.string.making_models_desc_general))
                    append("\n\n")
                    
                    withStyle(style = SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold)) {
                        append(stringResource(R.string.exporting_files))
                    }
                    append("\n")
                    append(stringResource(R.string.exporting_files_desc_general))
                    append("\n\n")
                    
                    withStyle(style = SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold)) {
                        append(stringResource(R.string.configuring_interaction))
                    }
                    append("\n")
                    append(stringResource(R.string.configuring_interaction_desc))
                    append("\n\n")
                    
                    withStyle(style = SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold)) {
                        append(stringResource(R.string.packaging_and_importing))
                    }
                    append("\n")
                    append(stringResource(R.string.packaging_and_importing_desc))
                }
                
                Text(
                    text = annotatedString,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
                )

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                            text = stringResource(R.string.dragonbones_important_tip),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(all = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(oldEditorUrl))
                        context.startActivity(intent)
                    }) {
                        Text(stringResource(R.string.download_old_editor))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(newEditorUrl))
                        context.startActivity(intent)
                    }) {
                        Text(stringResource(R.string.visit_online_editor))
                    }
                }
            }
        }
    }
} 