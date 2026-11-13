package ca.mpreg.webgpuviewer.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Which Compose pages in reach may hold capture scratch, and when a page holds it. */
@RunWith(RobolectricTestRunner::class)
class CaptureBudgetTest {

    private fun need(onScreen: Boolean = true, captured: Boolean = true, distance: Int = 0, bytes: Long = 10L) =
        CaptureNeed(onScreen, captured, distance, bytes)

    @Test
    fun theNearestPagesTakeTheBudgetAndTheFarthestGoWithout() {
        val claims = listOf(
            CaptureClaim("far", need(distance = 2)),
            CaptureClaim("current", need(distance = 0)),
            CaptureClaim("next", need(distance = 1)),
        )
        assertEquals(setOf("current", "next"), withinCaptureBudget(claims, 25L))
    }

    @Test
    fun theFirstClaimCapturesEvenPastTheBudget() {
        val claims = listOf(CaptureClaim("current", need(bytes = 30L)), CaptureClaim("next", need(distance = 1, bytes = 1L)))
        assertEquals(setOf("current"), withinCaptureBudget(claims, 25L))
    }

    @Test
    fun claimsWithinTheBudgetAllCapture() {
        val claims = listOf(CaptureClaim("current", need()), CaptureClaim("next", need(distance = 1)))
        assertEquals(setOf("current", "next"), withinCaptureBudget(claims, 25L))
    }

    @Test
    fun theCurrentPageOnScreenGoesBeforeAnOffScreenFirstCapture() {
        val claims = listOf(
            CaptureClaim("below, never captured", need(onScreen = false, captured = false, distance = 3)),
            CaptureClaim("current", need(distance = 0)),
        )
        assertEquals(setOf("current"), withinCaptureBudget(claims, 15L))
    }

    @Test
    fun onScreenAFirstCaptureGoesBeforeAFreshOne() {
        val claims = listOf(
            CaptureClaim("current, captured", need(distance = 0)),
            CaptureClaim("next, never captured", need(captured = false, distance = 1)),
        )
        assertEquals(setOf("next, never captured"), withinCaptureBudget(claims, 15L))
    }

    @Test
    fun aPageHoldingScratchCountsAgainstTheBudget() {
        assertTrue(claimsCaptureScratch(need(), warmingUp = false, dirty = false, holdsScratch = true))
    }

    @Test
    fun aCapturedPageWithNothingNewNeitherHoldingScratchClaimsNone() {
        assertFalse(claimsCaptureScratch(need(), warmingUp = false, dirty = false, holdsScratch = false))
    }

    @Test
    fun anOffScreenPageClaimsOnlyItsFirstCapture() {
        assertFalse(claimsCaptureScratch(need(onScreen = false), warmingUp = false, dirty = true, holdsScratch = false))
    }

    @Test
    fun anOnScreenPageWithAChangeClaims() {
        assertTrue(claimsCaptureScratch(need(), warmingUp = false, dirty = true, holdsScratch = false))
    }

    @Test
    fun anIdlePageKeepsItsScratchForAFewPasses() {
        assertTrue(keepsIdleScratch(IDLE_SCRATCH_PASSES - 1))
    }

    @Test
    fun anIdlePageGivesItsScratchBackAfterThem() {
        assertFalse(keepsIdleScratch(IDLE_SCRATCH_PASSES))
    }
}
