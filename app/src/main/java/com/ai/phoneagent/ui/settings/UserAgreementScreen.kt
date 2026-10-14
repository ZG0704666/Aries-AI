package com.ai.phoneagent.ui.settings

import com.ai.phoneagent.ui.components.AriesPageTopBar
import android.os.Build
import android.text.Html
import android.view.Gravity
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.ai.phoneagent.R
import com.ai.phoneagent.feature.settings.R as SettingsR
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserAgreementScreen(navController: NavController) {
    val spacingLg = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_lg)
    val spacingXl = dimensionResource(com.ai.phoneagent.core.designsystem.R.dimen.m3t_spacing_xl)
    val agreementHtml = stringResource(R.string.user_agreement_content)
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val containerColor = MaterialTheme.colorScheme.surface.toArgb()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            AriesPageTopBar(stringResource(SettingsR.string.user_agreement_title), { navController.popBackStack() })
        },
    ) { innerPadding ->
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().padding(innerPadding)) {
            com.ai.phoneagent.ui.onboarding.AgreementPanel(
                onAccept = { navController.popBackStack() },
                actionLabelRes = com.ai.phoneagent.R.string.action_close,
                requireAcceptance = false,
            )
        }
    }
}
