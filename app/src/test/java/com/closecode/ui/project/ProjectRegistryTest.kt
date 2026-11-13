package com.closecode.ui.project

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProjectRegistryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun registry(): ProjectRegistry =
        ProjectRegistry(File(tmp.root, "project-registry.json"))

    @Test
    fun committedNamesAndPackagesStayReserved() {
        val registry = registry()
        val id = ProjectIdentity.newId()
        registry.begin(id, "notes", "Notes", "com.closecode.notes")
        registry.commit(id)

        assertTrue(registry.nameTaken("notes"))
        assertTrue(registry.nameTaken("  NOTES  "))
        assertTrue(registry.packageTaken("COM.CLOSECODE.NOTES"))
        assertTrue(registry.slugTaken("notes"))
        assertFalse(registry.nameTaken("notes", exceptId = id))
    }

    @Test
    fun anotherProjectCannotTakeTheSameNameOrPackage() {
        val registry = registry()
        val first = ProjectIdentity.newId()
        registry.begin(first, "notes", "Notes", "com.closecode.notes")
        registry.commit(first)

        val nameError = runCatching {
            registry.begin(ProjectIdentity.newId(), "notes-2", "notes", "com.closecode.other")
        }.exceptionOrNull()
        assertTrue(nameError is ProjectConflictException)

        val packageError = runCatching {
            registry.begin(ProjectIdentity.newId(), "other", "Other", "com.closecode.notes")
        }.exceptionOrNull()
        assertTrue(packageError is ProjectConflictException)
        // The message names the project that holds the name, so the user can act on it.
        assertEquals(
            "That package name is already used by \"Notes\". Pick a unique one.",
            packageError?.message,
        )
    }

    @Test
    fun renameKeepsTheOldNameReserved() {
        val registry = registry()
        val id = ProjectIdentity.newId()
        registry.begin(id, "notes", "Notes", "com.closecode.notes")
        registry.commit(id)
        registry.rename(id, "Journal")

        assertEquals("Journal", registry.entry(id)?.name)
        assertTrue(registry.nameTaken("Notes"))
        assertTrue(registry.nameTaken("journal"))
        assertFalse(registry.nameTaken("Journal", exceptId = id))
    }

    @Test
    fun deletingKeepsTheIdentityReserved() {
        val registry = registry()
        val id = ProjectIdentity.newId()
        registry.begin(id, "notes", "Notes", "com.closecode.notes")
        registry.commit(id)
        registry.markDeleted(id)

        assertTrue(registry.entry(id)?.deleted == true)
        assertTrue(registry.nameTaken("Notes"))
        assertTrue(registry.packageTaken("com.closecode.notes"))
        assertTrue(registry.reservedPackages().contains("com.closecode.notes"))
    }

    @Test
    fun migrationCanRecordDuplicateLegacyNames() {
        val registry = registry()
        val first = ProjectIdentity.legacyId("notes")
        val second = ProjectIdentity.legacyId("notes-2")
        registry.ensure(first, "notes", "Notes", "com.closecode.notes")
        registry.ensure(second, "notes-2", "Notes", "com.closecode.notes")

        assertEquals(2, registry.entries().size)
        assertTrue(registry.nameTaken("notes"))
        assertTrue(registry.packageTaken("com.closecode.notes"))
    }

    @Test
    fun pendingCreationCanBeCancelledWithoutLeavingAReservation() {
        val registry = registry()
        val id = ProjectIdentity.newId()
        registry.begin(id, "notes", "Notes", "com.closecode.notes")
        registry.cancel(id)

        assertNull(registry.entry(id))
        assertFalse(registry.nameTaken("Notes"))
        assertFalse(registry.packageTaken("com.closecode.notes"))
    }

    @Test
    fun pendingCreationsFromAnEarlierProcessAreDiscarded() {
        val registry = registry()
        val stale = ProjectIdentity.newId()
        val current = ProjectIdentity.newId()
        registry.begin(stale, "stale", "Stale", "com.closecode.stale", now = 1_000)
        registry.begin(current, "current", "Current", "com.closecode.current", now = 3_000)

        registry.discardPendingBefore(2_000)

        assertNull(registry.entry(stale))
        assertTrue(registry.entry(current)?.pending == true)
        assertFalse(registry.nameTaken("Stale"))
        assertTrue(registry.nameTaken("Current"))
    }

    @Test
    fun corruptRegistryIsQuarantinedAndCanBeRebuilt() {
        val file = File(tmp.root, "project-registry.json")
        file.writeText("{not json")
        val registry = ProjectRegistry(file)
        assertTrue(registry.entries().isEmpty())
        assertTrue(File(tmp.root, "project-registry.json.corrupt").isFile)

        val id = ProjectIdentity.newId()
        registry.ensure(id, "notes", "Notes", "com.closecode.notes")
        assertEquals(1, registry.entries().size)
    }

    @Test
    fun legacyIdIsStableAndDifferentForDifferentSlugs() {
        assertEquals(ProjectIdentity.legacyId("notes"), ProjectIdentity.legacyId("notes"))
        assertFalse(ProjectIdentity.legacyId("notes") == ProjectIdentity.legacyId("journal"))
    }
}
