package com.closecode.ai

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
    fun theCpuMessageNamesWhatThePhoneReported() {
        val message = unsupportedCpuMessage(listOf("armeabi-v7a", "armeabi"))

        assertTrue(message.contains("armeabi-v7a, armeabi"))
        assertTrue(message.contains("arm64-v8a"))
        assertTrue(message.contains("x86_64"))
    }

    @Test
    fun aHealthyServerReportsItsVersion() {
        assertEquals("1.18.35", healthyVersion("""{"healthy":true,"version":"1.18.35"}"""))
        assertEquals("", healthyVersion("""{"healthy":true}"""))
        assertNull(healthyVersion("""{"healthy":false,"version":"1.18.35"}"""))
        assertNull(healthyVersion("not json"))
    }

    @Test
    fun thePinnedVersionMatchesTheReleaseTag() {
        assertEquals("1.18.35", OpenCodeDefaults.VERSION)
        assertEquals("v1.18.35", OpenCodeDefaults.TAG)
    }

    @Test
    fun theOrphanMatcherOnlyFindsTheWebServer() {
        assertTrue(
            isOpenCodeServerCommandLine(
                "/data/opencode\u0000web\u0000--hostname\u0000127.0.0.1\u0000--port\u00004098",
            ),
        )
        assertFalse(isOpenCodeServerCommandLine("/data/opencode\u0000auth\u0000login"))
        assertTrue(isOpenCodeServerCommandLine("/system/bin/sh\u0000-c\u0000opencode web"))
        assertFalse(isOpenCodeServerCommandLine("/system/bin/sh\u0000-c\u0000echo opencode"))
    }

    @Test
    fun aListeningRowNamesTheInodeOfTheFixedPort() {
        val rows = listOf(
            "   0: 0100007F:1002 00000000:0000 0A 00000000:00000000 00:00000000 " +
                "00000000     0        0 48556 1 ffff000000000000 100 0 0 10 0",
            "   1: 0100007F:1F90 00000000:0000 0A 00000000:00000000 00:00000000 " +
                "00000000     0        0 48557 1 ffff000000000000 100 0 0 10 0",
        )

        assertEquals(setOf("48556"), procNetListenInodes(rows, 4098))
        assertEquals(setOf("48557"), procNetListenInodes(rows, 8080))
    }

    @Test
    fun rowsThatDoNotListenOnThePortCarryNoInode() {
        val rows = listOf(
            "  sl  local_address rem_address st tx_queue rx_queue tr tm->when " +
                "retrnsmt uid timeout inode",
            // The same port, but established rather than listening.
            "   0: 0100007F:1002 0100007F:1F90 01 00000000:00000000 00:00000000 " +
                "00000000     0        0 77 1 0 100 0 0 10 0",
            // A listening row on a different port.
            "   1: 0100007F:1F90 00000000:0000 0A 00000000:00000000 00:00000000 " +
                "00000000     0        0 78 1 0 100 0 0 10 0",
            // A row that is too short to hold an inode.
            "   2:",
        )

        assertTrue(procNetListenInodes(rows, 4098).isEmpty())
    }

    @Test
    fun anIpv6ListeningRowIsReadTheSameWay() {
        val rows = listOf(
            "   0: 00000000000000000000000000000000:1002 " +
                "00000000000000000000000000000000:0000 0A 00000000:00000000 00:00000000 " +
                "00000000     0        0 603 1 0 100 0 0 10 0",
        )

        assertEquals(setOf("603"), procNetListenInodes(rows, 4098))
    }

    @Test
    fun theCountdownSaysWhenTheNextTryComes() {
        assertEquals("Retrying in 3 s", retryCountdownText(3))
        assertEquals("Retrying in 0 s", retryCountdownText(0))
        assertEquals("Retrying in 0 s", retryCountdownText(-4))
    }

    @Test
    fun aSlowBootstrapIsNotTreatedAsADeadServer() {
        assertFalse(serverNeedsRestart(processAlive = true, silentForMs = 0))
        assertFalse(serverNeedsRestart(true, SERVER_SILENCE_LIMIT_MS - 1))
    }

    @Test
    fun aServerSilentForTheWholeGracePeriodIsRestarted() {
        assertTrue(serverNeedsRestart(true, SERVER_SILENCE_LIMIT_MS))
        assertTrue(serverNeedsRestart(true, SERVER_SILENCE_LIMIT_MS + 1))
    }

    @Test
    fun aDeadServerIsRestartedAtOnce() {
        assertTrue(serverNeedsRestart(processAlive = false, silentForMs = 0))
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

    @Test
    fun thePageUrlSelectsTheProjectSession() {
        assertEquals(
            "http://127.0.0.1:4098/" +
                "L2RhdGEvdXNlci8wL2NvbS5jbG9zZWNvZGUvZmlsZXMvcHJvamVjdHMvbGFuZGluZy1wYWdlLTI" +
                "/session",
            projectPageUrl(
                "http://127.0.0.1:4098/",
                "/data/user/0/com.closecode/files/projects/landing-page-2",
            ),
        )
    }

    @Test
    fun thePageUrlUsesTheDirectoryRouteThePageUnderstands() {
        val url = projectPageUrl("http://127.0.0.1:4098", "/data/user/0/com.closecode/files/p")

        assertTrue(url.startsWith("http://127.0.0.1:4098/L2RhdGEvdXNlci8wL2NvbS5jbG9zZWNvZGU"))
        assertTrue(url.endsWith("/session"))
        assertFalse(url.contains("project-session"))
    }

    @Test
    fun thePageUrlEncodesTheDirectoryAsUnpaddedBase64UrlUtf8() {
        val directory = "/data/user/0/com.closecode/files/проект"
        val url = projectPageUrl("http://127.0.0.1:4098", directory)

        assertEquals(
            "http://127.0.0.1:4098/" +
                "L2RhdGEvdXNlci8wL2NvbS5jbG9zZWNvZGUvZmlsZXMv0L_RgNC-0LXQutGC" +
                "/session",
            url,
        )
        assertFalse(url.contains("="))
    }

    @Test
    fun theSameProjectPathIsRecognised() {
        assertTrue(
            sameProjectPath(
                "/data/user/0/com.closecode/files/p",
                "/data/user/0/com.closecode/files/p",
            ),
        )
    }

    @Test
    fun aDifferentProjectPathIsNotTheSame() {
        assertFalse(
            sameProjectPath(
                "/data/user/0/com.closecode/files/p",
                "/data/user/0/com.closecode/files/q",
            ),
        )
    }

    @Test
    fun aBlankProjectPathIsNeverTheSame() {
        assertFalse(sameProjectPath("", "/data/user/0/com.closecode/files/p"))
        assertFalse(sameProjectPath("/data/user/0/com.closecode/files/p", ""))
    }

    @Test
    fun theAuthWrapperUsesTheServersEnvironment() {
        val script = authWrapperScript(
            temp = "/data/tmp",
            home = "/data/home",
            data = "/data/home/data",
            config = "/data/home/config",
            cache = "/data/home/cache",
            path = "/data/bin:/system/bin",
            loader = "/data/alpine/lib/ld-musl-x86_64.so.1",
            libraries = "/data/alpine/lib:/data/alpine/usr/lib",
            binary = "/data/opencode",
        )

        assertTrue(script.startsWith("#!/system/bin/sh"))
        assertTrue(script.contains("export TMPDIR='/data/tmp'"))
        assertTrue(script.contains("export HOME='/data/home'"))
        assertTrue(script.contains("export XDG_DATA_HOME='/data/home/data'"))
        assertTrue(script.contains("export XDG_CONFIG_HOME='/data/home/config'"))
        assertTrue(script.contains("export XDG_CACHE_HOME='/data/home/cache'"))
        assertTrue(script.contains("export PATH='/data/bin:/system/bin'"))
        assertTrue(script.contains("'/data/alpine/lib/ld-musl-x86_64.so.1'"))
        assertTrue(
            script.contains(
                "--library-path '/data/alpine/lib:/data/alpine/usr/lib'",
            ),
        )
        assertTrue(script.contains("'/data/opencode' \"$@\""))
    }

    @Test
    fun theAuthWrapperUsesTheCaAndProxyWhenTheyAreAvailable() {
        val script = authWrapperScript(
            temp = "/data/tmp",
            home = "/data/home",
            data = "/data/home/data",
            config = "/data/home/config",
            cache = "/data/home/cache",
            path = "/data/bin:/system/bin",
            loader = "/data/loader",
            libraries = "/data/lib:/data/usr/lib",
            binary = "/data/opencode",
            caFile = "/data/opencode/ca.pem",
            proxyPort = 4099,
        )

        assertTrue(script.contains("export SSL_CERT_FILE='/data/opencode/ca.pem'"))
        assertTrue(script.contains("export NODE_EXTRA_CA_CERTS='/data/opencode/ca.pem'"))
        assertTrue(script.contains("export HTTP_PROXY='http://127.0.0.1:4099'"))
        assertTrue(script.contains("export HTTPS_PROXY='http://127.0.0.1:4099'"))
        assertTrue(script.contains("export NO_PROXY='localhost,127.0.0.1,::1'"))
    }

    @Test
    fun theAuthWrapperQuotesAPathWithSpaces() {
        val script = authWrapperScript(
            temp = "/data/tmp",
            home = "/data/home with space",
            data = "/data/home/data",
            config = "/data/home/config",
            cache = "/data/home/cache",
            path = "/data/bin:/system/bin",
            loader = "/data/loader",
            libraries = "/data/lib:/data/usr/lib",
            binary = "/data/opencode",
        )

        assertTrue(script.contains("export HOME='/data/home with space'"))
    }

    @Test
    fun theAuthWrapperAddsShellToolsWhenTheyAreInstalled() {
        val script = authWrapperScript(
            temp = "/data/tmp",
            home = "/data/home",
            data = "/data/home/data",
            config = "/data/home/config",
            cache = "/data/home/cache",
            path = "/data/opencode/bin:/data/file/u/bin:/system/bin",
            loader = "/data/loader",
            libraries = "/data/lib:/data/usr/lib",
            binary = "/data/opencode",
            shellToolsPrefix = "/data/file/u",
        )

        assertTrue(script.contains("export CLOSECODE_SHELL_TOOLS='/data/file/u'"))
        assertTrue(script.contains("export PREFIX='/data/file/u'"))
        assertTrue(script.contains("export TERMUX_PREFIX='/data/file/u'"))
        assertTrue(script.contains("'/data/opencode/bin:/data/file/u/bin:/system/bin'"))
    }

    @Test
    fun theBashShimExportsTheShellToolsEnvironment() {
        val script = openCodeBashScript()

        assertTrue(script.contains("SHELL_TOOLS=\"\$CLOSECODE_SHELL_TOOLS\""))
        assertTrue(script.contains("export PREFIX=\"\$SHELL_TOOLS\""))
        assertTrue(script.contains("export TERMUX_PREFIX=\"\$SHELL_TOOLS\""))
        assertTrue(script.contains("export PATH=\"\$SHELL_TOOLS/bin:"))
        assertTrue(script.contains("export LD_LIBRARY_PATH=\"\$SHELL_TOOLS/lib"))
        assertTrue(script.contains("exec /system/bin/sh -c"))
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
        // A phone can list an ABI it only translates as well as the one it runs. The first
        // ABI is the phone's own, so it decides; a translated build is a build that crashes.
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

        assertTrue(url.startsWith("https://github.com/anomalyco/opencode/releases/download/"))
        assertTrue(url.endsWith("/opencode-linux-arm64-musl.tar.gz"))
        assertTrue(url.contains(OpenCodeDefaults.TAG))
    }

    @Test
    fun aNewProgramReplacesTheOldOneAtomically() {
        val dir = File(System.getProperty("java.io.tmpdir"), "opencode-binary-${System.nanoTime()}")
        dir.mkdirs()
        val target = File(dir, "opencode").apply { writeText("old") }
        val staged = File(dir, "opencode.new").apply { writeText("new") }

        try {
            replaceFileAtomically(staged, target)

            assertEquals("new", target.readText())
            assertFalse(staged.exists())
            assertFalse(File(dir, "opencode.old").exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun aMissingNewProgramDoesNotReplaceTheOldOne() {
        val dir = File(System.getProperty("java.io.tmpdir"), "opencode-binary-${System.nanoTime()}")
        dir.mkdirs()
        val target = File(dir, "opencode").apply { writeText("old") }

        try {
            try {
                replaceFileAtomically(File(dir, "opencode.new"), target)
                throw AssertionError("Expected the missing staged program to fail")
            } catch (_: IllegalStateException) {
            }

            assertEquals("old", target.readText())
        } finally {
            dir.deleteRecursively()
        }
    }
}

class OpenCodeCaTest {

    @Test
    fun aCertificateBecomesAPemBlock() {
        val pem = OpenCodeCa.pem(listOf(byteArrayOf(1, 2, 3)))

        assertTrue(pem.startsWith("-----BEGIN CERTIFICATE-----\n"))
        assertTrue(pem.contains("AQID"))
        assertTrue(pem.endsWith("-----END CERTIFICATE-----\n"))
    }

    @Test
    fun noCertificatesMakeNoPem() {
        assertTrue(OpenCodeCa.pem(emptyList()).isEmpty())
    }

    @Test
    fun theCaIsGivenToEveryToolThatUsesTls() {
        val environment = OpenCodeCa.environment("/data/opencode/ca.pem")

        assertEquals("/data/opencode/ca.pem", environment["SSL_CERT_FILE"])
        assertEquals("/data/opencode/ca.pem", environment["NODE_EXTRA_CA_CERTS"])
        assertEquals("/data/opencode/ca.pem", environment["CURL_CA_BUNDLE"])
        assertEquals("/data/opencode/ca.pem", environment["GIT_SSL_CAINFO"])
        assertEquals("1", environment["NODE_USE_SYSTEM_CA"])
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

class OpenCodePageLogTest {

    @Test
    fun keepsTheNewestLinesAndDropsTheOldest() {
        OpenCodePageLog.clear()
        repeat(205) { OpenCodePageLog.add("line $it") }

        assertEquals(200, OpenCodePageLog.lines.size)
        assertTrue(OpenCodePageLog.lines.first().contains("line 5"))
        assertTrue(OpenCodePageLog.lines.last().contains("line 204"))

        OpenCodePageLog.clear()
    }

    @Test
    fun ignoresBlankLinesAndKeepsTheOrder() {
        OpenCodePageLog.clear()
        OpenCodePageLog.add("first")
        OpenCodePageLog.add("   ")
        OpenCodePageLog.add("second")

        assertEquals(
            listOf("first", "second"),
            OpenCodePageLog.lines.map { it.substringAfter(' ') },
        )

        OpenCodePageLog.clear()
    }

    @Test
    fun aVeryLongLineIsCutToTheCap() {
        OpenCodePageLog.clear()
        OpenCodePageLog.add("x".repeat(600))

        assertEquals(500, OpenCodePageLog.lines.single().substringAfter(' ').length)

        OpenCodePageLog.clear()
    }
}
