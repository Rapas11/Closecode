package com.closecode.ui.onboarding

import com.closecode.ui.settings.InMemoryKeyValueStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstRunStoreTest {

    @Test
    fun aFreshInstallHasNotCompletedSetup() {
        assertFalse(FirstRunStore(InMemoryKeyValueStore()).isCompleted())
    }

    @Test
    fun finishingOrSkippingMarksTheRunComplete() {
        val kv = InMemoryKeyValueStore()
        val store = FirstRunStore(kv)

        store.complete()

        assertTrue(store.isCompleted())
        assertTrue(FirstRunStore(kv).isCompleted())
    }

    @Test
    fun theMarkerIsSeparateFromSettings() {
        val kv = InMemoryKeyValueStore()
        FirstRunStore(kv).complete()

        assertTrue(kv.getBoolean("first_run_completed", false))
        assertFalse(kv.getBoolean("open_last_project", false))
    }
}
