package com.closecode.ui.github

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The storage policy the user asked for: the Keystore or nothing.
 *
 * These run against fake files because the real ones are Android's — the point pinned here is
 * what the store does when the encrypted side is missing, which is exactly the state a broken
 * Keystore leaves a phone in. The old code fell back to plain text there; it must not.
 */
class TokenStoreTest {

    /** In-memory stand-in for one preferences file, counting what happened to it. */
    private class FakeFile(var token: String? = null) : TokenFile {
        var writes = 0
        var clears = 0

        override fun read(): String? = token

        override fun write(token: String) {
            this.token = token
            writes++
        }

        override fun clear() {
            token = null
            clears++
        }
    }

    @Test
    fun aBrokenStoreSignsOutAndWipesTheOldPlainToken() {
        val legacy = FakeFile("plain-token")
        val store = TokenStore(secure = null, legacy = legacy)

        assertNull("no secure store means signed out", store.read())
        assertNull("the plain copy is wiped, not adopted", legacy.token)
    }

    @Test
    fun aBrokenStoreNeverWritesPlainText() {
        val legacy = FakeFile()
        val store = TokenStore(secure = null, legacy = legacy)

        assertFalse(store.write("fresh-token"))
        assertNull("nothing lands in the clear", legacy.token)
        assertEquals(0, legacy.writes)
    }

    @Test
    fun aWorkingStoreKeepsTheTokenEncryptedOnly() {
        val secure = FakeFile()
        val legacy = FakeFile()
        val store = TokenStore(secure = secure, legacy = legacy)

        assertTrue(store.write("fresh-token"))
        assertEquals("fresh-token", secure.token)
        assertNull(legacy.token)
        assertEquals("fresh-token", store.read())
    }

    @Test
    fun anOldPlainTokenIsAdoptedOnceThenWiped() {
        val secure = FakeFile()
        val legacy = FakeFile("old-token")
        val store = TokenStore(secure = secure, legacy = legacy)

        assertEquals("old-token", store.read())
        assertEquals("the encrypted store now holds it", "old-token", secure.token)
        assertNull("the plain copy is gone", legacy.token)
    }

    @Test
    fun clearingTakesTheTokenOutOfBothFiles() {
        val secure = FakeFile("fresh-token")
        val legacy = FakeFile("old-token")
        val store = TokenStore(secure = secure, legacy = legacy)

        store.clear()
        assertNull(secure.token)
        assertNull(legacy.token)
        assertNull(store.read())
    }
}
