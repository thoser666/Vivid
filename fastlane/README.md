fastlane documentation
----

# Installation

Make sure you have the latest version of the Xcode command line tools installed:

```sh
xcode-select --install
```

For _fastlane_ installation instructions, see [Installing _fastlane_](https://docs.fastlane.tools/#installing-fastlane)

# Available Actions

## Android

### android test

```sh
[bundle exec] fastlane android test
```

Run all unit tests

### android build_debug

```sh
[bundle exec] fastlane android build_debug
```

Build debug APK

### android build_release

```sh
[bundle exec] fastlane android build_release
```

Build release APK

### android ci_debug

```sh
[bundle exec] fastlane android ci_debug
```

Run tests and build debug

### android ci_release

```sh
[bundle exec] fastlane android ci_release
```

Run tests and build release

### android release_alpha

```sh
[bundle exec] fastlane android release_alpha
```

Create and push an alpha release tag. Runs all unit tests, verifies you're on `develop`, auto-detects the next version (e.g. `v0.2.0-alpha` → `v0.3.0-alpha`), then pushes the tag. The CI `android_fastlane.yml` workflow picks up `v*`-tags and builds + publishes a stable prerelease.

Options:
- `version:` — override auto-detection (e.g. `version:"v0.5.0-alpha"`)

### android release_github

```sh
[bundle exec] fastlane android release_github
```

Build release APK and publish it as a GitHub release (requires gh + GH_TOKEN). Stable for v*-tags, rolling nightly prerelease for branch pushes

### android publish_release

```sh
[bundle exec] fastlane android publish_release
```

Publish a pre-built release APK as a GitHub release (requires gh + GH_TOKEN). Used by CI after `build_release` for error isolation.

----

This README.md is auto-generated and will be re-generated every time [_fastlane_](https://fastlane.tools) is run.

More information about _fastlane_ can be found on [fastlane.tools](https://fastlane.tools).

The documentation of _fastlane_ can be found on [docs.fastlane.tools](https://docs.fastlane.tools).
