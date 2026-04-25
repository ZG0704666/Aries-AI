package com.ai.phoneagent.ui.settings

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.phoneagent.R
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Crown
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Zap

private enum class MembershipTier {
    Basic,
    Pro,
    Ultimate,
}

private data class FeatureRow(
    val label: String,
    val basic: String,
    val pro: String,
    val ultimate: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MembershipScreen(
    onBack: () -> Unit,
) {
    val spacingXs = dimensionResource(R.dimen.m3t_spacing_xs)
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(R.dimen.m3t_spacing_md)
    val spacingLg = dimensionResource(R.dimen.m3t_spacing_lg)
    val spacingXl = dimensionResource(R.dimen.m3t_spacing_xl)

    val features = listOf(
        FeatureRow(
            label = stringResource(R.string.membership_feature_daily_tasks),
            basic = stringResource(R.string.membership_feature_basic_value),
            pro = stringResource(R.string.membership_feature_pro_value),
            ultimate = stringResource(R.string.membership_feature_ultimate_value),
        ),
        FeatureRow(
            label = stringResource(R.string.membership_feature_models),
            basic = stringResource(R.string.membership_feature_basic_models),
            pro = stringResource(R.string.membership_feature_pro_models),
            ultimate = stringResource(R.string.membership_feature_ultimate_models),
        ),
        FeatureRow(
            label = stringResource(R.string.membership_feature_automation),
            basic = stringResource(R.string.membership_feature_basic_automation),
            pro = stringResource(R.string.membership_feature_pro_automation),
            ultimate = stringResource(R.string.membership_feature_ultimate_automation),
        ),
        FeatureRow(
            label = stringResource(R.string.membership_feature_support),
            basic = stringResource(R.string.membership_feature_basic_support),
            pro = stringResource(R.string.membership_feature_pro_support),
            ultimate = stringResource(R.string.membership_feature_ultimate_support),
        ),
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.membership_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Lucide.ArrowLeft,
                            contentDescription = stringResource(R.string.about_back),
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(
                start = spacingLg,
                top = spacingSm,
                end = spacingLg,
                bottom = spacingXl,
            ),
            verticalArrangement = Arrangement.spacedBy(spacingMd),
        ) {
            item {
                TierCard(
                    tier = MembershipTier.Basic,
                    icon = { Icon(Lucide.Zap, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    title = stringResource(R.string.membership_tier_basic),
                    price = stringResource(R.string.membership_tier_basic_price),
                    description = stringResource(R.string.membership_tier_basic_desc),
                    isCurrentPlan = true,
                )
            }

            item {
                TierCard(
                    tier = MembershipTier.Pro,
                    icon = { Icon(Lucide.Sparkles, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    title = stringResource(R.string.membership_tier_pro),
                    price = stringResource(R.string.membership_tier_pro_price),
                    description = stringResource(R.string.membership_tier_pro_desc),
                    isRecommended = true,
                    isCurrentPlan = false,
                )
            }

            item {
                TierCard(
                    tier = MembershipTier.Ultimate,
                    icon = { Icon(Lucide.Crown, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    title = stringResource(R.string.membership_tier_ultimate),
                    price = stringResource(R.string.membership_tier_ultimate_price),
                    description = stringResource(R.string.membership_tier_ultimate_desc),
                    isCurrentPlan = false,
                )
            }

            item {
                FeatureComparisonTable(features = features)
            }
        }
    }
}

@Composable
private fun TierCard(
    tier: MembershipTier,
    icon: @Composable () -> Unit,
    title: String,
    price: String,
    description: String,
    isRecommended: Boolean = false,
    isCurrentPlan: Boolean = false,
) {
    val spacingXs = dimensionResource(R.dimen.m3t_spacing_xs)
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(R.dimen.m3t_spacing_md)
    val spacingLg = dimensionResource(R.dimen.m3t_spacing_lg)

    val containerColor = when (tier) {
        MembershipTier.Pro -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        MembershipTier.Ultimate -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        else -> MaterialTheme.colorScheme.surface
    }

    val borderStroke = if (isRecommended) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = MaterialTheme.shapes.extraLarge,
        border = borderStroke,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacingLg),
            verticalArrangement = Arrangement.spacedBy(spacingSm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = when (tier) {
                        MembershipTier.Pro -> MaterialTheme.colorScheme.primaryContainer
                        MembershipTier.Ultimate -> MaterialTheme.colorScheme.tertiaryContainer
                        else -> MaterialTheme.colorScheme.secondaryContainer
                    },
                ) {
                    Box(modifier = Modifier.padding(spacingSm)) {
                        icon()
                    }
                }
                Spacer(modifier = Modifier.width(spacingMd))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (isRecommended) {
                            Spacer(modifier = Modifier.width(spacingSm))
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.primary,
                            ) {
                                Text(
                                    text = "推荐",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = spacingSm, vertical = spacingXs),
                                )
                            }
                        }
                    }
                }
            }

            Text(
                text = price,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (isCurrentPlan) {
                OutlinedButton(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth().height(dimensionResource(R.dimen.m3t_compact_button_height)),
                    enabled = false,
                ) {
                    Text(stringResource(R.string.membership_current_plan))
                }
            } else {
                Button(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth().height(dimensionResource(R.dimen.m3t_compact_button_height)),
                    colors = when (tier) {
                        MembershipTier.Pro -> ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        )
                        MembershipTier.Ultimate -> ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary,
                        )
                        else -> ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    },
                ) {
                    Text(stringResource(R.string.membership_subscribe))
                }
            }
        }
    }
}

@Composable
private fun FeatureComparisonTable(features: List<FeatureRow>) {
    val spacingXs = dimensionResource(R.dimen.m3t_spacing_xs)
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(R.dimen.m3t_spacing_md)
    val spacingLg = dimensionResource(R.dimen.m3t_spacing_lg)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacingLg),
            verticalArrangement = Arrangement.spacedBy(spacingMd),
        ) {
            Text(
                text = "功能对比",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            features.forEach { feature ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(spacingXs),
                ) {
                    Text(
                        text = feature.label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacingSm),
                    ) {
                        FeatureValueCell(
                            value = feature.basic,
                            modifier = Modifier.weight(1f),
                        )
                        FeatureValueCell(
                            value = feature.pro,
                            modifier = Modifier.weight(1f),
                            highlight = true,
                        )
                        FeatureValueCell(
                            value = feature.ultimate,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureValueCell(
    value: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
) {
    val spacingXs = dimensionResource(R.dimen.m3t_spacing_xs)
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)

    val containerColor = if (highlight) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacingSm, vertical = spacingXs),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = if (highlight) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}