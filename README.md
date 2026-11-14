# WebGPUViewer (Reikai's fork)

An Android image viewer drawn with WebGPU, for comic pages: paged and continuous reading, two-page
spreads, HDR, tiled rendering of large images and page-turn transitions. This is
[Reikai](https://github.com/unseensnick/Reikai)'s fork of
[mpreg-ca/webgpuviewer](https://github.com/mpreg-ca/webgpuviewer), which Reikai's high quality
manga renderer is built on.

The fork stays a small set of commits on top of an upstream release.

## What the fork adds

On top of upstream 58, as release `58.1.3` (details in [CHANGELOG.md](CHANGELOG.md)):

- **Space between a spread's two pages**, as a share of one page's width. The spread uses spare
  screen width before its pages shrink, and a single page or a wide page beside a spread keeps the
  spread pages' height.
- **A viewport report on any scroll, never on a landing.** The continuous viewer calls `onViewport`
  whenever its position, zoom or read-through page changed, so a programmatic scroll is reported like
  a fling, while nothing is reported from an open, a jump or a restore until the strip is scrolled. A
  landed page reads nothing above it until a scroll crosses a page edge.
- **`ImageViewerState.pageAt`**, the page under a point: a spread's side, nothing in the gap, or
  the page shown whole, also mid-turn and on the continuous strip.
- **One shared capture scratch for Compose pages**, so a strip of loading or error pages holds one
  page's capture memory instead of one per page in reach, which ran the Java heap out.

## Branches and releases

- `master` mirrors upstream and is not built.
- `reikai` is upstream's release tag plus the fork's commits. Every push builds and tests it.
- A `v<upstream>.<minor>.<patch>` tag on `reikai` (`v58.1.0`) publishes a GitHub release with the
  AAR and an ivy descriptor. Upstream's own integer tags (`58`) are never released from here.
  `scripts/version.sh` derives the version from the tag alone.

## Using it

The artifact keeps upstream's coordinate, `ca.mpreg:webgpuviewer`, so a build swaps the version and
points that one module at this repository's releases:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        exclusiveContent {
            forRepository {
                ivy("https://github.com/unseensnick/webgpuviewer/releases/download") {
                    patternLayout {
                        artifact("v[revision]/[artifact]-[revision].[ext]")
                        ivy("v[revision]/ivy-[revision].xml")
                    }
                    metadataSources { ivyDescriptor() }
                }
            }
            filter { includeModule("ca.mpreg", "webgpuviewer") }
        }
    }
}
```

```toml
# gradle/libs.versions.toml
webgpuviewer = { module = "ca.mpreg:webgpuviewer", version = "58.1.0" }
```

## Moving to a new upstream release

1. Fetch upstream and rebase `reikai`'s own commits onto its new tag.
2. Run `./gradlew :library:testDebugUnitTest`.
3. Tag `v<new upstream>.1.0` and push the tag; the release workflow publishes it.
4. Add the release to [CHANGELOG.md](CHANGELOG.md).

## License

MIT, as upstream. See [LICENSE](LICENSE).
