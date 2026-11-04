package com.closecode.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * CloseCode design system tokens.
 *
 * Every value below is taken from the design spec ("App screens design spec",
 * screens 01–57, dark-theme variants in CloseCode-dark-screens-1/2.pdf).
 * No screen may contain a loose hex colour, font size or corner radius — read it
 * from here instead.
 *
 * Light values are verbatim from the spec table. Dark values are adapted to read
 * on a near-black background, keeping the same hues.
 */
@Composable
fun CcTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkCcColors() else LightCcColors()
    CompositionLocalProvider(LocalCcColors provides colors) {
        content()
    }
}

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

    // -- Search match highlight --
    override val matchBg: Color = Color(0x33F0C232),
    override val matchText: Color = Color(0xFF5C4500),

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

    // -- Search match highlight --
    override val matchBg: Color = Color(0x33F0C232),
    override val matchText: Color = Color(0xFFFFD166),

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
    val matchBg: Color
    val matchText: Color
    val projectColours: List<Color>
    val dim: Color
    val dimFirstDialog: Color
    val dimSmallMenu: Color
    val shadow: Color
    val iconTileTint: Color
}

private val lightAsInterface: CcColors = LightCcColors()
private val darkAsInterface: CcColors = DarkCcColors()

/** Composition local holding the active colour tokens. */
val LocalCcColors: ProvidableCompositionLocal<CcColors> =
    staticCompositionLocalOf { lightAsInterface }

/** Typography tokens from the spec. */
@Immutable
data class CcType(
    // Sizes in sp
    val hero: Float = 33f,
    val heroWeight: FontWeight = FontWeight.ExtraBold,
    val setupTitle: Float = 27f,
    val setupTitleWeight: FontWeight = FontWeight.ExtraBold,
    val panelTitle: Float = 21.5f,
    val panelTitleWeight: FontWeight = FontWeight.ExtraBold,
    val sheetTitle: Float = 19.5f,
    val sheetTitleWeight: FontWeight = FontWeight.ExtraBold,
    val screenTitle: Float = 16.5f,
    val screenTitleWeight: FontWeight = FontWeight.Bold,
    val itemTitle: Float = 14.5f,
    val itemTitleWeight: FontWeight = FontWeight.Medium,
    val body: Float = 14f,
    val bodyWeight: FontWeight = FontWeight.Normal,
    val small: Float = 13.25f,
    val caption: Float = 12.25f,
    val sectionLabel: Float = 11f,
    val sectionLabelWeight: FontWeight = FontWeight.ExtraBold,
    val sectionLabelTracking: Float = 0.8f,
    val code: Float = 11.5f,
    val terminal: Float = 11f,
    val lineHeight: Float = 1.4f,
)

/** Spacing and shape tokens from the spec. */
@Immutable
data class CcSpace(
    // Screen
    val sidePadding: Float = 14f,        // 14 to 18 dp
    val rowPaddingStart: Float = 8f,
    val rowPaddingEnd: Float = 14f,
    val relatedGap: Float = 10f,         // 8 to 12 dp

    // Radii
    val tileRadius: Float = 14f,         // 13 to 15 dp
    val buttonRadius: Float = 14f,       // 12 to 16 dp
    val cardRadius: Float = 19f,         // 18 to 20 dp
    val groupedSettingsRadius: Float = 18f,
    val chipRadius: Float = 18f,         // 15 to 22 dp
    val pillRadius: Float = 21f,
    val sheetRadius: Float = 26f,        // top corners
    val panelRadius: Float = 26f,        // top-left
    val phonePreviewRadius: Float = 30f,

    // Borders
    val borderWidth: Float = 1f,
    val selectedOutline: Float = 2f,
    val focusRing: Float = 1.5f,

    // Fixed heights
    val statusBarArea: Float = 34f,
    val headerHeight: Float = 56f,
    val homeIndicatorArea: Float = 16f,

    // Component sizes
    val railWidth: Float = 66f,
    val railItem: Float = 46f,
    val railItemGap: Float = 11f,
    val projectAvatar: Float = 46f,
    val panelRowHeight: Float = 58f,
    val panelRowPaddingStart: Float = 8f,
    val panelRowPaddingEnd: Float = 14f,
    val panelRowGap: Float = 12f,
    val tile42: Float = 42f,
    val tile36: Float = 36f,
    val treeRowHeight: Float = 38f,
    val treeIndent: Float = 16f,
    val primaryButtonHeight: Float = 48f,
    val primaryButtonWelcomeHeight: Float = 52f,
    val smallActionButtonHeight: Float = 40f,
    val textFieldHeight: Float = 44f,
    val searchFieldHeight: Float = 42f,
    val chipHeight: Float = 30f,
    val segmentedTrackPadding: Float = 3f,
    val toggleSize: Float = 42f,
    val toggleKnob: Float = 19f,
    val progressHeight: Float = 6f,
    val tickBoxSize: Float = 20f,
    val badgePillHeight: Float = 24f,
    val touchTarget: Float = 44f,

    // Header
    val headerBackPadding: Float = 12f,
    val headerEditorPadding: Float = 18f,
    val headerRightPadding: Float = 14f,
    val headerItemGap: Float = 10f,
    val headerBadgeGap: Float = 7f,
)

/** Motion values from the spec. */
@Immutable
data class CcMotion(
    val sidebarSpringDamping: Float = 0.85f,
    val sidebarSpringStiffness: Float = 400f,
    val sidebarOpenThreshold: Float = 0.40f,
    val sheetSlideMs: Int = 250,
    val menuFadeMs: Int = 120,
    val menuScaleFrom: Float = 0.95f,
    val screenPushMs: Int = 220,
    val toggleKnobMs: Int = 160,
    val progressBarMs: Int = 200,
    val backToastSeconds: Int = 2,
)

/** Icon sizes (dp) and stroke widths from the spec. */
@Immutable
data class CcIconSpec(
    val railToolSize: Float = 22f,
    val railToolStroke: Float = 1.8f,
    val backChevronSize: Float = 20f,
    val backChevronStroke: Float = 2.2f,
    val headerActionSize: Float = 18f,
    val headerActionStroke: Float = 1.9f,
    val panelActionSize: Float = 17f,
    val panelActionStroke: Float = 1.9f,
    val listTileSize: Float = 18f,
    val panelRowTileSize: Float = 21f,
    val listTileStroke: Float = 1.8f,
    val buttonIconSize: Float = 16f,
    val buttonIconStroke: Float = 2.0f,
    val runPlaySize: Float = 17f,
    val treeRowSize: Float = 17f,
    val treeRowStroke: Float = 1.7f,
    val rowChevronSize: Float = 16f,
    val rowChevronStroke: Float = 2.2f,
    val tickSize: Float = 18f,
    val tickStroke: Float = 2.4f,
    val emptyIconSize: Float = 37f,
    val emptyIconStroke: Float = 1.6f,
    val badgeGlyphSize: Float = 11f,
    val badgeGlyphStroke: Float = 3f,
    val minTouchTarget: Float = 38f,
    val preferredTouchTarget: Float = 44f,
)

/** Composition local holding the active type tokens. */
val LocalCcType: ProvidableCompositionLocal<CcType> =
    staticCompositionLocalOf { CcType() }

/** Composition local holding the active spacing tokens. */
val LocalCcSpace: ProvidableCompositionLocal<CcSpace> =
    staticCompositionLocalOf { CcSpace() }

/** Composition local holding the active motion tokens. */
val LocalCcMotion: ProvidableCompositionLocal<CcMotion> =
    staticCompositionLocalOf { CcMotion() }

/** Composition local holding the active icon spec. */
val LocalCcIconSpec: ProvidableCompositionLocal<CcIconSpec> =
    staticCompositionLocalOf { CcIconSpec() }
