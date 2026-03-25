package com.ai.phoneagent.ui.updates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.ExternalLink
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ai.phoneagent.core.designsystem.R as DesignSystemR
import com.ai.phoneagent.feature.updates.R
import com.ai.phoneagent.updates.ReleaseEntry
import com.ai.phoneagent.viewmodel.UpdateHistoryViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateHistoryScreen(
    navController: NavController,
    viewModel: UpdateHistoryViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val spacingSm = dimensionResource(DesignSystemR.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(DesignSystemR.dimen.m3t_spacing_md)
    val spacingLg = dimensionResource(DesignSystemR.dimen.m3t_spacing_lg)
    val spacingXl = dimensionResource(DesignSystemR.dimen.m3t_spacing_xl)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.m3t_updates_title)) },
                navigationIcon = {
                     IconButton(onClick = { navController.popBackStack() }) {
                         Icon(
                             imageVector = Lucide.ArrowLeft,
                             contentDescription = stringResource(com.ai.phoneagent.feature.settings.R.string.about_back),
                         )
                     }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                modifier = Modifier.statusBarsPadding(),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
        ) {
            // Pre-release switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacingLg, vertical = spacingSm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.m3t_updates_include_prerelease),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Switch(
                    checked = uiState.includePrerelease,
                    onCheckedChange = { viewModel.setIncludePrerelease(it) }
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                if (uiState.releases.isEmpty() && !uiState.loading && uiState.error == null) {
                    Text(
                        text = stringResource(R.string.m3t_updates_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = spacingLg, top = spacingSm, end = spacingLg, bottom = spacingXl),
                        verticalArrangement = Arrangement.spacedBy(spacingMd),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(uiState.releases) { release ->
                            ReleaseItemCard(
                                release = release,
                                onClick = { viewModel.showDetails(release) },
                                onDownload = { viewModel.handleDownload(release) },
                                onOpenRelease = { viewModel.openReleaseUrlWithFeedback(release.releaseUrl) }
                            )
                        }

                        item {
                            if (uiState.loading) {
                                Box(modifier = Modifier.fillMaxWidth().padding(spacingMd), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            } else if (uiState.error != null) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(spacingMd),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = uiState.error ?: "",
                                        color = MaterialTheme.colorScheme.error,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(spacingSm))
                                    Button(onClick = { viewModel.retry() }) {
                                        Text(stringResource(R.string.m3t_updates_retry))
                                    }
                                }
                            } else if (uiState.hasMore && uiState.releases.isNotEmpty()) {
                                Button(
                                    onClick = { viewModel.loadMore() },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = spacingMd)
                                ) {
                                    Text(stringResource(R.string.m3t_updates_load_more))
                                }
                            } else if (!uiState.hasMore && uiState.releases.isNotEmpty()) {
                                Text(
                                    text = stringResource(R.string.m3t_updates_no_more),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = spacingMd)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    uiState.detailDialogState?.let { entry ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissDetails() },
            title = { Text(entry.versionTag) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = entry.body.ifBlank { stringResource(R.string.m3t_updates_no_changelog) },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissDetails()
                    viewModel.openReleaseUrlWithFeedback(entry.releaseUrl)
                }) {
                    Text(stringResource(R.string.m3t_updates_open_release))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDetails() }) {
                    Text(stringResource(R.string.m3t_updates_close))
                }
            }
        )
    }

    uiState.downloadOptionsDialogState?.let { state ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissDownloadOptionsDialog() },
            title = { Text(stringResource(R.string.m3t_updates_choose_source)) },
            text = {
                Column {
                    state.options.forEach { option ->
                        TextButton(
                            onClick = {
                                viewModel.dismissDownloadOptionsDialog()
                                viewModel.openReleaseUrlWithFeedback(option.second)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(option.first)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissDownloadOptionsDialog() }) {
                    Text(stringResource(com.ai.phoneagent.feature.settings.R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun ReleaseItemCard(
    release: ReleaseEntry,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onOpenRelease: () -> Unit
) {
    val spacingSm = dimensionResource(DesignSystemR.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(DesignSystemR.dimen.m3t_spacing_md)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(spacingMd)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = release.versionTag,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (release.isPrerelease) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "Pre-release",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = spacingSm, vertical = 2.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(spacingSm))
            
            Text(
                text = release.date,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(spacingSm))
            
            Text(
                text = release.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(spacingMd))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onOpenRelease) {
                    Icon(Lucide.ExternalLink, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(spacingSm))
                    Text(stringResource(R.string.m3t_updates_open_release))
                }
                Spacer(modifier = Modifier.width(spacingSm))
                Button(onClick = onDownload) {
                    Icon(Lucide.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(spacingSm))
                    Text(stringResource(R.string.m3t_updates_download))
                }
            }
        }
    }
}
