package ca.mpreg.webgpuviewer.viewer

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.coroutines.resume

/** A strip of 1000 x 1000 pages in a 1000 x 2000 viewport, so two pages fill the screen at zoom 1. */
@RunWith(RobolectricTestRunner::class)
class ContinuousViewportTest {

    private val pages = List(8) { ImagePage.Dummy(1000, 1000) }

    /** Index into [pages] that the strip's page 0 is. */
    private var current = 3

    private val state = ImageViewerContinuousState().apply {
        renderer.width = 1000
        renderer.height = 2000
        fetchPage = { pages.getOrNull(current + it) }
        onPageChange = { current += it }
    }

    private var reports = 0

    /** What each report read through, in order. */
    private val readThrough = mutableListOf<ImagePage?>()

    init {
        state.onViewport = {
            reports++
            readThrough.add(it)
        }
    }

    /** Three screens' worth of pages, so one never reads through while it is page 0. */
    private val tall = List(8) { ImagePage.Dummy(1000, 6000) }

    /** One frame, as the render loop takes it. */
    private fun frame() {
        ImageViewerContinuousState::class.java.getDeclaredMethod("captureRenderState")
            .apply { isAccessible = true }
            .invoke(state)
    }

    /** Hands out frames one at a time, so an animation's value can be read between them. */
    private class StepClock : MonotonicFrameClock {
        private var waiting: CancellableContinuation<Unit>? = null
        private var nanos = 0L

        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            suspendCancellableCoroutine { waiting = it }
            return onFrame(nanos)
        }

        fun frame(atNanos: Long) {
            nanos = atNanos
            waiting?.also { waiting = null }?.resume(Unit)
        }
    }

    private val clock = StepClock()
    private val scope = CoroutineScope(Dispatchers.Unconfined + clock)

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun aGestureCanSettleAtAHomeScaleBelowTheZoomOutFloor() {
        state.minScale = 0.1f
        state.homeScale = 0.01f
        assertEquals(0.01f, state.minScale)
    }

    @Test
    fun aHomeScaleAboveTheZoomOutFloorKeepsTheFloor() {
        state.minScale = 0.1f
        state.homeScale = 1f
        assertEquals(0.1f, state.minScale)
    }

    @Test
    fun aScrollWithNoGestureIsReported() {
        frame()
        val before = reports
        state.scrollBy(100f)
        frame()
        assertEquals(before + 1, reports)
    }

    @Test
    fun aFrameThatMovedNothingIsNotReportedAgain() {
        frame()
        state.scrollBy(100f)
        frame()
        val before = reports
        frame()
        assertEquals(before, reports)
    }

    @Test
    fun aZoomAboutTheMiddleIsReported() {
        state.scrollBy(1500f)
        frame()
        val before = reports
        // As a gesture zooms, keeping the screen's middle still: the camera, centred there, does not move.
        val diff = 1f / 2f - 1f / state.scale
        state.scale = 2f
        state.scrollBy(-0.5f * diff * state.height)
        frame()
        assertEquals(before + 1, reports)
    }

    @Test
    fun aReloadedPageInPlaceIsReported() {
        state.scrollBy(100f)
        frame()
        val before = reports
        // The same place in a chapter loaded again: new pages at the same index, nothing moved.
        val reloaded = List(8) { ImagePage.Dummy(1000, 1000) }
        state.fetchPage = { reloaded.getOrNull(current + it) }
        frame()
        assertEquals(before + 1, reports)
    }

    @Test
    fun aPageDecodingAfterALandingIsNotReported() {
        frame()
        val before = reports
        // As a page below decoding to a new height: what reads through changes, with nothing moved.
        val decoded = List(8) { ImagePage.Dummy(1000, 400) }
        state.fetchPage = { decoded.getOrNull(current + it) }
        frame()
        assertEquals(before, reports)
    }

    @Test
    fun aJumpIsNotReported() {
        // Moved first, so the strip is no longer landing from the open.
        state.scrollBy(100f)
        frame()
        val before = reports
        current = 4
        state.resetScroll()
        frame()
        assertEquals(before, reports)
    }

    @Test
    fun aScrollAfterAJumpIsReported() {
        frame()
        current = 4
        state.resetScroll()
        frame()
        val before = reports
        state.scrollBy(100f)
        frame()
        assertEquals(before + 1, reports)
    }

    @Test
    fun aRestoreIsNotReported() {
        state.scrollBy(300f)
        frame()
        val saved = state.savePosition()
        state.scrollBy(900f)
        frame()
        val before = reports
        state.restorePosition(saved)
        frame()
        assertEquals(before, reports)
    }

    @Test
    fun aRestoreWaitingForItsPageIsNotReported() {
        state.scrollBy(300f)
        frame()
        val saved = state.savePosition()
        state.scrollBy(-300f)
        // As a reopen: the position arrives before its pages do, and is applied by the frame they arrive in.
        state.fetchPage = { null }
        state.restorePosition(saved)
        frame()
        state.fetchPage = { pages.getOrNull(current + it) }
        val before = reports
        frame()
        assertEquals(before, reports)
    }

    @Test
    fun aTouchAfterAJumpDoesNotReadThePageAbove() {
        state.fetchPage = { tall.getOrNull(current + it) }
        // Read past a page first, so the page above would count.
        state.scrollBy(6100f)
        frame()
        current = 6
        state.resetScroll()
        frame()
        val before = reports
        state.scrollBy(10f)
        frame()
        assertEquals(listOf<ImagePage?>(null), readThrough.drop(before))
    }

    @Test
    fun aTouchAfterARestoreDoesNotReadThePageAbove() {
        state.fetchPage = { tall.getOrNull(current + it) }
        state.scrollBy(100f)
        val saved = state.savePosition()
        state.scrollBy(12000f)
        frame()
        state.restorePosition(saved)
        frame()
        val before = reports
        state.scrollBy(10f)
        frame()
        assertEquals(listOf<ImagePage?>(null), readThrough.drop(before))
    }

    @Test
    fun aScrollPastAPageEdgeReadsThePageAbove() {
        state.fetchPage = { tall.getOrNull(current + it) }
        frame()
        current = 4
        state.resetScroll()
        frame()
        val before = reports
        state.scrollBy(6100f)
        frame()
        assertEquals(listOf<ImagePage?>(tall[4]), readThrough.drop(before))
    }

    @Test
    fun aScrollBackPastAPageEdgeReadsThePageAbove() {
        state.fetchPage = { tall.getOrNull(current + it) }
        frame()
        current = 4
        state.resetScroll()
        frame()
        val before = reports
        // Half way up the page before the landed one, whose bottom is still below the screen.
        state.scrollBy(-3000f)
        frame()
        assertEquals(listOf<ImagePage?>(tall[2]), readThrough.drop(before))
    }

    @Test
    fun aTouchAfterALandingDoesNotReadThePagesItShowedWhole() {
        // Pages 3 and 4 both fit on screen as the strip lands.
        frame()
        val before = reports
        state.scrollBy(10f)
        frame()
        assertEquals(listOf<ImagePage?>(null), readThrough.drop(before))
    }

    @Test
    fun aPageScrolledOnScreenAfterALandingReadsThrough() {
        frame()
        val before = reports
        // Page 5's bottom comes on screen.
        state.scrollBy(1000f)
        frame()
        assertEquals(listOf<ImagePage?>(pages[5]), readThrough.drop(before))
    }

    @Test
    fun aJumpForgetsWhatTheLastLandingShowed() {
        frame()
        state.scrollBy(1000f)
        frame()
        // Back to page 3, whose bottom the first landing showed: this landing is reading it fresh.
        current = 1
        state.resetScroll()
        frame()
        val before = reports
        state.scrollBy(1000f)
        frame()
        assertEquals(listOf<ImagePage?>(pages[3]), readThrough.drop(before))
    }

    @Test
    fun theFirstFrameIsNotReported() {
        frame()
        assertEquals(0, reports)
    }

    @Test
    fun theFirstFrameWithPagesIsNotReported() {
        // A cut-down strip, so the page below the top one reads through on screen.
        val short = List(8) { ImagePage.Dummy(1000, 500) }
        state.fetchPage = { null }
        frame()
        state.fetchPage = { short.getOrNull(current + it) }
        frame()
        assertEquals(0, reports)
    }

    @Test
    fun aSlideInIsNotReported() {
        state.scope = scope
        // Moved first, so the strip is no longer landing from the open.
        state.scrollBy(100f)
        frame()
        val before = reports
        state.animateSlideIn(1)
        clock.frame(0L)
        frame()
        clock.frame(16_000_000L)
        frame()
        assertEquals(before, reports)
    }

    @Test
    fun theFrameASlideInLandsOnIsNotReported() {
        state.scope = scope
        state.scrollBy(100f)
        frame()
        state.animateSlideIn(1)
        clock.frame(0L)
        frame()
        val before = reports
        // Long past the spring's settling, so this frame ends it.
        clock.frame(5_000_000_000L)
        frame()
        assertEquals(before, reports)
    }

    @Test
    fun aHomeScaleSetByCodeIsNotReported() {
        state.scale = 0.4f
        // Moved first, so the strip is no longer landing from the open.
        state.scrollBy(100f)
        frame()
        val before = reports
        // Lifts the zoom from 0.4 to the new home of 0.8.
        state.homeScale = 0.8f
        frame()
        assertEquals(before, reports)
    }

    @Test
    fun aPressFindsThePageUnderIt() {
        // Page 0 is pages[3] at the top, pages[4] below it; 300 pixels scrolled moves the seam up to 700.
        state.scrollBy(300f)
        assertSame(pages[4], state.pageAt(Offset(0.5f, 800f / 2000f)))
    }

    @Test
    fun zoomedOutTheSamePressLandsFurtherDown() {
        // At half zoom the 2000 pixel screen spans 4000 page pixels: three quarters down is page 0's fourth below.
        state.scale = 0.5f
        assertSame(pages[6], state.pageAt(Offset(0.5f, 0.75f)))
    }

    @Test
    fun aPressPastTheLastPageFindsNone() {
        current = 7
        assertNull(state.pageAt(Offset(0.5f, 0.75f)))
    }

    @Test
    fun aPressInTheGapBetweenPagesFindsNone() {
        state.pageGap = 0.1f
        // A 200 pixel gap trails the 1000 pixel page 0.
        assertNull(state.pageAt(Offset(0.5f, 1100f / 2000f)))
    }

    @Test
    fun aPressDuringASlideInFindsThePageAboveTheArrivingOne() {
        state.scope = scope
        // Arrived from below: page 0 starts half a screen down, so the page above it fills the top half.
        state.animateSlideIn(1)
        clock.frame(0L)
        assertSame(pages[2], state.pageAt(Offset(0.5f, 500f / 2000f)))
    }

    @Test
    fun aPressDuringASlideInFindsNoneInTheGapAboveTheArrivingPage() {
        state.scope = scope
        state.pageGap = 0.1f
        // Page 0 starts at 1000; the 200 pixel gap trailing the page above it runs from 800.
        state.animateSlideIn(1)
        clock.frame(0L)
        assertNull(state.pageAt(Offset(0.5f, 900f / 2000f)))
    }
}
