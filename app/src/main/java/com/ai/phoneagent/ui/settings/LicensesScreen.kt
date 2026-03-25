package com.ai.phoneagent.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import com.ai.phoneagent.core.designsystem.theme.AriesSettingsSectionHeader
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.ai.phoneagent.R
import com.ai.phoneagent.feature.settings.R as SettingsR
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide

private data class License(
    val name: String,
    val description: String,
    val license: String,
)

private val licenses =
    listOf(
        License("AndroidX Core KTX", "Kotlin extensions for Android core libraries", "Apache-2.0"),
        License("AndroidX AppCompat", "Backward-compatible Android UI components", "Apache-2.0"),
        License("Material Components", "Material Design components for Android", "Apache-2.0"),
        License("AndroidX RecyclerView", "Efficient list display widget", "Apache-2.0"),
        License("AndroidX ConstraintLayout", "Flexible layout manager", "Apache-2.0"),
        License("AndroidX Lifecycle", "Lifecycle-aware components", "Apache-2.0"),
        License("AndroidX Work", "Background task scheduling", "Apache-2.0"),
        License("Kotlin Coroutines", "Asynchronous programming support", "Apache-2.0"),
        License("OkHttp", "HTTP client for Android and Java", "Apache-2.0"),
        License("Retrofit", "Type-safe HTTP client", "Apache-2.0"),
        License("Gson", "JSON serialization/deserialization library", "Apache-2.0"),
        License("multiplatform-markdown-renderer", "Compose Markdown rendering", "Apache-2.0"),
        License("sherpa-ncnn", "Offline speech recognition engine", "Apache-2.0"),
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(navController: NavController) {
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(R.dimen.m3t_spacing_md)
    val spacingLg = dimensionResource(R.dimen.m3t_spacing_lg)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(SettingsR.string.about_open_source_licenses)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Lucide.ArrowLeft,
                            contentDescription = stringResource(SettingsR.string.about_back),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                modifier = Modifier.statusBarsPadding(),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(horizontal = spacingLg, vertical = spacingLg),
            verticalArrangement = Arrangement.spacedBy(spacingMd),
         ) {
             items(licenses, key = { it.name }) { item ->
                 ListItem(
                     colors =
                         ListItemDefaults.colors(
                             containerColor = MaterialTheme.colorScheme.background,
                         ),
                     headlineContent = {
                         Text(
                             text = item.name,
                             style = MaterialTheme.typography.titleSmall,
                             color = MaterialTheme.colorScheme.onSurface,
                         )
                     },
                     supportingContent = {
                         Text(
                             text = item.description,
                             style = MaterialTheme.typography.bodySmall,
                             color = MaterialTheme.colorScheme.onSurfaceVariant,
                         )
                     },
                     trailingContent = {
                         Surface(
                             color = MaterialTheme.colorScheme.surfaceVariant,
                             shape = MaterialTheme.shapes.small,
                         ) {
                             Text(
                                 text = stringResource(R.string.m3t_license_format, item.license),
                                 style = MaterialTheme.typography.labelSmall,
                                 color = MaterialTheme.colorScheme.onSurfaceVariant,
                                 modifier = Modifier.padding(horizontal = spacingSm, vertical = spacingSm),
                             )
                         }
                     },
                 )
             }
        }
    }
}
