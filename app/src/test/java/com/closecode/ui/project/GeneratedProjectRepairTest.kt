package com.closecode.ui.project

import com.closecode.ui.model.ProjectConfig
import com.closecode.ui.model.SelectedTemplate
import com.closecode.ui.model.TemplateTab
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Bug 1 of the build-90 brief: one template, two front ends.
 *
 * The "New project" screen and `closecode init` must write the same files from the same
 * source, and a project the app generated before the fix must be repaired — otherwise every
 * project created in the app fails its first build.
 */
class GeneratedProjectRepairTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** The file the app used to write, before the import was added. */
    private fun oldTemplate(): String = """
        package com.closecode.buildprobe

        import android.os.Bundle
        import androidx.activity.ComponentActivity
        import androidx.activity.compose.setContent

        class MainActivity : ComponentActivity() {
            override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)
                setContent {
                    ClosecodeTheme {
                        Text("Hello, world")
                    }
                }
            }
        }
    """.trimIndent() + "\n"

    private fun projectWith(file: String): File {
        val dir = tmp.newFolder("project")
        val activity = File(dir, "app/src/main/java/com/closecode/buildprobe/MainActivity.kt")
        activity.parentFile?.mkdirs()
        activity.writeText(file)
        return dir
    }

    @Test
    fun theShippedTemplateImportsWhatItUses() {
        val files = Scaffold.filesFor(
            "freshapp",
            SelectedTemplate(TemplateTab.App, 0),
            ProjectConfig(packageName = "com.closecode.freshapp"),
        )
        val activity = files.first { it.path.endsWith("MainActivity.kt") }

        assertTrue(
            "the template uses Text but never imports it",
            activity.content.contains("import androidx.compose.material3.Text"),
        )
    }

    @Test
    fun anOldGeneratedProjectGetsTheImportItIsMissing() {
        val dir = projectWith(oldTemplate())

        val changed = GeneratedProjectRepair.repair(dir, imported = false)

        assertEquals(1, changed)
        val text = File(
            dir,
            "app/src/main/java/com/closecode/buildprobe/MainActivity.kt",
        ).readText()
        assertTrue(text.contains("import androidx.compose.material3.Text"))
        // The rest of the file is untouched.
        assertTrue(text.contains("Text(\"Hello, world\")"))
        assertTrue(text.contains("class MainActivity : ComponentActivity()"))
    }

    @Test
    fun aProjectThatIsAlreadyRightIsNotTouched() {
        val dir = projectWith(
            oldTemplate().replace(
                "import androidx.activity.compose.setContent",
                "import androidx.activity.compose.setContent\nimport androidx.compose.material3.Text",
            ),
        )

        assertEquals(0, GeneratedProjectRepair.repair(dir, imported = false))
    }

    @Test
    fun aProjectTheUserBroughtInIsNeverTouched() {
        val dir = projectWith(oldTemplate())
        val before = File(
            dir,
            "app/src/main/java/com/closecode/buildprobe/MainActivity.kt",
        ).readText()

        assertEquals(0, GeneratedProjectRepair.repair(dir, imported = true))
        assertEquals(
            before,
            File(dir, "app/src/main/java/com/closecode/buildprobe/MainActivity.kt").readText(),
        )
    }

    @Test
    fun aFileThatDoesNotUseTextIsLeftAlone() {
        val dir = projectWith(
            """
            package com.closecode.buildprobe

            class Plain {
                fun value(): Int = 1
            }
            """.trimIndent() + "\n",
        )

        assertEquals(0, GeneratedProjectRepair.repair(dir, imported = false))
        assertFalse(
            File(dir, "app/src/main/java/com/closecode/buildprobe/MainActivity.kt")
                .readText()
                .contains("material3"),
        )
    }
}
