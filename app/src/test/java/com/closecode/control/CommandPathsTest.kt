package com.closecode.control

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Item 1 of the build-86 brief: a path means what the user typed.
 *
 * Every command that takes a path goes through this one place, so the next command cannot
 * get it wrong — the same mistake was in three commands at once.
 */
class CommandPathsTest {

    @Test
    fun aRelativePathBelongsToTheFolderTheCommandWasRunFrom() {
        val resolved = CommandPaths.resolve("t.apk", "/data/user/0/com.closecode/cache/work")

        assertEquals("/data/user/0/com.closecode/cache/work/t.apk", resolved.absolutePath)
    }

    @Test
    fun anAbsolutePathIsLeftAlone() {
        val resolved = CommandPaths.resolve("/tmp/some.apk", "/data/user/0/com.closecode/cache/work")

        assertEquals("/tmp/some.apk", resolved.absolutePath)
    }

    @Test
    fun aPathInsideAFolderKeepsTheFolder() {
        val resolved = CommandPaths.resolve("build/outputs/app.apk", "/data/user/0/com.closecode/work")

        assertEquals(
            "/data/user/0/com.closecode/work/build/outputs/app.apk",
            resolved.absolutePath,
        )
    }

    @Test
    fun withoutAFolderTheProcessFolderIsUsed() {
        val resolved = CommandPaths.resolve("t.apk", null)

        assertEquals(File("t.apk").absolutePath, resolved.absolutePath)
        assertTrue(resolved.isAbsolute)
    }

    @Test
    fun theVenvCommandUsesTheSameResolver() {
        assertEquals(
            CommandPaths.resolve("venvcmd", "/data/user/0/com.closecode/work"),
            VenvCommand.resolve("venvcmd", "/data/user/0/com.closecode/work"),
        )
    }

    private fun assertTrue(value: Boolean) = org.junit.Assert.assertTrue(value)
}
