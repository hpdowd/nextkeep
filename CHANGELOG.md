# Changelog

All notable changes to NextKeep are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/), and the project uses
[semantic-ish versioning](README.md#versioning-and-releases) derived from git tags.
Each release's APKs are published at
[github.com/hpdowd/nextkeep/releases](https://github.com/hpdowd/nextkeep/releases).

## [1.1] - 2026-06-14

First release signed with the stable key, and the first that can update in place.
Moving to it from the debug-signed `1.0`/`1.1-rc1` needs a one-time uninstall +
reinstall; every release after this updates from within the app.

### Added
- **In-app updater** — Settings → About → *Check for updates* fetches the latest
  GitHub release, compares versions, and downloads + installs the signed APK in
  place (via `FileProvider` + `REQUEST_INSTALL_PACKAGES`).
- **Stable release signing** — release builds are signed with a fixed key, locally
  from `keystore.properties` and in CI from repository secrets, so updates install
  over each other.
- **Heading size** setting — scales markdown headings independently of the font
  size.
- **Card preview** setting — Short/Medium/Long control over how much of a note's
  body shows on a list card.

### Changed
- A note's **title is now sent explicitly** to the server, so its file is named
  after the title (the Notes API v1 `title` is read/write).
- Markdown **headings are smaller** and sized relative to body text, so they no
  longer dominate cards and the editor preview.

### Fixed
- The **QR scanner's camera permission** flow: it requests access on entry and, if
  permission was denied, points to app settings and re-checks on return, instead of
  failing.

### Docs
- Documented the git-derived versioning, the release/tag flow, and how the in-app
  updater relies on signature continuity (README + CLAUDE.md).

## [1.1-rc1] - 2026-06-13

### Changed
- Note-list typography: bigger titles, shorter previews, X-Small font option.

### Fixed
- IME (soft keyboard) handling via Compose insets under edge-to-edge.

## [1.0] - 2026-06-13

Initial release — a Google Keep–styled Android client for Nextcloud Notes
(Kotlin + Jetpack Compose, Material 3, offline-first).

### Added
- Offline-first store and **two-way sync** (Room cache + Notes API v1): local edits
  win and are pushed first, deletes use tombstones with an Undo window, and `If-Match`
  conflicts keep both copies.
- **Keep-style UI**: staggered note grid, pill search bar, Material 3 with dynamic
  color, light/dark/AMOLED themes, pinned notes (favorites) and labels (categories).
- Dependency-free **markdown engine**: editor formatting toolbar, block renderer,
  tappable checkboxes, and Enter-to-continue lists.
- **QR login**: scans Nextcloud's `nc://login/...` code via CameraX + ZXing.
- **Encrypted credentials** at rest (Android Keystore AES-GCM).
- **Settings**: theme, font size, grid columns, and sort order.
- **App lock** (biometric / device credential) and **share-to-create** a note.
- App version **derived from git** and shown in Settings; cloud **CI** builds APKs on
  GitHub/Gitea Actions.

[1.1]: https://github.com/hpdowd/nextkeep/releases/tag/v1.1
[1.1-rc1]: https://github.com/hpdowd/nextkeep/releases/tag/v1.1-rc1
[1.0]: https://github.com/hpdowd/nextkeep/releases/tag/v1.0
