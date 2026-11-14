# Changelog

What each release of this fork adds on top of the upstream release it is built on. Upstream's own
changes are in [mpreg-ca/webgpuviewer](https://github.com/mpreg-ca/webgpuviewer).

## [Unreleased]

### Fixed

- **A Compose page no longer waits to attach while another page's capture is uploading.** 58.1.4's
  capture pass stopped at the first page that took the shared scratch, so while a page kept changing,
  pages behind it in turn could not attach or take their size. Every pass now visits every page; only
  the upload waits its turn.

### Removed

- **58.1.2's zoom-out floor that followed a lower home zoom is gone.** `minScale` is upstream's again: a
  gesture settles no lower than it, so a home zoom below it holds only until the first touch. Reikai
  no longer offers a home zoom below the floor, so nothing used it.

## [58.1.4] - 2026-11-14

Built on upstream 58.

### Changed

- **Compose pages that keep changing refresh about four times as often.** With one shared capture
  scratch, the next waiting page now captures as soon as the last upload finishes instead of on the
  next frame, so a loading spinner on a zoomed-out strip redraws every 80 to 100 ms rather than
  every 400. A page still in its warmup keeps capturing once a frame.

## [58.1.3] - 2026-11-13

Built on upstream 58.

### Fixed

- **A continuous viewer no longer reports a landing as reading.** After an open, a jump
  (`resetScroll`), a restore (`restorePosition`), a slide-in (`animateSlideIn`) or a home zoom set by
  code (`homeScale`), `onViewport` stays silent until the next `scrollBy`, and a page the landing
  showed whole does not read through until the next placement. So neither pages decoding around the
  landed one nor the first touch after it change what an app saves. 58.1.0 reported every moved
  frame, and an app saving progress from it moved the saved page on every reopen, back one at a time
  or, zoomed out, forward several.
- **After a landing, the page above page 0 no longer reads through until a scroll crosses a page
  edge.** Upstream's fallback counted it whenever nothing on screen had been read past, so the first
  touch after a jump or a reopen reported the page before the landed one.

## [58.1.2] - 2026-11-13

Built on upstream 58.

### Fixed

- **A continuous viewer's home zoom below its zoom-out floor now holds after a gesture.**
  `ImageViewerContinuousState.minScale` never exceeds `homeScale`, so a home zoom of 1% with a 10%
  floor no longer snaps to 10% on the first touch.

## [58.1.1] - 2026-11-13

Built on upstream 58.

### Changed

- **Compose pages share one capture scratch** (one bitmap and one direct buffer), used by one
  capture at a time, in place of 58.1.0's per-page scratch and 64 MiB budget. On Android a direct
  buffer is Java heap, so a buffer per page in reach ran the heap out on a zoomed-out strip of
  loading pages. Turns go to the page the window carries, then pages on screen, then the longest
  waiting; the scratch is freed once no Compose page is in reach.
  `ImageViewerState.composeCaptureBudgetBytes` is gone.
- **`Mipmap.update` yields on the same time slice as `upload`** instead of after every chunk, so a
  capture or an animated page's frame no longer moves at about a chunk per frame.
- **`ImagePage.ImageSpread.sideAt` is internal**; `ImageViewerState.pageAt` answers the same
  question.

## [58.1.0] - 2026-11-13

Built on upstream 58.

### Added

- **Space between a spread's two pages.** `ImageViewerState` takes the gap as a share of one page's
  width; the spread fits as two pages plus the gap, so spare width is used before the pages shrink.
  A lone page beside a spread is laid out as a spread side, and a wide page shown alone fits the
  spread's full width.
- **`ImageViewerState.pageAt(point)`**, the page under a point given as the 0-1 fractions `onTap`
  and `onLongTap` pass: a spread's side, null in the gap, or the page shown whole. Mid-turn it asks
  the transition (`Transition.pageAt`): the slide transitions answer exactly, the others give the
  page more than half turned in. The continuous viewer answers from its own placement.
- **A capture budget for Compose pages** (`composeCaptureBudgetBytes`, 64 MiB by default). Pages on
  screen go first, then pages with no capture yet, then the nearest; the first in that order
  always captures. A page past the budget keeps its last capture, and an idle page gives its
  memory back.

### Changed

- **The continuous viewer reports the viewport on any scroll**, not only during a fling or a pan:
  `onViewport` runs whenever the position, zoom or read-through page changed, at most once a frame.

[58.1.2]: https://github.com/unseensnick/webgpuviewer/releases/tag/v58.1.2
[58.1.1]: https://github.com/unseensnick/webgpuviewer/releases/tag/v58.1.1
[58.1.0]: https://github.com/unseensnick/webgpuviewer/releases/tag/v58.1.0
