package com.closecode.tools

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OptionalToolsManifestTest {

    @Test
    fun shellPackCarriesDeployAndArchiveTools() {
        val manifest = ShellToolsManifest.parse(File("src/main/assets/shell-tools.json").readText())
        assertTrue(manifest.roots.containsAll(listOf("openssh", "rsync", "tar")))
        assertTrue(manifest.required.containsAll(listOf("bin/ssh", "bin/scp", "bin/rsync", "bin/tar")))
    }

    @Test
    fun mediaPackHasFfmpegSqliteAndImageMagick() {
        val manifest = ShellToolsManifest.parse(File("src/main/assets/shell-media.json").readText())
        assertTrue(manifest.roots.containsAll(listOf("ffmpeg", "sqlite", "imagemagick")))
        assertTrue(manifest.required.containsAll(listOf("bin/ffmpeg", "bin/sqlite3", "bin/magick")))
    }

    @Test
    fun languagePacksHaveTheirCompilers() {
        val go = ShellToolsManifest.parse(File("src/main/assets/shell-go.json").readText())
        val rust = ShellToolsManifest.parse(File("src/main/assets/shell-rust.json").readText())
        assertTrue(go.required.containsAll(listOf("bin/go", "bin/gofmt")))
        assertTrue(rust.required.containsAll(listOf("bin/rustc", "bin/cargo")))
    }
}
