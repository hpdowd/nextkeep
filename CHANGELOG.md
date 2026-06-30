# Changelog

All notable changes to NextKeep are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/), and the project uses
[semantic-ish versioning](README.md#versioning-and-releases) derived from git tags.
Each release's APKs are published at
[github.com/hpdowd/nextkeep/releases](https://github.com/hpdowd/nextkeep/releases).

## [1.2.3] - 2026-06-30

### Fixed
- **Nested checklist items rendered as plain bullets.** A checklist item indented
  under another list item (e.g. via the editor's indent button) showed as a bullet
  point with the literal text `[ ] foo` / `[x] foo` instead of a checkbox, because
  the block parser's task-list pattern didn't allow leading whitespace the way the
  bullet/numbered-list patterns do — only the editing/tap-to-toggle side handled
  indentation correctly. `MdBlock.Task` now carries an indent like the other list
  blocks, and the renderer indents the checkbox row to match.

## [1.2.2] - 2026-06-30

### Added
- **Markdown tables render.** GFM-style pipe tables (with `:---:`-style column
  alignment) now show as a bordered, aligned grid in note cards and the editor
  preview, instead of raw `| a | b |` text. Read-only — there's no toolbar button
  to insert one yet.

## [1.2.1] - 2026-06-27

### Fixed
- **QR login scanner crashed instead of asking for the camera.** Tapping *Scan
  login QR code* threw `IllegalArgumentException: Can only use lower 16 bits for
  requestCode` and crashed before the permission dialog could appear — it only
  worked if camera access had already been granted by hand. The host activity is a
  `FragmentActivity` (needed for the biometric app lock), whose 16-bit request-code
  limit is violated by the AndroidX Compose result registry, so
  `rememberLauncherForActivityResult(...).launch(...)` threw on launch. Camera
  permission and the "open settings" trip now go through FragmentActivity-safe
  helpers (`ui/ActivityResultCompat.kt`), which also fixes the same latent crash in
  the in-app updater's install-permission prompt. (1.1 attempted this flow but
  never reached the crash, since it sits on the request itself.)

## [1.2] - 2026-06-23

### Added
- **Editor undo/redo** — Undo and Redo buttons in the formatting toolbar revert
  and re-apply text changes. Continuous typing collapses into one step, while
  toolbar actions and list continuations are their own steps (logic in
  `EditHistory`, unit-tested).
- **Quick-capture launcher shortcuts** — long-pressing the app icon offers
  *New note* and *New checklist*, opening straight into a fresh editor.
- **Home-screen widget** — a Glance widget listing recent notes; tap a note to
  open it or the *+* to start a new one. It refreshes as notes change.

### Changed
- Opening a note now places the caret at the **end** of its body, so editing
  continues from the existing text.

### Fixed
- **Spurious "(conflict)" duplicate notes.** A stale local etag could fail the
  `If-Match` on push and fork a copy even when nothing had really diverged. The
  pull now treats the etag as authoritative (timestamp is only a fallback for
  blank-etag servers), so etags no longer go stale, and the conflict handler
  re-checks the server's content and only keeps both copies when the text
  genuinely differs.

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

[1.2.3]: https://github.com/hpdowd/nextkeep/releases/tag/v1.2.3
[1.2.2]: https://github.com/hpdowd/nextkeep/releases/tag/v1.2.2
[1.2.1]: https://github.com/hpdowd/nextkeep/releases/tag/v1.2.1
[1.2]: https://github.com/hpdowd/nextkeep/releases/tag/v1.2
[1.1]: https://github.com/hpdowd/nextkeep/releases/tag/v1.1
[1.1-rc1]: https://github.com/hpdowd/nextkeep/releases/tag/v1.1-rc1
[1.0]: https://github.com/hpdowd/nextkeep/releases/tag/v1.0
