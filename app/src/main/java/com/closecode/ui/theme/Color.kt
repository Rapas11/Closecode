package com.closecode.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class PageColors(
    val bg: Color,
    val tx: Color,
    val mu: Color,
    val ln: Color,
    val ac: Color,
    val ab: Color,
    val af: Color,
    val cd: Color,
    val keyword: Color,
    val string: Color,
    val comment: Color,
    val fn: Color,
    val glow: Color,
    val type: Color,
    val number: Color,
    val panel: Color,
    val panel2: Color,
    val panel3: Color,
    val editor: Color,
    val selection: Color,
    val red: Color,
)

val LightColors = PageColors(
    bg = Color(0xFFF8F7F3),
    tx = Color(0xFF12151C),
    mu = Color(0xFF5D6472),
    ln = Color(0xFFE0DDD5),
    ac = Color(0xFFB45309),
    ab = Color(0xFF12151C),
    af = Color(0xFFF8F7F3),
    cd = Color(0xFFFFFFFF),
    keyword = Color(0xFF7C3AED),
    string = Color(0xFF0F7A55),
    comment = Color(0xFF8B909C),
    fn = Color(0xFF1D6FD1),
    glow = Color(0x1FB45309),
    type = Color(0xFF1F6FEB),
    number = Color(0xFFB45309),
    panel = Color(0xFFF2F0EA),
    panel2 = Color(0xFFEAE7DF),
    panel3 = Color(0xFFE2DED4),
    editor = Color(0xFFFDFCFA),
    selection = Color(0xFFDDD8CC),
    red = Color(0xFFC0392B),
)

val DarkColors = PageColors(
    bg = Color(0xFF090C12),
    tx = Color(0xFFEEF0F5),
    mu = Color(0xFF98A0B0),
    ln = Color(0xFF1F2633),
    ac = Color(0xFFFFB454),
    ab = Color(0xFFEEF0F5),
    af = Color(0xFF090C12),
    cd = Color(0xFF0F141D),
    keyword = Color(0xFFC4A3FF),
    string = Color(0xFF7FE0B4),
    comment = Color(0xFF5E6779),
    fn = Color(0xFF7DB8FF),
    glow = Color(0x1FFFB454),
    type = Color(0xFF8EC7FF),
    number = Color(0xFFFFB454),
    panel = Color(0xFF0F141D),
    panel2 = Color(0xFF131A24),
    panel3 = Color(0xFF18212D),
    editor = Color(0xFF0B1017),
    selection = Color(0xFF263447),
    red = Color(0xFFFF7D8A),
)

val LocalPageColors = staticCompositionLocalOf { LightColors }