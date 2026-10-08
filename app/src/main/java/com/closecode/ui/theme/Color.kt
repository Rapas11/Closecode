package com.closecode.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The sixteen colours a terminal program can ask for.
 *
 * These are not the editor's syntax colours, and are deliberately kept apart from them: a program
 * that asks for "red" means the conventional terminal red, and borrowing the theme's error red would
 * make an ordinary warning look like a build failure. One set per theme, chosen to stay legible on
 * that theme's editor background — which is why the light theme's "black" is a mid grey, since a
 * true black on a near-white editor is invisible and a terminal's darkest colour still has to read.
 *
 * Index order matches the ANSI numbering: 0 black, 1 red, 2 green, 3 yellow, 4 blue, 5 magenta,
 * 6 cyan, 7 white. The bright half is the same eight at 90–97.
 */
@Immutable
data class AnsiColors(
    val normal: List<Color>,
    val bright: List<Color>,
) {
    init {
        require(normal.size == 8) { "the eight ANSI colours are not optional" }
        require(bright.size == 8) { "the eight bright ANSI colours are not optional" }
    }

/** The colour an SGR code selects, taking 90–97 for the bright half and background codes too. */
    fun at(code: Int): Color = when {
        code in 30..37 -> normal[code - 30]
        code in 90..97 -> bright[code - 90]
        // A background code carries the same colour number as its foreground twin.
        code in 40..47 -> normal[code - 40]
        code in 100..107 -> bright[code - 100]
        else -> normal[0]
    }

    /**
     * The colour for an index in 0–15, the form the parser keeps it in: 0–7 for the ordinary eight,
     * 8–15 for their bright twins.
     *
     * The bright half used to be unreachable. Every bright index collapsed onto its ordinary twin on
     * the way in and only the ordinary half was read on the way out, so a program's bright yellow,
     * red, green and blue all drew in their dimmer versions and the `bright` half of every theme
     * was dead weight. Splitting the numbering is what makes them draw at all, and it costs nothing:
     * the two halves are already in the table, they were simply never reached.
     */
    fun colorOf(index: Int): Color = when {
        index in 8..15 -> bright[index - 8]
        else -> normal[index.coerceIn(0, 7)]
    }
}

/**
 * Forge spec section 2. Every value below is taken from the spec table verbatim; 1 px in the
 * prototype is 1 dp here.
 *
 * Fields kept from the previous palette (bg, tx, panel, ac, ...) are preserved so the existing
 * call sites keep working; they are mapped onto the spec tokens in the comments.
 */
@Immutable
data class PageColors(
    // -- Spec section 2, core tokens ------------------------------------------------
    val bg: Color,           // background
    val panel: Color,        // panel
    val panel2: Color,       // panel-2: active tab, hover, progress track
    val ln: Color,           // line: all 1 dp borders and dividers
    val tx: Color,           // text
    val mu: Color,           // muted
    val ac: Color,           // accent
    val selection: Color,    // accent-soft: selected rows
    val editor: Color,       // editor bg
    val red: Color,          // error
    val amber: Color,        // warning
    val success: Color,      // success / OK / info
    val purple: Color,       // path-bar code button, selected autocomplete row

    // -- Spec section 2, problem colors ---------------------------------------------
    val errorPillBg: Color,
    val errorPillTx: Color,
    val warnPillBg: Color,
    val warnPillTx: Color,
    val errorUnderline: Color,
    val warnUnderline: Color,

    // -- Spec section 2, syntax colors ---------------------------------------------
    val keyword: Color,
    val string: Color,
    val comment: Color,
    val fn: Color,
    val number: Color,
    val annotation: Color,
    val type: Color,         // class / type name

    // -- Spec section 6.6, quick-fix lightbulb -------------------------------------
    val bulbBorder: Color,
    val bulbBg: Color,
    val bulbIcon: Color,

    // -- Indentation guides ----------------------------------------------------
    // About 15% of the text colour. A guide is a structural hint the reader looks *past*, not a
    // rule in its own right: at the strength of a real divider it competed with the code it was
    // annotating, which is the whole job of an annotation not to do.
    val guideRule: Color,

    // -- Terminal ---------------------------------------------------------------
    val ansi: AnsiColors,

    // -- Translucent overlays; identical in both themes because they sit on the editor
    val stackBadge: Color,   // multi-problem badge, rgba(128,128,128,.28)
    val javaIconBg: Color,
    val javaIconTx: Color,
    val kotlinIconBg: Color,
    val kotlinIconTx: Color,
    val xmlIconBg: Color,
    val xmlIconTx: Color,
    val autoIconBg: Color,        // autocomplete icon tile
    val autoIconTx: Color,
    val autoIconBgSelected: Color,
    val scrim: Color,             // drawer / palette scrim

    // -- Legacy aliases kept for existing call sites ---------------------------------
    val panel3: Color,      // active-tab pill and toast surface -> accent-soft
    val cd: Color,          // cards and Material surface -> panel
    val ab: Color,          // primary action background -> accent
    val af: Color,          // label drawn on top of an accent fill
    val glow: Color,        // faint accent wash for the page background
)

/**
 * The sixteen terminal colours on the light editor background.
 *
 * "Black" is a mid grey and "white" is a slate: on a near-white editor the conventional extremes
 * would give a program that asked for plain text an unreadable line, or a program that asked for
 * white text no background to show it against.
 */
val LightAnsi = AnsiColors(
    normal = listOf(
        Color(0xFF3A4252), // black
        Color(0xFFC62839), // red
        Color(0xFF0F7A54), // green
        Color(0xFF8A5A00), // yellow
        Color(0xFF2457FF), // blue
        Color(0xFF7A3FD1), // magenta
        Color(0xFF0E7490), // cyan
        Color(0xFF55607A), // white
    ),
    bright = listOf(
        Color(0xFF7A8496), // bright black
        Color(0xFFD1344B), // bright red
        Color(0xFF12805C), // bright green
        Color(0xFFA86A00), // bright yellow
        Color(0xFF3B63D8), // bright blue
        Color(0xFF9B6FE0), // bright magenta
        Color(0xFF0E8FA8), // bright cyan
        Color(0xFF101826), // bright white
    ),
)

/** The sixteen terminal colours on the dark editor background. */
val DarkAnsi = AnsiColors(
    normal = listOf(
        Color(0xFF3B4252), // black
        Color(0xFFFF6B81), // red
        Color(0xFF3ECF9B), // green
        Color(0xFFF0B429), // yellow
        Color(0xFF7B9BFF), // blue
        Color(0xFFC08BFF), // magenta
        Color(0xFF5FD3D3), // cyan
        Color(0xFFD7DEEA), // white
    ),
    bright = listOf(
        Color(0xFF6B7688), // bright black
        Color(0xFFFF9AA7), // bright red
        Color(0xFF6FE0B0), // bright green
        Color(0xFFF5C26B), // bright yellow
        Color(0xFFA6BEFF), // bright blue
        Color(0xFFDCB0FF), // bright magenta
        Color(0xFF8FE6E6), // bright cyan
        Color(0xFFFFFFFF), // bright white
    ),
)

val LightColors = PageColors(
    bg = Color(0xFFECEFF4),
    panel = Color(0xFFFFFFFF),
    panel2 = Color(0xFFF3F5F9),
    ln = Color(0xFFDDE2EA),
    tx = Color(0xFF101826),
    mu = Color(0xFF66728A),
    ac = Color(0xFF2457FF),
    selection = Color(0xFFE3EAFF),
    editor = Color(0xFFFBFCFE),
    red = Color(0xFFD1344B),
    amber = Color(0xFFA86A00),
    success = Color(0xFF12805C),
    purple = Color(0xFF5B3FD1),

    errorPillBg = Color(0xFFFDE3E6),
    errorPillTx = Color(0xFFA1232F),
    warnPillBg = Color(0xFFFFF0CF),
    warnPillTx = Color(0xFF8A5A00),
    errorUnderline = Color(0xFFEF4444),
    warnUnderline = Color(0xFFE0A100),

    keyword = Color(0xFF7B3FE4),
    string = Color(0xFF0D7A55),
    comment = Color(0xFF8793A8),
    fn = Color(0xFF2457FF),
    number = Color(0xFFC2410C),
    annotation = Color(0xFFB45309),
    type = Color(0xFF946200),

    bulbBorder = Color(0xFF8A93A6),
    bulbBg = Color(0xFFFFFFFF),
    bulbIcon = Color(0xFFC98A00),

    // The text colour at 15% alpha.
    guideRule = Color(0x26101826),

    ansi = LightAnsi,

    stackBadge = Color(0x47808080),
    javaIconBg = Color(0x33D97706),
    javaIconTx = Color(0xFFE08A1E),
    kotlinIconBg = Color(0x337B3FE4),
    kotlinIconTx = Color(0xFF7B3FE4),
    xmlIconBg = Color(0x2E2457FF),
    xmlIconTx = Color(0xFF2457FF),
    autoIconBg = Color(0x38D9A028),
    autoIconTx = Color(0xFFD9A028),
    autoIconBgSelected = Color(0x38FFFFFF),
    scrim = Color(0x59000000),

    panel3 = Color(0xFFE3EAFF),
    cd = Color(0xFFFFFFFF),
    ab = Color(0xFF2457FF),
    af = Color(0xFFFFFFFF),
    glow = Color(0x1F2457FF),
)

val DarkColors = PageColors(
    bg = Color(0xFF0B0F16),
    panel = Color(0xFF131923),
    panel2 = Color(0xFF1A2230),
    ln = Color(0xFF263043),
    tx = Color(0xFFE7ECF5),
    mu = Color(0xFF8B98B0),
    ac = Color(0xFF7B9BFF),
    selection = Color(0xFF1C2A52),
    editor = Color(0xFF0F141D),
    red = Color(0xFFFF6B81),
    amber = Color(0xFFF0B429),
    success = Color(0xFF3ECF9B),
    purple = Color(0xFF5B3FD1),

    errorPillBg = Color(0xFF4A2A31),
    errorPillTx = Color(0xFFFFB4BD),
    warnPillBg = Color(0xFF473A1C),
    warnPillTx = Color(0xFFF5C26B),
    errorUnderline = Color(0xFFEF4444),
    warnUnderline = Color(0xFFE0A100),

    keyword = Color(0xFFB794FF),
    string = Color(0xFF6FE0B0),
    comment = Color(0xFF5D6A82),
    fn = Color(0xFF7FB2FF),
    number = Color(0xFFFFA86B),
    annotation = Color(0xFFF5C26B),
    type = Color(0xFFE5C07B),

    bulbBorder = Color(0xFF9C9CAB),
    bulbBg = Color(0xFF1F1F26),
    bulbIcon = Color(0xFFF5DC8A),

    // The text colour at 15% alpha.
    guideRule = Color(0x26E7ECF5),

    ansi = DarkAnsi,

    stackBadge = Color(0x47808080),
    javaIconBg = Color(0x33D97706),
    javaIconTx = Color(0xFFE08A1E),
    kotlinIconBg = Color(0x337B3FE4),
    kotlinIconTx = Color(0xFF7B3FE4),
    xmlIconBg = Color(0x2E2457FF),
    xmlIconTx = Color(0xFF7FB2FF),
    autoIconBg = Color(0x38D9A028),
    autoIconTx = Color(0xFFD9A028),
    autoIconBgSelected = Color(0x38FFFFFF),
    scrim = Color(0x59000000),

    panel3 = Color(0xFF1C2A52),
    cd = Color(0xFF131923),
    ab = Color(0xFF7B9BFF),
    // Spec section 4.1 asks for a white "Run" label. The dark accent is a light blue, so a
    // white label would sit near 2.3:1 against it; this near-black keeps the button legible.
    af = Color(0xFF0B0F16),
    glow = Color(0x1F7B9BFF),
)

val LocalPageColors = staticCompositionLocalOf { LightColors }

/**
 * The terminal palettes a reader can choose between.
 *
 * Four of these are schemes people already know by name, so choosing one gets them the colours they
 * have on every other machine. Each is a full set of sixteen, tuned to read on the app's editor
 * background rather than copied wholesale — a palette that was legible on its own scheme's ground
 * can be unreadable on this one, and a terminal that cannot be read is not a theme.
 *
 * These are keyed by the names in `TerminalThemeChoices`, so adding a scheme there without adding
 * one here falls back to Closecode's own rather than drawing nothing.
 */
val TerminalPalettes: Map<String, AnsiColors> = mapOf(
    "Closecode" to DarkAnsi,
    // Dracula: its background, with its foreground family shifted for a near-black ground.
    "Dracula" to AnsiColors(
        normal = listOf(
            Color(0xFF21222C), Color(0xFFFF5555), Color(0xFF50FA7B), Color(0xFFF1FA8C),
            Color(0xFFBD93F9), Color(0xFFFF79C6), Color(0xFF8BE9FD), Color(0xFFF8F8F2),
        ),
        bright = listOf(
            Color(0xFF6272A4), Color(0xFFFF6E6E), Color(0xFF69FF94), Color(0xFFFFEA80),
            Color(0xFFD6ACFF), Color(0xFFFF92D0), Color(0xFFA4FFFF), Color(0xFFFFFFFF),
        ),
    ),
    // One Dark: desaturated and low-contrast on purpose, which is the whole point of it.
    "One Dark" to AnsiColors(
        normal = listOf(
            Color(0xFF3E4451), Color(0xFFE05561), Color(0xFF8CC265), Color(0xFFD18F52),
            Color(0xFF4AA5F0), Color(0xFFC162DE), Color(0xFF42B3C2), Color(0xFFABB2BF),
        ),
        bright = listOf(
            Color(0xFF5C6370), Color(0xFFFF616E), Color(0xFFA5D075), Color(0xFFF0A45D),
            Color(0xFF4DC4FF), Color(0xFFD37AD9), Color(0xFF4CD1E0), Color(0xFFE6E6E6),
        ),
    ),
    // Monokai: warm greys, and a brown so dark it is nearly the background — which is how Monokai
    // has always drawn comments, and the reason its dark greys are so light.
    "Monokai" to AnsiColors(
        normal = listOf(
            Color(0xFF3D3D3D), Color(0xFFF92672), Color(0xFFA6E22E), Color(0xFFE6DB74),
            Color(0xFF66D9EF), Color(0xFFAE81FF), Color(0xFFA1EFE4), Color(0xFFF8F8F2),
        ),
        bright = listOf(
            Color(0xFF75715E), Color(0xFFF92672), Color(0xFFA6E22E), Color(0xFFE6DB74),
            Color(0xFF66D9EF), Color(0xFFAE81FF), Color(0xFFA1EFE4), Color(0xFFFFFFFF),
        ),
    ),
    // Matrix: green, in the four shades the film uses, so a green-on-black habit reads correctly.
    "Matrix" to AnsiColors(
        normal = listOf(
            Color(0xFF0B3D0B), Color(0xFF00FF41), Color(0xFF00C853), Color(0xFF7CFF7C),
            Color(0xFF00E5A0), Color(0xFF3BFFC3), Color(0xFF00FF9C), Color(0xFFB7FFC7),
        ),
        bright = listOf(
            Color(0xFF1B5E20), Color(0xFF6EFF8A), Color(0xFF4DFF8A), Color(0xFFB7FFC7),
            Color(0xFF54FFB0), Color(0xFF8CFFD6), Color(0xFF6EFFC0), Color(0xFFFFFFFF),
        ),
    ),
)
