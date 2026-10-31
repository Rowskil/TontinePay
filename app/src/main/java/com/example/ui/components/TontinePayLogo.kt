package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.AccentTheme

@Composable
fun TontinePayLogo(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp? = null,
    cornerRadius: Dp = (size * 0.24f),
    accentTheme: AccentTheme? = null,
    useAccentTheme: Boolean = true,
    showContainer: Boolean = true,
    containerColor: Color? = null,
    borderColor: Color? = null
) {
    val activeAccent = accentTheme ?: resolveAccentTheme(MaterialTheme.colorScheme.primary)

    val drawableRes = if (useAccentTheme && activeAccent != null) {
        when (activeAccent) {
            AccentTheme.EMERALD -> R.drawable.logo_tontinepay_emerald
            AccentTheme.AMBER -> R.drawable.logo_tontinepay_amber
            AccentTheme.WAVE -> R.drawable.logo_tontinepay_wave
            AccentTheme.INDIGO -> R.drawable.logo_tontinepay_indigo
            AccentTheme.CORAL -> R.drawable.logo_tontinepay_coral
            AccentTheme.NEUTRAL -> R.drawable.logo_tontinepay_neutral
        }
    } else {
        R.drawable.logo_tontinepay
    }

    val effectiveIconSize = iconSize ?: (size * 0.90f)

    if (showContainer) {
        val bg = containerColor ?: MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        val border = borderColor ?: MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(cornerRadius))
                .background(bg)
                .border(1.dp, border, RoundedCornerShape(cornerRadius)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = drawableRes),
                contentDescription = "Logo TontinePay",
                modifier = Modifier
                    .size(effectiveIconSize)
                    .clip(RoundedCornerShape(cornerRadius * 0.7f))
            )
        }
    } else {
        Image(
            painter = painterResource(id = drawableRes),
            contentDescription = "Logo TontinePay",
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(cornerRadius))
        )
    }
}

private fun resolveAccentTheme(primaryColor: Color): AccentTheme? {
    return AccentTheme.entries.firstOrNull { it.primary == primaryColor }
}
