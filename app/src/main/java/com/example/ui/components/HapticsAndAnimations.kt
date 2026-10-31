package com.example.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * Modern Micro-interactions & Haptic Engine
 * Benchmarked on Telegram (iOS/Android) and Instagram:
 * - Fluid spring press feedback on touch targets
 * - Light, pleasant haptic ticks (predefined EFFECT_CLICK/EFFECT_TICK or fallback)
 */
object HapticHelper {
    fun triggerLightClick(context: Context, hapticFeedback: HapticFeedback? = null) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                    ?: hapticFeedback?.performHapticFeedback(HapticFeedbackType.LongPress)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                    ?: hapticFeedback?.performHapticFeedback(HapticFeedbackType.LongPress)
            } else {
                hapticFeedback?.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        } catch (_: Exception) {
            hapticFeedback?.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    fun triggerMediumClick(context: Context, hapticFeedback: HapticFeedback? = null) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                    ?: hapticFeedback?.performHapticFeedback(HapticFeedbackType.LongPress)
            } else {
                hapticFeedback?.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        } catch (_: Exception) {
            hapticFeedback?.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
}

/**
 * Subtle & Snappy Press modifier that provides visual scale-down and light tactile feedback
 * without blocking gestures, freezing on single clicks, or requiring a second tap.
 * Scale is subtle (0.985f) to remain barely perceptible and elegant.
 */
fun Modifier.bounceClick(
    minScale: Float = 0.985f,
    onClick: (() -> Unit)? = null
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale = remember { Animatable(1f) }
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(isPressed) {
        if (isPressed) {
            HapticHelper.triggerLightClick(context, haptic)
            scale.animateTo(
                minScale,
                spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessHigh
                )
            )
        } else {
            scale.animateTo(
                1f,
                spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }

    Modifier
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        HapticHelper.triggerLightClick(context, haptic)
                        onClick()
                    }
                )
            } else {
                Modifier
            }
        )
}

/**
 * Interactive press scale effect driven by InteractionSource (useful for Material Buttons/Cards)
 */
fun Modifier.pressScaleEffect(
    interactionSource: MutableInteractionSource,
    minScale: Float = 0.985f
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale = remember { Animatable(1f) }
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(isPressed) {
        if (isPressed) {
            HapticHelper.triggerLightClick(context, haptic)
            scale.animateTo(
                minScale,
                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
            )
        } else {
            scale.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
            )
        }
    }

    Modifier.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}
