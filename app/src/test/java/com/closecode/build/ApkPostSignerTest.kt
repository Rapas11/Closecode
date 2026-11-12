package com.closecode.build

import java.io.File
import java.security.KeyStore
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Item 1 of the build-85 brief: `closecode sign-apk` must really replace the signature,
 * and must report the certificate the file carries.
 *
 * The test builds its own APK and its own two keystores, signs the APK with the first key,
 * signs it again with the second, and checks that the file really changed keys — and that
 * the certificate the app reports is the one in the file.
 */
class ApkPostSignerTest {

    /** A minimal but real APK: a zip with an AndroidManifest.xml. */
    private fun apkFixture(target: File) {
        ZipOutputStream(target.outputStream()).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("AndroidManifest.xml"))
            zip.write(
                ("<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                    "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" " +
                    "package=\"com.closecode.test\" android:versionCode=\"1\" />").toByteArray(),
            )
            zip.closeEntry()
            zip.putNextEntry(java.util.zip.ZipEntry("resources.arsc"))
            zip.write(ByteArray(4))
            zip.closeEntry()
        }
    }

    /** A keystore made with the JDK's own keytool. */
    private fun keystore(path: File): Boolean {
        val java = File(System.getProperty("java.home"), "bin/keytool")
        if (!java.isFile) return false
        val result = runCatching {
            ProcessBuilder(
                java.absolutePath,
                "-genkeypair",
                "-keystore", path.absolutePath,
                "-storepass", "android",
                "-keypass", "android",
                "-alias", "androiddebugkey",
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-validity", "10000",
                "-dname", "CN=Test Key, O=Test, C=US",
            ).redirectErrorStream(true).start()
                .inputStream.bufferedReader().use { it.readText() }
        }.getOrNull() ?: return false
        return path.isFile && result.contains("Warning") || path.isFile
    }

    private fun identity(path: File): SigningIdentity {
        val store = KeyStore.getInstance("JKS")
        store.load(path.inputStream(), "android".toCharArray())
        val key = store.getKey("androiddebugkey", "android".toCharArray())
        val chain = store.getCertificateChain("androiddebugkey")
        return SigningIdentity(
            key as java.security.PrivateKey,
            chain.map { it as java.security.cert.X509Certificate },
        )
    }

    @Test
    fun signingReplacesTheSignatureAndTheCertificateIsReadBackFromTheFile() {
        val dir = File(System.getProperty("java.io.tmpdir"), "closecode-signing")
        dir.mkdirs()
        val first = File(dir, "first.jks")
        val second = File(dir, "second.jks")
        assumeTrue("keytool is not available", keystore(first))
        assumeTrue("keytool is not available", keystore(second))

        val apk = File(dir, "test.apk")
        apkFixture(apk)
        try {
            assertEquals("the empty APK should not verify", false, ApkPostSigner.isVerified(apk, 29))
            assertEquals(null, ApkPostSigner.certificateIn(apk, 29))

            // The first key signs it.
            ApkPostSigner.sign(apk, identity(first), 29)
            val afterFirst = ApkPostSigner.certificateIn(apk, 29)
            assertTrue("the certificate could not be read back", afterFirst != null)
            assertTrue(
                "the file does not carry the key that signed it",
                afterFirst.orEmpty().contains("Test Key"),
            )

            // The second key replaces the first one: the file must really change keys.
            ApkPostSigner.sign(apk, identity(second), 29)
            val afterSecond = ApkPostSigner.certificateIn(apk, 29)
            assertNotEquals(
                "the signature was not replaced",
                afterFirst,
                afterSecond,
            )
            assertTrue(
                "the file does not carry the key that signed it",
                afterSecond.orEmpty().contains("Test Key"),
            )
        } finally {
            apk.delete()
            first.delete()
            second.delete()
        }
    }

    @Test
    fun aFileThatIsNotAnApkHasNoCertificate() {
        val file = File.createTempFile("closecode-not-an-apk", ".bin")
        try {
            file.writeText("not an apk")
            assertEquals(null, ApkPostSigner.certificateIn(file, 29))
            assertEquals(false, ApkPostSigner.isVerified(file, 29))
        } finally {
            file.delete()
        }
    }
}
