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

    init {
        state.onViewport = { reports++ }
    }

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
    fun aJumpIsReported() {
        frame()
        val before = reports
        current = 6
        state.resetScroll()
        frame()
        assertEquals(before + 1, reports)
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
