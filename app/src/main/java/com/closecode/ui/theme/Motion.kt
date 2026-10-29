package com.closecode.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import com.closecode.ui.settings.LocalAnimationsEnabled

/** A duration-based spec when motion is wanted, and an instant one when the switch is off. */
@Composable
fun <T> motion(durationMillis: Int): FiniteAnimationSpec<T> =
    if (LocalAnimationsEnabled.current) tween(durationMillis) else snap()
