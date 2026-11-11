package com.closecode.ui.project

import com.closecode.build.AndroidProjectFiles
import com.closecode.build.ProjectPins
import com.closecode.ui.model.ProjectConfig
import com.closecode.ui.model.SelectedTemplate
import com.closecode.ui.model.TemplateTab
import com.closecode.ui.model.Templates

data class ScaffoldFile(
    val path: String,
    val content: String,
)

object Scaffold {

    fun filesFor(
        projectName: String,
        selection: SelectedTemplate,
        config: ProjectConfig,
    ): List<ScaffoldFile> {
        val template = Templates[selection.tab].orEmpty()[selection.index]
        return if (selection.tab == TemplateTab.App) {
            if (config.language == "Java") {
                androidJavaFiles(projectName, template.name, config)
            } else {
                androidKotlinFiles(projectName, template.name, config)
            }
        } else {
            webFiles(projectName, template.name)
        }
    }

    private fun androidKotlinFiles(
        projectName: String,
        templateName: String,
        config: ProjectConfig,
    ): List<ScaffoldFile> {
        val packagePath = config.packageName.replace('.', '/')
        return buildList {
            add(ScaffoldFile("settings.gradle.kts", settingsGradle(projectName)))
            add(ScaffoldFile("build.gradle.kts", rootGradle(config.gradleVersion, kotlin = true)))
            add(ScaffoldFile("gradle.properties", gradleProperties()))
            add(
                ScaffoldFile(
                    "gradle/wrapper/gradle-wrapper.properties",
                    wrapperProperties(config.gradleVersion),
                ),
            )
            add(ScaffoldFile("gradlew", AndroidProjectFiles.GRADLEW))
            add(
                ScaffoldFile(
                    "app/build.gradle.kts",
                    appGradle(config),
                )
            )
            add(
                ScaffoldFile(
                    "app/src/main/AndroidManifest.xml",
                    manifest(),
                )
            )
            add(
                ScaffoldFile(
                    "app/src/main/java/$packagePath/MainActivity.kt",
                    mainActivity(config.packageName),
                )
            )
            add(
                ScaffoldFile(
                    "app/src/main/java/$packagePath/Theme.kt",
                    themeKotlin(config.packageName),
                )
            )
            add(ScaffoldFile("app/src/main/res/values/strings.xml", stringsXml(projectName)))
            add(ScaffoldFile("app/src/main/res/values/themes.xml", themesXml()))
            addAll(androidExtras(templateName, config.packageName, packagePath))
        }
    }

    /**
     * A Java project is a normal Android project, just without Kotlin or Compose. It uses the
     * platform Activity, Views and XML layouts, so it needs none of the Compose libraries the
     * on-device cache does not carry for Java-only builds.
     */
    private fun androidJavaFiles(
        projectName: String,
        templateName: String,
        config: ProjectConfig,
    ): List<ScaffoldFile> {
        val packagePath = config.packageName.replace('.', '/')
        return buildList {
            add(ScaffoldFile("settings.gradle.kts", settingsGradle(projectName)))
            add(ScaffoldFile("build.gradle.kts", rootGradle(config.gradleVersion, kotlin = false)))
            add(ScaffoldFile("gradle.properties", gradleProperties()))
            add(
                ScaffoldFile(
                    "gradle/wrapper/gradle-wrapper.properties",
                    wrapperProperties(config.gradleVersion),
                ),
            )
            add(ScaffoldFile("gradlew", AndroidProjectFiles.GRADLEW))
            add(ScaffoldFile("app/build.gradle.kts", appGradle(config)))
            add(ScaffoldFile("app/src/main/AndroidManifest.xml", manifest()))
            add(
                ScaffoldFile(
                    "app/src/main/java/$packagePath/MainActivity.java",
                    mainActivityJava(config.packageName),
                ),
            )
            add(ScaffoldFile("app/src/main/res/layout/activity_main.xml", activityMainXml()))
            add(ScaffoldFile("app/src/main/res/values/strings.xml", stringsXml(projectName)))
            add(ScaffoldFile("app/src/main/res/values/themes.xml", themesXml()))
            addAll(androidJavaExtras(templateName, config.packageName, packagePath))
        }
    }

    private fun androidJavaExtras(
        templateName: String,
        packageName: String,
        packagePath: String,
    ): List<ScaffoldFile> = when (templateName) {
        "To-do list" -> listOf(
            ScaffoldFile(
                "app/src/main/java/$packagePath/Task.java",
                javaTask(packageName),
            ),
            ScaffoldFile(
                "app/src/main/java/$packagePath/TaskDao.java",
                javaTaskDao(packageName),
            ),
        )

        "Notes" -> listOf(
            ScaffoldFile(
                "app/src/main/java/$packagePath/Note.java",
                javaNote(packageName),
            ),
            ScaffoldFile(
                "app/src/main/java/$packagePath/NoteDao.java",
                javaNoteDao(packageName),
            ),
        )

        "Weather" -> listOf(
            ScaffoldFile(
                "app/src/main/java/$packagePath/Forecast.java",
                javaForecast(packageName),
            ),
            ScaffoldFile(
                "app/src/main/java/$packagePath/WeatherApi.java",
                javaWeatherApi(packageName),
            ),
            ScaffoldFile(
                "app/src/main/java/$packagePath/WeatherScreen.java",
                javaScreen(packageName, "Weather"),
            ),
        )

        "Chat" -> listOf(
            ScaffoldFile(
                "app/src/main/java/$packagePath/Message.java",
                javaMessage(packageName),
            ),
            ScaffoldFile(
                "app/src/main/java/$packagePath/ChatScreen.java",
                javaScreen(packageName, "Chat"),
            ),
        )

        "Tip calculator" -> listOf(
            ScaffoldFile(
                "app/src/main/java/$packagePath/TipScreen.java",
                javaScreen(packageName, "Tip"),
            ),
        )

        else -> emptyList()
    }

    private fun androidExtras(
        templateName: String,
        packageName: String,
        packagePath: String,
    ): List<ScaffoldFile> = when (templateName) {
        "To-do list" -> listOf(
            ScaffoldFile(
                "app/src/main/java/$packagePath/Task.kt",
                dataClassKotlin(packageName, "Task", "id: Long, title: String, done: Boolean"),
            ),
            ScaffoldFile(
                "app/src/main/java/$packagePath/TaskDao.kt",
                interfaceKotlin(packageName, "TaskDao", "fun all(): List<Task>"),
            ),
        )

        "Notes" -> listOf(
            ScaffoldFile(
                "app/src/main/java/$packagePath/Note.kt",
                dataClassKotlin(packageName, "Note", "id: Long, title: String, body: String"),
            ),
            ScaffoldFile(
                "app/src/main/java/$packagePath/NoteDao.kt",
                interfaceKotlin(packageName, "NoteDao", "fun all(): List<Note>"),
            ),
        )

        "Weather" -> listOf(
            ScaffoldFile(
                "app/src/main/java/$packagePath/Forecast.kt",
                dataClassKotlin(packageName, "Forecast", "day: String, high: Int, low: Int"),
            ),
            ScaffoldFile(
                "app/src/main/java/$packagePath/WeatherApi.kt",
                interfaceKotlin(
                    packageName,
                    "WeatherApi",
                    "fun forecast(city: String): List<Forecast>",
                ),
            ),
            ScaffoldFile(
                "app/src/main/java/$packagePath/WeatherScreen.kt",
                screenKotlin(packageName, "Weather"),
            ),
        )

        "Chat" -> listOf(
            ScaffoldFile(
                "app/src/main/java/$packagePath/Message.kt",
                dataClassKotlin(packageName, "Message", "id: String, body: String, mine: Boolean"),
            ),
            ScaffoldFile(
                "app/src/main/java/$packagePath/ChatScreen.kt",
                screenKotlin(packageName, "Chat"),
            ),
        )

        "Tip calculator" -> listOf(
            ScaffoldFile(
                "app/src/main/java/$packagePath/TipScreen.kt",
                screenKotlin(packageName, "Tip"),
            ),
        )

        else -> emptyList()
    }

    private fun webFiles(
        projectName: String,
        templateName: String,
    ): List<ScaffoldFile> = buildList {
        add(ScaffoldFile("index.html", indexHtml(projectName)))
        add(ScaffoldFile("styles.css", stylesCss()))
        add(ScaffoldFile("README.md", readme(projectName, templateName)))
        add(ScaffoldFile("assets/.gitkeep", ""))
        addAll(webExtras(templateName))
    }

    private fun webExtras(templateName: String): List<ScaffoldFile> =
        when (templateName) {
            "Portfolio" -> listOf(
                ScaffoldFile("projects.html", indexHtml("Projects")),
                ScaffoldFile("projects.json", jsonList("title")),
            )

            "Blog" -> listOf(
                ScaffoldFile("main.js", mainJs()),
                ScaffoldFile("posts/.gitkeep", ""),
            )

            "Online store" -> listOf(
                ScaffoldFile("main.js", mainJs()),
                ScaffoldFile("products.json", jsonList("title", "price")),
            )

            "Dashboard" -> listOf(
                ScaffoldFile("main.js", mainJs()),
                ScaffoldFile("charts.js", mainJs()),
                ScaffoldFile("data.csv", "label,value\nAlpha,10\nBeta,20\nGamma,30\n"),
            )

            "Documentation" -> listOf(
                ScaffoldFile("main.js", mainJs()),
                ScaffoldFile("docs/index.md", readme("Documentation", "Documentation")),
            )

            else -> emptyList()
        }

    private fun settingsGradle(projectName: String): String = """
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

        rootProject.name = "${slugify(projectName)}"
        include(":app")
    """.trimIndent() + "\n"

    private fun rootGradle(gradleVersion: String, kotlin: Boolean): String {
        val agp = ProjectPins.agpForGradle(gradleVersion)
        val kotlinPlugin = if (kotlin) {
            "\n    id(\"org.jetbrains.kotlin.android\") version \"${ProjectPins.KOTLIN}\" apply false"
        } else {
            ""
        }
        return """
            plugins {
                id("com.android.application") version "$agp" apply false$kotlinPlugin
            }
        """.trimIndent() + "\n"
    }

    private fun gradleProperties(): String = """
        org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
        android.useAndroidX=true
    """.trimIndent() + "\n"

    /**
     * Wrapper metadata for a desktop checkout. The phone uses the `gradlew` delegate, which
     * runs the Gradle distribution installed from Settings instead of downloading it again.
     */
    private fun wrapperProperties(gradleVersion: String): String = """
        distributionBase=GRADLE_USER_HOME
        distributionPath=wrapper/dists
        distributionUrl=https://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip
        networkTimeout=10000
        validateDistributionUrl=true
        zipStoreBase=GRADLE_USER_HOME
        zipStorePath=wrapper/dists
    """.trimIndent() + "\n"

    private fun appGradle(config: ProjectConfig): String {
        val java = config.language == "Java"
        val kotlinPlugin = if (java) {
            ""
        } else {
            "\n    id(\"org.jetbrains.kotlin.android\")"
        }
        val compose = if (java) {
            ""
        } else {
            """

                composeOptions {
                    kotlinCompilerExtensionVersion = "${ProjectPins.COMPOSE_COMPILER}"
                }
            """
        }
        val dependencies = if (java) {
            ""
        } else {
            """

                dependencies {
                    implementation("androidx.core:core-ktx:1.13.1")
                    implementation("androidx.activity:activity-compose:1.9.3")
                    implementation(platform("androidx.compose:compose-bom:${ProjectPins.COMPOSE_BOM}"))
                    implementation("androidx.compose.ui:ui")
                    implementation("androidx.compose.material3:material3")
                }
            """
        }
        return """
            plugins {
                id("com.android.application")$kotlinPlugin
            }

            android {
                namespace = "${config.packageName}"
                compileSdk = ${config.compileSdk}

                defaultConfig {
                    applicationId = "${config.packageName}"
                    minSdk = ${config.minSdk}
                    targetSdk = ${config.targetSdk}
                    versionCode = 1
                    versionName = "1.0"
                }

                buildTypes {
                    ${config.buildType.lowercase()} {
                        isMinifyEnabled = false
                    }
                }

                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }$compose
            }$dependencies
        """.trimIndent() + "\n"
    }

    private fun manifest(): String = """
        <?xml version="1.0" encoding="utf-8"?>
        <manifest xmlns:android="http://schemas.android.com/apk/res/android">

            <application
                android:label="@string/app_name"
                android:theme="@style/Theme.App">
                <activity
                    android:name=".MainActivity"
                    android:exported="true">
                    <intent-filter>
                        <action android:name="android.intent.action.MAIN" />
                        <category android:name="android.intent.category.LAUNCHER" />
                    </intent-filter>
                </activity>
            </application>

        </manifest>
    """.trimIndent() + "\n"

    private fun mainActivity(packageName: String): String = """
        package $packageName

        import android.os.Bundle
        import androidx.activity.ComponentActivity
        import androidx.activity.compose.setContent

        class MainActivity : ComponentActivity() {
            override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)
                setContent {
                    ClosecodeTheme {
                        Text("Hello, world")
                    }
                }
            }
        }
    """.trimIndent() + "\n"

    private fun themeKotlin(packageName: String): String = """
        package $packageName

        import androidx.compose.runtime.Composable

        @Composable
        fun ClosecodeTheme(content: @Composable () -> Unit) {
            content()
        }
    """.trimIndent() + "\n"

    private fun mainActivityJava(packageName: String): String = """
        package $packageName;

        import android.app.Activity;
        import android.os.Bundle;

        public class MainActivity extends Activity {
            @Override
            protected void onCreate(Bundle savedInstanceState) {
                super.onCreate(savedInstanceState);
                setContentView(R.layout.activity_main);
            }
        }
    """.trimIndent() + "\n"

    private fun activityMainXml(): String = """
        <?xml version="1.0" encoding="utf-8"?>
        <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:orientation="vertical"
            android:padding="24dp">

            <TextView
                android:id="@+id/title"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/app_name"
                android:textSize="28sp"
                android:textStyle="bold" />

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="12dp"
                android:text="@string/welcome"
                android:textSize="16sp" />

        </LinearLayout>
    """.trimIndent() + "\n"

    private fun javaTask(packageName: String): String = """
        package $packageName;

        public class Task {
            public final long id;
            public final String title;
            public final boolean done;

            public Task(long id, String title, boolean done) {
                this.id = id;
                this.title = title;
                this.done = done;
            }
        }
    """.trimIndent() + "\n"

    private fun javaTaskDao(packageName: String): String = """
        package $packageName;

        import java.util.List;

        public interface TaskDao {
            List<Task> all();
        }
    """.trimIndent() + "\n"

    private fun javaNote(packageName: String): String = """
        package $packageName;

        public class Note {
            public final long id;
            public final String title;
            public final String body;

            public Note(long id, String title, String body) {
                this.id = id;
                this.title = title;
                this.body = body;
            }
        }
    """.trimIndent() + "\n"

    private fun javaNoteDao(packageName: String): String = """
        package $packageName;

        import java.util.List;

        public interface NoteDao {
            List<Note> all();
        }
    """.trimIndent() + "\n"

    private fun javaForecast(packageName: String): String = """
        package $packageName;

        public class Forecast {
            public final String day;
            public final int high;
            public final int low;

            public Forecast(String day, int high, int low) {
                this.day = day;
                this.high = high;
                this.low = low;
            }
        }
    """.trimIndent() + "\n"

    private fun javaWeatherApi(packageName: String): String = """
        package $packageName;

        import java.util.List;

        public interface WeatherApi {
            List<Forecast> forecast(String city);
        }
    """.trimIndent() + "\n"

    private fun javaMessage(packageName: String): String = """
        package $packageName;

        public class Message {
            public final String id;
            public final String body;
            public final boolean mine;

            public Message(String id, String body, boolean mine) {
                this.id = id;
                this.body = body;
                this.mine = mine;
            }
        }
    """.trimIndent() + "\n"

    private fun javaScreen(packageName: String, name: String): String = """
        package $packageName;

        public class ${name}Screen {
            public String title() {
                return "$name";
            }
        }
    """.trimIndent() + "\n"

    private fun dataClassKotlin(
        packageName: String,
        name: String,
        fields: String,
    ): String {
        val constructorFields = fields.split(", ").joinToString(",\n    ") { field ->
            val clean = field.split(":").first().trim()
            val type = field.substringAfter(":").trim()
            "val $clean: $type"
        }
        return """
            package $packageName

            data class $name(
                $constructorFields
            )
        """.trimIndent() + "\n"
    }

    private fun interfaceKotlin(
        packageName: String,
        name: String,
        members: String,
    ): String = """
        package $packageName

        interface $name {
            $members
        }
    """.trimIndent() + "\n"

    private fun screenKotlin(packageName: String, name: String): String = """
        package $packageName

        import androidx.compose.runtime.Composable
        import androidx.compose.material3.Text

        @Composable
        fun ${name}Screen() {
            Text("$name")
        }
    """.trimIndent() + "\n"

    private fun stringsXml(projectName: String): String = """
        <?xml version="1.0" encoding="utf-8"?>
        <resources>
            <string name="app_name">${escapeXml(projectName)}</string>
            <string name="welcome">Edit this screen and build.</string>
        </resources>
    """.trimIndent() + "\n"

    private fun themesXml(): String = """
        <?xml version="1.0" encoding="utf-8"?>
        <resources>
            <style name="Theme.App" parent="android:Theme.Material.NoActionBar" />
        </resources>
    """.trimIndent() + "\n"

    /**
     * The page a new project starts with.
     *
     * Richer than it was on purpose. It used to be a bare `<h1>`, which made Preview impossible to
     * tell apart from a failure: an empty-looking page is what a broken `styles.css` also looks
     * like. A heading, a line of copy, a button and a rule give the preview something to show and the
     * editor something to open, and it is all the reader can delete.
     *
     * The title and the heading both say the project's name, which is the same thing the editor's own
     * HTML snippet now says — the two used to disagree, so the file on disk and the file the snippet
     * produced were visibly different documents.
     */
    private fun indexHtml(projectName: String): String = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <title>${escapeHtml(projectName)}</title>
          <link rel="stylesheet" href="styles.css">
        </head>
        <body>
          <main class="hero">
            <h1>${escapeHtml(projectName)}</h1>
            <p>Edit this page and watch it change.</p>
            <button type="button" class="primary">Run</button>
            <hr>
            <small>Created by Closecode</small>
          </main>
        </body>
        </html>
    """.trimIndent() + "\n"

    private fun stylesCss(): String = """
        :root {
          --bg: #f8f7f3;
          --tx: #12151c;
        }

        * {
          box-sizing: border-box;
          margin: 0;
        }

        body {
          background: var(--bg);
          color: var(--tx);
          font-family: system-ui, sans-serif;
        }

        .hero {
          display: grid;
          gap: 12px;
          justify-items: start;
          padding: 48px 22px;
        }

        .hero p {
          margin: 0;
          color: #5b6b83;
          max-width: 34ch;
          line-height: 1.5;
        }

        .hero .primary {
          background: #2457ff;
          color: #fff;
          border: 0;
          border-radius: 10px;
          padding: 12px 22px;
          font: inherit;
          font-weight: 600;
        }

        .hero small {
          color: #8793a8;
        }
    """.trimIndent() + "\n"

    private fun mainJs(): String = """
        document.addEventListener('DOMContentLoaded', function () {
          console.log('ready');
        });
    """.trimIndent() + "\n"

    private fun jsonList(vararg fields: String): String {
        val keys = fields.joinToString(", ") { "\"$it\"" }
        return """
            [
              { $keys },
              { $keys }
            ]
        """.trimIndent() + "\n"
    }

    private fun readme(projectName: String, templateName: String): String = """
        # $projectName

        Created with Closecode from the $templateName template.
    """.trimIndent() + "\n"

    private fun slugify(value: String): String = value
        .lowercase()
        .filter { it.isLetterOrDigit() || it == '-' }
        .replace(Regex("-+"), "-")
        .trim('-')
        .ifEmpty { "project" }

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private fun escapeXml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "\\\"")
}