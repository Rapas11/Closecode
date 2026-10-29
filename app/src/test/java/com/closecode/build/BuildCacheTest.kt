package com.closecode.build

import java.io.File
import java.security.KeyPairGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BuildCacheTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val config = BuildConfigValues(
        packageName = "com.example.app",
        minSdk = 28,
        targetSdk = 35,
        versionCode = 1,
        versionName = "1.0",
        useCompose = true,
    )

    @Test
    fun theSameInputsProduceTheSameKey() {
        val cache = BuildCache(tmp.newFolder("cache"))
        val project = project()
        val tools = tools()

        assertEquals(
            cache.key(project, config, tools),
            cache.key(project, config, tools),
        )
    }

    @Test
    fun changingAProjectFileChangesTheKey() {
        val cache = BuildCache(tmp.newFolder("cache"))
        val project = project()
        val tools = tools()
        val before = cache.key(project, config, tools)

        File(project, "app/src/main/java/Main.kt").writeText("class Main { val changed = true }\n")

        assertNotEquals(before, cache.key(project, config, tools))
    }

    @Test
    fun changingALibraryChangesTheKey() {
        val cache = BuildCache(tmp.newFolder("cache"))
        val project = project()
        val tools = tools()
        val before = cache.key(project, config, tools)

        tools.libraries.libraries.single().jar!!.writeText("a longer library jar")

        assertNotEquals(before, cache.key(project, config, tools))
    }

    @Test
    fun aStoredApkIsFoundAndCleared() {
        val cache = BuildCache(tmp.newFolder("cache"))
        val key = "abc123"
        val apk = File(tmp.root, "app-debug.apk").apply { writeText("signed apk") }

        assertNull(cache.lookup("my-project", key))
        cache.store("my-project", key, apk)

        val hit = cache.lookup("my-project", key)
        assertNotNull(hit)
        assertEquals("signed apk", hit!!.readText())

        cache.clear("my-project")
        assertNull(cache.lookup("my-project", key))
    }

    private fun project(): File {
        val dir = tmp.newFolder("project")
        File(dir, "app/src/main/java").mkdirs()
        File(dir, "app/src/main/java/Main.kt").writeText("class Main\n")
        File(dir, "settings.gradle.kts").writeText("rootProject.name = \"app\"\n")
        return dir
    }

    private fun tools(): BuildTools {
        val root = tmp.newFolder("tools")
        val aapt2 = File(root, "aapt2").apply { writeText("aapt2") }
        val androidJar = File(root, "android.jar").apply { writeText("android") }
        val stdlib = File(root, "kotlin-stdlib.jar").apply { writeText("stdlib") }
        val libraryJar = File(root, "library.jar").apply { writeText("library") }
        val libraries = LibraryIndex(
            root = root,
            libraries = listOf(
                CachedLibrary(
                    module = "androidx.core:core-ktx",
                    version = "1.13.1",
                    jar = libraryJar,
                    res = null,
                    manifest = null,
                ),
            ),
        )
        val key = KeyPairGenerator.getInstance("RSA").apply { initialize(1024) }
            .generateKeyPair().private
        return BuildTools(
            aapt2 = aapt2,
            androidJar = androidJar,
            stdlib = stdlib,
            libraries = libraries,
            signer = SigningIdentity(key, emptyList()),
            toolFiles = emptyMap(),
        )
    }
}
