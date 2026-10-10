package com.closecode

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import com.closecode.ui.ide.TerminalSessions
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.HeroScreen
import com.closecode.ui.ide.IdeScreen
import com.closecode.ui.model.SelectedTemplate
import com.closecode.ui.project.ProjectRecord
import com.closecode.ui.settings.LocalSettings
import com.closecode.ui.settings.OptionSetting
import com.closecode.ui.settings.SettingsScreen
import com.closecode.ui.settings.rememberSettingsStore
import com.closecode.ui.theme.ClosecodeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashReport.install(filesDir)
        // A crash leaves its trace in a file; the next launch offers it up for copying instead
        // of swallowing it. There is no logcat on a phone, so this dialog is the whole report.
        val pendingCrash = CrashReport.readLast(filesDir)
        setContent {
            var crashText by rememberSaveable { mutableStateOf(pendingCrash) }
            val settingsStore = rememberSettingsStore()
            var settings by remember(settingsStore) { mutableStateOf(settingsStore.read()) }
            // The theme is a preference, not a per-session toggle: System follows the device and
            // Dark or Light is whatever the settings screen was last left on, on every launch.
            val useDark = when (settings.theme) {
                "Dark" -> true
                "Light" -> false
                else -> isSystemInDarkTheme()
            }
            ClosecodeTheme(forceDark = useDark) {
                if (crashText != null) {
                    CrashDialog(
                        trace = crashText.orEmpty(),
                        onClose = {
                            CrashReport.clear(filesDir)
                            crashText = null
                        },
                    )
                }
                var encoded by rememberSaveable { mutableStateOf("") }
                var settingsOpen by rememberSaveable { mutableStateOf(false) }
                var section by rememberSaveable { mutableStateOf("general") }
                var recentOpen by rememberSaveable { mutableStateOf(false) }

                val repository = rememberProjectRepository()
                val project = ProjectCodec.decode(encoded)
                // One .closecode.json per project, so this is a directory read — off the
                // composition path and onto Dispatchers.IO.
                var recentCount by remember { mutableIntStateOf(0) }
                LaunchedEffect(encoded, repository) {
                    recentCount = withContext(Dispatchers.IO) { repository.listProjects().size }
                }

                // "Open last project" decides what a launch lands on. The system restores the last
                // one for us; this is the single place that is allowed to say no, checked once at
                // startup — a project opened later in the session must never be thrown away by it.
                LaunchedEffect(Unit) {
                    if (!settings.openLastProject) encoded = ""
                }

                // Above the screen switch on purpose. The settings screen replaces the IDE rather
                // than sitting over it, so anything the IDE owns in a plain remember is destroyed
                // the moment settings opens — which is how changing the terminal's colour could kill
                // the shell and leave nothing to recolour.
                val terminalSessions = remember(project?.slug) { TerminalSessions() }

                CompositionLocalProvider(LocalSettings provides settings) {
                    when {
                        project == null && recentOpen -> RecentProjectsRoute(
                            repository = repository,
                            onOpen = { record ->
                                recentOpen = false
                                encoded = encodeRecord(record)
                            },
                            onBack = { recentOpen = false },
                        )

                        project == null -> HeroScreen(
                            recentCount = recentCount,
                            onProjectCreated = { name, selection, templateName, config ->
                                val record = repository.create(name, selection, config)
                                encoded = ProjectCodec.encode(
                                    Project(record.name, selection, templateName, config)
                                )
                            },
                            // A clone already owns its directory and its record, so it carries its
                            // own slug rather than deriving one from the name.
                            onProjectCloned = { record -> encoded = encodeRecord(record) },
                            onOpenRecent = { recentOpen = true },
                        )

                        settingsOpen -> {
                            BackHandler { settingsOpen = false }
                            SettingsScreen(
                                activeId = section,
                                onActiveChange = { section = it },
                                onBack = { settingsOpen = false },
                                settings = settings,
                                onSettingsChange = { settings = settingsStore.write(it) },
                            )
                        }

                        // Keyed on the project, not on composable identity. Cloning from inside the
                        // IDE swaps `project` while this branch stays composed, and every
                        // per-project holder — tabs, open buffers, the tree walk, the GitHub
                        // snapshot — would otherwise carry the old project's contents into the new.
                        else -> key(project.slug) {
                            IdeScreen(
                                projectName = project.name,

                                templateName = project.templateName,
                                slug = project.slug,
                                repository = repository,
                                onOpenSettings = {
                                    section = "general"
                                    settingsOpen = true
                                },
                                onExit = { encoded = "" },
                                terminalSessions = terminalSessions,
                                // A clone already owns its directory and its record, so it carries
                                // its own slug rather than deriving one from the name — and it
                                // lands here, in the IDE, without the hero screen in between.
                                onProjectCloned = { record -> encoded = encodeRecord(record) },
                                onToggleTheme = {
                                    val next = when (settings.theme) {
                                        "System" -> "Dark"
                                        "Dark" -> "Light"
                                        else -> "System"
                                    }
                                    settings = settingsStore.write(
                                        settings.withOption(OptionSetting.THEME, next)
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    private fun encodeRecord(record: ProjectRecord): String = ProjectCodec.encode(
        Project(
            name = record.name,
            selection = SelectedTemplate(record.tab, record.templateIndex),
            templateName = record.templateName,
            config = record.config,
            // The record's own id, not one re-derived from the name: a cloned project's folder is
            // named when the slug is reserved, and re-slugifying could point at a different one.
            slug = record.slug,
        )
    )
}

/**
 * The previous run's crash, offered for copying. Plain Material styling on purpose: this shows
 * above every screen including a broken one, so it reads nothing from the app's own theme.
 */
@Composable
private fun CrashDialog(trace: String, onClose: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("The app crashed", fontSize = 17.sp) },
        text = {
            Text(
                text = trace.take(2000),
                fontSize = 11.sp,
                maxLines = 14,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    clipboard.setText(AnnotatedString(trace))
                    onClose()
                },
            ) { Text("Copy") }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text("Close") }
        },
    )
}