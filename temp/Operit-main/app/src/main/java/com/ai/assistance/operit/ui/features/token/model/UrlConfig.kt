package com.ai.assistance.operit.ui.features.token.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

@Serializable
data class TabConfig(
    val title: String,
    val url: String
)

@Serializable
data class UrlConfig(
    val name: String = "DeepSeek",
    val signInUrl: String = "https://platform.deepseek.com/sign_in",
    val tabs: List<TabConfig> = listOf(
        TabConfig("API 密钥", "https://platform.deepseek.com/api_keys"),
        TabConfig("用量", "https://platform.deepseek.com/usage"), 
        TabConfig("充值", "https://platform.deepseek.com/top_up"),
        TabConfig("个人资料", "https://platform.deepseek.com/profile")
    )
)

// 导航目标数据类
data class NavDestination(
    val title: String, 
    val url: String, 
    val icon: ImageVector
)

// 获取导航目标的图标
fun getIconForIndex(index: Int): ImageVector = when (index) {
    0 -> Icons.Default.Key
    1 -> Icons.Default.Dashboard
    2 -> Icons.Default.CreditCard
    3 -> Icons.Default.Person
    else -> Icons.Default.Key
} 