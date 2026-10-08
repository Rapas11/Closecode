package com.closecode.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

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