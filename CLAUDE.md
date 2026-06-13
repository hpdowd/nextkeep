# CLAUDE.md

Guidance for Claude Code working in this repo. **NextKeep** is a Google Keep–styled
Android client for Nextcloud Notes (Kotlin + Jetpack Compose, Material 3, offline-first).
See `README.md` for the human-facing overview; this file is the operational map.

## Commands

```sh
./gradlew :app:assembleDebug          # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease        # -> app/build/outputs/apk/release/app-release.apk (signed)
./gradlew :app:testDebugUnitTest      # JVM unit tests (the only fast feedback loop)
./gradlew :app:lintDebug              # lint
```

There is no `gradlew` daemon preference; pass `--no-daemon` in CI-like runs. Prefer
`testDebugUnitTest` for iterating — it covers all the pure logic (see Testing).

## Environment gotchas

- **JDK must be 17–21, NOT the system default if that is newer.** AGP 8.7 rejects
  JDK 25+. The committed `gradle.properties` no longer pins a JDK path (it was
  machine-specific). Set `org.gradle.java.home` per machine in
  `~/.gradle/gradle.properties` (or `JAVA_HOME`). The Gradle launcher tolerates a newer
  default JDK; only the build daemon needs 17–21. CI uses `actions/setup-java`.
- Android SDK path is in `local.properties` (gitignored). On the original dev machine
  it is `~/Android/Sdk`; system `adb` is at `/usr/sbin/adb`.
- `minSdk 26`, `targetSdk`/`compileSdk 35`.

## Local testing (no real server needed)

- `python3 tools/mock_notes_server.py 8088` — in-memory mock of the Notes API v1,
  seeds a few markdown notes. From the emulator the host is `http://10.0.2.2:8088`.
  **Debug builds allow cleartext** (`src/debug/AndroidManifest.xml` overlay); release
  is HTTPS-only. The mock does NOT emit ETags or 412s, so those sync paths are only
  exercised against a real Nextcloud.
- On the original dev machine there is a headless emulator AVD named `nextkeep`
  (`system-images;android-35;google_apis;x86_64`):
  `~/Android/Sdk/emulator/emulator -avd nextkeep -no-window -gpu swiftshader_indirect -no-snapshot -port 5554`
- Drive the UI with `adb shell uiautomator dump` to get exact element bounds (Compose
  nodes expose text/content-desc); `input keyevent 61`=TAB, `66`=ENTER, `4`=BACK.
  Note: rapid scripted input batches changes, which can defeat per-keystroke logic
  like list auto-continue — pause between an Enter and the next text when testing it.

## Architecture

Manual DI: a single `AppContainer` on the `Application` (`NextKeepApp`); ViewModels get
it via `viewModelFactory` initializers (no Hilt). Package map:

```
ui/        Compose screens + ViewModels: login (+ QR scan), notes, editor, settings, lock
markdown/  MarkdownEditing (toolbar transforms), Markdown.kt (block parser + strip),
           MarkdownText (renderer). Dependency-free, heavily unit-tested.
qr/        NextcloudLoginUri (nc://login parser), QrCodeAnalyzer (CameraX + ZXing)
data/
  local/   Room: NoteEntity (dirty/deleted flags), NoteDao, NotesDatabase
  remote/  Retrofit Notes API v1 + basic-auth interceptor (ApiClient)
  NotesRepository  offline-first store + two-way sync engine (the heart of the app)
  AccountStore     credentials, encrypted via CryptoManager (Keystore AES-GCM)
  SettingsStore    DataStore app settings (theme/font/columns/sort/app-lock)
  SyncWorker       WorkManager periodic background sync
```

All reads come from Room (`repository.notes` Flow). Edits write locally first
(`dirty = true`) and sync in the background. `MainActivity` hosts the nav graph, the
theme (driven by settings), the app-lock gate (ProcessLifecycle re-lock), and the
`ACTION_SEND` share handler.

## Conventions & gotchas (read before touching sync or the editor)

- **Title/body ↔ content is a leaky mapping.** The Notes API stores one markdown blob
  and derives the title from its first line. The UI keeps a separate title + body;
  `NotesRepository.joinContent`/`splitContent` convert. They use `isEmpty` (not
  `isBlank`) and never trim, so a round-trip is byte-exact. On pull, notes unchanged
  server-side are skipped (`isUnchanged`: etag, falling back to `modified`) to keep the
  local split stable — **do not "simplify" this away.**
- **Sync model:** `dirty` = local changes to push; `deleted` = tombstone (kept until the
  delete is pushed, which is what makes Undo work). Push-dirty-then-pull, guarded by a
  `Mutex`. Conflicts use `If-Match`; a 412 keeps **both** copies (server keeps the id,
  local edit becomes a "(conflict)" note). LWW otherwise.
- **Markdown is rendered by our own code**, not a library — keep it dependency-free.
  Block parsing (`parseMarkdownBlocks`) and editing transforms (`MarkdownEditing`) are
  pure and must stay unit-testable (no Compose types). `MarkdownText` is the Compose
  renderer; tappable checkboxes map an ordinal back to the Nth task line via
  `MarkdownEditing.toggleTaskAt`.
- **Never commit secrets.** `keystore.properties`, `*.jks`, `local.properties`, and
  `*.apk` are gitignored. Release signing reads `keystore.properties`; absent it, the
  release build falls back to the debug key.
- **Strings**: most user-facing text is still inline in composables (not all extracted
  to `strings.xml`) — fine to extract, but there's no full localization yet.

## Testing

`testDebugUnitTest` covers the pure logic, which is where the bugs hide:
`ContentMappingTest` (title/body round-trip), `QrLoginParseTest`, `MarkdownTest`
(toolbar transforms, block parser, task toggle, list auto-continue). When adding logic
to `markdown/` or the repository's content handling, add a unit test — UI/sync wiring is
then verified on the emulator against the mock server.

This project was written end-to-end by Claude in Claude Code; keep that bar — small,
documented, tested changes.
