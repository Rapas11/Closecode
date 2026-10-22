package com.closecode.ai

import com.closecode.ui.ide.jsString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream

class OpenCodeModelTest {

    @Test
    fun theStepsAreTheFourTheMockupShows() {
        val steps = openCodeSteps()

        assertEquals(4, steps.size)
        assertEquals(
            listOf(
                "Checking files",
                "Connecting your project",
                "Starting the server",
                "Waiting for it to answer",
            ),
            steps.map { it.label },
        )
        assertEquals(OpenCodeStepState.Working, steps[0].state)
        assertEquals(OpenCodeStepState.Pending, steps[1].state)
        assertEquals("Waiting", steps[3].note)
    }

    @Test
    fun eachStepIsDoneOnceItHasHappened() {
        val steps = openCodeSteps(
            filesChecked = true,
            projectConnected = true,
            serverStarted = true,
            answered = true,
        )

        assertTrue(steps.all { it.state == OpenCodeStepState.Done })
        assertTrue(steps.all { it.note == "Done" })
    }

    @Test
    fun aFailureIsShownOnEveryStep() {
        val steps = openCodeSteps(failed = true)

        assertTrue(steps.all { it.state == OpenCodeStepState.Failed })
    }

    @Test
    fun theCountdownSaysWhenTheNextTryComes() {
        assertEquals("Retrying in 3 s", retryCountdownText(3))
        assertEquals("Retrying in 0 s", retryCountdownText(0))
        assertEquals("Retrying in 0 s", retryCountdownText(-4))
    }

    @Test
    fun theLaunchLineIsTheCommandTheAppRuns() {
        assertEquals(
            "Launch: opencode web --hostname 127.0.0.1 --port 4098",
            OpenCodeDefaults.launchLine(),
        )
    }

    @Test
    fun theServerIsOnlyEverBoundToTheLoopback() {
        assertEquals("127.0.0.1", OpenCodeDefaults.HOST)
    }
}

class OpenCodeBinaryTest {

    @Test
    fun anArmPhoneGetsTheArmBuild() {
        assertEquals(
            "opencode-linux-arm64-musl.tar.gz",
            OpenCodeBinary.assetForAbis(listOf("arm64-v8a", "armeabi-v7a")),
        )
    }

    @Test
    fun anEmulatorGetsTheX64Build() {
        assertEquals(
            "opencode-linux-x64-musl.tar.gz",
            OpenCodeBinary.assetForAbis(listOf("x86_64", "x86")),
        )
    }

    @Test
    fun thePreferredAbiDecidesEvenWhenAnotherOneIsThere() {
        // MuMu lists arm64 because it can translate ARM — but it runs x86_64, and an arm64
        // program on it is a program that crashes. The first ABI is the phone's own.
        assertEquals(
            "opencode-linux-x64-musl.tar.gz",
            OpenCodeBinary.assetForAbis(listOf("x86_64", "arm64-v8a", "x86")),
        )
        assertEquals(
            "opencode-linux-arm64-musl.tar.gz",
            OpenCodeBinary.assetForAbis(listOf("arm64-v8a", "armeabi-v7a", "armeabi")),
        )
    }

    @Test
    fun anUnknownCpuGetsNothing() {
        assertNull(OpenCodeBinary.assetForAbis(listOf("mips", "riscv64")))
    }

    @Test
    fun theDownloadComesFromTheReleaseThatMatchesThePinnedVersion() {
        val url = OpenCodeBinary.downloadUrl("opencode-linux-arm64-musl.tar.gz")

        assertTrue(url.startsWith("https://github.com/sst/opencode/releases/download/"))
        assertTrue(url.endsWith("/opencode-linux-arm64-musl.tar.gz"))
        assertTrue(url.contains(OpenCodeDefaults.TAG))
    }
}

class OpenCodeRuntimeTest {

    @Test
    fun theCpuDecidesWhichLibrariesComeDown() {
        assertEquals("aarch64", OpenCodeRuntime.alpineArch(listOf("arm64-v8a", "armeabi-v7a")))
        assertEquals("x86_64", OpenCodeRuntime.alpineArch(listOf("x86_64", "arm64-v8a", "x86")))
        assertNull(OpenCodeRuntime.alpineArch(listOf("armeabi-v7a")))
        assertNull(OpenCodeRuntime.alpineArch(emptyList()))
    }

    @Test
    fun everyPackageIsPinnedToAHash() {
        for (arch in listOf("aarch64", "x86_64")) {
            val packages = OpenCodeRuntime.packages(arch)
            // The loader, the C++ library, libgcc and zlib: all four, or the program cannot run.
            assertEquals(4, packages.size)
            assertTrue(packages.any { it.file.startsWith("musl-") })
            assertTrue(packages.any { it.file.startsWith("libstdc++") })
            assertTrue(packages.any { it.file.startsWith("libgcc-") })
            for (pkg in packages) {
                assertTrue(pkg.file.endsWith(".apk"))
                assertEquals(
                    "${arch}/${pkg.file} has no hash pinned",
                    64,
                    pkg.sha256.length,
                )
                assertTrue(pkg.sha256.all { it in "0123456789abcdef" })
            }
        }
    }

    @Test
    fun aCpuWithoutABuildGetsNoLibraries() {
        assertTrue(OpenCodeRuntime.packages("armeabi").isEmpty())
    }

    @Test
    fun thePackagesComeFromThePinnedAlpineRelease() {
        val url = OpenCodeRuntime.url("aarch64", "libstdc++-15.2.0-r5.apk")

        assertTrue(url.startsWith("https://dl-cdn.alpinelinux.org/alpine/"))
        assertTrue(url.contains(OpenCodeRuntime.ALPINE))
        assertTrue(url.endsWith("/aarch64/libstdc%2B%2B-15.2.0-r5.apk"))
    }
}

class TarReaderTest {

    private val dir: File by lazy {
        File(
            System.getProperty("java.io.tmpdir"),
            "opencode-tar-test-${System.nanoTime()}",
        ).apply { mkdirs() }
    }

    @Test
    fun readsTheProgramOutOfTheArchive() {
        val archive = tarGz(
            "opencode" to ByteArray(4096) { (it % 251).toByte() },
            "README.md" to "read me".toByteArray(),
        )

        val first = TarReader.firstFile(ByteArrayInputStream(archive))

        assertNotNull(first)
        val (entry, bytes) = first!!
        assertEquals("opencode", entry.name)
        assertEquals(4096, bytes.size)
        assertEquals((0 % 251).toByte(), bytes[0])
    }

    @Test
    fun readsEveryFileInTheArchive() {
        val archive = tarGz(
            "opencode" to ByteArray(16),
            "LICENSE" to ByteArray(8),
        )

        val entries = TarReader.entries(ByteArrayInputStream(archive))

        assertEquals(listOf("opencode", "LICENSE"), entries.map { it.first.name })
        assertEquals(listOf(16L, 8L), entries.map { it.first.size })
    }

    @Test
    fun anEmptyArchiveHoldsNoFile() {
        val archive = tarGz()

        assertNull(TarReader.firstFile(ByteArrayInputStream(archive)))
    }

    @Test
    fun theProgramIsWrittenToDiskAndNeverHeldInMemory() {
        val archive = tarGz(
            "opencode" to ByteArray(4096) { (it % 251).toByte() },
            "README.md" to "read me".toByteArray(),
        )
        val into = File(dir, "opencode")

        val entry = TarReader.extractFirstFile(ByteArrayInputStream(archive), into)

        assertNotNull(entry)
        assertEquals("opencode", entry!!.name)
        assertEquals(4096L, entry.size)
        assertEquals(4096L, into.length())
        assertEquals((0 % 251).toByte(), into.readBytes()[0])
        // The half-written file is renamed into place, so nothing is left beside it.
        assertFalse(File(dir, "opencode.part").exists())
    }

    @Test
    fun whatComesBeforeTheProgramIsWalkedPast() {
        // A directory and then the program: everything before it is read past and dropped, or
        // an archive that begins with anything other than the program could not be unpacked.
        val archive = tarGzWith(
            FileEntry("bin", ByteArray(0), '5'),
            FileEntry("opencode", ByteArray(70_000) { (it % 251).toByte() }, '0'),
        )
        val into = File(dir, "opencode")

        val entry = TarReader.extractFirstFile(ByteArrayInputStream(archive), into)

        assertNotNull(entry)
        assertEquals("opencode", entry!!.name)
        assertEquals(70_000L, into.length())
        assertEquals((69_999 % 251).toByte(), into.readBytes()[69_999])
    }

    @Test
    fun anArchiveWithoutAProgramWritesNothing() {
        val archive = tarGzWith(FileEntry("bin", ByteArray(0), '5'))
        val into = File(dir, "opencode")

        assertNull(TarReader.extractFirstFile(ByteArrayInputStream(archive), into))
        assertFalse(into.exists())
    }

    @Test
    fun aPackageIsUnpackedWithItsLinkNames() {
        // An Alpine package: a directory, the library, and the short name everything links to.
        val archive = tarGzWith(
            FileEntry("usr", ByteArray(0), '5'),
            FileEntry("usr/lib", ByteArray(0), '5'),
            FileEntry("usr/lib/libstdc++.so.6.0.34", "the real library".toByteArray(), '0'),
            FileEntry("usr/lib/libstdc++.so.6", ByteArray(0), '2', link = "libstdc++.so.6.0.34"),
        )
        val into = File(dir, "pkg")

        val names = TarReader.extractInto(ByteArrayInputStream(archive), into)

        assertTrue(File(into, "usr/lib/libstdc++.so.6.0.34").isFile)
        // The short name is there too, as a copy — a phone's file system need not allow links,
        // and the loader looks for that exact name.
        val short = File(into, "usr/lib/libstdc++.so.6")
        assertTrue(short.isFile)
        assertEquals("the real library", short.readText())
        assertEquals(listOf("usr/lib/libstdc++.so.6.0.34", "usr/lib/libstdc++.so.6"), names)
        // Nothing half-written is left behind beside what was unpacked.
        assertEquals(
            emptyList<String>(),
            (into.list() ?: emptyArray()).filter { it.endsWith(".part") },
        )
    }

    @Test
    fun aNameTryingToGetOutOfTheDirectoryIsRefused() {
        val archive = tarGzWith(
            FileEntry("../escape.so", "gone".toByteArray(), '0'),
            FileEntry("lib/libz.so.1", "here".toByteArray(), '0'),
        )
        val into = File(dir, "inside")

        val names = TarReader.extractInto(ByteArrayInputStream(archive), into)

        assertEquals(listOf("lib/libz.so.1"), names)
        assertFalse(File(dir, "escape.so").exists())
        assertEquals("here", File(into, "lib/libz.so.1").readText())
    }

    /** A tiny tar writer, so the reader is tested against the format rather than against itself. */
    private fun tarGz(vararg files: Pair<String, ByteArray>): ByteArray =
        tarGzWith(*files.map { FileEntry(it.first, it.second, '0') }.toTypedArray())

    private fun tarGzWith(vararg files: FileEntry): ByteArray {
        val out = ByteArrayOutputStream()
        for ((name, bytes, type, link) in files) {
            val header = ByteArray(512)
            val nameBytes = name.toByteArray()
            System.arraycopy(nameBytes, 0, header, 0, nameBytes.size)
            writeOctal(header, 124, 12, bytes.size.toLong())
            header[156] = type.code.toByte()
            // A symlink carries its target in the header, where the file's bytes would be.
            if (link.isNotEmpty()) {
                val target = link.toByteArray()
                System.arraycopy(target, 0, header, 157, minOf(target.size, 100))
            }
            System.arraycopy("ustar".toByteArray(), 0, header, 257, 5)
            out.write(header)
            out.write(bytes)
            val padding = (512 - (bytes.size % 512)) % 512
            out.write(ByteArray(padding))
        }
        // Two empty blocks end the archive.
        out.write(ByteArray(1024))
        val gzipped = ByteArrayOutputStream()
        GZIPOutputStream(gzipped).use { it.write(out.toByteArray()) }
        return gzipped.toByteArray()
    }

    private data class FileEntry(
        val name: String,
        val bytes: ByteArray,
        val type: Char,
        val link: String = "",
    )

    private fun writeOctal(header: ByteArray, offset: Int, length: Int, value: Long) {
        val end = 0.toChar()
        val text = value.toString(8).padStart(length - 1, '0') + end
        System.arraycopy(text.toByteArray(), 0, header, offset, length)
    }
}

class JsStringTest {

    @Test
    fun aPasswordWithAQuoteCannotBreakTheScript() {
        val quoted = jsString("pa\"ss\\word")

        assertEquals("\"pa\\\"ss\\\\word\"", quoted)
    }
}
