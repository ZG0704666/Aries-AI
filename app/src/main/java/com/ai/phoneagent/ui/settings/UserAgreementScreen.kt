package com.ai.phoneagent.ui.settings

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
    val spacingLg = dimensionResource(R.dimen.m3t_spacing_lg)
    val spacingXl = dimensionResource(R.dimen.m3t_spacing_xl)
    val agreementHtml = stringResource(R.string.user_agreement_content)
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val containerColor = MaterialTheme.colorScheme.surface.toArgb()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(SettingsR.string.user_agreement_title)) },
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
            )
        },
    ) { innerPadding ->
        AndroidView(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = spacingXl, vertical = spacingLg),
            factory = { context ->
                val horizontalPadding = context.resources.getDimensionPixelSize(R.dimen.m3t_spacing_xl)
                val verticalPadding = context.resources.getDimensionPixelSize(R.dimen.m3t_spacing_lg)
                ScrollView(context).apply {
                    isFillViewport = true
                    overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                    setBackgroundColor(containerColor)
                    addView(
                        TextView(context).apply {
                            setTextColor(textColor)
                            textSize = 15f
                            setLineSpacing(0f, 1.35f)
                            gravity = Gravity.START
                            setBackgroundColor(containerColor)
                            setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
                        },
                    )
                }
            },
            update = { scrollView ->
                val textView = scrollView.getChildAt(0) as? TextView ?: return@AndroidView
                textView.text =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        Html.fromHtml(agreementHtml, Html.FROM_HTML_MODE_COMPACT)
                    } else {
                        @Suppress("DEPRECATION")
                        Html.fromHtml(agreementHtml)
                    }
            },
        )
    }
}
