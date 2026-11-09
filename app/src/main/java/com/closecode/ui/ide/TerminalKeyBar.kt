package com.closecode.ui.ide

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.closecode.ui.settings.KeyValueStore
import com.closecode.ui.settings.SharedPreferencesKeyValueStore

/**
 * Commands remembered for one project.
 *
 * Stored in the app's own preferences under the project's slug, never in the project folder: a file
 * there would appear in the file drawer and in Source Control, and a log of what somebody typed at
 * their shell is not something to put in front of them unasked, let alone in a commit.
 *
 * The preference store is wrapped rather than reached for directly so this can be tested against an
 * in-memory map, because `SharedPreferences` is not on the unit-test classpath.
 */
internal class TerminalHistoryStore(
    private val kv: KeyValueStore,
    private val persist: Boolean = true,
) {

    /** Newest first, exactly as it is kept, so reading one back needs no reversing. */
    fun read(slug: String): List<String> =
        if (!persist) emptyList()
        else kv.getString(key(slug), null)
            ?.split('\n')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

    fun write(slug: String, history: List<String>) {
        if (!persist) return
        if (history.isEmpty()) {
            kv.remove(key(slug))
        } else {
            kv.putString(key(slug), history.take(TERMINAL_HISTORY_LIMIT).joinToString("\n"))
        }
    }

    private fun key(slug: String) = "terminal.history.$slug"
}

/** A store for [slug], remembered for as long as this screen is composed. */
@Composable
internal fun rememberTerminalHistory(slug: String, persist: Boolean = true): TerminalHistoryStore {
    val context = LocalContext.current
    return remember(slug, persist) {
        TerminalHistoryStore(SharedPreferencesKeyValueStore(context), persist)
    }
}