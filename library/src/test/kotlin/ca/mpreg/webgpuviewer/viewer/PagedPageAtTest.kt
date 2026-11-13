package ca.mpreg.webgpuviewer.viewer

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The page under a press in a 2000 x 1000 paged viewer, at rest and mid-turn under the default slide. */
@RunWith(RobolectricTestRunner::class)
class PagedPageAtTest {

    private val left = ImagePage.Dummy(1000, 1500)
    private val right = ImagePage.Dummy(1000, 1500)
    private val pages = listOf(ImagePage.Dummy(1000, 1500), ImagePage.ImageSpread(left, right), ImagePage.Dummy(1000, 1500))

    private fun viewer(reversed: Boolean = false) = ImageViewerState(isReversed = reversed).apply {
        renderer.width = 2000
        renderer.height = 1000
        fetchPage = { pages.getOrNull(1 + it) }
    }

    @Test
    fun atRestASpreadsSideIsTheOneUnderThePress() {
        assertSame(left, viewer().pageAt(Offset(0.25f, 0.5f)))
    }

    @Test
    fun atRestASpreadsGapIsNoPage() {
        val viewer = viewer().apply { spreadGap = 0.2f }
        assertNull(viewer.pageAt(Offset(0.5f, 0.5f)))
    }

    @Test
    fun atRestAPageShownWholeAnswersAnywhere() {
        val viewer = viewer().apply { fetchPage = { pages.getOrNull(it) } }
        assertSame(pages[0], viewer.pageAt(Offset(0.02f, 0.9f)))
    }

    @Test
    fun midTurnThePressFindsThePageSlidingIn() {
        // Half a turn: the current spread spans the screen's left half, the next page its right half.
        val viewer = viewer().apply { pageOffset = 0.5f }
        assertSame(pages[2], viewer.pageAt(Offset(0.75f, 0.5f)))
    }

    @Test
    fun midTurnASpreadsSideIsFoundWhereItHasSlidTo() {
        // A quarter turn moves the seam from the middle to a quarter of the way across.
        val viewer = viewer().apply { pageOffset = 0.25f }
        assertSame(right, viewer.pageAt(Offset(0.4f, 0.5f)))
    }

    @Test
    fun aReversedTurnSlidesThePreviousPageIn() {
        val viewer = viewer(reversed = true).apply { pageOffset = 0.5f }
        assertSame(pages[0], viewer.pageAt(Offset(0.75f, 0.5f)))
    }
}
