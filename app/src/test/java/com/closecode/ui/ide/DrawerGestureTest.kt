package com.closecode.ui.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gesture that opens the drawer.
 *
 * The swipe itself needs a device to be worth anything, but the rule it obeys does not: a pull only
 * counts when it starts in the narrow band at the left edge and travels mostly right. A drag
 * anywhere else is the editor's or terminal's own scroll, and a nudge inside the band is a mis-tap.
 */
class DrawerGestureTest {

    @Test
    fun onlyARightwardPullFromTheEdgeOpensAClosedDrawer() {
        assertTrue(shouldClaimSidebarSwipe(10f, 45f, 40f, 2f, 18f, sidebarOpen = false))
        assertTrue(shouldClaimSidebarSwipe(45f, 45f, 18f, 0f, 18f, sidebarOpen = false))

        // A drag that starts clear of the edge belongs to the content underneath.
        assertFalse(shouldClaimSidebarSwipe(300f, 45f, 40f, 2f, 18f, sidebarOpen = false))

        // A nudge that never leaves the band is a mis-tap on the side of the phone.
        assertFalse(shouldClaimSidebarSwipe(10f, 45f, 4f, 0f, 18f, sidebarOpen = false))

        // Leftward never opens — that is the drawer closing, or a scroll back to the start.
        assertFalse(shouldClaimSidebarSwipe(10f, 45f, -40f, 0f, 18f, sidebarOpen = false))
    }

    @Test
    fun verticalAndDiagonalDragsStayWithTheContent() {
        assertFalse(shouldClaimSidebarSwipe(10f, 45f, 40f, 30f, 18f, sidebarOpen = false))
        assertFalse(shouldClaimSidebarSwipe(10f, 45f, 40f, 40f, 18f, sidebarOpen = false))
    }

    @Test
    fun anOpenDrawerCanBeDraggedClosedFromAnywhere() {
        assertTrue(shouldClaimSidebarSwipe(300f, 45f, -40f, 0f, 18f, sidebarOpen = true))
        assertTrue(shouldClaimSidebarSwipe(300f, 45f, 40f, 0f, 18f, sidebarOpen = true))
        assertFalse(shouldClaimSidebarSwipe(300f, 45f, -4f, 0f, 18f, sidebarOpen = true))
    }

    @Test
    fun theEdgeBandIsNarrowEnoughToLeaveTheRestOfTheScreenAlone() {
        // Narrow enough not to steal ordinary scrolls, wide enough that a pull aimed at the
        // edge does not have to be aimed precisely.
        assertEquals(45f, DrawerEdgeBand.value, 0f)
    }
}
