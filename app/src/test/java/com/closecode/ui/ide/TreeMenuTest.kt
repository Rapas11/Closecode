package com.closecode.ui.ide

import com.closecode.ui.model.FileKind
import com.closecode.ui.model.ProjectFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The drawer's long-press menu: what a row offers, and what an import calls the file it just
 * brought in. Both are decisions a user sees on every single gesture, so neither is left to
 * whatever order the list happened to be built in.
 */
class TreeMenuTest {

    private fun folder(path: String) = ProjectFile(
        name = path.substringAfterLast('/'),
        kind = FileKind.Folder,
        language = "",
        depth = path.count { it == '/' },
        expandable = true,
        path = path,
    )

    private fun file(path: String) = ProjectFile(
        name = path.substringAfterLast('/'),
        kind = FileKind.Kotlin,
        language = "Kotlin",
        depth = path.count { it == '/' },
        path = path,
    )

    @Test
    fun aFolderOffersWhereNewThingsGoAndAFileOffersTheFileItself() {
        val folderMenu = actionsForTree(folder("app/src"))
        val fileMenu = actionsForTree(file("app/src/Main.kt"))

        assertEquals(
            listOf(
                TreeAction.NEW_FILE,
                TreeAction.NEW_FOLDER,
                TreeAction.RENAME,
                TreeAction.MOVE,
                TreeAction.COPY,
                TreeAction.IMPORT,
                TreeAction.DELETE,
            ),
            folderMenu,
        )
        assertEquals(
            listOf(
                TreeAction.RENAME,
                TreeAction.MOVE,
                TreeAction.COPY,
                TreeAction.SHARE,
                TreeAction.DELETE,
            ),
            fileMenu,
        )
    }

    @Test
    fun foldersAreNeverHandedToAnotherAppAndFilesAlwaysCan() {
        // A content URI for a directory has no receiver, and the app's projects live in private
        // storage, so the share sheet is only ever offered something it can actually take.
        assertFalse(TreeAction.SHARE in actionsForTree(folder("app")))
        assertTrue(TreeAction.SHARE in actionsForTree(file("app/README.md")))
    }

    @Test
    fun everyActionHasALabelSomebodyCanRead() {
        TreeAction.entries.forEach { action ->
            assertTrue("$action has no label", action.label.isNotEmpty())
            assertEquals("$action label has stray space", action.label, action.label.trim())
        }
    }

    @Test
    fun anImportKeepsANameThatIsAlreadyTaken() {
        val used = setOf("app/Main.kt", "app/Main2.kt", "app/Main3.kt")

        assertEquals(
            "Main4.kt",
            uniqueLeafIn("app", "Main.kt") { it in used },
        )
    }

    @Test
    fun anImportLandsOnItsNameWhenNothingIsInTheWay() {
        assertEquals(
            "Main.kt",
            uniqueLeafIn("app", "Main.kt") { false },
        )
        assertEquals(
            "LICENSE",
            uniqueLeafIn("", "LICENSE") { false },
        )
    }

    @Test
    fun anImportOfANameWithNoExtensionStillGetsAUniqueOne() {
        assertEquals(
            "LICENSE2",
            uniqueLeafIn("", "LICENSE") { it == "LICENSE" },
        )
    }

    @Test
    fun anImportNeverTreatsADotfileAsAnExtension() {
        // `.env` split at the last dot would give an empty base and produce ".env2" as "" + "2" +
        // ".env". The leading dot is part of the name, not a separator.
        assertEquals(
            ".env2",
            uniqueLeafIn("", ".env") { it == ".env" },
        )
    }

    @Test
    fun aFolderWhereTheFileIsGoingDecidesThePathThatIsChecked() {
        // The answer is the leaf, because the caller joins it back onto the folder itself.
        assertEquals(
            "Main2.kt",
            uniqueLeafIn("src", "Main.kt") { it == "src/Main.kt" },
        )
        assertEquals(
            "Main.kt",
            uniqueLeafIn("src", "Main.kt") { it == "other/Main.kt" },
        )
    }
}

/**
 * Tapping a folder in the drawer opens it when it is shut and shuts it when it is open.
 *
 * Both branches used to add and remove on the wrong side, so a tap never changed the set: a folder
 * opened only when a file was created inside it, because that path takes ancestors out of the set
 * on purpose — which is what made it look like folders opened on their own.
 */
class FolderToggleTest {

    @Test
    fun tappingAnOpenFolderShutsIt() {
        assertEquals(setOf("app"), toggleCollapsed(emptySet(), "app"))
    }

    @Test
    fun tappingAClosedFolderOpensIt() {
        assertEquals(emptySet<String>(), toggleCollapsed(setOf("app"), "app"))
    }

    @Test
    fun togglingTwiceBringsBackTheSameSet() {
        val open = setOf("app", "app/src")
        assertEquals(open, toggleCollapsed(toggleCollapsed(open, "app"), "app"))
    }

    @Test
    fun onlyTheFolderThatWasTappedIsRecorded() {
        // The drawer hides a subtree by depth, so the collapsed set never has to carry the
        // children as well — and adding them would make reopening a folder leave its own
        // children shut.
        val collapsed = toggleCollapsed(emptySet(), "app")
        assertEquals(setOf("app"), collapsed)
        assertFalse("app/src" in collapsed)
    }

    @Test
    fun theOtherFoldersAreLeftAlone() {
        val collapsed = setOf("app", "docs")
        assertEquals(setOf("app", "docs", "app/src"), toggleCollapsed(collapsed, "app/src"))
        assertEquals(setOf("docs"), toggleCollapsed(collapsed, "app"))
        assertEquals(setOf("app"), toggleCollapsed(collapsed, "docs"))
    }
}
