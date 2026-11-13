package ca.mpreg.webgpuviewer.viewer

import ca.mpreg.webgpuviewer.renderer.forEachTileClipSpan
import ca.mpreg.webgpuviewer.renderer.haloExtend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A spread parted by its viewer's gap, laid out against a 2000 x 1000 landscape viewport. */
@RunWith(RobolectricTestRunner::class)
class ImageSpreadGapTest {

    /** A viewer with no surface, whose drawn pages a test names. */
    private class Viewer : ImageViewerState() {
        init {
            renderer.width = 2000
            renderer.height = 1000
        }

        fun show(vararg pages: ImagePage) {
            onScreenPages = pages.toList()
        }
    }

    private val viewer = Viewer()

    private fun page(width: Int = 1000, height: Int = 1500) = ImagePage.Dummy(width, height)

    private val left = page()
    private val right = page()

    /** Two 1000 x 1500 pages fit their halves by height at 2/3, with or without a small gap. */
    private fun spread(
        gap: Float,
        left: ImagePage? = this.left,
        right: ImagePage? = this.right,
    ): ImagePage.ImageSpread {
        viewer.spreadGap = gap
        return ImagePage.ImageSpread(left, right).also { it.attach(viewer, null) {} }
    }

    @Test
    fun noGapKeepsTheSidesOnTheSeam() {
        assertEquals(1000f to 1000f, spread(gap = 0f).horizontalExtent())
    }

    @Test
    fun theGapOnScreenIsItsShareOfTheViewportHeight() {
        // 10% of 1000 is 100 on screen, 50 a side; the fit of 2/3 makes that 75 spread pixels a side.
        assertEquals(1075f, spread(gap = 0.1f).horizontalExtent().first, 0.01f)
    }

    @Test
    fun theGapIsTakenOutOfTheHalfOfSidesThatFitByWidth() {
        viewer.renderer.height = 2000
        // Square, the pages fit their halves by width, less 100 a side for a 10% gap: 0.9, and 900 + 100 = 1000.
        assertEquals(1000f, spread(gap = 0.1f).horizontalExtent().first * 0.9f, 0.5f)
    }

    @Test
    fun aLoneSideTakesNoGap() {
        assertEquals(1000f to 0f, spread(gap = 0.1f, right = null).horizontalExtent())
    }

    @Test
    fun aComposedSideIsLaidOutInItsHalfLessHalfTheGap() {
        val loading = ImagePage.Compose(ComposePageSize.Of(950, 400))
        assertEquals(950, spread(gap = 0.1f, right = loading).composedSideWidth(2000))
    }

    @Test
    fun anImageBesideAComposedSideKeepsTheGap() {
        val loading = ImagePage.Compose(ComposePageSize.Of(950, 400))
        assertEquals(75f, spread(gap = 0.1f, right = loading).halfImageGap, 0.01f)
    }

    @Test
    fun aNewGapMakesNewTiles() {
        val spread = spread(gap = 0f)
        val before = spread.contentVersion
        viewer.spreadGap = 0.1f
        assertNotEquals(before, spread.contentVersion)
    }

    @Test
    fun aPressInTheGapIsNoSide() {
        // At the fit, the 100 pixel gap runs 0.475 to 0.525 of the width.
        assertNull(spread(gap = 0.1f).sideAt(0.51f, offsetX = 0f))
    }

    @Test
    fun aPressJustLeftOfTheGapIsTheLeftSide() {
        assertSame(left, spread(gap = 0.1f).sideAt(0.47f, offsetX = 0f))
    }

    @Test
    fun aPressJustRightOfTheGapIsTheRightSide() {
        assertSame(right, spread(gap = 0.1f).sideAt(0.53f, offsetX = 0f))
    }

    @Test
    fun aSpreadOnScreenAtHomeStaysHomeAsTheGapChanges() {
        viewer.renderer.height = 2000
        val spread = spread(gap = 0f).also { viewer.show(it) }
        viewer.spreadGap = 0.1f
        // Fit by width, so the gap shrinks the fit from 1 to 0.9.
        assertEquals(0.9f, spread.scale, 0.001f)
    }

    @Test
    fun aZoomedSpreadPannedToItsEdgeIsKeptInsideTheNarrowerBounds() {
        val spread = spread(gap = 0.2f).also { viewer.show(it) }
        spread.setPos(x = spread.maxX(2f), scale = 2f)
        viewer.spreadGap = 0f
        assertEquals(spread.maxX(2f), spread.x, 0.0001f)
    }

    @Test
    fun aSpreadPlacedOffScreenGoesHomeAsItArrives() {
        viewer.renderer.height = 2000
        val spread = spread(gap = 0f)
        viewer.spreadGap = 0.1f
        viewer.show(spread)
        assertEquals(0.9f, spread.scale, 0.001f)
    }

    @Test
    fun aSpreadsComposedSideIsTrackedAgainstItsSpread() {
        val loading = ImagePage.Compose(ComposePageSize.Of(950, 400))
        val spread = spread(gap = 0.1f, right = loading)
        val found = HashMap<ImagePage.Compose, ImagePage.ImageSpread?>()
        spread.collectComposePages(found)
        assertSame(spread, found[loading])
    }

    @Test
    fun aSpreadsComposedLeftSideIsTrackedAgainstItsSpread() {
        val loading = ImagePage.Compose(ComposePageSize.Of(950, 400))
        val spread = spread(gap = 0.1f, left = loading)
        val found = HashMap<ImagePage.Compose, ImagePage.ImageSpread?>()
        spread.collectComposePages(found)
        assertSame(spread, found[loading])
    }

    @Test
    fun aComposedSpreadSideLaysOutInItsSide() {
        val spread = spread(gap = 0.1f, right = ImagePage.Compose(ComposePageSize.Of(950, 400)))
        assertEquals(950, composedSlotWidth(spread, 2000))
    }

    @Test
    fun aComposedPageOfItsOwnLaysOutInTheWholeViewport() {
        assertEquals(2000, composedSlotWidth(null, 2000))
    }

    @Test
    fun partedInnerEdgesExtendAcrossHalfTheGap() {
        assertEquals(3f, haloExtend(8f, isLeftmost = true, isRightmost = false, halfGap = 3f)!!.right)
    }

    @Test
    fun aWideGapExtendsTheWholeHalo() {
        assertEquals(8f, haloExtend(8f, isLeftmost = false, isRightmost = true, halfGap = 40f)!!.left)
    }

    @Test
    fun meetingInnerEdgesAreNotExtended() {
        assertEquals(0f, haloExtend(8f, isLeftmost = true, isRightmost = false, halfGap = 0f)!!.right)
    }

    @Test
    fun outerEndsExtendTheWholeHalo() {
        assertEquals(8f, haloExtend(8f, isLeftmost = true, isRightmost = false, halfGap = 3f)!!.left)
    }

    private fun spans(clipL: Float, clipR: Float, seam: Float, halfGap: Float): List<Pair<Float, Float>> {
        val out = ArrayList<Pair<Float, Float>>()
        forEachTileClipSpan(clipL, clipR, seam, halfGap) { l, r -> out.add(l to r) }
        return out
    }

    @Test
    fun tilesMeetingAtTheSeamBlitAsOneSpan() {
        assertEquals(listOf(100f to 900f), spans(100f, 900f, seam = 500f, halfGap = 0f))
    }

    @Test
    fun tilesOfAPartedSpreadSkipTheGap() {
        assertEquals(listOf(100f to 460f, 540f to 900f), spans(100f, 900f, seam = 500f, halfGap = 40f))
    }

    @Test
    fun aGapReachingPastTheClipLeavesOnlyTheSideStillInIt() {
        assertEquals(listOf(540f to 900f), spans(480f, 900f, seam = 500f, halfGap = 40f))
    }
}
