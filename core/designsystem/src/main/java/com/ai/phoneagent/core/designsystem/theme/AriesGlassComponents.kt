package com.ai.phoneagent.core.designsystem.theme

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import com.ai.phoneagent.core.designsystem.R

@Composable
fun AriesGlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(dimensionResource(R.dimen.aries_card_radius))
    Surface(modifier = modifier.ariesGlass(shape), shape = shape,
        color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.padding(dimensionResource(R.dimen.aries_setting_padding)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.m3t_spacing_sm)), content = content)
    }
}

@Composable
fun AriesGlassButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    contentColor: Color = MaterialTheme.colorScheme.onSurface, content: @Composable RowScope.() -> Unit) {
    val shape = RoundedCornerShape(dimensionResource(R.dimen.aries_action_radius))
    Button(onClick = onClick, enabled = enabled,
        modifier = modifier.ariesGlass(shape), shape = shape,
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = contentColor,
            disabledContainerColor = Color.Transparent), content = content)
}
