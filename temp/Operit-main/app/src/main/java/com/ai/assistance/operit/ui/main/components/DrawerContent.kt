package com.ai.assistance.operit.ui.main.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.common.NavItem
import com.ai.assistance.operit.ui.main.NavGroup
import com.ai.assistance.operit.ui.main.screens.OperitRouter
import com.ai.assistance.operit.ui.main.screens.Screen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Content for the expanded navigation drawer */
@Composable
fun DrawerContent(
        navGroups: List<NavGroup>,
        currentScreen: Screen,
        selectedItem: NavItem,
        isNetworkAvailable: Boolean,
        networkType: String,
        scope: CoroutineScope,
        drawerState: androidx.compose.material3.DrawerState,
        onScreenSelected: (Screen, NavItem) -> Unit
) {
        // 添加滚动功能的Column
        Column(
                modifier =
                        Modifier.fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                                .padding(
                                        end = 8.dp,
                                        // Ensure bottom items aren’t obscured by system nav bar
                                        bottom = WindowInsets.navigationBars
                                                .asPaddingValues()
                                                .calculateBottomPadding()
                                )
        ) {
                // 抽屉标题
                Spacer(modifier = Modifier.height(54.dp))
                Text(
                        text = stringResource(id = R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )

                // 网络状态显示
                Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                        Icon(
                                imageVector =
                                        if (isNetworkAvailable) Icons.Default.Wifi
                                        else Icons.Default.WifiOff,
                                contentDescription = "网络状态",
                                tint =
                                        if (isNetworkAvailable) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                                text = networkType,
                                style = MaterialTheme.typography.bodyMedium,
                                color =
                                        if (isNetworkAvailable) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.error
                        )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(modifier = Modifier.height(8.dp))

                // 分组导航菜单
                navGroups.forEach { group ->
                        NavigationDrawerItemHeader(group.title)
                        group.items.forEach { item ->
                                CompactNavigationDrawerItem(
                                        icon = item.icon,
                                        label = stringResource(id = item.titleResId),
                                        selected = selectedItem == item,
                                        onClick = {
                                                onScreenSelected(
                                                        OperitRouter.getScreenForNavItem(item),
                                                        item
                                                )
                                                scope.launch { drawerState.close() }
                                        }
                                )
                        }
                }

                // 为了在底部留出一些空间，避免最后一个选项贴底
                Spacer(modifier = Modifier.height(16.dp))
        }
}

/** Content for the collapsed navigation drawer (for tablet mode) */
@Composable
fun CollapsedDrawerContent(
        navItems: List<NavItem>,
        selectedItem: NavItem,
        isNetworkAvailable: Boolean,
        onScreenSelected: (Screen, NavItem) -> Unit
) {
        // 折叠状态下只显示图标
        Column(
                modifier =
                        Modifier.fillMaxHeight()
                                .verticalScroll(rememberScrollState()) // 添加滚动支持
                                .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
                // 抽屉标题 - 仅图标
                Spacer(modifier = Modifier.height(8.dp))

                // 网络状态图标 - 与其他图标保持一致
                IconButton(onClick = { /* 点击图标操作可选 */}) {
                        Icon(
                                imageVector =
                                        if (isNetworkAvailable) Icons.Default.Wifi
                                        else Icons.Default.WifiOff,
                                contentDescription = "网络状态",
                                tint =
                                        if (isNetworkAvailable) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp) // 与其他图标大小一致
                        )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(modifier = Modifier.fillMaxWidth(0.6f))
                Spacer(modifier = Modifier.height(16.dp))

                // 图标列表 - 只显示图标按钮
                for (item in navItems) {
                        IconButton(
                                onClick = {
                                        onScreenSelected(
                                                OperitRouter.getScreenForNavItem(item),
                                                item
                                        )
                                },
                                modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                                Icon(
                                        imageVector = item.icon,
                                        contentDescription = stringResource(id = item.titleResId),
                                        tint =
                                                if (selectedItem == item)
                                                        MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                )
                        }
                }

                // 底部留白，避免最后一项靠底
                Spacer(modifier = Modifier.height(16.dp))
        }
}
