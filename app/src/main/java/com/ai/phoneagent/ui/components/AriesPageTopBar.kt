package com.ai.phoneagent.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import com.ai.phoneagent.R
import com.ai.phoneagent.core.designsystem.R as DesignR
import com.ai.phoneagent.core.designsystem.theme.AriesGlassIconButton
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
            // 返回按钮：圆形液态玻璃（Material A 交互玻璃 + 按压高光），亮暗色 token 自适应。
            if (onBack != null) {
                AriesGlassIconButton(
                    onClick = onBack,
                    modifier = Modifier.padding(start = dimensionResource(DesignR.dimen.aries_composer_inset_x)),
                ) {
                    Icon(
                        Lucide.ArrowLeft,
                        stringResource(R.string.ui_v2_back),
                        modifier = Modifier.size(dimensionResource(DesignR.dimen.aries_back_icon)),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
    )
}
