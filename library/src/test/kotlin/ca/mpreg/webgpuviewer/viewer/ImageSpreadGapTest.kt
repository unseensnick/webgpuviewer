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
    fun theGapIsItsShareOfAPagesWidth() {
        // 10% of a 1000 pixel page is 100, 50 a side.
        assertEquals(1050f, spread(gap = 0.1f).horizontalExtent().first, 0.01f)
    }

    @Test
    fun theGapFollowsThePagesOwnWidth() {
        // 800 pixel pages: 10% is 80, 40 a side.
        val narrow = spread(gap = 0.1f, left = page(800, 1200), right = page(800, 1200))
        assertEquals(840f, narrow.horizontalExtent().first, 0.01f)
    }

    @Test
    fun eachSideIsDrawnItsShareOfTheGapOffTheSeam() {
        // The left side's centre sits half its width plus its 50 pixel share left of the seam: 550 of 2000.
        assertEquals(-0.275f, spread(gap = 0.1f).sideTransform(left, 2000f)!![0], 0.0001f)
    }

    @Test
    fun spareWidthTakesTheGapBeforeThePagesShrink() {
        // Landscape, two pages fit by height at 2/3 with 667 pixels to spare, more than a 20% gap needs.
        assertEquals(2f / 3f, spread(gap = 0.2f).homeScale, 0.0001f)
    }

    @Test
    fun aSpreadFittingByWidthShrinksByTwoPagesAndTheGapOverTwoPages() {
        viewer.renderer.height = 2000
        // Square, the pages fit by width at 1 with no gap; a 20% gap makes the spread 2.2 pages wide.
        assertEquals(2f / 2.2f, spread(gap = 0.2f).homeScale, 0.0001f)
    }

    /** A 2000 x 1500 double page shown on its own between spreads, attached to the viewer. */
    private fun wideAlone(gap: Float): ImagePage.Dummy {
        viewer.spreadGap = gap
        return page(2000, 1500).apply {
            fitsAsSpread = true
            attach(viewer, null) {}
        }
    }

    @Test
    fun aWidePageAloneFitsAtTheSpreadsPageHeight() {
        viewer.renderer.height = 2000
        val spreadPageHeight = spread(gap = 0.2f).homeScale * 1500
        assertEquals(spreadPageHeight, wideAlone(gap = 0.2f).homeScale * 1500, 0.01f)
    }

    @Test
    fun aWidePageAloneAtNoGapFitsTheScreenAsBefore() {
        viewer.renderer.height = 2000
        assertEquals(1f, wideAlone(gap = 0f).homeScale, 0.0001f)
    }

    @Test
    fun aWidePageAloneZoomsOutNoFurtherThanItsFit() {
        viewer.renderer.height = 2000
        val wide = wideAlone(gap = 0.2f)
        assertEquals(wide.homeScale, wide.minScale, 0.0001f)
    }

    @Test
    fun aWidePagePlacedOffScreenGoesHomeAsItArrives() {
        viewer.renderer.height = 2000
        val wide = wideAlone(gap = 0f)
        viewer.spreadGap = 0.2f
        viewer.show(wide)
        assertEquals(2f / 2.2f, wide.scale, 0.0001f)
    }

    @Test
    fun aLoneSideSitsHalfTheGapOffTheSeamAsInASpread() {
        assertEquals(1050f to 0f, spread(gap = 0.1f, right = null).horizontalExtent())
    }

    @Test
    fun aLoneSideFitsAtTheScaleItWouldHaveInASpread() {
        viewer.renderer.height = 2000
        assertEquals(2f / 2.2f, spread(gap = 0.2f, right = null).homeScale, 0.0001f)
    }

    @Test
    fun aPressInTheGapBesideALoneSideIsNoSide() {
        val lone = spread(gap = 0.1f, right = null).apply { setPos(x = 0f, y = 0f, scale = 2f / 3f) }
        assertNull(lone.sideAt(0.49f, offsetX = 0f))
    }

    @Test
    fun aLoneComposedSideIsLaidOutInItsHalfLessHalfTheGap() {
        // A composed side fills its half less its own share of the gap: 1000 / 1.05.
        val loading = ImagePage.Compose(ComposePageSize.Of(952, 400))
        assertEquals(952, spread(gap = 0.1f, left = loading, right = null).composedSideWidth(2000))
    }

    @Test
    fun aComposedSideIsLaidOutInItsHalfLessHalfTheGap() {
        val loading = ImagePage.Compose(ComposePageSize.Of(952, 400))
        assertEquals(952, spread(gap = 0.1f, right = loading).composedSideWidth(2000))
    }

    @Test
    fun anImageBesideAComposedSideKeepsTheGap() {
        val loading = ImagePage.Compose(ComposePageSize.Of(952, 400))
        assertEquals(50f, spread(gap = 0.1f, right = loading).gapLeftOfSeam, 0.01f)
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
        // At the fit of 2/3, the gap of 100 spread pixels is 67 on screen, 0.483 to 0.517 of the width.
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
        // Fit by width, so the gap shrinks the fit from 1 to 1 / 1.05.
        assertEquals(1f / 1.05f, spread.scale, 0.001f)
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
        assertEquals(1f / 1.05f, spread.scale, 0.001f)
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
        val spread = spread(gap = 0.1f, right = ImagePage.Compose(ComposePageSize.Of(952, 400)))
        assertEquals(952, composedSlotWidth(spread, 2000))
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

    private fun spans(clipL: Float, clipR: Float, gapStart: Float, gapEnd: Float): List<Pair<Float, Float>> {
        val out = ArrayList<Pair<Float, Float>>()
        forEachTileClipSpan(clipL, clipR, gapStart, gapEnd) { l, r -> out.add(l to r) }
        return out
    }

    @Test
    fun tilesMeetingAtTheSeamBlitAsOneSpan() {
        assertEquals(listOf(100f to 900f), spans(100f, 900f, gapStart = 500f, gapEnd = 500f))
    }

    @Test
    fun tilesOfAPartedSpreadSkipTheGap() {
        assertEquals(listOf(100f to 460f, 540f to 900f), spans(100f, 900f, gapStart = 460f, gapEnd = 540f))
    }

    @Test
    fun aGapReachingPastTheClipLeavesOnlyTheSideStillInIt() {
        assertEquals(listOf(540f to 900f), spans(480f, 900f, gapStart = 460f, gapEnd = 540f))
    }
}
