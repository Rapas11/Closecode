package com.closecode.preview

import androidx.compose.runtime.Composable

/**
 * The one link between CloseCode and a generated preview harness.
 *
 * The harness is compiled into a preview APK together with the project's own classes, then
 * loaded in CloseCode's `:preview` process. Both sides name this interface, so the copy in
 * CloseCode is the one the harness implements at runtime; the generated APK carries an
 * identical declaration only so its compiler can see it.
 *
 * Content draws the project's own composable. It is never called outside a ComposeView, and
 * it is the only method a harness needs, which keeps the bridge small.
 */
interface PreviewHost {
    @Composable
    fun Content()
}
