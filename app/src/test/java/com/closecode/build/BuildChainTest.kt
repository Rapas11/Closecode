package com.closecode.build

import com.android.apksig.ApkVerifier
import java.io.File
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The verse-5b proof: the same chain the phone runs, end to end, with the original
 * compiler jars, the extracted library cache and a desktop aapt2. It builds a real
 * Compose app, then verifies the signed APK and its manifest.
 *
 * Skipped unless the pack inputs are named, exactly like ToolchainProofTest, so the
 * normal suite stays fast. Run with:
 *
 *   -Dtoolchain.dir=build-toolchain/downloads
 *   -Dlibraries.dir=build-toolchain/libraries
 *   -Daapt2.bin=<sdk>/build-tools/35.0.0/aapt2[.exe]
 *   -Dandroid.jar=build-toolchain/out/android-35.jar
 *   -Dcompose.plugin=build-toolchain/downloads/compiler-1.5.14.jar
 */
class BuildChainTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private data class Kit(
        val downloads: File,
        val libraries: File,
        val aapt2: File,
        val androidJar: File,
        val composePlugin: File,
        val stdlib: File,
        val toolFiles: Map<String, File>,
    )

    private fun kitOrSkip(): Kit {
        val downloads = (System.getProperty("toolchain.dir") ?: System.getenv("TOOLCHAIN_DIR"))
            ?.let { File(it) }
        val libraries = (System.getProperty("libraries.dir") ?: System.getenv("LIBRARIES_DIR"))
            ?.let { File(it) }
        val aapt2 = System.getProperty("aapt2.bin")?.let { File(it) }
        val androidJar = System.getProperty("android.jar")?.let { File(it) }
        val composePlugin = System.getProperty("compose.plugin")?.let { File(it) }
        assumeTrue("toolchain.dir not set", downloads != null && downloads.isDirectory)
        assumeTrue("libraries.dir not set", libraries != null && libraries.isDirectory)
        assumeTrue("aapt2.bin not set", aapt2 != null && aapt2.isFile)
        assumeTrue("android.jar not set", androidJar != null && androidJar.isFile)
        assumeTrue("compose.plugin not set", composePlugin != null && composePlugin.isFile)

        val names = mapOf(
            "kotlin-compiler" to "kotlin-compiler-embeddable-1.9.24.jar",
            "kotlin-stdlib" to "kotlin-stdlib-1.9.24.jar",
            "kotlin-reflect" to "kotlin-reflect-1.9.24.jar",
            "kotlin-script-runtime" to "kotlin-script-runtime-1.9.24.jar",
            "kotlin-daemon-embeddable" to "kotlin-daemon-embeddable-1.9.24.jar",
            "trove4j" to "trove4j-1.0.20200330.jar",
            "annotations" to "annotations-13.0.jar",
            "coroutines" to "kotlinx-coroutines-core-jvm-1.9.0.jar",
            "ecj" to "ecj-3.46.0.jar",
            "r8" to "r8-8.13.25.jar",
        )
        val toolFiles = names.mapNotNull { (id, name) ->
            File(downloads!!, name).takeIf { it.isFile }?.let { id to it }
        }.toMap()
        assumeTrue("missing jars under ${downloads!!.path}", toolFiles.size == names.size)
        return Kit(
            downloads = downloads!!,
            libraries = libraries!!,
            aapt2 = aapt2!!,
            androidJar = androidJar!!,
            composePlugin = composePlugin!!,
            stdlib = toolFiles.getValue("kotlin-stdlib"),
            toolFiles = toolFiles,
        )
    }

    @Before
    fun allowLongToolRuns() {
        // Nothing to set: the chain runs in this JVM and the tools enforce their own timeouts.
    }

    @Test
    fun buildsAndSignsAComposeApp() {
        val kit = kitOrSkip()
        val index = LibraryIndex.load(kit.libraries)
        assertTrue("the library index did not load", index != null && index.libraries.isNotEmpty())
        val project = tmp.newFolder("project")
        writeProject(project)

        val result = BuildChain.build(
            projectDir = project,
            config = BuildConfigValues(
                packageName = "com.example.buildtest",
                minSdk = 29,
                targetSdk = 35,
                versionCode = 7,
                versionName = "1.2",
                useCompose = true,
            ),
            tools = BuildTools(
                aapt2 = kit.aapt2,
                androidJar = kit.androidJar,
                stdlib = kit.stdlib,
                libraries = index!!,
                signer = testSigner(),
                toolFiles = kit.toolFiles,
                composePlugin = kit.composePlugin,
            ),
        )

        println("build times: " + result.times.joinToString { "${it.step}=${it.millis}ms" })
        assertTrue(
            "build failed at ${result.failure?.step}: ${result.failure?.message}\n" +
                "diagnostics: ${result.diagnostics}\nlog:\n${result.log.takeLast(4000)}",
            result.ok,
        )
        val apk = result.apk!!
        assertTrue("apk missing", apk.isFile && apk.length() > 100_000)
        assertEquals("app-debug.apk", apk.name)

        val verification = ApkVerifier.Builder(apk).build().verify()
        assertTrue("signature did not verify: ${verification.errors}", verification.isVerified)

        ZipFile(apk).use { zip ->
            assertTrue("no classes.dex", zip.getEntry("classes.dex") != null)
            assertTrue("no resources.arsc", zip.getEntry("resources.arsc") != null)
            assertTrue("no manifest", zip.getEntry("AndroidManifest.xml") != null)
        }

        // resources.arsc must be stored and 4-byte aligned, or API 30+ refuses the APK.
        val apkBytes = apk.readBytes()
        var arscOffset = -1
        var cursor = 0
        while (cursor <= apkBytes.size - 30) {
            val local = apkBytes[cursor] == 0x50.toByte() &&
                apkBytes[cursor + 1] == 0x4b.toByte() &&
                apkBytes[cursor + 2] == 0x03.toByte() &&
                apkBytes[cursor + 3] == 0x04.toByte()
            if (local) {
                val nameLen = (apkBytes[cursor + 26].toInt() and 0xff) or
                    ((apkBytes[cursor + 27].toInt() and 0xff) shl 8)
                val extraLen = (apkBytes[cursor + 28].toInt() and 0xff) or
                    ((apkBytes[cursor + 29].toInt() and 0xff) shl 8)
                val name = String(apkBytes, cursor + 30, nameLen, Charsets.UTF_8)
                if (name == "resources.arsc") {
                    arscOffset = cursor + 30 + nameLen + extraLen
                    break
                }
            }
            cursor++
        }
        assertTrue("resources.arsc not found in local headers", arscOffset >= 0)
        assertEquals("resources.arsc is not 4-byte aligned", 0, arscOffset % 4)

        // The Compose compiler plugin really ran: the transformed composable carries the
        // composer parameter, which plain kotlinc would never emit.
        val greeting = File(project, "build/classes/com/example/buildtest/MainActivityKt.class")
        assertTrue("GreetingKt.class missing", greeting.isFile)
        val classText = String(greeting.readBytes(), Charsets.ISO_8859_1)
        assertTrue(
            "the Compose plugin did not transform Greeting",
            classText.contains("androidx/compose/runtime/Composer"),
        )

        val dump = DriverExec.runNative(
            kit.aapt2,
            listOf("dump", "badging", apk.absolutePath),
            project,
        )
        assertEquals(dump.output, 0, dump.code)
        assertTrue(dump.output, dump.output.contains("package: name='com.example.buildtest'"))
        assertTrue(dump.output, dump.output.contains("versionCode='7'"))
        assertTrue(dump.output, dump.output.contains("versionName='1.2'"))

        val buildJson = File(project, "build/build.json")
        assertTrue("build.json missing", buildJson.isFile)
        assertTrue(buildJson.readText().contains("\"status\": \"success\""))
    }

    private fun writeProject(project: File) {
        File(project, "app/build.gradle.kts").apply {
            parentFile.mkdirs()
            writeText(
                """
                plugins {
                    id("com.android.application")
                    id("org.jetbrains.kotlin.android")
                }

                android {
                    namespace = "com.example.buildtest"
                    compileSdk = 35
                    defaultConfig {
                        applicationId = "com.example.buildtest"
                        minSdk = 29
                        targetSdk = 35
                        versionCode = 7
                        versionName = "1.2"
                    }
                }
                """.trimIndent() + "\n",
            )
        }
        File(project, "app/src/main/AndroidManifest.xml").apply {
            parentFile.mkdirs()
            writeText(
                """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                    <application android:label="@string/app_name" android:theme="@android:style/Theme.Material.Light.NoActionBar">
                        <activity android:name=".MainActivity" android:exported="true">
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN" />
                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """.trimIndent() + "\n",
            )
        }
        File(project, "app/src/main/res/values/strings.xml").apply {
            parentFile.mkdirs()
            writeText(
                """
                <resources>
                    <string name="app_name">Build Test</string>
                </resources>
                """.trimIndent() + "\n",
            )
        }
        File(project, "app/src/main/java/com/example/buildtest/MainActivity.kt").apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.example.buildtest

                import android.os.Bundle
                import androidx.activity.ComponentActivity
                import androidx.activity.compose.setContent
                import androidx.compose.foundation.layout.Box
                import androidx.compose.foundation.layout.fillMaxSize
                import androidx.compose.material3.MaterialTheme
                import androidx.compose.material3.Text
                import androidx.compose.runtime.Composable
                import androidx.compose.ui.Modifier
                import androidx.compose.ui.tooling.preview.Preview

                class MainActivity : ComponentActivity() {
                    override fun onCreate(savedInstanceState: Bundle?) {
                        super.onCreate(savedInstanceState)
                        setContent { MaterialTheme { Greeting() } }
                    }
                }

                @Composable
                fun Greeting() {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Text("Hello from CloseCode")
                    }
                }

                @Preview(showBackground = true)
                @Composable
                fun GreetingPreview() {
                    MaterialTheme { Greeting() }
                }
                """.trimIndent() + "\n",
            )
        }
    }

    /** A software key for the test; the phone uses its Android Keystore key instead. */
    private fun testSigner(): SigningIdentity {
        val store = File(tmp.root, "test.p12")
        val keytool = File(
            System.getProperty("java.home"),
            "bin/" + if (File(System.getProperty("java.home"), "bin/keytool.exe").isFile) {
                "keytool.exe"
            } else {
                "keytool"
            },
        )
        val process = ProcessBuilder(
            keytool.absolutePath,
            "-genkeypair",
            "-storetype", "PKCS12",
            "-keystore", store.absolutePath,
            "-storepass", "closecode",
            "-keypass", "closecode",
            "-alias", "test",
            "-keyalg", "RSA",
            "-keysize", "2048",
            "-validity", "10000",
            "-dname", "CN=CloseCode Test, O=CloseCode, C=US",
        ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        assertTrue("keytool failed: $output", process.waitFor() == 0)
        val keyStore = KeyStore.getInstance("PKCS12")
        store.inputStream().use { keyStore.load(it, "closecode".toCharArray()) }
        val key = keyStore.getKey("test", "closecode".toCharArray()) as java.security.PrivateKey
        val chain = keyStore.getCertificateChain("test").map { it as X509Certificate }
        return SigningIdentity(key = key, chain = chain)
    }
}
