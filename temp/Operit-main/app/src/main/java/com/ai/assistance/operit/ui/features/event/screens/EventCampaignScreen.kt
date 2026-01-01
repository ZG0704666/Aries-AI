package com.ai.assistance.operit.ui.features.event.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun EventCampaignScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Column {
            Text(
                text = "🎉 Operit AI创作激励活动 🎉",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )

            Text(
                text = "【活动奖励】",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "小红书赛道：\n🥇30元 🥈15元 🥉10元",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text =
                    "B站 / YouTube 赛道：\n" +
                        "🥇100元 🥈50元 🥉20元\n\n" +
                        "在 YouTube 发布的 Operit 相关视频，将与 B 站赛道统一排名、统一结算奖励。",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "【额外福利】",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "所有参与者瓜分 100 元手气红包！",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "【参与方式】",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text =
                    "1. 在小红书 / B站 / YouTube 发布 Operit AI 助手相关内容（教程、演示等）\n" +
                        "2. 截图数据并提交至用户群的收集表\n" +
                        "3. 等待排名公布，领取奖金",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text =
                    "注意事项：12 月开始的所有发布都算数。\n" +
                        "标题 / 简介 / 标签均不做强制要求，只需内容与 Operit 相关即可。\n" +
                        "在 B站 / YouTube 发布视频时，可根据平台流量优化标题。\n" +
                        "在小红书发布内容时，更推荐分享你的真实使用体验和感受，而不要写成生硬的推广文案。",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "【截止时间】",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "2025 年 12 月 24 日 23:59",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "【参与入口】",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text =
                    "官方 QQ 群：458862019\n" +
                        "也可通过 B 站搜索「OperitAI」在评论区找到用户群，" +
                        "或在文档中查找最新群信息。",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
