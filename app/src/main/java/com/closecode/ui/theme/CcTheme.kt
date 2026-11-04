package com.closecode.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Sans = Manrope (the app's bundled UI font) and Mono = JetBrains Mono, per the spec's
// "use the app's existing fonts if it already has them".

/**
 * CloseCode design system tokens — every value is taken from the design spec
 * ("App screens design spec", screens 01–57, light + dark values).
 *
 * No screen may contain a loose hex colour, font size or corner radius — read it
 * from here instead. Light values are verbatim from the spec table; the dark
 * values are the fixed dark palette from the same document.
 */

/** The design-token colour palette, light theme. Verbatim from the spec table. */
@Immutable
data class LightCcColors(
    // -- Core surface colours ---------------------------------------------------
    override val rail: Color = Color(0xFFE3E5EA),   // left tool rail, status bar area, outer background behind panel
    override val panel: Color = Color(0xFFF1F2F5),  // side panel, bottom sheets, popup menus
    override val content: Color = Color(0xFFFAFAFB), // editor and every full screen background; never pure white
    override val field: Color = Color(0xFFE9EBEF),  // text fields, grouped settings cards, unselected chips, key row keys, back button, result cards

    // -- Selection --------------------------------------------------------------
    override val selectedRow: Color = Color(0xFFDCDFE5),   // selected row in lists
    override val selectedFile: Color = Color(0xFFDDE0FB),  // selected file in the tree (text #2E3036)

    // -- Borders ----------------------------------------------------------------
    override val border: Color = Color(0xFFD5D8DE),   // card, menu and sheet borders (1 dp)
    override val divider: Color = Color(0xFFE3E5EA),  // lines between header and content, between rows (1 dp)
    override val toggleOff: Color = Color(0xFFB8BCC5), // toggle track when off

    // -- Text -------------------------------------------------------------------
    override val text: Color = Color(0xFF1F2024),    // titles, main text
    override val text2: Color = Color(0xFF2E3036),   // body text, code text
    override val textSoft: Color = Color(0xFF4F535C), // secondary button text, unselected chip text
    override val muted: Color = Color(0xFF626772),   // descriptions, sub lines, section labels
    override val faint: Color = Color(0xFF858A95),   // line numbers, hints, disabled icons

    // -- Brand / status colours -------------------------------------------------
    override val accent: Color = Color(0xFF5865F2),   // primary buttons, selected rail item, selected chip, links, toggle on, progress, focus ring
    override val green: Color = Color(0xFF248046),   // run button, success, added lines, "Ready"
    override val amber: Color = Color(0xFFC98A0A),   // modified, warnings
    override val red: Color = Color(0xFFDA373C),     // errors, delete, badges, "Not installed"
    override val blue: Color = Color(0xFF2F6FE0),    // info and config file icons
    override val purple: Color = Color(0xFF7B5CE0),   // code file icons
    override val yellow: Color = Color(0xFFD99A06),  // folder icons
    override val teal: Color = Color(0xFF1F9D6B),    // success icons
    override val orange: Color = Color(0xFFD97706),  // warning triangle
    override val pink: Color = Color(0xFFDB4C9B),    // image icon

    // -- Syntax colours (light) ------------------------------------------------
    override val syntaxKeyword: Color = Color(0xFF8250DF),
    override val syntaxType: Color = Color(0xFF1B7C83),
    override val syntaxString: Color = Color(0xFF0A7D4F),
    override val syntaxFunction: Color = Color(0xFF6F5A00),
    override val syntaxNumber: Color = Color(0xFF0A7D4F),
    override val syntaxComment: Color = Color(0xFF5E7F46),
    override val syntaxVariable: Color = Color(0xFF0A5EA8),
    override val syntaxPlain: Color = Color(0xFF2E3036),

    // -- App screens accent colours (from spec inline hexes) --------------------
    override val projectColours: List<Color> = listOf(
        Color(0xFF2563EB),
        Color(0xFF16A34A),
        Color(0xFFEA580C),
        Color(0xFF0E7490),
        Color(0xFF7C3AED),
        Color(0xFFBE185D),
    ),

    // -- Overlays ---------------------------------------------------------------
    override val dim: Color = Color(0x4C1E2332),   // rgba(30,35,50,0.30) layer behind sheets and menus
    override val dimFirstDialog: Color = Color(0x611E2332), // 0.38 behind first-time dialogs
    override val dimSmallMenu: Color = Color(0x2E1E2332),   // 0.18 behind small menus
    override val shadow: Color = Color(0x401E2332),  // rgba(30,35,50,0.25), blur 30, y 12 — popup menus and sliding editor card
    override val iconTileTint: Color = Color(0x22000000), // icon colour at 13% opacity behind a coloured icon

    // -- Search match & diff -----------------------------------------------------
    override val diffAddBg: Color = Color(0xFFDDF3E4),
    override val diffAddText: Color = Color(0xFF1A7F37),
    override val diffDelBg: Color = Color(0xFFFBE4E5),
    override val diffDelText: Color = Color(0xFFDA373C),
    override val matchBg: Color = Color(0xFFFBE7A1),
    override val matchText: Color = Color(0xFF6B4E00),
) : CcColors

/** The design-token colour palette, dark theme. Adapted from the spec table for a near-black ground. */
@Immutable
data class DarkCcColors(
    // -- Core surface colours ---------------------------------------------------
    override val rail: Color = Color(0xFF1E1F22),
    override val panel: Color = Color(0xFF2B2D31),
    override val content: Color = Color(0xFF313338),
    override val field: Color = Color(0xFF383A40),

    // -- Selection --------------------------------------------------------------
    override val selectedRow: Color = Color(0xFF404249),
    override val selectedFile: Color = Color(0xFF404249),

    // -- Borders ----------------------------------------------------------------
    override val border: Color = Color(0xFF3F4147),
    override val divider: Color = Color(0xFF26272B),
    override val toggleOff: Color = Color(0xFF4A4D55),

    // -- Text -------------------------------------------------------------------
    override val text: Color = Color(0xFFF2F3F5),
    override val text2: Color = Color(0xFFDBDEE1),
    override val textSoft: Color = Color(0xFFB5BAC1),
    override val muted: Color = Color(0xFF949BA4),
    override val faint: Color = Color(0xFF6D6F78),

    // -- Brand / status colours -------------------------------------------------
    override val accent: Color = Color(0xFF5865F2),
    override val green: Color = Color(0xFF23A559),
    override val amber: Color = Color(0xFFF0B232),
    override val red: Color = Color(0xFFF23F42),
    override val blue: Color = Color(0xFF2F6FE0),
    override val purple: Color = Color(0xFF7B5CE0),
    override val yellow: Color = Color(0xFFD99A06),
    override val teal: Color = Color(0xFF1F9D6B),
    override val orange: Color = Color(0xFFD97706),
    override val pink: Color = Color(0xFFDB4C9B),

    // -- Syntax colours (dark, brighter per spec) ------------------------------
    override val syntaxKeyword: Color = Color(0xFFC792EA),
    override val syntaxType: Color = Color(0xFF4EC9B0),
    override val syntaxString: Color = Color(0xFFCE9178),
    override val syntaxFunction: Color = Color(0xFFDCDCAA),
    override val syntaxNumber: Color = Color(0xFFB5CEA8),
    override val syntaxComment: Color = Color(0xFF6A9955),
    override val syntaxVariable: Color = Color(0xFF9CDCFE),
    override val syntaxPlain: Color = Color(0xFFDBDEE1),

    // -- App screens accent colours (same hues as light) ------------------------
    override val projectColours: List<Color> = listOf(
        Color(0xFF2563EB),
        Color(0xFF16A34A),
        Color(0xFFEA580C),
        Color(0xFF0E7490),
        Color(0xFF7C3AED),
        Color(0xFFBE185D),
    ),

    // -- Overlays ---------------------------------------------------------------
    override val dim: Color = Color(0x80000000), // rgba(0,0,0,0.50)
    override val dimFirstDialog: Color = Color(0x80000000),
    override val dimSmallMenu: Color = Color(0x50000000),
    override val shadow: Color = Color(0x401E2332),
    override val iconTileTint: Color = Color(0x22000000),

    // -- Search match & diff -----------------------------------------------------
    override val diffAddBg: Color = Color(0xFF12281F),
    override val diffAddText: Color = Color(0xFF3FB97A),
    override val diffDelBg: Color = Color(0xFF2A1519),
    override val diffDelText: Color = Color(0xFFEF5B5B),
    override val matchBg: Color = Color(0xFF4A3D14),
    override val matchText: Color = Color(0xFFFFD97D),
) : CcColors

/** Every token that exists in both light and dark palettes. */
interface CcColors {
    val rail: Color
    val panel: Color
    val content: Color
    val field: Color
    val selectedRow: Color
    val selectedFile: Color
    val border: Color
    val divider: Color
    val toggleOff: Color
    val text: Color
    val text2: Color
    val textSoft: Color
    val muted: Color
    val faint: Color
    val accent: Color
    val green: Color
    val amber: Color
    val red: Color
    val blue: Color
    val purple: Color
    val yellow: Color
    val teal: Color
    val orange: Color
    val pink: Color
    val syntaxKeyword: Color
    val syntaxType: Color
    val syntaxString: Color
    val syntaxFunction: Color
    val syntaxNumber: Color
    val syntaxComment: Color
    val syntaxVariable: Color
    val syntaxPlain: Color
    val projectColours: List<Color>
    val dim: Color
    val dimFirstDialog: Color
    val dimSmallMenu: Color
    val shadow: Color
    val iconTileTint: Color
    val diffAddBg: Color
    val diffAddText: Color
    val diffDelBg: Color
    val diffDelText: Color
    val matchBg: Color
    val matchText: Color
}

private val lightAsInterface: CcColors = LightCcColors()
private val darkAsInterface: CcColors = DarkCcColors()

/** Composition local holding the active colour tokens. */
val LocalCcColors: ProvidableCompositionLocal<CcColors> =
    staticCompositionLocalOf { lightAsInterface }

/** The font families the type tokens are built from. UI = Sans (Manrope), code = Manrope's Mono. */
private val UiFont = Sans
private val CodeFont = Mono

/** Text styles from the design spec. Line height 1.4 of the size unless noted. */
object CcType {
    val hero = TextStyle(
        fontFamily = UiFont,
        fontSize = 33.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-1).sp,
    )

    val setupTitle = TextStyle(
        fontFamily = UiFont,
        fontSize = 27.sp,
        fontWeight = FontWeight.ExtraBold,
    )

    val panelTitle = TextStyle(
        fontFamily = UiFont,
        fontSize = 21.sp,
        fontWeight = FontWeight.ExtraBold,
    )

    val sheetTitle = TextStyle(
        fontFamily = UiFont,
        fontSize = 19.sp,
        fontWeight = FontWeight.ExtraBold,
    )

    val screenTitle = TextStyle(
        fontFamily = UiFont,
        fontSize = 16.5.sp,
        fontWeight = FontWeight.Bold,
    )

    val itemTitle = TextStyle(
        fontFamily = UiFont,
        fontSize = 14.5.sp,
        fontWeight = FontWeight.SemiBold,
    )

    val body = TextStyle(
        fontFamily = UiFont,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
    )

    val small = TextStyle(
        fontFamily = UiFont,
        fontSize = 13.sp,
    )

    val caption = TextStyle(
        fontFamily = UiFont,
        fontSize = 12.sp,
    )

    val section = TextStyle(
        fontFamily = UiFont,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
    )

    val code = TextStyle(
        fontFamily = CodeFont,
        fontSize = 11.5.sp,
        lineHeight = 21.sp,
    )

    val log = TextStyle(
        fontFamily = CodeFont,
        fontSize = 11.sp,
        lineHeight = 16.sp,
    )
}

/** Corner shapes from the design spec. */
object CcShape {
    val tile = RoundedCornerShape(13.dp)
    val tileLarge = RoundedCornerShape(15.dp)
    val button = RoundedCornerShape(15.dp)
    val card = RoundedCornerShape(18.dp)
    val chip = RoundedCornerShape(15.dp)
    val sheetTop = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    val panelTopLeft = RoundedCornerShape(topStart = 26.dp)
    val menu = RoundedCornerShape(16.dp)
    val dialog = RoundedCornerShape(24.dp)
}

/** How screens read the active tokens; mirrors the spec's `CcTheme.colors`. */
object CcTheme {
    val colors: CcColors @Composable get() = LocalCcColors.current
}

/** Provides the active colour tokens (light or dark) to the subtree. */
@Composable
fun ProvideCcColors(
    darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) darkAsInterface else lightAsInterface
    CompositionLocalProvider(LocalCcColors provides colors) {
        content()
    }
}

/** Tile tint = colour at 13% opacity. */
fun Color.tint() = this.copy(alpha = 0.13f)
