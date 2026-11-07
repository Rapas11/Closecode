package com.closecode.ui.ide

import com.closecode.ui.model.FileKind
import org.junit.Assert.assertEquals
import org.junit.Test

class FileCreationTest {

    @Test
    fun nothingSelectedLandsAtTheProjectRoot() {
        assertEquals("", newEntryParent(null, null))
        assertEquals("", newEntryParent("", null))
    }

    @Test
    fun aSelectedFolderOwnsTheNewEntry() {
        assertEquals("app/src", newEntryParent("app/src", FileKind.Folder))
    }

    @Test
    fun aSelectedFileLandsBesideItself() {
        assertEquals("app/src", newEntryParent("app/src/Main.kt", FileKind.Kotlin))
    }

    @Test
    fun aRootFileLandsAtTheRoot() {
        assertEquals("", newEntryParent("README.md", FileKind.Markdown))
    }

    @Test
    fun theParentIsFrozenWhenTheDialogOpens() {
        val parent = newEntryParent("feature/auth/Login.kt", FileKind.Kotlin)
        assertEquals("feature/auth", parent)
        assertEquals(parent, newEntryParent("feature/auth/Login.kt", FileKind.Kotlin))
    }
}
