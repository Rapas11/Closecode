package com.closecode.ui

import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path

/**
 * CloseCode design-system icons.
 *
 * Every icon here is a line icon on a 24 by 24 grid drawn as Compose ImageVector
 * with the stroke widths, round caps and round joins from the design spec.
 * Play is the only filled icon. Colour is a parameter, not baked in.
 */
object CcIcons {

    // -- Files & folders ---------------------------------------------------------
    val Folder: ImageVector = ImageVector.Builder(
        name = "folder", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Unspecified),
            strokeLineWidth = 1.8f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        ) {
            moveTo(4f, 20f)
            horizontalLineToRelative(16f)
            arcToRelative(2f, 2f, 0f, false, false, 2f, -2f)
            verticalLineTo(8f)
            arcToRelative(2f, 2f, 0f, false, false, -2f, -2f)
            horizontalLineToRelative(-7.9f)
            arcToRelative(2f, 2f, 0f, false, true, -1.69f, -0.9f)
            lineTo(9.6f, 3.9f)
            arcToRelative(2f, 2f, 0f, false, false, -1.67f, -0.9f)
            horizontalLineTo(4f)
            arcToRelative(2f, 2f, 0f, false, false, -2f, 2f)
            verticalLineToRelative(13f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            close()
        }
    }.build()

    val Refresh: ImageVector = ImageVector.Builder(
        name = "refresh", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Unspecified),
            strokeLineWidth = 1.8f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        ) {
            moveTo(3f, 12f)
            arcToRelative(9f, 9f, 0f, false, true, 9f, -9f)
            arcToRelative(9.75f, 9.75f, 0f, false, true, 6.74f, 2.74f)
            lineTo(21f, 8f)
            moveTo(21f, 3f)
            verticalLineToRelative(5f)
            horizontalLineToRelative(-5f)
            moveTo(21f, 12f)
            arcToRelative(9f, 9f, 0f, false, true, -9f, 9f)
            arcToRelative(9.75f, 9.75f, 0f, false, true, -6.7f, -4f)
            lineTo(3f, 16f)
            moveTo(8f, 16f)
            lineTo(4f, 16f)
            verticalLineToRelative(4f)
        }
    }.build()

    val Branch: ImageVector = ImageVector.Builder(
        name = "branch", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Unspecified),
            strokeLineWidth = 1.8f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        ) {
            moveTo(6f, 3f); verticalLineToRelative(12f)
            moveTo(18f, 9f); arcToRelative(3f, 3f, 0f, true, false, 0f, -6f); arcToRelative(3f, 3f, 0f, true, false, 0f, 6f)
            moveTo(6f, 21f); arcToRelative(3f, 3f, 0f, true, false, 0f, -6f); arcToRelative(3f, 3f, 0f, true, false, 0f, 6f)
            moveTo(18f, 9f); arcToRelative(9f, 9f, 0f, false, true, -9f, 9f)
        }
    }.build()

    val History: ImageVector = ImageVector.Builder(
        name = "history", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Unspecified),
            strokeLineWidth = 1.8f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        ) {
            moveTo(3f, 12f)
            arcToRelative(9f, 9f, 0f, true, false, 3f, -6.7f)
            lineTo(3f, 8f)
            moveTo(3f, 3f)
            verticalLineToRelative(5f)
            horizontalLineToRelative(5f)
            moveTo(12f, 7f)
            verticalLineToRelative(5f)
            lineTo(16f, 14f)
        }
    }.build()

    val Down: ImageVector = ImageVector.Builder(
        name = "down", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Unspecified),
            strokeLineWidth = 1.8f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        ) {
            moveTo(21f, 15f); verticalLineToRelative(4f); arcToRelative(2f, 2f, 0f, false, true, -2f, 2f); horizontalLineTo(5f); arcToRelative(2f, 2f, 0f, false, true, -2f, -2f); verticalLineToRelative(-4f)
            moveTo(7f, 10f); lineToRelative(5f, 5f); lineToRelative(5f, -5f)
            moveTo(12f, 15f); verticalLineTo(3f)
        }
    }.build()

    val Up: ImageVector = ImageVector.Builder(
        name = "up", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Unspecified),
            strokeLineWidth = 1.8f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        ) {
            moveTo(21f, 15f); verticalLineToRelative(4f); arcToRelative(2f, 2f, 0f, false, true, -2f, 2f); horizontalLineTo(5f); arcToRelative(2f, 2f, 0f, false, true, -2f, -2f); verticalLineToRelative(-4f)
            moveTo(17f, 8f); lineToRelative(-5f, -5f); lineToRelative(-5f, 5f)
            moveTo(12f, 3f); verticalLineToRelative(12f)
        }
    }.build()

    val More: ImageVector = ImageVector.Builder(
        name = "more", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Unspecified),
            strokeLineWidth = 1.8f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        ) {
            moveTo(12f, 5f); verticalLineToRelative(0.01f)
            moveTo(12f, 12f); verticalLineToRelative(0.01f)
            moveTo(12f, 19f); verticalLineToRelative(0.01f)
        }
    }.build()

    val Plus: ImageVector = ImageVector.Builder(
        name = "plus", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Unspecified),
            strokeLineWidth = 1.8f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        ) {
            moveTo(12f, 5f); verticalLineToRelative(14f)
            moveTo(5f, 12f); horizontalLineToRelative(14f)
        }
    }.build()

    val Check: ImageVector = ImageVector.Builder(
        name = "check", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Unspecified),
            strokeLineWidth = 2.4f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        ) {
            moveTo(20f, 6f); lineTo(9f, 17f); lineToRelative(-5f, -5f)
        }
    }.build()

    val ChevronDown: ImageVector = ImageVector.Builder(
        name = "cl", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Unspecified),
            strokeLineWidth = 2.2f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        ) {
            moveTo(6f, 9f); lineToRelative(6f, 6f); lineToRelative(6f, -6f)
        }
    }.build()

    val ChevronUp: ImageVector = ImageVector.Builder(
        name = "cr", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Unspecified),
            strokeLineWidth = 2.2f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        ) {
            moveTo(9f, 18f); lineToRelative(6f, -6f); lineToRelative(-6f, -6f)
        }
    }.build()

    val ChevronRight: ImageVector = ImageVector.Builder(
        name = "cd", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Unspecified),
            strokeLineWidth = 2.2f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        ) {
            moveTo(9f, 18f); lineToRelative(6f, -6f); lineToRelative(-6f, -6f)
        }
    }.build()

    val Search: ImageVector = ImageVector.Builder(
        name = "search", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Unspecified),
            strokeLineWidth = 1.8f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        ) {
            moveTo(11f, 19f)
            arcToRelative(8f, 8f, 0f, true, false, 0f, -16f)
            arcToRelative(8f, 8f, 0f, true, false, 0f, 16f)
            moveTo(21f, 21f); lineToRelative(-4.3f, -4.3f)
        }
    }.build()

    val Filter: ImageVector = ImageVector.Builder(
        name = "filter", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Unspecified),
            strokeLineWidth = 1.8f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        ) {
            moveTo(3f, 6f); horizontalLineToRelative(18f)
            moveTo(7f, 12f); horizontalLineToRelative(10f)
            moveTo(10f, 18f); horizontalLineToRelative(4f)
        }
    }.build()

    val Copy: ImageVector = ImageVector.Builder(
        name = "copy", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Unspecified),
            strokeLineWidth = 1.8f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        ) {
            moveTo(9f, 9f); horizontalLineToRelative(11f); verticalLineToRelative(11f); horizontalLineTo(9f); close()
            moveTo(5f, 15f); horizontalLineTo(4f); verticalLineTo(4f); horizontalLineToRelative(11f); verticalLineToRelative(1f)
        }
    }.build()

    val File: ImageVector = ImageVector.Builder(
        name = "file", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Unspecified),
            strokeLineWidth = 1.8f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        ) {
            moveTo(14f, 2f); horizontalLineTo(6f); arcToRelative(2f, 2f, 0f, false, false, -2f, 2f); verticalLineToRelative(16f); arcToRelative(2f, 2f, 0f, false, false, 2f, 2f); horizontalLineToRelative(12f); arcToRelative(2f, 2f, 0f, false, false, 2f, -2f); verticalLineTo(8f); close()
            moveTo(14f, 2f); verticalLineToRelative(6f); horizontalLineToRelative(6f)
        }
    }.build()

    val Undo: ImageVector = ImageVector.Builder(
        name = "undo", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Unspecified),
            strokeLineWidth = 1.8f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        ) {
            moveTo(9f, 14f); lineTo(4f, 9f); lineToRelative(5f, -5f)
            moveTo(4f, 9f); horizontalLineToRelative(10f); arcToRelative(6f, 6f, 0f, false, true, 0f, 12f); horizontalLineToRelative(-3f)
        }
    }.build()

    val Redo: ImageVector = ImageVector.Builder(
        name = "redo", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Unspecified),
            strokeLineWidth = 1.8f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        ) {
            moveTo(15f, 14f); lineToRelative(5f, -5f); lineToRelative(-5f, -5f)
            moveTo(20f, 9f); horizontalLineTo(10f); arcToRelative(6f, 6f, 0f, false, false, 0f, 12f); horizontalLineToRelative(3f)
        }
    }.build()

    val Save: ImageVector = ImageVector.Builder(
        name = "save", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Unspecified),
            strokeLineWidth = 1.8f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        ) {
            moveTo(19f, 21f); horizontalLineTo(5f); arcToRelative(2f, 2f, 0f, false, true, -2f, -2f); verticalLineTo(5f); arcToRelative(2f, 2f, 0f, false, true, 2f, -2f); horizontalLineToRelative(11f); lineToRelative(5f, 5f); verticalLineToRelative(11f); arcToRelative(2f, 2f, 0f, false, true, -2f, 2f); close()
            moveTo(17f, 21f); verticalLineToRelative(-8f); horizontalLineTo(7f); verticalLineToRelative(8f)
            moveTo(7f, 3f); verticalLineToRelative(5f); horizontalLineToRelative(8f)
        }
    }.build()

    val Term: ImageVector = ImageVector.Builder(
        name = "term", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Unspecified),
            strokeLineWidth = 1.8f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        ) {
            moveTo(4f, 17f); lineToRelative(6f, -6f); lineToRelative(-6f, -6f)
            moveTo(12f, 19f); horizontalLineToRelative(8f)
        }
    }.build()

    val Menu: ImageVector = ImageVector.Builder(
        name = "menu", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Unspecified),
            strokeLineWidth = 1.8f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        ) {
            moveTo(4f, 6f); horizontalLineToRelative(16f)
            moveTo(4f, 12f); horizontalLineToRelative(16f)
            moveTo(4f, 18f); horizontalLineToRelative(16f)
        }
    }.build()

    val Close: ImageVector = ImageVector.Builder(
        name = "x", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Unspecified),
            strokeLineWidth = 1.8f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        ) {
            moveTo(18f, 6f); lineTo(6f, 18f)
            moveTo(6f, 6f); lineToRelative(12f, 12f)
        }
    }.build()

    val Bolt: ImageVector = ImageVector.Builder(
        name = "bolt", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Unspecified),
            strokeLineWidth = 1.8f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        ) {
            moveTo(13f, 2f); lineTo(3f, 14f); horizontalLineToRelative(9f); lineToRelative(-1f, 8f); lineToRelative(10f, -12f); horizontalLineToRelative(-9f); close()
        }
    }.build()

    val Keyboard: ImageVector = ImageVector.Builder(
        name = "kbd", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Unspecified),
            strokeLineWidth = 1.8f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        ) {
            moveTo(4f, 5f); horizontalLineToRelative(16f); arcToRelative(2f, 2f, 0f, false, true, 2f, 2f); verticalLineToRelative(10f); arcToRelative(2f, 2f, 0f, false, true, -2f, 2f); horizontalLineTo(4f); arcToRelative(2f, 2f, 0f, false, true, -2f, -2f); verticalLineTo(7f); arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
            moveTo(6f, 9f); horizontalLineToRelative(0.01f)
            moveTo(10f, 9f); horizontalLineToRelative(0.01f)
            moveTo(14f, 9f); horizontalLineToRelative(0.01f)
            moveTo(18f, 9f); horizontalLineToRelative(0.01f)
            moveTo(7f, 15f); horizontalLineToRelative(10f)
        }
    }.build()

    val CodeSymbol: ImageVector = ImageVector.Builder(
        name = "code", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Unspecified),
            strokeLineWidth = 1.8f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        ) {
            moveTo(16f, 18f); lineToRelative(6f, -6f); lineToRelative(-6f, -6f)
            moveTo(8f, 6f); lineToRelative(-6f, 6f); lineToRelative(6f, 6f)
        }
    }.build()

    val Layers: ImageVector = ImageVector.Builder(
        name = "layers", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Unspecified),
            strokeLineWidth = 1.8f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        ) {
            moveTo(12f, 2f); lineToRelative(10f, 5f); lineTo(12f, 12f); lineTo(2f, 7f); close()
            moveTo(2f, 17f); lineToRelative(10f, 5f); lineToRelative(10f, -5f)
            moveTo(2f, 12f); lineToRelative(10f, 5f); lineToRelative(10f, -5f)
        }
    }.build()

    val Image: ImageVector = ImageVector.Builder(
        name = "image", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Unspecified),
            strokeLineWidth = 1.8f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        ) {
            moveTo(5f, 3f); horizontalLineToRelative(14f); arcToRelative(2f, 2f, 0f, false, true, 2f, 2f); verticalLineToRelative(14f); arcToRelative(2f, 2f, 0f, false, true, -2f, 2f); horizontalLineTo(5f); arcToRelative(2f, 2f, 0f, false, true, -2f, -2f); verticalLineTo(5f); arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
            moveTo(9f, 8f); arcToRelative(1f, 1f, 0f, true, false, 0.001f, 0.001f)
            moveTo(21f, 15f); lineToRelative(-5f, -5f); lineTo(5f, 21f)
        }
    }.build()

    val Trash: ImageVector = ImageVector.Builder(
        name = "trash", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Unspecified),
            strokeLineWidth = 1.8f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        ) {
            moveTo(3f, 6f); horizontalLineToRelative(18f)
            moveTo(8f, 6f); verticalLineTo(4f); horizontalLineToRelative(8f); verticalLineToRelative(2f)
            moveTo(19f, 6f); lineToRelative(-1f, 14f); horizontalLineTo(6f); lineTo(5f, 6f)
        }
    }.build()

    val Share: ImageVector = ImageVector.Builder(
        name = "share", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Unspecified),
            strokeLineWidth = 1.8f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        ) {
            moveTo(4f, 12f); verticalLineToRelative(8f); horizontalLineToRelative(16f); verticalLineToRelative(-8f)
            moveTo(16f, 6f); lineTo(12f, 2f); lineTo(8f, 6f)
            moveTo(12f, 2f); verticalLineToRelative(13f)
        }
    }.build()

    val Bug: ImageVector = ImageVector.Builder(
        name = "bug", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Unspecified),
            strokeLineWidth = 1.8f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        ) {
            moveTo(8f, 2f); lineToRelative(1.9f, 1.9f)
            moveTo(14.1f, 3.9f); lineTo(16f, 2f)
            moveTo(9f, 7.1f); verticalLineTo(6f); arcToRelative(3f, 3f, 0f, false, true, 6f, 0f); verticalLineToRelative(1.1f)
            moveTo(12f, 20f); arcToRelative(6f, 6f, 0f, false, true, -6f, -6f); verticalLineToRelative(-3f); arcToRelative(4f, 4f, 0f, false, true, 4f, -4f); horizontalLineToRelative(4f); arcToRelative(4f, 4f, 0f, false, true, 4f, 4f); verticalLineToRelative(3f); arcToRelative(6f, 6f, 0f, false, true, -6f, 6f); close()
            moveTo(12f, 20f); verticalLineToRelative(-9f)
            moveTo(6.5f, 13f); horizontalLineTo(2f)
            moveTo(17.5f, 13f); horizontalLineTo(22f)
        }
    }.build()

    val Flask: ImageVector = ImageVector.Builder(
        name = "flask", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Unspecified),
            strokeLineWidth = 1.8f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        ) {
            moveTo(10f, 2f); verticalLineToRelative(7.5f); lineTo(4f, 20f); arcToRelative(1f, 1f, 0f, false, false, 0.9f, 1.5f); horizontalLineToRelative(14.2f); arcToRelative(1f, 1f, 0f, false, false, 0.9f, -1.5f); lineTo(14f, 9.5f); verticalLineTo(2f)
            moveTo(8.5f, 2f); horizontalLineToRelative(7f)
        }
    }.build()

    val Alert: ImageVector = ImageVector.Builder(
        name = "alert", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Unspecified),
            strokeLineWidth = 1.8f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        ) {
            moveTo(12f, 9f); verticalLineToRelative(4f)
            moveTo(12f, 17f); horizontalLineToRelative(0.01f)
            moveTo(10.3f, 3.9f); lineTo(1.8f, 18f); arcToRelative(2f, 2f, 0f, false, false, 1.7f, 3f); horizontalLineToRelative(17f); arcToRelative(2f, 2f, 0f, false, false, 1.7f, -3f); lineTo(13.7f, 3.9f); arcToRelative(2f, 2f, 0f, false, false, -3.4f, 0f)
        }
    }.build()

    val GitHub: ImageVector = ImageVector.Builder(
        name = "gh", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Unspecified),
            strokeLineWidth = 1.8f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        ) {
            // Octicons "mark-github" path (as per the spec's gh icon).
            moveTo(15f, 22f)
            verticalLineToRelative(-4f)
            arcToRelative(4.8f, 4.8f, 0f, false, false, -1f, -3.5f)
            curveToRelative(3f, 0f, 6f, -2f, 6f, -5.5f)
            curveToRelative(0.08f, -1.25f, -0.27f, -2.48f, -1f, -3.5f)
            curveToRelative(0.28f, -1.15f, 0.28f, -2.35f, 0f, -3.5f)
            lineToRelative(0f, 0f)
            lineToRelative(0f, -3.5f)
            curveToRelative(-2.64f, -0.5f, -5.36f, -0.5f, -8f, 0f)
            curveToRelative(-2.64f, 0.5f, -4f, 2f, -4f, 2f)
            curveToRelative(-0.3f, 1.15f, -0.3f, 2.35f, 0f, 3.5f)
            arcToRelative(5.5f, 5.5f, 0f, false, false, -1f, 3.5f)
            curveToRelative(0f, 3.5f, 3f, 5.5f, 6f, 5.5f)
            curveToRelative(-0.39f, 0.49f, -0.68f, 1.05f, -0.85f, 1.65f)
            curveToRelative(-0.17f, 0.6f, -0.22f, 1.23f, -0.15f, 1.85f)
            verticalLineToRelative(4f)
            moveTo(9f, 18f)
            curveToRelative(-4.51f, 2f, -5f, -2f, -7f, -2f)
        }
    }.build()

    val Send: ImageVector = ImageVector.Builder(
        name = "send", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Unspecified),
            strokeLineWidth = 1.8f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        ) {
            moveTo(22f, 2f); lineTo(11f, 13f)
            moveTo(22f, 2f); lineTo(15f, 22f); lineTo(11f, 13f); lineTo(2f, 9f); close()
        }
    }.build()

    val Cog: ImageVector = ImageVector.Builder(
        name = "cog", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Unspecified),
            strokeLineWidth = 1.8f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        ) {
            moveTo(12f, 15f); arcToRelative(3f, 3f, 0f, true, false, 0f, -6f); arcToRelative(3f, 3f, 0f, true, false, 0f, 6f)
            moveTo(19.4f, 15f); arcToRelative(1.65f, 1.65f, 0f, false, false, 0.33f, 1.82f); lineToRelative(0.06f, 0.06f); arcToRelative(2f, 2f, 0f, true, false, -2.83f, 2.83f); lineToRelative(-0.06f, -0.06f); arcToRelative(1.65f, 1.65f, 0f, false, false, -1.82f, -0.33f); arcToRelative(1.65f, 1.65f, 0f, false, false, -1f, 1.51f); verticalLineTo(21f); arcToRelative(2f, 2f, 0f, true, false, -4f, 0f); verticalLineToRelative(-0.09f); arcToRelative(1.65f, 1.65f, 0f, false, false, -1f, -1.51f); arcToRelative(1.65f, 1.65f, 0f, false, false, -1.82f, 0.33f); lineToRelative(-0.06f, 0.06f); arcToRelative(2f, 2f, 0f, true, false, -2.83f, -2.83f); lineToRelative(0.06f, -0.06f); arcToRelative(1.65f, 1.65f, 0f, false, false, 0.33f, -1.82f); arcToRelative(1.65f, 1.65f, 0f, false, false, -1.51f, -1f); horizontalLineTo(3f); arcToRelative(2f, 2f, 0f, true, false, 0f, -4f); horizontalLineToRelative(0.09f); arcToRelative(1.65f, 1.65f, 0f, false, false, 1.51f, -1f); arcToRelative(1.65f, 1.65f, 0f, false, false, -0.33f, -1.82f); lineToRelative(-0.06f, -0.06f); arcToRelative(2f, 2f, 0f, true, false, 2.83f, -2.83f); lineToRelative(0.06f, 0.06f); arcToRelative(1.65f, 1.65f, 0f, false, false, 1.82f, 0.33f); arcToRelative(1.65f, 1.65f, 0f, false, false, 1f, -1.51f); verticalLineTo(3f); arcToRelative(2f, 2f, 0f, true, false, 4f, 0f); verticalLineToRelative(0.09f); arcToRelative(1.65f, 1.65f, 0f, false, false, 1f, 1.51f); arcToRelative(1.65f, 1.65f, 0f, false, false, 1.82f, -0.33f); lineToRelative(0.06f, -0.06f); arcToRelative(2f, 2f, 0f, true, false, 2.83f, 2.83f); lineToRelative(-0.06f, 0.06f); arcToRelative(1.65f, 1.65f, 0f, false, false, -0.33f, 1.82f); arcToRelative(1.65f, 1.65f, 0f, false, false, 1.51f, 1f); horizontalLineTo(21f); arcToRelative(2f, 2f, 0f, true, false, 0f, 4f); horizontalLineToRelative(-0.09f); arcToRelative(1.65f, 1.65f, 0f, false, false, -1.51f, 1f)
        }
    }.build()

    val ExternalLink: ImageVector = ImageVector.Builder(
        name = "ext", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Unspecified),
            strokeLineWidth = 1.8f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        ) {
            moveTo(18f, 13f); verticalLineToRelative(6f); arcToRelative(2f, 2f, 0f, false, true, -2f, 2f); horizontalLineTo(5f); arcToRelative(2f, 2f, 0f, false, true, -2f, -2f); verticalLineTo(8f); arcToRelative(2f, 2f, 0f, false, true, 2f, -2f); horizontalLineToRelative(6f)
            moveTo(15f, 3f); horizontalLineToRelative(6f); verticalLineToRelative(6f)
            moveTo(10f, 14f); lineTo(21f, 3f)
        }
    }.build()

    val Play: ImageVector = ImageVector.Builder(
        name = "play", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(
            fill = SolidColor(Color.Unspecified),
        ) {
            moveTo(7f, 4.5f)
            verticalLineToRelative(15f)
            lineToRelative(13f, -7.5f)
            close()
        }
    }.build()

    val User: ImageVector = ImageVector.Builder(
        name = "user", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Unspecified),
            strokeLineWidth = 1.8f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        ) {
            moveTo(20f, 21f); verticalLineToRelative(-2f); arcToRelative(4f, 4f, 0f, false, false, -4f, -4f); horizontalLineTo(8f); arcToRelative(4f, 4f, 0f, false, false, -4f, 4f); verticalLineToRelative(2f)
            moveTo(12f, 11f); arcToRelative(4f, 4f, 0f, true, false, 0f, -8f); arcToRelative(4f, 4f, 0f, true, false, 0f, 8f)
        }
    }.build()

    val Panel: ImageVector = ImageVector.Builder(
        name = "panel", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Unspecified),
            strokeLineWidth = 1.8f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        ) {
            moveTo(5f, 3f); horizontalLineToRelative(14f); arcToRelative(2f, 2f, 0f, false, true, 2f, 2f); verticalLineToRelative(14f); arcToRelative(2f, 2f, 0f, false, true, -2f, 2f); horizontalLineTo(5f); arcToRelative(2f, 2f, 0f, false, true, -2f, -2f); verticalLineTo(5f); arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
            moveTo(9f, 3f); verticalLineToRelative(18f)
        }
    }.build()

    val Globe: ImageVector = ImageVector.Builder(
        name = "globe", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Unspecified),
            strokeLineWidth = 1.8f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        ) {
            moveTo(12f, 22f); arcToRelative(10f, 10f, 0f, true, false, 0f, -20f); arcToRelative(10f, 10f, 0f, true, false, 0f, 20f)
            moveTo(2f, 12f); horizontalLineToRelative(20f)
            moveTo(12f, 2f); arcToRelative(15f, 15f, 0f, false, true, 0f, 20f); arcToRelative(15f, 15f, 0f, false, true, 0f, -20f)
        }
    }.build()

    val Flag: ImageVector = ImageVector.Builder(
        name = "flag", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Unspecified),
            strokeLineWidth = 1.8f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        ) {
            moveTo(4f, 22f); verticalLineTo(4f)
            moveTo(4f, 4f); horizontalLineToRelative(13f); lineToRelative(-2f, 4f); lineToRelative(2f, 4f); horizontalLineTo(4f)
        }
    }.build()

    val Moon: ImageVector = ImageVector.Builder(
        name = "moon", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Unspecified),
            strokeLineWidth = 1.8f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        ) {
            moveTo(21f, 12.8f); arcToRelative(9f, 9f, 0f, true, false, -9.8f, -9.8f); arcToRelative(7f, 7f, 0f, false, false, 9.8f, 9.8f)
        }
    }.build()

    val Sun: ImageVector = ImageVector.Builder(
        name = "sun2", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Unspecified),
            strokeLineWidth = 1.8f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        ) {
            moveTo(12f, 17f); arcToRelative(5f, 5f, 0f, true, false, 0f, -10f); arcToRelative(5f, 5f, 0f, true, false, 0f, 10f)
            moveTo(12f, 1f); verticalLineToRelative(2f)
            moveTo(12f, 21f); verticalLineToRelative(2f)
            moveTo(4.2f, 4.2f); lineToRelative(1.4f, 1.4f)
            moveTo(18.4f, 18.4f); lineToRelative(1.4f, 1.4f)
            moveTo(1f, 12f); horizontalLineToRelative(2f)
            moveTo(21f, 12f); horizontalLineToRelative(2f)
            moveTo(4.2f, 19.8f); lineToRelative(1.4f, -1.4f)
            moveTo(18.4f, 5.6f); lineToRelative(1.4f, -1.4f)
        }
    }.build()

    val Lock: ImageVector = ImageVector.Builder(
        name = "lock", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Unspecified),
            strokeLineWidth = 1.8f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        ) {
            moveTo(5f, 11f); horizontalLineToRelative(14f); verticalLineToRelative(10f); horizontalLineTo(5f); verticalLineTo(11f)
            moveTo(8f, 11f); verticalLineTo(7f); arcToRelative(4f, 4f, 0f, false, true, 8f, 0f); verticalLineToRelative(4f)
        }
    }.build()

    val Info: ImageVector = ImageVector.Builder(
        name = "info", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Unspecified),
            strokeLineWidth = 1.8f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        ) {
            moveTo(12f, 22f); arcToRelative(10f, 10f, 0f, true, false, 0f, -20f); arcToRelative(10f, 10f, 0f, true, false, 0f, 20f)
            moveTo(12f, 16f); verticalLineToRelative(-4f)
            moveTo(12f, 8f); horizontalLineToRelative(0.01f)
        }
    }.build()

    val Db: ImageVector = ImageVector.Builder(
        name = "db", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Unspecified),
            strokeLineWidth = 1.8f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        ) {
            moveTo(12f, 8f); curveToRelative(4.4f, 0f, 8f, -1.3f, 8f, -3f); reflectiveCurveToRelative(-3.6f, -3f, -8f, -3f); reflectiveCurveToRelative(-8f, 1.3f, -8f, 3f); reflectiveCurveToRelative(3.6f, 3f, 8f, 3f)
            moveTo(4f, 5f); verticalLineToRelative(14f); curveToRelative(0f, 1.7f, 3.6f, 3f, 8f, 3f); reflectiveCurveToRelative(8f, -1.3f, 8f, -3f); verticalLineTo(5f)
            moveTo(4f, 12f); curveToRelative(0f, 1.7f, 0f, 3f, 8f, 3f); reflectiveCurveToRelative(8f, -1.3f, 8f, -3f)
        }
    }.build()
}
