package com.ai.phoneagent.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import com.ai.phoneagent.R
import com.ai.phoneagent.core.designsystem.R as DesignR
import com.ai.phoneagent.core.designsystem.theme.ariesGlass
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide

/** Aries 自有顶部栏；系统状态栏空间由窗口 insets 提供。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AriesPageTopBar(title: String, onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit = {}) {
    val density = LocalDensity.current
    TopAppBar(
        title = { Text(title, modifier = Modifier.padding(start = dimensionResource(DesignR.dimen.aries_toolbar_title_gap)), style = MaterialTheme.typography.titleLarge.copy(fontSize = with(density) { dimensionResource(DesignR.dimen.aries_type_title).toSp() }), fontWeight = FontWeight.Bold) },
        expandedHeight = dimensionResource(DesignR.dimen.aries_toolbar_height),
        navigationIcon = {
            // 仅当提供返回回调时显示返回按钮
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.padding(start = dimensionResource(DesignR.dimen.aries_composer_inset_x))
                    .size(dimensionResource(DesignR.dimen.aries_touch_target))) {
                    Box(Modifier.size(dimensionResource(DesignR.dimen.aries_back_button)).ariesGlass(CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Lucide.ArrowLeft, stringResource(R.string.ui_v2_back), Modifier.size(dimensionResource(DesignR.dimen.aries_back_icon)))
                    }
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
    )
}
