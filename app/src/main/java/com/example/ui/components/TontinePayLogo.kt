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
    size: Dp = 48.dp,
    iconSize: Dp? = null,
    cornerRadius: Dp = 0.dp,
    accentTheme: AccentTheme? = null,
    useAccentTheme: Boolean = true,
    showContainer: Boolean = false,
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

    // Pure logo without enclosing square/card container for high visibility and uniformity
    Image(
        painter = painterResource(id = drawableRes),
        contentDescription = "Logo TontinePay",
        modifier = modifier.size(size)
    )
}

private fun resolveAccentTheme(primaryColor: Color): AccentTheme? {
    return AccentTheme.entries.firstOrNull { it.primary == primaryColor }
}
