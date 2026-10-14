package com.ai.phoneagent.core.designsystem.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import com.ai.phoneagent.core.designsystem.R

data class AriesBackdropStyle(val dark: Boolean, val amoled: Boolean, val defaultAccent: Boolean)
val LocalAriesBackdrop = staticCompositionLocalOf { AriesBackdropStyle(false, false, true) }

/** Soft source gradients, without blurring foreground text or capturing the window. */
@Composable
fun AriesBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val style = LocalAriesBackdrop.current
    val background = MaterialTheme.colorScheme.background
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val green = colorResource(R.color.aries_glow_green)
    val purple = colorResource(R.color.aries_glow_purple)
    Box(modifier.fillMaxSize().drawWithCache {
        val top = Brush.radialGradient(
            listOf((if (style.defaultAccent) green else primary).copy(alpha = .34f), Color.Transparent),
            center = Offset(size.width * .18f, size.height * .08f), radius = size.width * 1.25f,
        )
        val bottom = Brush.radialGradient(
            listOf((if (style.defaultAccent) purple else tertiary).copy(alpha = .28f), Color.Transparent),
            center = Offset(size.width * .82f, size.height * .96f), radius = size.width * 1.2f,
        )
        onDrawBehind {
            drawRect(background)
            if (style.dark && !style.amoled) { drawRect(top); drawRect(bottom) }
        }
    }, content = content)
}
