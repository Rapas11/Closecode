package com.closecode.ui.ide

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Icons drawn from the 24 x 24 SVG paths in the HTML prototype, so the app's glyphs are the same
 * shapes as the reference instead of whatever codepoints the bundled fonts happen to carry. The
 * default stroke is 2 dp with round caps and joins, exactly as the prototype's `svg.i` class.
 */
private fun icon(name: String, path: String, filled: Boolean = false): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(path),
        fill = if (filled) SolidColor(Color.Black) else null,
        stroke = if (filled) null else SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ).build()

object CcIcons {
    val Logo = icon("logo", "M8 8l-4 4 4 4M16 8l4 4-4 4")
    val Save = icon("save", "M5 4h11l3 3v13H5zM8 4v5h7V4M8 20v-6h8v6")
    val Undo = icon("undo", "M9 14L4 9l5-5M4 9h10a6 6 0 0 1 0 12h-3")
    val Redo = icon("redo", "M15 14l5-5-5-5M20 9H10a6 6 0 0 0 0 12h3")
    // Run: an outline, not a solid wedge. The accent fill behind it is already the strongest
    // thing on the bar, and a filled triangle at 18 dp just turned into a blob. The corners are
    // radiused on the path itself — a round line join can only soften a vertex by the stroke's
    // own width, which at 2 dp is nowhere near the curve this needs.
    val Play = icon(
        "play",
        "M5.5 8.83A2.5 2.5 0 0 1 9.25 6.67L14.75 9.84A2.5 2.5 0 0 1 14.75 14.17" +
            "L9.25 17.34A2.5 2.5 0 0 1 5.5 15.17Z",
    )
    val More = icon(
        "more",
        "M10.5 5a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0-3 0zM10.5 12a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0-3 0z" +
            "M10.5 19a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0-3 0z",
        filled = true,
    )
    val Chevron = icon("chevron", "M9 6l6 6-6 6")
    val Up = icon("up", "M6 15l6-6 6 6")
    val Down = icon("down", "M6 9l6 6 6-6")
    val Copy = icon("copy", "M11 9h7a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2h-7a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2zM5 15V6a2 2 0 0 1 2-2h8")
    val Warning = icon("warning", "M12 3l10 18H2zM12 10v5M12 18h.01")
    val ErrorCircle = icon("error", "M3 12a9 9 0 1 0 18 0a9 9 0 1 0-18 0zM9 9l6 6M15 9l-6 6")
    val Stack = icon("stack", "M12 3l9 5-9 5-9-5zM3 13l9 5 9-5")
    val Bulb = icon("bulb", "M9 18h6M10 21h4M12 3a6 6 0 0 0-3.5 10.9c.6.5 1 1.2 1 2.1h5c0-.9.4-1.6 1-2.1A6 6 0 0 0 12 3z")
    val Close = icon("close", "M6 6l12 12M18 6L6 18")

    // Not in the prototype: the search sheets and the command palette still need a magnifier, even
    // though the button that used to open them from the header is gone.
    val Search = icon("search", "M11 4a7 7 0 1 0 0 14a7 7 0 0 0 0-14zM16 16l4 4")

    // The terminal, in the slot the magnifier used to hold: a `>_` inside a window, because a
    // `>_` on its own reads as text rather than as a button.
    val Terminal = icon("terminal", "M4 5h16v14H4zM8 9.5l2.5 2.5-2.5 2.5M13 14.5h3")

    // Path bar: back to the code, into the block editor, and Preview for anything renderable.
    val Code = icon("code", "M9 7l-5 5 5 5M15 7l5 5-5 5")
    val Layers = icon("layers", "M12 3l9 5-9 5-9-5zM3 13l9 5 9-5M3 17l9 5 9-5")
    val Image = icon("image", "M4 5h16v14H4zM4 16l4-4 3 3 4-4 5 5")

    // Explorer: the two actions that used to live only in the overflow menu.
    val FilePlus = icon(
        "file-plus",
        "M14 3H6a1 1 0 0 0-1 1v16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V8zM14 3v5h5M12 11v6M9 14h6",
    )
    val FolderPlus = icon(
        "folder-plus",
        "M3 6a1 1 0 0 1 1-1h5l2 2h8a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1zM12 10v6M9 13h6",
    )

    // The drawer's two row glyphs. Every file is the same document and every folder the same open
    // folder: a badge per extension answered a question nobody was asking — which file, not which
    // language — and the row's colour already says the language.
    val Document = icon(
        "document",
        "M14 3v4a1 1 0 0 0 1 1h4M17 21H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h7l5 5v11a2 2 0 0 1-2 2" +
            "M9 9h1M8 13h6M8 17h6",
    )
    val FolderOpen = icon(
        "folder-open",
        "M5 19l2.757-7.351a1 1 0 0 1 .938-.649h12.294a1 1 0 0 1 .986 1.164l-.996 5.211" +
            "a2 2 0 0 1-1.98 1.625H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h4l3 3h7a2 2 0 0 1 2 2v2",
    )

    // Preview toolbar: re-read the file, and take a live page out to the device's browser.
    val Refresh = icon(
        "refresh",
        "M23 4v6h-6M1 20v-6h6M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15",
    )
    val External = icon(
        "external",
        "M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6M15 3h6v6M10 14L21 3",
    )
}

@Composable
fun CcIcon(icon: ImageVector, tint: Color, size: Dp = 18.dp, modifier: Modifier = Modifier) {
    Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = modifier.size(size))
}