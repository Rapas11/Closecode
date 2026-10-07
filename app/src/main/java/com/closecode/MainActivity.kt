package com.closecode

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import com.closecode.ui.HeroScreen
import com.closecode.ui.ide.IdeScreen
import com.closecode.ui.model.SelectedTemplate
import com.closecode.ui.project.ProjectRecord
import com.closecode.ui.settings.SettingsScreen
import com.closecode.ui.theme.ClosecodeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var themeOverride by rememberSaveable { mutableStateOf<Boolean?>(null) }
            val systemDark = isSystemInDarkTheme()
            ClosecodeTheme(forceDark = themeOverride) {
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
                        // A clone already owns its directory and its record, so it carries its own
                        // slug rather than deriving one from the name.
                        onProjectCloned = { record -> encoded = encodeRecord(record) },
                        onOpenRecent = { recentOpen = true },
                    )

                    settingsOpen -> {
                        BackHandler { settingsOpen = false }
                        SettingsScreen(
                            activeId = section,
                            onActiveChange = { section = it },
                            onBack = { settingsOpen = false },
                        )
                    }

                    // Keyed on the project, not on composable identity. Cloning from inside the
                    // IDE swaps `project` while this branch stays composed, and every per-project
                    // holder — tabs, open buffers, the tree walk, the GitHub snapshot — would
                    // otherwise carry the old project's contents into the new one.
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
                            // A clone already owns its directory and its record, so it carries its
                            // own slug rather than deriving one from the name — and it lands here,
                            // in the IDE, without going back through the hero screen.
                            onProjectCloned = { record -> encoded = encodeRecord(record) },
                            onToggleTheme = {
                                themeOverride = !(themeOverride ?: systemDark)
                            },
                        )
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