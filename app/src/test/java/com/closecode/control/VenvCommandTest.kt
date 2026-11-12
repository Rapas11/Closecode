package com.closecode.control

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Item 2 of the build-85 brief: `closecode venv venvcmd` must create the environment in
 * the folder the user is standing in, not in the root of the filesystem.
 */
class VenvCommandTest {

    @Test
    fun aRelativePathBelongsToTheFolderTheCommandWasRunFrom() {
        val resolved = VenvCommand.resolve("venvcmd", "/data/user/0/com.closecode/cache/work")

        assertEquals("/data/user/0/com.closecode/cache/work/venvcmd", resolved.absolutePath)
    }

    @Test
    fun anAbsolutePathIsLeftAlone() {
        val resolved = VenvCommand.resolve("/tmp/myenv", "/data/user/0/com.closecode/cache/work")

        assertEquals("/tmp/myenv", resolved.absolutePath)
    }

    @Test
    fun aFolderNameIsResolvedInsideTheCurrentFolder() {
        val resolved = VenvCommand.resolve("envs/one", "/data/user/0/com.closecode/cache/work")

        assertEquals("/data/user/0/com.closecode/cache/work/envs/one", resolved.absolutePath)
    }
}
