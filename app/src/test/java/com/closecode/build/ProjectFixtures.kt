package com.closecode.build

import com.closecode.ui.model.ProjectConfig
import com.closecode.ui.model.SelectedTemplate
import com.closecode.ui.model.TemplateTab
import com.closecode.ui.project.Scaffold
import java.io.File

/**
 * Test fixtures. The template fixture is written by the app's own scaffold, so the
 * project check is tested against exactly what CloseCode creates, not a hand-made
 * imitation that could drift from it.
 */
object ProjectFixtures {

    /** A CloseCode-created Android project, written by [Scaffold]. */
    fun template(
        root: File,
        packageName: String = "com.closecode.biz",
        minSdk: String = "29",
        targetSdk: String = "35",
        compileSdk: String = "35",
        templateIndex: Int = 0,
    ): File {
        val config = ProjectConfig(
            packageName = packageName,
            minSdk = minSdk,
            compileSdk = compileSdk,
            targetSdk = targetSdk,
        )
        val selection = SelectedTemplate(TemplateTab.App, templateIndex)
        Scaffold.filesFor("Biz", selection, config).forEach { file ->
            write(root, file.path, file.content)
        }
        return root
    }

    /** The check input a template project's record produces. */
    fun templateInput(
        packageName: String = "com.closecode.biz",
        minSdk: Int = 29,
        targetSdk: Int = 35,
        compileSdk: Int = 35,
        otherPackages: Set<String> = emptySet(),
        freeBytes: Long? = null,
        cacheModules: Set<String> = emptySet(),
    ): ProjectCheck.Input = ProjectCheck.Input(
        savedPackage = packageName,
        savedMinSdk = minSdk,
        savedTargetSdk = targetSdk,
        savedCompileSdk = compileSdk,
        otherPackages = otherPackages,
        freeBytes = freeBytes,
        cacheModules = cacheModules,
    )

    /**
     * A project whose files say everything — what an import or a clone looks like. No
     * record values are passed for one of these: the check reads its own files.
     */
    fun imported(root: File, packageName: String = "com.example.biz"): File {
        write(
            root,
            "build.gradle.kts",
            """
                plugins {
                    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
                }
            """.trimIndent() + "\n",
        )
        write(
            root,
            "app/build.gradle.kts",
            """
                plugins {
                    id("com.android.application")
                    id("org.jetbrains.kotlin.android")
                }

                android {
                    namespace = "$packageName"
                    compileSdk = 35

                    defaultConfig {
                        applicationId = "$packageName"
                        minSdk = 24
                        targetSdk = 35
                        versionCode = 1
                        versionName = "1.0"
                    }

                    compileOptions {
                        sourceCompatibility = JavaVersion.VERSION_17
                        targetCompatibility = JavaVersion.VERSION_17
                    }
                }
            """.trimIndent() + "\n",
        )
        write(
            root,
            "app/src/main/AndroidManifest.xml",
            """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest package="$packageName">

                    <application android:label="Imported">
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
        write(
            root,
            "app/src/main/java/${packageName.replace('.', '/')}/MainActivity.kt",
            "package $packageName\n\nclass MainActivity\n",
        )
        return root
    }

    /**
     * Biz-shaped build files: the version catalog, Kotlin 2.x with the Compose plugin,
     * Supabase, Vico charts, KSP. Shaped after the description in the spec — no keys and
     * nothing from Biz itself, just the build grammar it uses.
     */
    fun bizShaped(root: File): File {
        write(
            root,
            "settings.gradle.kts",
            """
                pluginManagement {
                    repositories {
                        google()
                        mavenCentral()
                        gradlePluginPortal()
                    }
                }

                dependencyResolutionManagement {
                    repositories {
                        google()
                        mavenCentral()
                    }
                }

                rootProject.name = "biz"
                include(":app")
            """.trimIndent() + "\n",
        )
        write(
            root,
            "gradle/libs.versions.toml",
            """
                [versions]
                agp = "8.5.0"
                kotlin = "2.0.21"
                coreKtx = "1.13.1"
                activityCompose = "1.9.3"
                composeBom = "2024.09.00"
                navigation = "2.8.0"
                supabase = "2.6.0"
                vico = "1.14.0"
                ksp = "2.0.21-1.0.25"

                [libraries]
                androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
                androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
                androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
                androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
                androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
                androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation" }
                supabase-postgrest = { group = "io.github.jan-tennert.supabase", name = "postgrest-kt", version.ref = "supabase" }
                supabase-auth = { group = "io.github.jan-tennert.supabase", name = "auth-kt", version.ref = "supabase" }
                vico-compose = { group = "com.patrykandpatrick.vico", name = "compose", version.ref = "vico" }

                [plugins]
                android-application = { id = "com.android.application", version.ref = "agp" }
                kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
                kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
                kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
                ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
            """.trimIndent() + "\n",
        )
        write(
            root,
            "build.gradle.kts",
            """
                plugins {
                    alias(libs.plugins.android.application) apply false
                    alias(libs.plugins.kotlin.android) apply false
                    alias(libs.plugins.kotlin.compose) apply false
                    alias(libs.plugins.kotlin.serialization) apply false
                    alias(libs.plugins.ksp) apply false
                }
            """.trimIndent() + "\n",
        )
        write(
            root,
            "app/build.gradle.kts",
            """
                plugins {
                    alias(libs.plugins.android.application)
                    alias(libs.plugins.kotlin.android)
                    alias(libs.plugins.kotlin.compose)
                    alias(libs.plugins.kotlin.serialization)
                    alias(libs.plugins.ksp)
                }

                android {
                    namespace = "com.example"
                    compileSdk = 35

                    defaultConfig {
                        applicationId = "com.example"
                        minSdk = 26
                        targetSdk = 35
                        versionCode = 1
                        versionName = "1.0"
                        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                    }

                    buildTypes {
                        release {
                            isMinifyEnabled = false
                        }
                    }

                    compileOptions {
                        sourceCompatibility = JavaVersion.VERSION_17
                        targetCompatibility = JavaVersion.VERSION_17
                    }

                    buildFeatures {
                        compose = true
                    }
                }

                dependencies {
                    implementation(libs.androidx.core.ktx)
                    implementation(libs.androidx.activity.compose)
                    implementation(platform(libs.androidx.compose.bom))
                    implementation(libs.androidx.compose.ui)
                    implementation(libs.androidx.compose.material3)
                    implementation(libs.androidx.navigation.compose)
                    implementation(libs.supabase.postgrest)
                    implementation(libs.supabase.auth)
                    implementation(libs.vico.compose)
                    ksp(libs.supabase.postgrest)
                }
            """.trimIndent() + "\n",
        )
        write(
            root,
            "app/src/main/AndroidManifest.xml",
            """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest package="com.example">

                    <uses-permission android:name="android.permission.CAMERA" />

                    <application android:label="Biz">
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
        write(root, "app/src/main/java/com/example/MainActivity.kt", "package com.example\n")
        return root
    }

    private fun write(root: File, path: String, content: String) {
        val file = File(root, path)
        file.parentFile?.mkdirs()
        file.writeText(content)
    }
}
