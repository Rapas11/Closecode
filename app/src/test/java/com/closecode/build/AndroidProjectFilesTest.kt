package com.closecode.build

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AndroidProjectFilesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun aPcProjectGetsThePhoneWrapperAndSdkPath() {
        val project = tmp.newFolder("app")
        File(project, "app/build.gradle.kts").apply {
            parentFile?.mkdirs()
            writeText("plugins { id(\"com.android.application\") }\n")
        }
        File(project, "gradlew").writeText(
            "#!/usr/bin/env sh\n# org.gradle.wrapper.GradleWrapperMain\n",
        )

        val result = AndroidProjectFiles.prepare(project, File("/files/sdk"))

        val wrapper = File(project, "gradlew")
        assertTrue(result.changed.contains("gradlew"))
        assertTrue(result.changed.contains("local.properties"))
        assertTrue(wrapper.readText().startsWith("#!/system/bin/sh\n"))
        assertFalse(wrapper.readText().contains("/usr/bin/env"))
        assertTrue(wrapper.readText().contains("command -v gradle"))
        assertTrue(wrapper.readText().contains("GRADLE_USER_HOME"))
        assertTrue(wrapper.readText().contains("JAVA_HOME"))
        assertTrue(wrapper.readText().contains("\"\$GRADLE_BIN\" \"\$@\""))
        // The shim does not exec Gradle: it signs what Gradle built and then reports Gradle's
        // own exit code, so a failed build is still a failed build.
        assertTrue(wrapper.readText().contains("exit \"\$GRADLE_CODE\""))
        assertTrue(wrapper.canExecute())
        assertEquals("sdk.dir=/files/sdk", File(project, "local.properties").readText().trim())
    }

    @Test
    fun anExistingSdkPathIsUpdatedWithoutLosingOtherProperties() {
        val project = tmp.newFolder("app")
        File(project, "app/build.gradle.kts").apply {
            parentFile?.mkdirs()
            writeText("android {}\n")
        }
        File(project, "local.properties").writeText("sdk.dir=/old\nndk.dir=/ndk\n")

        AndroidProjectFiles.prepare(project, File("/new/sdk"))

        val text = File(project, "local.properties").readText()
        assertTrue("sdk.dir=/new/sdk" in text)
        assertTrue("ndk.dir=/ndk" in text)
        assertFalse("sdk.dir=/old" in text)
    }

    @Test
    fun anOldCloseCodeShimIsUpdatedWithTheAapt2AndJdkExports() {
        val project = tmp.newFolder("app")
        File(project, "app/build.gradle.kts").apply {
            parentFile?.mkdirs()
            writeText("android {}\n")
        }
        File(project, "gradlew").writeText(
            "#!/system/bin/sh\n" +
                "GRADLE_BIN=\"\$CLOSECODE_GRADLE\"\n" +
                "echo \"Gradle is not installed. Open Settings > Build and Toolchain > Full Gradle.\" >&2\n",
        )

        AndroidProjectFiles.prepare(project, File("/files/sdk"))

        val text = File(project, "gradlew").readText()
        assertEquals(AndroidProjectFiles.GRADLEW, text)
        assertTrue(text.contains("android.aapt2FromMavenOverride"))
        assertTrue(text.contains("org.gradle.java.home"))
        assertTrue(text.contains("CloseCode shim v2"))
    }

    @Test
    fun theShimSignsWhatGradleBuiltAndFollowsTheSelectedJdk() {
        val project = tmp.newFolder("app")
        File(project, "app/build.gradle.kts").apply {
            parentFile?.mkdirs()
            writeText("android {}\n")
        }

        AndroidProjectFiles.prepare(project, File("/files/sdk"))

        val text = File(project, "gradlew").readText()
        // The selected JDK wins over an inherited JAVA_HOME, so the launcher and the daemon
        // always run on the same Java.
        assertTrue(text.contains("org.gradle.java.home"))
        assertFalse(text.contains("if [ -z \"\$JAVA_HOME\" ] && [ -f \"\$PROPS\" ]"))
        // Gradle signs with the stock AOSP key; the shim asks the app to sign it properly.
        // Every variant is signed, so one key signs every build path.
        assertTrue(text.contains("sign-apk"))
        assertTrue(text.contains("build/outputs/apk/*/*.apk"))
        assertFalse(
            "the shim must not leave an APK alone just because it carries a signature",
            text.contains("--if-unsigned"),
        )
    }

    @Test
    fun aCustomWrapperIsNotOverwritten() {
        val project = tmp.newFolder("app")
        File(project, "app/build.gradle.kts").apply {
            parentFile?.mkdirs()
            writeText("android {}\n")
        }
        File(project, "gradlew").writeText("#!/system/bin/sh\necho custom\n")

        AndroidProjectFiles.prepare(project, File("/files/sdk"))

        assertEquals("#!/system/bin/sh\necho custom\n", File(project, "gradlew").readText())
    }

    @Test
    fun aWebProjectIsLeftAlone() {
        val project = tmp.newFolder("web")
        File(project, "index.html").writeText("<html></html>")

        val result = AndroidProjectFiles.prepare(project, File("/files/sdk"))

        assertTrue(result.changed.isEmpty())
        assertFalse(File(project, "local.properties").exists())
        assertFalse(File(project, "gradlew").exists())
    }
}
