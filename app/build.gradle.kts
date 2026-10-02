plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.closecode"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.closecode"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // kotlin-compiler-embeddable ships its own copies of stdlib internals
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            // kotlin-compiler-embeddable embeds its own copy of these stdlib
            // builtins. Keep one rather than failing the merge.
            pickFirsts += setOf(
                "kotlin/internal/internal.kotlin_builtins",
                "kotlin/reflect/reflect.kotlin_builtins",
                "kotlin/kotlin.kotlin_builtins",
                "kotlin/coroutines/coroutines.kotlin_builtins",
                "kotlin/concurrent/atomics/atomics.kotlin_builtins",
                "kotlin/collections/collections.kotlin_builtins",
                "kotlin/annotation/annotation.kotlin_builtins",
                "kotlin/ranges/ranges.kotlin_builtins",
                "kotlin/sequences/sequences.kotlin_builtins",
                "kotlin/text/strings.kotlin_builtins",
            )
        }
    }
}

dependencies {
    
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.04.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    testImplementation("junit:junit:4.13.2")
    // JVM-only. The probe loads this jar through a URLClassLoader rather than the app
    // classpath, but the tests need to locate the file on disk to hand it over.
    // This never reaches the APK.
    testImplementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.4.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}




// TEMPORARY: the Kotlin compiler must exist on disk as a real jar. IntelliJ's
// PathManager.getResourceRoot() resolves classes through Class.getResource(...),
// which always returns null in a dexed APK because there are no .class files.
// Loading the compiler through a URLClassLoader over a real jar fixes that.
// This configuration exists only to copy that jar into assets; it is not on the
// app's compile or runtime classpath, so the compiler is never dexed.
val kotlinCompilerJar by configurations.creating

dependencies {
    kotlinCompilerJar("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.4.0")
}

val unpackKotlinHome by tasks.registering(Copy::class) {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from({ configurations.getByName("debugRuntimeClasspath")
        .resolvedConfiguration.resolvedArtifacts
        .map { it.file }
        .filter { f ->
            f.name == "kotlin-stdlib-2.4.0.jar" ||
                f.name == "annotations-23.0.0.jar" ||
                f.name == "kotlin-script-runtime-2.4.0.jar"
        } }) {
        rename { f ->
            when {
                f.startsWith("kotlin-stdlib") -> "kotlin-stdlib.jar"
                f.startsWith("kotlin-script-runtime") -> "kotlin-script-runtime.jar"
                else -> "annotations.jar"
            }
        }
    }
    from({ kotlinCompilerJar.resolve().map { it } }) {
        rename { "kotlin-compiler.jar" }
    }
    from(file("${rootProject.file("local.properties")}").takeIf { it.exists() }?.let { props ->
        val sdk = props.readLines()
            .firstOrNull { it.trim().startsWith("sdk.dir") }
            ?.substringAfter("=")
            ?.trim()
        if (sdk != null) file("$sdk/platforms/android-35/android.jar") else null
    }) {
        rename { "android.jar" }
    }
    includeEmptyDirs = false
    into(layout.buildDirectory.dir("generated/kotlinHomeAssets/kotlin-home/lib"))
}

android.sourceSets.getByName("main") {
    assets.srcDir(layout.buildDirectory.dir("generated/kotlinHomeAssets"))
}

tasks.matching { it.name == "mergeDebugAssets" || it.name == "mergeReleaseAssets" }.configureEach {
    dependsOn(unpackKotlinHome)
}

// AGP does not put these jars on java.class.path where the unit tests can scan for
// them, so hand the paths over directly. Only realised when a test task runs.
// findByName rather than getByName: a missing name must not break configuration.
tasks.withType<Test>().configureEach {
    val compilerFiles: Set<java.io.File> =
        runCatching { kotlinCompilerJar.resolve() }.getOrElse { emptySet() }
    val unitTestConf = configurations.firstOrNull { it.name.endsWith("UnitTestRuntimeClasspath") }
    val runtimeFiles: List<java.io.File> = runCatching {
        unitTestConf?.resolvedConfiguration?.resolvedArtifacts?.map { it.file } ?: emptyList()
    }.getOrElse { emptyList() }

    fun pick(name: String, vararg exclude: String): String =
        runtimeFiles.firstOrNull { f ->
            f.name.startsWith(name) && exclude.none { f.name.contains(it) }
        }?.absolutePath ?: ""

    systemProperty(
        "probe.compilerJar",
        compilerFiles.firstOrNull { it.name.startsWith("kotlin-compiler-embeddable") }
            ?.absolutePath ?: "",
    )
    systemProperty("probe.stdlibJar", pick("kotlin-stdlib-", "common", "jdk"))
    systemProperty("probe.scriptRuntimeJar", pick("kotlin-script-runtime-"))
    systemProperty("probe.annotationsJar", pick("annotations-", "apiguardian"))
}
