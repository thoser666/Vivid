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

### android lint

```sh
[bundle exec] fastlane android lint
```

Run lint & static analysis on all modules

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

Create and push an alpha release tag — runs tests, auto-versions, triggers CI

### android release_beta

```sh
[bundle exec] fastlane android release_beta
```

Create and push a beta release tag — runs tests, auto-versions, triggers CI (mirror of release_alpha, stage beta)

### android release_github

```sh
[bundle exec] fastlane android release_github
```

Build release APK and publish it as a GitHub release (requires gh + GH_TOKEN)

### android publish_release

```sh
[bundle exec] fastlane android publish_release
```

Publish a pre-built release APK as a GitHub release (requires gh + GH_TOKEN, pre-built APK)

### android publish_play

```sh
[bundle exec] fastlane android publish_play
```

Build a release AAB signed with the UPLOAD key and upload it to Google Play (requires UPLOAD_KEYSTORE_* + Play service-account credentials; dry_run:true baut + verifiziert ohne Upload)

### android sweep_orphan_drafts

```sh
[bundle exec] fastlane android sweep_orphan_drafts
```

Report orphaned draft releases on v* tags (no auto-delete - a draft may be intentional)

### android capture_play_screenshots

```sh
[bundle exec] fastlane android capture_play_screenshots
```

Capture the two Play Store screenshots via UI tests (fastlane screengrab) — requires a running emulator/device; replaces the placeholder PNGs under fastlane/metadata/android/images/phoneScreenshots/ and verifies the metadata gate

----

This README.md is auto-generated and will be re-generated every time [_fastlane_](https://fastlane.tools) is run.

More information about _fastlane_ can be found on [fastlane.tools](https://fastlane.tools).

The documentation of _fastlane_ can be found on [docs.fastlane.tools](https://docs.fastlane.tools).
