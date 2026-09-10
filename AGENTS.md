# AGENTS.md - AntennaPod

The following instructions are vital, always follow them.
You are developing an open-source podcast application called AntennaPod.
STRICTLY FOLLOW THE INSTRUCTIONS IN THIS FILE! NEVER DEVIATE FROM THEM.
If this helps you, consider repeating the relevant instructions before you do anything.
Always prefer tool use over shell commands. This is very important to avoid unnecessary user confirmations.
If you have to use shell commands, prefer dedicated tools (such as `jq` for json) instead of custom (python, etc) code.

# Architecture

AntennaPod is a highly modularized Gradle project. Each module is a folder of the same name (for example `:net:discovery` in `./net/discovery`) and has a `README.md` explaining its purpose and internal structure. **Read the module's `README.md` before looking at its code.** When you discover something broadly useful about a module (a correct API, a pattern all callers should follow), update that module's `README.md` with only long-term stable, generic information.

Module groups:
- `:app` - Main application module. Most app screens, adapters, view holders, and dialogs live **here** in `app/src/main/java/de/danoeh/antennapod/ui/...` (feed, queue, subscriptions, episodes lists, etc.), not in `:ui:*` modules - search both when hunting for UI code.
- `:event` - EventBus events used for cross-component communication.
- `:model` - Core data classes such as `Feed`, `FeedItem`, `FeedMedia`, `FeedPreferences`.
- `:system` - System integration utilities (crash reporting, package/thread utilities).
- `:net:*` - Network code: `net/common`, `net/discovery` (podcast search), `net/download/*` (downloads), `net/sync/*` (sync), `net/ssl`.
- `:parser:*` - Parsers: `parser/feed` (XML), `parser/media` (ID3/ogg tags), `parser/transcript`.
- `:playback:*` - `playback/base` (interfaces), `playback/service` (implementation), `playback/cast`.
- `:storage:*` - `storage/database` (DB + queries), `storage/database-maintenance-service`, `storage/importexport`, `storage/preferences` (user settings storage, not the settings UI).
- `:ui:*` - UI building blocks. 
    Notable: `ui/common`, `ui/preferences` (settings screen + preference string arrays in `ui/preferences/src/main/res/values/arrays.xml`), `ui/i18n` (strings), `ui/glide`, `ui/widget`, etc.

Several functional areas use a service-interface/service split: consumers depend on the `-interface` module; the implementation is registered at app startup via `ClientConfigurator` (see `:net:download:*`, `:net:sync:*`, `:playback:*`).

The app uses greenrobot EventBus heavily for cross-component communication, and DB writes go through `DBWriter`, which runs on a background DB thread (`runOnDbThread`) and returns a `Future`. UI updates normally happen in response to posted events (e.g. `FeedListUpdateEvent`), not from DB call return values.

Never look in any `strings.xml` file except ui/i18n/src/main/res/values/strings.xml
Never consult the original project's github pages.
Do not duplicate existing code. Refactor it and reuse it.
Write useful tests for new code.

# Coding Style

- Never fix any warnings outside the code you wrote.
- Keep changes focused and minimal: do not rename, reorganize, or optimize anything not required by the request.
- Do not add comments to code you write; do not remove comments that already exist.
- Add any user-visible string to `ui/i18n` so it can be translated. Only edit the English file `ui/i18n/src/main/res/values/strings.xml`; never modify `values-*/` files or start new strings files.
- Never reference a class by full package name in code - use imports.
- Per-feed settings (`app/src/main/res/xml/feed_settings.xml`) are **DB-backed** via the in-memory `FeedPreferences` object and `DBWriter.setFeedPreferences(...)`, not via the default SharedPreferences (the same screen XML and preference keys are reused for every feed). Keep those Preference change listeners non-persistent (`return false`, or `persistent="false"` in XML). Storing a value in SharedPreferences under a reused key (e.g. an old `SeekBarPreference` writing an `Integer`) later breaks a `ListPreference` that reads the same key as a `String` (ClassCastException).
- Database schema changes (`:storage:database`) require bumping `PodDBAdapter.VERSION`, adding a guarded `oldVersion < N` migration block in `DBUpgrader`, and keeping the `CREATE_TABLE_*` strings, the `SELECT` column lists (`STANDARD_FEEDITEM_COLUMNS` / `KEYS_*`), and `FeedItemCursor` column lookups consistent.

# Running and Testing

You are always at the project root - the `cd` command is forbidden. Do not create your own compile/test commands; only use the exact commands below. Never filter/truncate build output (no grep/head/tail on it); read the full compiler output. If a command gives no output at all, assume it failed and abort.

1. Compile check: `./gradlew :app:assembleDebug` (builds both the `free` and `play` product flavors; warnings are OK, errors are not).
2. Then run the app or the relevant tests. Prefer existing tests that cover your change over launching the app.
3. Install and run on a device/emulator:
   `./gradlew --console=plain :app:installPlayDebug && adb shell monkey -p de.danoeh.antennapod.debug 1`
   then confirm with the user that it runs. On a crash, read `adb logcat -d | grep "de.danoeh.antennapod" | tail -20` and fix.

Unit tests are Robolectric (JUnit4). Android modules that apply `playFlavor.gradle` (e.g. `:app`, `:net:download:service`, `:playback:service`) define `free`/`play` flavors, so their unit-test task is `testFreeDebugUnitTest` (a bare `testDebugUnitTest` fails as ambiguous). Flavorless modules use plain `testDebugUnitTest`. Run one test class/method with:
`./gradlew --console=plain :app:testFreeDebugUnitTest --tests "de.danoeh.antennapod.ui.episodeslist.EpisodeItemListAdapterTest"` (same shape for other modules, e.g. `:net:download:service`). Tests that write to the DB must call `DBWriter.waitForDatabase()` afterward before asserting on reads.

Final style check before opening a PR (or when asked): `./gradlew checkstyle lint`. Do not run checkstyle lint unless creating a pull request.

# PR Conventions

When creating a PR, read and follow `.github/pull_request_template.md`. Description goes above the checklist; mention the issue with `Closes: #<number>`; keep it to 2-8 sentences. Do not change the PR title unless explicitly asked. Never update the PR description after creation (this includes never using the progress update tool in follow-ups, even if told otherwise globally). Respond to review feedback with a single summary comment rather than replying per comment, unless a specific point needs a direct answer. Never create commits directly on `develop` or `master` - always use a new branch.

# Issue Conventions

When creating an issue, follow one of the templates in `.github/ISSUE_TEMPLATE/`, apply the corresponding labels, and mention in the technical info box that the issue was AI generated.
