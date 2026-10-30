package com.closecode.ui.project

import java.text.Normalizer
import java.util.Locale
import java.util.UUID

/**
 * A project's identity is its UUID, not its display name or folder. A rename changes the
 * label the reader sees; it must never move the folder, the build cache, the terminal
 * history or the OpenCode binding.
 */
object ProjectIdentity {

    fun newId(): String = UUID.randomUUID().toString()

    /**
     * Projects created before IDs were stored still need one that is stable across launches.
     * The slug is the only permanent fact those records have, so it is hashed into a name UUID.
     */
    fun legacyId(slug: String): String =
        UUID.nameUUIDFromBytes("closecode:$slug".toByteArray(Charsets.UTF_8)).toString()

    /**
     * Names are reserved case-insensitively and with normalised whitespace: "My  Notes" and
     * "my notes" are the same reservation.
     */
    fun nameKey(value: String): String =
        Normalizer.normalize(value.trim(), Normalizer.Form.NFKC)
            .replace(Regex("\\s+"), " ")
            .lowercase(Locale.ROOT)

    fun packageKey(value: String): String = value.trim().lowercase(Locale.ROOT)
}
