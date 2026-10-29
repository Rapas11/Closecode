package com.closecode.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.closecode.ui.settings.KeyValueStore
import com.closecode.ui.settings.SharedPreferencesKeyValueStore

/**
 * Whether the first-run setup has been finished or skipped. Kept apart from [Settings] because
 * the settings screen promises that every field it shows is a preference the app honours; this
 * is a one-way launch marker, not a knob.
 */
class FirstRunStore(private val kv: KeyValueStore) {

    fun isCompleted(): Boolean = kv.getBoolean(KEY_COMPLETED, false)

    fun complete() {
        kv.putBoolean(KEY_COMPLETED, true)
    }

    private companion object {
        const val KEY_COMPLETED = "first_run_completed"
    }
}

@Composable
fun rememberFirstRunStore(): FirstRunStore {
    val context = LocalContext.current
    return remember(context) {
        FirstRunStore(SharedPreferencesKeyValueStore(context))
    }
}
