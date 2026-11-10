package com.closecode.tools

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OptionalToolsManifestTest {

    @Test
    fun shellPackCarriesDeployAndArchiveTools() {
        val manifest = ShellToolsManifest.parse(File("src/main/assets/shell-tools.json").readText())
        assertTrue(manifest.roots.containsAll(listOf("openssh", "rsync", "tar", "openssl", "xz-utils")))
        assertTrue(manifest.required.containsAll(
            listOf("bin/ssh", "bin/scp", "bin/rsync", "bin/tar", "bin/openssl", "bin/xz"),
        ))
    }

    @Test
    fun mediaPackHasFfmpegSqliteAndImageMagick() {
        val manifest = ShellToolsManifest.parse(File("src/main/assets/shell-media.json").readText())
        assertTrue(manifest.roots.containsAll(listOf("ffmpeg", "sqlite", "imagemagick")))
        assertTrue(manifest.required.containsAll(listOf("bin/ffmpeg", "bin/sqlite3", "bin/magick")))
    }

    @Test
    fun languagePacksHaveTheirCompilers() {
        val expected = mapOf(
            "go" to listOf("bin/go", "bin/gofmt"),
            "rust" to listOf("bin/rustc", "bin/cargo"),
            "dart" to listOf("bin/dart"),
            "php" to listOf("bin/php"),
            "ruby" to listOf("bin/ruby"),
            "deno" to listOf("bin/deno"),
            "bun" to listOf("bin/bun"),
        )
        expected.forEach { (language, binaries) ->
            val manifest = ShellToolsManifest.parse(
                File("src/main/assets/shell-$language.json").readText(),
            )
            assertTrue("$language pack", manifest.required.containsAll(binaries))
        }
    }
}
