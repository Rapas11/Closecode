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

    private fun gitHubRow(login: String?): Row.Nav {
        val section = sections(SettingsLive(), login).first { it.id == "git" }
        val group = section.groups.first { it.title == "GitHub" }
        val row = group.rows.first { it.name == "GitHub account" }
        assertTrue("the GitHub row is a link row", row is Row.Nav)
        return row as Row.Nav
    }

    @Test
    fun theRowAsksToConnectWhenSignedOut() {
        val row = gitHubRow(null)

        assertEquals("Connect", row.action)
        assertEquals(SettingsAction.CONNECT_GITHUB, row.id)
        assertNotNull("the row has something to say", row.description)
    }

    @Test
    fun theRowSaysWhichAccountIsConnected() {
        val row = gitHubRow("octocat")

        assertEquals("Disconnect", row.action)
        assertEquals(SettingsAction.DISCONNECT_GITHUB, row.id)
        assertEquals("Connected as @octocat.", row.description)
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

        assertEquals(SettingsAction.entries.toSet(), asked.toSet())
    }
}
