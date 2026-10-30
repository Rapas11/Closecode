package com.closecode.ui.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.debug.BuildInfo
import com.closecode.ui.components.OutlineButton
import com.closecode.ui.components.TextLink
import com.closecode.ui.project.ProjectRecord
import com.closecode.ui.project.ProjectRepository
import com.closecode.ui.project.ProjectZip
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Sans
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The storage page: every project lives in this app's private storage, so an uninstall or a
 * broken phone would take the work with them. This card is the way out: export a project as
 * one zip, import a zip back as a project, and clear the leftovers old experiments left.
 */
@Composable
internal fun StorageProjectsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val c = LocalPageColors.current
    val scope = rememberCoroutineScope()
    val repository = remember { ProjectRepository(context) }
    val projects = remember { repository.listProjects() }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var exportSlug by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        val slug = exportSlug
        if (uri != null && slug != null) {
            busy = true
            val root: File? = runCatching { repository.projectDir(slug) }.getOrNull()
            val record = projects.firstOrNull { it.slug == slug }
            scope.launch {
                note = if (root == null || record == null) {
                    "The project is not on the phone anymore."
                } else {
                    withContext(Dispatchers.IO) {
                        try {
                            context.contentResolver.openOutputStream(uri)?.use { out ->
                                val count = ProjectZip.write(
                                    root,
                                    ProjectZip.manifest(
                                        record.name,
                                        record.slug,
                                        System.currentTimeMillis(),
                                        BuildInfo.label,
                                    ),
                                    out,
                                )
                                "Exported ${record.name} — $count files, ${humanSize(uriSize(context, uri))}."
                            } ?: "The zip could not be written where the save dialog chose."
                        } catch (failure: Throwable) {
                            "Export stopped: ${failure.message ?: failure.javaClass.simpleName}"
                        }
                    }
                }
                busy = false
                exportSlug = null
            }
        } else {
            exportSlug = null
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            busy = true
            scope.launch {
                val outcome = withContext(Dispatchers.IO) {
                    try {
                        val manifestJson = context.contentResolver.openInputStream(uri)?.use { input ->
                            ProjectZip.manifestFromZip(input)
                        }
                        val name = ProjectZip.nameOf(manifestJson)
                            ?: "Imported ${System.currentTimeMillis() / 1000 % 1000}"
                        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                            input.readBytes()
                        } ?: return@withContext "The file could not be read."
                        val record = repository.importCloned(name) { target: File ->
                            val written = ProjectZip.extract(
                                java.io.ByteArrayInputStream(bytes),
                                target,
                            )
                            if (written == 0 && manifestJson == null) {
                                throw IllegalArgumentException(
                                    "That file holds no project: it is not a CloseCode export.",
                                )
                            }
                        }
                        "Imported ${record.name} — it is on the start screen."
                    } catch (failure: Throwable) {
                        "Import stopped: ${failure.message ?: failure.javaClass.simpleName}"
                    }
                }
                note = outcome
                busy = false
            }
        }
    }

    SettingsPageScaffold(title = "Project export & import", onBack = onBack) {
        PageCard {
            Text(
                text = "Export a project",
                color = c.tx,
                fontSize = 15.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "One zip per project — sources, manifest and settings, without build " +
                    "output. Keep it anywhere: Google Drive, mail, another phone. If this app " +
                    "or the phone is ever lost, the project is not.",
                color = c.mu,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                fontFamily = Sans,
            )
            if (projects.isEmpty()) {
                Text(
                    text = "No projects to export yet.",
                    color = c.mu,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                )
            }
            projects.forEach { project ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Text(
                        text = project.name,
                        color = c.tx,
                        fontSize = 13.sp,
                        fontFamily = Sans,
                        modifier = Modifier.weight(1f),
                    )
                    if (project.imported) {
                        Text(
                            text = "imported",
                            color = c.mu,
                            fontSize = 11.sp,
                            fontFamily = Sans,
                        )
                    }
                    TextLink(
                        label = "Export",
                        onClick = {
                            exportSlug = project.slug
                            exportLauncher.launch(
                                "${cleanZipName(project.name)}-closecode-export.zip",
                            )
                        },
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }

        PageCard {
            Text(
                text = "Import a project",
                color = c.tx,
                fontSize = 15.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "Pick a zip this app exported before — the project comes back with its " +
                    "own name on the start screen.",
                color = c.mu,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                fontFamily = Sans,
            )
            OutlineButton(
                label = "Import from a zip file",
                onClick = {
                    importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                },
            )
        }

        PageCard {
            Text(
                text = "Housekeeping",
                color = c.tx,
                fontSize = 15.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Medium,
            )
            val leftovers = remember { leftoverTestDirs(context) }
            Text(
                text = if (leftovers.isEmpty()) {
                    "No leftover experiment directories in the app's storage."
                } else {
                    "Old experiment directories are still there: ${leftovers.joinToString(", ")}"
                },
                color = c.mu,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                fontFamily = Sans,
            )
            if (leftovers.isNotEmpty()) {
                OutlineButton(
                    label = "Delete the leftovers",
                    onClick = {
                        busy = true
                        scope.launch {
                            val gone = withContext(Dispatchers.IO) {
                                leftovers.sumOf { name ->
                                    File(context.filesDir, name).takeIf { it.isDirectory }
                                        ?.deleteRecursively()
                                        ?.takeIf { it }?.let { 1 } ?: 0
                                }
                            }
                            note = "Deleted $gone leftover directories."
                            busy = false
                        }
                    },
                )
            }
        }

        if (note.isNotBlank()) {
            Text(
                text = note,
                color = if (busy) c.mu else c.tx,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                fontFamily = Sans,
            )
        }
        if (busy) {
            Text(
                text = "Working…",
                color = c.mu,
                fontSize = 12.sp,
                fontFamily = Sans,
            )
        }
    }
}

/** A file name that flies: no slashes, a short zip prefix from the project name. */
private fun cleanZipName(name: String): String =
    name.trim().replace(Regex("[^a-zA-Z0-9._-]"), "-").take(40).ifBlank { "project" }

/** How big the saved zip became, right after it is written. */
private fun uriSize(context: Context, uri: Uri): Long =
    runCatching { context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } }
        .getOrNull() ?: 0L

/** The plain facts storage holds: projects, OpenCode's home, exports, reports. */
internal fun leftOverTestDirsListing(context: Context): List<String> = leftoverTestDirs(context)

/** Old test-home directories from experiments, ready to delete: home-copy, home-test*. */
private fun leftoverTestDirs(context: Context): List<String> {
    val names = context.filesDir.listFiles()?.map { it.name } ?: emptyList()
    return names.filter {
        it == "home-copy" || it.startsWith("home-test") || (it == "home" && false)
    }
}
