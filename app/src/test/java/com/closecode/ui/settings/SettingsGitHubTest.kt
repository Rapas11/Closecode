package com.closecode.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The GitHub row on the settings screen.
 *
 * It used to be a row that led nowhere: it said "Connect" and pressing it did nothing, while the
 * working sign-in lived in the Source Control tab and the hero screen. These tests hold the row to
 * what it now does — it asks for the sign-in, it says which account is connected, and it can end
 * the connection.
 */
class SettingsGitHubTest {

    private fun gitHubSection(login: String?) = sections(SettingsLive(), login)
        .first { it.id == "git" }
        .groups.first { it.title == "Account" }

    @Test
    fun theRowAsksToConnectWhenSignedOut() {
        val row = gitHubSection(null).rows.first { it.name == "GitHub account" } as Row.Nav

        assertEquals("Sign in", row.action)
        assertEquals(SettingsAction.CONNECT_GITHUB, row.id)
        assertNotNull("the row has something to say", row.description)
    }

    @Test
    fun theRowSaysWhichAccountIsConnected() {
        val row = gitHubSection("octocat").rows.first { it.name == "octocat" } as Row.Account

        assertEquals("Sign out", row.action)
        assertEquals("octocat", row.name)
        assertEquals("Signed in with GitHub", row.subtitle)
        assertEquals("O", row.initials)
    }

    @Test
    fun signingInDoesNotAddOrRemoveRows() {
        // The screen is a fixed set of rows; the account changes what one of them says, and
        // nothing about how many there are.
        assertEquals(
            sections(SettingsLive(), null).flatMap { it.groups }.flatMap { it.rows }.size,
            sections(SettingsLive(), "octocat").flatMap { it.groups }.flatMap { it.rows }.size,
        )
    }

    @Test
    fun everySettingsActionIsReachableFromARow() {
        // An action nothing asks for is dead code, and an action a row asks for but the screen
        // ignores is worse. Every entry has to be on a row — the GitHub row asks for one of its
        // two depending on whether this phone is signed in, so both screens are checked.
        val asked = listOf(null, "octocat")
            .flatMap { login ->
                sections(SettingsLive(), login)
                    .flatMap { section -> section.groups.flatMap { it.rows } }
                    .flatMap { row ->
                        when (row) {
                            is Row.Action -> listOf(row.id)
                            is Row.Nav -> listOfNotNull(row.id)
                            else -> emptyList()
                        }
                    }
            }

        println("ASKED: $asked")
        assertEquals(
            setOf(
                SettingsAction.PROJECT_LOCATION,
                SettingsAction.SETUP_GUIDE,
                SettingsAction.SHELL_TOOLS,
                SettingsAction.AI_SETTINGS,
                SettingsAction.DOWNLOAD_TOOLCHAIN,
                SettingsAction.SAVED_COMMANDS,
                SettingsAction.CONNECT_GITHUB,
                SettingsAction.CREDENTIALS,
                SettingsAction.EDIT_GIT_NAME,
                SettingsAction.EDIT_GIT_EMAIL,
                SettingsAction.SIGNING_KEYS,
                SettingsAction.AGENT_PERMISSIONS,
                SettingsAction.AI_PROVIDER_KEY,
                SettingsAction.UPDATE_RUNTIME,
                SettingsAction.PRIVACY,
                SettingsAction.STORAGE_INSPECT_PROJECTS,
                SettingsAction.STORAGE_INSPECT_BUILD_TOOLS,
                SettingsAction.STORAGE_INSPECT_SHELL_TOOLS,
                SettingsAction.CLEAR_CACHES,
                SettingsAction.SAVED_SIGN_INS,
                SettingsAction.SOURCE_CODE,
                SettingsAction.LICENSES,
            ),
            asked.toSet(),
        )
    }
}
