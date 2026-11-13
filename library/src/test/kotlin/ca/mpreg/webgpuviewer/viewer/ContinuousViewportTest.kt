package ca.mpreg.webgpuviewer.viewer

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

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
}
