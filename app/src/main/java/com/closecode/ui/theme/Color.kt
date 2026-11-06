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

    // The five bracket depths, for the optional rainbow-bracket setting. Taken from the syntax
    // colours rather than invented: they are already muted enough to sit behind a line of code
    // without reading as part of its meaning, and both themes have a set that works on their own
    // editor background.
    val bracket1: Color,
    val bracket2: Color,
    val bracket3: Color,
    val bracket4: Color,
    val bracket5: Color,

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

    // -- Code editor design spec tokens --------------------------------------------
    // One named token per row of the spec's Colors and fonts table. Values below never repeat a
    // hex: the table is the single source of truth, and anything that needs one of these colors
    // reads it here. Names follow the spec except where the historic palette already owns the
    // name with a different value (keyword/type/string/selection/bulbBg/bulbIcon), in which case
    // the editor token takes a code-/tile-/glyph-qualified name and the comment says which spec
    // row it is.
    val editorBg: Color,
    val activeLineBg: Color,
    val divider: Color,
    val text: Color,
    val lineNumber: Color,
    val lineNumberActive: Color,
    val indentGuide: Color,
    val accent: Color,
    val error: Color,
    val codeKeyword: Color,   // spec: keyword
    val codeComment: Color,   // spec: comment
    val codeNumber: Color,    // spec: number
    val codeVariable: Color,  // spec: variable
    val codeType: Color,      // spec: type
    val codeFunction: Color,  // spec: function
    val codeString: Color,    // spec: string
    val popupBg: Color,
    val popupBorder: Color,
    val popupSelected: Color,
    val popupText: Color,
    val popupMuted: Color,
    val textSelection: Color, // spec: selection (text-selection background)
    val toolbarBg: Color,
    val toolbarBorder: Color,
    val toolbarAccent: Color,
    val bulbTileBg: Color,    // spec: bulbBg (the 18 dp tile, not the old floating card)
    val bulbGlyph: Color,     // spec: bulbIcon
    val pillBg: Color,
    val pillBorder: Color,
    val pillText: Color,
    val gitAdded: Color,
    val gitModified: Color,
    val gitDeleted: Color,
    val foldArrow: Color,
    // Hexes the spec gives inline outside the token table:
    val brightText: Color,    // #E6E8EE project name, active tab, selected-tab text
    val hintText: Color,      // #6B7385 tab text, signature description
    val iconTint: Color,       // #9AA3B5 top-bar icons, signature text
    val runFill: Color,        // #2F6FED run button
    val tabActiveBg: Color,    // #131722 active tab background
    val foldChipBg: Color,     // #1A1F2B folded-block ellipsis chip
    val scrollThumb: Color,    // #3A4256 scrollbar thumb
    val badgeP: Color,         // #7A5AF0 property / parameter badge
    val badgeF: Color,         // #2F9E7A function badge
    val badgeM: Color,         // #C28A2C member / variable badge
    val warnText: Color,       // #E5C07B warning squiggle, tab/save dots

    // -- App screens design spec tokens --------------------------------------------
    // One token per row of the spec's token table. Dark values are verbatim; light values are
    // adapted the same way the editor tokens were (same hues, readable on near-white).
    val surface: Color,        // cards and panels on the dark background
    val surfaceBorder: Color,   // 1 dp card border
    val chipBg: Color,         // segmented controls, chips, search fields
    val fieldBg: Color,        // text fields, terminal and log background
    val fieldBorder: Color,     // 1 dp text-field border
    val primary: Color,         // the one large main button on a screen
    val primaryTint: Color,     // outline buttons with blue text
    val link: Color,            // links and outline button text
    val textMuted: Color,       // descriptions and helper text
    val textFaint: Color,       // hints, disabled text, folder counts
    val warning: Color,         // modified files, unsaved dots, warnings
    val statusAddedBg: Color,   // A badge background
    val statusModifiedBg: Color, // M badge background
    val statusDeletedBg: Color, // D badge background
    // App screens, hexes the spec gives inline:
    val fileRowActive: Color,   // open file's row background (#1B2850)
    val htmlIcon: Color,        // HTML file icon
    val keyRowBg: Color,        // terminal key row background (#12151D)
    val keyBg: Color,           // editor + terminal key background (#1A1F2B)
    val cmdButtonBg: Color,      // commands panel Keyboard button
    val cmdAddBg: Color,         // commands panel Add background
    // -- Screens update spec tokens, grouped: one constructor parameter keeps the
    // data class under the JVM method-size limit no matter how many tokens join it.
    val spec: SpecColors,
    // -- Light redesign tokens. Defaults are the light mockup so existing light call sites can
    // migrate without touching every constructor; DarkColors supplies its own set.
    val mock: MockColors = MockColors(),
)

/**
 * Tokens extracted from the 45-screen light handoff. The older PageColors fields remain the
 * compatibility layer; new shell and screen work reads these named values directly.
 */
@Immutable
data class MockColors(
    val shell: Color = Color(0xFFE3E5EA),
    val rail: Color = Color(0xFFE3E5EA),
    val page: Color = Color(0xFFFAFAFB),
    val panel: Color = Color(0xFFF1F2F5),
    val content: Color = Color(0xFFFAFAFB),
    val card: Color = Color(0xFFF1F2F5),
    val cardMuted: Color = Color(0xFFE9EBEF),
    val field: Color = Color(0xFFE9EBEF),
    val input: Color = Color(0xFFE9EBEF),
    val track: Color = Color(0xFFE3E5EA),
    val selected: Color = Color(0xFFDCDFE5),
    val selectedFile: Color = Color(0xFFDDE0FB),
    val toggleOff: Color = Color(0xFFB8BCC5),
    val divider: Color = Color(0xFFE3E5EA),
    val border: Color = Color(0xFFD5D8DE),
    val accent: Color = Color(0xFF5865F2),
    val onAccent: Color = Color(0xFFFFFFFF),
    val green: Color = Color(0xFF248046),
    val onGreen: Color = Color(0xFFFFFFFF),
    val text: Color = Color(0xFF1F2024),
    val text2: Color = Color(0xFF2E3036),
    val textSoft: Color = Color(0xFF4F535C),
    val muted: Color = Color(0xFF626772),
    // v7 token blue/purple/yellow/orange/pink: the table owns these names, so the tint family
    // reads the exact hexes the table gives, not lookalikes.
    val faint: Color = Color(0xFF858A95),
    val danger: Color = Color(0xFFDA373C),
    val dangerBg: Color = Color(0xFFFBE4E5),
    val warning: Color = Color(0xFFC98A0A),
    val warningBg: Color = Color(0xFFFBEED6),
    val successBg: Color = Color(0xFFDDF3E4),
    val blueBg: Color = Color(0xFFDDE7FF),
    val blue: Color = Color(0xFF2F6FE0),
    val purpleBg: Color = Color(0xFFEEE7FF),
    val purple: Color = Color(0xFF7B5CE0),
    val pinkBg: Color = Color(0xFFFCE7F3),
    val pink: Color = Color(0xFFDB4C9B),
    val orangeBg: Color = Color(0xFFFFEBDD),
    val orange: Color = Color(0xFFD97706),
    val yellowBg: Color = Color(0xFFFAF0D9),
    val yellow: Color = Color(0xFFD99A06),
    val teal: Color = Color(0xFF1F9D6B),
    val grayBg: Color = Color(0xFFE5E7EB),
    val gray: Color = Color(0xFF4B5563),
    val badge: Color = Color(0xFFDA373C),
    // The scrim behind the sliding editor card is 30% of #1E2332, not a plain black wash; the
    // card under it has to keep looking light (v7 correction 7).
    val scrim: Color = Color(0x4D1E2332),
    val railWidth: Float = 66f,
    val panelMaxWidth: Float = 300f,
    val panelRadius: Float = 26f,
    val cardRadius: Float = 18f,
    val iconRadius: Float = 15f,
    val buttonRadius: Float = 15f,
)

/**
 * The dark theme's own token row: the 45-screen handoff's Discord-style greys (November 2026
 * rebase). Layout, sizes, shapes and icons are identical to the light row — only these values
 * change.
 */
val DarkMockColors = MockColors(
    shell = Color(0xFF1E1F22),
    rail = Color(0xFF1E1F22),
    page = Color(0xFF313338),
    panel = Color(0xFF2B2D31),
    content = Color(0xFF313338),
    card = Color(0xFF2B2D31),
    cardMuted = Color(0xFF383A40),
    field = Color(0xFF383A40),
    input = Color(0xFF1E1F22),
    track = Color(0xFF1E1F22),
    selected = Color(0xFF404249),
    selectedFile = Color(0xFF404249),
    toggleOff = Color(0xFF4A4D55),
    divider = Color(0xFF26272B),
    border = Color(0xFF3F4147),
    accent = Color(0xFF5865F2),
    onAccent = Color(0xFFFFFFFF),
    green = Color(0xFF23A559),
    onGreen = Color(0xFFFFFFFF),
    text = Color(0xFFF2F3F5),
    text2 = Color(0xFFDBDEE1),
    textSoft = Color(0xFFB5BAC1),
    muted = Color(0xFF949BA4),
    faint = Color(0xFF6D6F78),
    danger = Color(0xFFF23F42),
    dangerBg = Color(0xFF3A1A1F),
    warning = Color(0xFFF0B232),
    warningBg = Color(0xFF3A3115),
    successBg = Color(0xFF16382A),
    blueBg = Color(0xFF24354A),
    blue = Color(0xFF7FB0FF),
    purpleBg = Color(0xFF2C2447),
    purple = Color(0xFFB794FF),
    pinkBg = Color(0xFF3A1A2A),
    pink = Color(0xFFFF8FB8),
    orangeBg = Color(0xFF3A2615),
    orange = Color(0xFFF0A45D),
    yellow = Color(0xFFF0B232),
    teal = Color(0xFF23A559),
    grayBg = Color(0xFF383A40),
    gray = Color(0xFF9AA3B5),
    badge = Color(0xFFF23F42),
    // rgba(0,0,0,0.50)
    scrim = Color(0x80000000),
)

/**
 * Screens update spec tokens: shared menu, search, diff and history colors.
 * Dark values verbatim from the spec; light values adapted to read on near-white.
 */
@Immutable
data class SpecColors(
    val menuBg: Color,          // popup menu background
    val menuBorder: Color,      // popup menu border
    val menuDivider: Color,     // lines between menu groups
    val menuDanger: Color,      // destructive menu item
    val matchBg: Color,         // highlighted search match background
    val matchText: Color,       // highlighted search match text
    val diffAddBg: Color,       // added line background
    val diffDelBg: Color,       // removed line background
    val diffContext: Color,     // unchanged line text
    val railLine: Color,        // history timeline line
    val hashChipBg: Color,      // short commit code chip
    val hashChipText: Color,    // short commit code text
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
    bg = Color(0xFFFAFAFB),
    panel = Color(0xFFF1F2F5),
    panel2 = Color(0xFFE9EBEF),
    ln = Color(0xFFD5D8DE),
    tx = Color(0xFF1F2024),
    mu = Color(0xFF626772),
    ac = Color(0xFF5865F2),
    selection = Color(0xFFDCDFE5),
    editor = Color(0xFFFAFAFB),
    red = Color(0xFFDA373C),
    amber = Color(0xFFC98A0A),
    success = Color(0xFF248046),
    purple = Color(0xFF7B5CE0),

    errorPillBg = Color(0xFFFDE3E6),
    errorPillTx = Color(0xFFA1232F),
    warnPillBg = Color(0xFFFFF0CF),
    warnPillTx = Color(0xFF8A5A00),
    errorUnderline = Color(0xFFEF4444),
    warnUnderline = Color(0xFFE0A100),

    keyword = Color(0xFF8250DF),
    string = Color(0xFFA5461D),
    comment = Color(0xFF5E7F46),
    fn = Color(0xFF6F5A00),
    number = Color(0xFF0A7D4F),
    annotation = Color(0xFFB45309),
    type = Color(0xFF1B7C83),

    bulbBorder = Color(0xFF8A93A6),
    bulbBg = Color(0xFFF1F2F5),
    bulbIcon = Color(0xFFC98A00),

    // The text colour at 15% alpha.
    guideRule = Color(0x26101826),
    bracket1 = Color(0xFF8B5CF6),
    bracket2 = Color(0xFF0EA5A4),
    bracket3 = Color(0xFFD97706),
    bracket4 = Color(0xFF2563EB),
    bracket5 = Color(0xFFDB2777),

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

    panel3 = Color(0xFFDCDFE5),
    cd = Color(0xFFF1F2F5),
    ab = Color(0xFF5865F2),
    af = Color(0xFFFFFFFF),
    glow = Color(0x1F5865F2),

    // Code editor design spec tokens: light mockup values, with the editor kept crisp white.
    editorBg = Color(0xFFFAFAFB),
    activeLineBg = Color(0xFFE9EBEF),
    divider = Color(0xFFD5D8DE),
    text = Color(0xFF2E3036),
    lineNumber = Color(0xFF626772),
    lineNumberActive = Color(0xFF1F2024),
    indentGuide = Color(0x26202125),
    accent = Color(0xFF5865F2),
    error = Color(0xFFDA373C),
    codeKeyword = Color(0xFF8250DF),
    codeComment = Color(0xFF5E7F46),
    codeNumber = Color(0xFF0A7D4F),
    codeVariable = Color(0xFF0A5EA8),
    codeType = Color(0xFF1B7C83),
    codeFunction = Color(0xFF6F5A00),
    codeString = Color(0xFFA5461D),
    popupBg = Color(0xFFF1F2F5),
    popupBorder = Color(0xFFD5D8DE),
    popupSelected = Color(0xFFDCDFE5),
    popupText = Color(0xFF1F2024),
    popupMuted = Color(0xFF626772),
    textSelection = Color(0xFFDCDFE5),
    toolbarBg = Color(0xFFFAFAFB),
    toolbarBorder = Color(0xFFD5D8DE),
    toolbarAccent = Color(0xFF5865F2),
    bulbTileBg = Color(0xFFFFF4D6),
    bulbGlyph = Color(0xFF8A5A00),
    pillBg = Color(0xFFFDE8E8),
    pillBorder = Color(0xFFF2B8BC),
    pillText = Color(0xFFB4232A),
    gitAdded = Color(0xFF248046),
    gitModified = Color(0xFFC98A0A),
    gitDeleted = Color(0xFFDA373C),
    foldArrow = Color(0xFF8A8F99),
    brightText = Color(0xFF202125),
    hintText = Color(0xFF6B7078),
    iconTint = Color(0xFF6B7078),
    runFill = Color(0xFF248046),
    tabActiveBg = Color(0xFFFAFAFB),
    foldChipBg = Color(0xFFE9EBEF),
    scrollThumb = Color(0xFFC3C7D0),
    badgeP = Color(0xFF7C5CFC),
    badgeF = Color(0xFF248046),
    badgeM = Color(0xFFC98A0A),
    warnText = Color(0xFF8A5A00),
    surface = Color(0xFFF1F2F5),
    surfaceBorder = Color(0xFFD5D8DE),
    chipBg = Color(0xFFE9EBEF),
    fieldBg = Color(0xFFE9EBEF),
    fieldBorder = Color(0xFFD5D8DE),
    primary = Color(0xFF5865F2),
    primaryTint = Color(0xFFE8EAFF),
    link = Color(0xFF5865F2),
    textMuted = Color(0xFF626772),
    textFaint = Color(0xFF7A7F89),
    warning = Color(0xFFC98A0A),
    statusAddedBg = Color(0xFFDDF3E8),
    statusModifiedBg = Color(0xFFFBEED6),
    statusDeletedBg = Color(0xFFFDE8E8),
    fileRowActive = Color(0xFFDCDFE5),
    htmlIcon = Color(0xFFC25A2E),
    keyRowBg = Color(0xFFE9EBEF),
    keyBg = Color(0xFFE1E4E9),
    cmdButtonBg = Color(0xFFE9EBEF),
    cmdAddBg = Color(0xFFE9EBEF),
    spec = SpecColors(
        menuBg = Color(0xFFF1F2F5),
        menuBorder = Color(0xFFD5D8DE),
        menuDivider = Color(0xFFE3E5EA),
        menuDanger = Color(0xFFDA373C),
        matchBg = Color(0xFFFBE7A1),
        matchText = Color(0xFF5A4200),
        diffAddBg = Color(0xFFDDF3E4),
        diffDelBg = Color(0xFFFBE4E5),
        diffContext = Color(0xFF626772),
        railLine = Color(0xFFD5D8DE),
        hashChipBg = Color(0xFFE3E5EA),
        hashChipText = Color(0xFF4F46E5),
    ),
)

val DarkColors = PageColors(
    bg = Color(0xFF1E1F22),
    panel = Color(0xFF2B2D31),
    panel2 = Color(0xFF2B2D31),
    ln = Color(0xFF3F4147),
    tx = Color(0xFFF2F3F5),
    mu = Color(0xFF949BA4),
    ac = Color(0xFF5865F2),
    selection = Color(0xFF404249),
    editor = Color(0xFF313338),
    red = Color(0xFFF23F42),
    amber = Color(0xFFF0B232),
    success = Color(0xFF23A559),
    purple = Color(0xFFA78BFA),

    errorPillBg = Color(0xFF3A1A1F),
    errorPillTx = Color(0xFFFF8A8D),
    warnPillBg = Color(0xFF3A3115),
    warnPillTx = Color(0xFFF5C45B),
    errorUnderline = Color(0xFFEF4444),
    warnUnderline = Color(0xFFE0A100),

    keyBg = Color(0xFF383A40),

    keyword = Color(0xFFC792EA),
    string = Color(0xFFCE9178),
    comment = Color(0xFF6A9955),
    fn = Color(0xFFDCDCAA),
    number = Color(0xFFB5CEA8),
    annotation = Color(0xFFF5C26B),
    type = Color(0xFF4EC9B0),

    bulbBorder = Color(0xFF9C9CAB),
    bulbBg = Color(0xFF26272B),
    bulbIcon = Color(0xFFF5DC8A),

    // The text colour at 15% alpha.
    guideRule = Color(0x26F2F3F5),
    bracket1 = Color(0xFF9D8CFF),
    bracket2 = Color(0xFF5FD3C6),
    bracket3 = Color(0xFFF0B429),
    bracket4 = Color(0xFF7FB2FF),
    bracket5 = Color(0xFFFF8FB8),

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

    panel3 = Color(0xFF383A40),
    cd = Color(0xFF2B2D31),
    ab = Color(0xFF5865F2),
    // The dark accent is the indigo itself, so its label is white exactly as on light.
    af = Color(0xFFFFFFFF),
    glow = Color(0x1F5865F2),

    // Code editor design spec tokens, dark values from the screens handoff's grey ramp.
    editorBg = Color(0xFF313338),
    activeLineBg = Color(0xFF383A40),
    divider = Color(0xFF26272B),
    text = Color(0xFFDBDEE1),
    lineNumber = Color(0xFF6D6F78),
    lineNumberActive = Color(0xFFB5BAC1),
    indentGuide = Color(0xFF393C42),
    accent = Color(0xFF5865F2),
    error = Color(0xFFF23F42),
    codeKeyword = Color(0xFFC792EA),
    codeComment = Color(0xFF6A9955),
    codeNumber = Color(0xFFB5CEA8),
    codeVariable = Color(0xFF9CDCFE),
    codeType = Color(0xFF4EC9B0),
    codeFunction = Color(0xFFDCDCAA),
    codeString = Color(0xFFCE9178),
    popupBg = Color(0xFF2B2D31),
    popupBorder = Color(0xFF3F4147),
    popupSelected = Color(0xFF404249),
    popupText = Color(0xFFF2F3F5),
    popupMuted = Color(0xFF949BA4),
    textSelection = Color(0x33383A40),
    toolbarBg = Color(0xFF2B2D31),
    toolbarBorder = Color(0xFF3F4147),
    toolbarAccent = Color(0xFF5865F2),
    bulbTileBg = Color(0xFF2A2615),
    bulbGlyph = Color(0xFFE5C07B),
    pillBg = Color(0xFF3A1A1F),
    pillBorder = Color(0xFF5C2A31),
    pillText = Color(0xFFF2A0A0),
    gitAdded = Color(0xFF23A559),
    gitModified = Color(0xFFF0B232),
    gitDeleted = Color(0xFFF23F42),
    foldArrow = Color(0xFF9BA1A8),
    brightText = Color(0xFFF2F3F5),
    hintText = Color(0xFF6D6F78),
    iconTint = Color(0xFF949BA4),
    runFill = Color(0xFF23A559),
    tabActiveBg = Color(0xFF2B2D31),
    foldChipBg = Color(0xFF26272B),
    scrollThumb = Color(0xFF3E4046),
    badgeP = Color(0xFFA78BFA),
    badgeF = Color(0xFF23A559),
    badgeM = Color(0xFFF0B232),
    warnText = Color(0xFFF0B232),
    // App screens design spec tokens, dark values from the screens handoff's grey ramp.
    surface = Color(0xFF2B2D31),
    surfaceBorder = Color(0xFF3F4147),
    chipBg = Color(0xFF383A40),
    fieldBg = Color(0xFF383A40),
    fieldBorder = Color(0xFF3F4147),
    primary = Color(0xFF5865F2),
    primaryTint = Color(0x2E5865F2),
    link = Color(0xFF5865F2),
    textMuted = Color(0xFF949BA4),
    textFaint = Color(0xFF6D6F78),
    warning = Color(0xFFF0B232),
    statusAddedBg = Color(0xFF1F3B2B),
    statusModifiedBg = Color(0xFF3A3115),
    statusDeletedBg = Color(0xFF3A1A1F),
    fileRowActive = Color(0xFF404249),
    htmlIcon = Color(0xFFE5784A),
    keyRowBg = Color(0xFF2B2D31),
    cmdButtonBg = Color(0xFF383A40),
    cmdAddBg = Color(0xFF383A40),
    spec = SpecColors(
        menuBg = Color(0xFF2B2D31),
        menuBorder = Color(0xFF3F4147),
        menuDivider = Color(0xFF26272B),
        menuDanger = Color(0xFFF23F42),
        matchBg = Color(0xFFC9822F),
        matchText = Color(0xFF1B1D1F),
        diffAddBg = Color(0xFF1E3B2C),
        diffDelBg = Color(0xFF482226),
        diffContext = Color(0xFF949BA4),
        railLine = Color(0xFF33363A),
        hashChipBg = Color(0xFF33363A),
        hashChipText = Color(0xFF8089F5),
    ),
    mock = DarkMockColors,
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
