package com.closecode

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.closecode.ui.HeroScreen
import com.closecode.ui.ide.IdeScreen
import com.closecode.ui.model.SelectedTemplate
import com.closecode.ui.project.ProjectRecord
import com.closecode.ui.settings.SettingsScreen
import com.closecode.ui.theme.ClosecodeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ClosecodeTheme {
                var encoded by rememberSaveable { mutableStateOf("") }
                var settingsOpen by rememberSaveable { mutableStateOf(false) }
                var section by rememberSaveable { mutableStateOf("general") }
                var recentOpen by rememberSaveable { mutableStateOf(false) }

                val repository = rememberProjectRepository()
                val project = ProjectCodec.decode(encoded)
                val recentCount = remember(encoded) { repository.listProjects().size }

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

                    else -> IdeScreen(
                        projectName = project.name,

                        templateName = project.templateName,
                        slug = project.slug,
                        repository = repository,
                        onOpenSettings = {
                            section = "general"
                            settingsOpen = true
                        },
                        onExit = { encoded = "" },
                    )
                }
            }
        }
    }

    private fun encodeRecord(record: ProjectRecord): String = ProjectCodec.encode(
        Project(
            record.name,
            SelectedTemplate(record.tab, record.templateIndex),
            record.templateName,
            record.config,
        )
    )
}