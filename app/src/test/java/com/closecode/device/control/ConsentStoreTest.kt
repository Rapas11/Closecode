package com.closecode.device.control

import com.closecode.ui.settings.InMemoryKeyValueStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P2-1: the agent asks, the user decides.
 *
 * The store is the whole consent model, so it is tested without a device: a request records an
 * ask and grants nothing, and only resolving it (the UI's job) changes a grant.
 */
class ConsentStoreTest {

    private fun store() = ConsentStore(InMemoryKeyValueStore())

    @Test
    fun aRequestGrantsNothing() {
        val store = store()
        val request = store.request("shell", null)
        assertNotNull(request)
        assertFalse(store.isGranted("shell"))
        assertEquals("shell", request!!.capability)
    }

    @Test
    fun theUserApprovingTheRequestGrantsIt() {
        val store = store()
        val request = store.request("shell", null)!!
        assertTrue(store.resolve(request, approved = true))
        assertTrue(store.isGranted("shell"))
        assertNull(store.pending())
    }

    @Test
    fun theUserDenyingTheRequestGrantsNothing() {
        val store = store()
        val request = store.request("secrets", null)!!
        assertTrue(store.resolve(request, approved = false))
        assertFalse(store.isGranted("secrets"))
        assertNull(store.pending())
    }

    @Test
    fun aScopedRequestOnlyGrantsThatPackage() {
        val store = store()
        val request = store.request("notifications", "com.example.app")!!
        store.resolve(request, approved = true)

        assertTrue(store.isScopedGranted("notifications", "com.example.app"))
        assertFalse(store.isScopedGranted("notifications", "com.other.app"))
        assertFalse(store.isGranted("notifications"))
    }

    @Test
    fun theSensitiveCapabilitiesAreNeverSelfGrantable() {
        assertTrue(ConsentStore.isSensitive("shell"))
        assertTrue(ConsentStore.isSensitive("secrets"))
        assertTrue(ConsentStore.isSensitive("screen"))
        assertTrue(ConsentStore.isSensitive("ui-automation"))
        assertFalse(ConsentStore.isSensitive("build"))
        assertFalse(ConsentStore.isSensitive("network"))
    }

    @Test
    fun everyAliasResolvesToOneCapability() {
        assertEquals("ui-automation", ConsentStore.normalize("ui"))
        assertEquals("ui-automation", ConsentStore.normalize("automation"))
        assertEquals("screen", ConsentStore.normalize("screenshot"))
        assertEquals("files", ConsentStore.normalize("storage"))
        assertEquals("shell", ConsentStore.normalize("pty"))
        assertEquals("notifications", ConsentStore.normalize("notify"))
        assertEquals("secrets", ConsentStore.normalize("vault"))
        assertNull(ConsentStore.normalize("launch-missiles"))
    }

    @Test
    fun thePendingRequestSurvivesUntilItIsAnswered() {
        val store = store()
        store.request("files", "com.example.app")
        val pending = store.pending()
        assertNotNull(pending)
        assertEquals("files", pending!!.capability)
        assertEquals("com.example.app", pending.scope)
    }
}
