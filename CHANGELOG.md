# Changelog

What each release of this fork adds on top of the upstream release it is built on. Upstream's own
changes are in [mpreg-ca/webgpuviewer](https://github.com/mpreg-ca/webgpuviewer).

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
  screen go first, then pages with no capture yet, then the nearest; the current page always
  captures. A page past the budget keeps its last capture, and an idle page gives its memory back.

### Changed

- **The continuous viewer reports the viewport on any scroll**, not only during a fling or a pan:
  `onViewport` runs whenever the position, zoom or read-through page changed, at most once a frame.

[58.1.0]: https://github.com/unseensnick/webgpuviewer/releases/tag/v58.1.0
