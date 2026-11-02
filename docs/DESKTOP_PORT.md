# Desktop Port Scope (Compose Desktop, Windows)

Goal: ship `MyBrain` as a **Windows executable** reusing the existing Kotlin Multiplatform
codebase. This document scopes the work, grounded in the actual codebase (surveyed 2026-10-30).

## Why this is feasible at all

- The entire UI + ViewModels + domain + RAG engine live in `commonMain` — Kotlin compiles
  them to JVM unchanged. `composeApp/src/commonMain/.../MyBrainApp.kt` is the shared
  application composable; desktop entry is just `Window { MyBrainApp() }`.
- **Room already uses the KMP setup**: `BundledSQLiteDriver` (cross-platform native SQLite),
  version 8 schema + migrations in `commonMain`. A JVM database is the same builder minus
  `Context`.
- **Preferences already use DataStore** — a KMP library; only DI wiring needs a JVM actual.
- Android-only code is quarantined in `androidMain` source sets behind ~12 expect/actual
  pairs (see inventory). The blast radius is contained.

## Port surface inventory

| Component | Location | Verdict | Work |
|---|---|---|---|
| UI, ViewModels, navigation | `*/presentation`, `composeApp/commonMain` | ✅ FREE | none expected |
| Domain + use cases | `*/domain` | ✅ FREE | none |
| RAG engine (chunks, embeddings, retriever, tool) | `ai/data/.../rag` | ✅ FREE | pure Kotlin + HTTP |
| `retrieveKnowledge` tool + assistant | `ai/data` | ✅ FREE | Koog runs on JVM |
| Chat providers (Ollama/LM Studio/OpenAI/Gemini API) | `ai/data` | ✅ FREE | HTTP-based |
| Room DB v8 + migrations + FTS | `core/database` | 🟢 TRIVIAL | JVM `DatabaseModule`: same builder, file path from user home, no `Context` |
| DataStore preferences | `core/preferences` | 🟢 TRIVIAL | JVM actual: `PreferenceDataStoreFactory` with okio path |
| `PlatformUtils`, `ClipboardUtils`, `AppVersion`, `ConcurrentMap` | expect/actual | 🟢 TRIVIAL | small JVM actuals (AWT clipboard, `ConcurrentHashMap`) |
| `PermissionState` | expect/actual | 🟢 TRIVIAL | JVM actual: always-granted stub |
| `CompressionManager` (sync payloads) | `local-sync` | 🟢 TRIVIAL | JVM `ZipFile`/`java.util.zip` |
| File storage (note files, external folder) | `core/storage` (SAF `FileUtilsRepositoryImpl`) | 🟡 MODERATE | rewrite on `java.nio.file` + desktop file chooser; folder URIs → desktop equivalents |
| `ShareNoteOptions` (share intents) | expect/actual | 🟡 MODERATE | JVM actual: "Save as…" / "Copy" / OS-open |
| `AppLockManager` (biometric + PIN) | `composeApp` expect/actual | 🟡 MODERATE | Phase 1: JVM actual = always-unlocked; PIN-only later if wanted |
| `EncryptionManager` (Android Keystore) | `local-sync` | 🟡 MODERATE | JVM actual: file-based key (DPAPI optional later) |
| **Reminders / task alarms** | `core/alarm` + Android scheduler | 🟠 SUBSTANTIAL | JVM `AlarmScheduler` actual. Phase 1: in-app-only reminders (fire while app runs). Windows toast notifications possible later |
| **Gemini Nano** | `ai/data/androidMain` | 🟠 (simple stub) | JVM actual: `status = Unavailable`, feature disabled; all other AI providers unaffected |
| Use this device as **sync server/pairing** (QR pair, discovery, tombstone worker) | `local-sync` | 🟠 SUBSTANTIAL | QR pairing without camera → manual IP entry only (dialog already exists); `WorkManager` tombstone pass → app-lifetime coroutine. Full re-iplementation only if sync-to-phone is wanted |
| Home-screen widget, Android app-functions (Siri-like) | `widget`, `appfunctions` | ⛔ EXCLUDED | Android-only; desktop target simply doesn't depend on them |
| Back-gesture handling, bottom nav viewport insets | `core/ui` android bits | 🟡 minor polish | desktop window chrome handles this naturally |

## Phased plan

### Phase 1 — Desktop skeleton that boots (est. 1–2 sessions)
New `desktopMain` target in `composeApp` (or sibling `desktopApp` module):
`main.kt` with `Window(...) { MyBrainApp() }`, JVM Koin wiring, stub JVM actuals for the
🟢-trivial list. Acceptance: app opens, dashboard renders, notes/diary/bookmarks/tasks CRUD +
search + **RAG works against local Ollama**, data persists in `%APPDATA%/MyBrain/`.

Deliverable: functional desktop build, not yet packaged.

### Phase 2 — Real platform integration (est. 2–3 sessions)
- File storage rewrite (NIO + desktop file/folder pickers) for file-backed notes and
  external-notes-folder feature
- Share/save alternatives, clipboard
- EncryptionManager JVM actual
- UI polish: window sizing, font scaling, keyboard shortcuts (Ctrl+S, Ctrl+F, Esc),
  no touch-only affordances

Deliverable: feels like a legitimate Windows app, not an emulation.

### Phase 3 — Reminders & long-running service (est. 1–2 sessions)
- JVM `AlarmScheduler` actual + background coroutine scheduler while app is running
- Optional: Windows toast notifications (java-doc / JNA, or `net.java.openjfx` tray)
- Tray icon + minimize-to-tray so reminders work even when closed to tray

### Phase 4 — Packaging (est. 1 session)
Compose Desktop `packageDistributionForCurrentOS` → **`.exe`/`.msi` installer** for Windows,
app icon, start-menu entry, per-user install. Ship via GitHub Release on your fork.

### Phase 5 (optional) — Sync with the phone
Desktop as sync peer: manual IP pairing, JVM crypto, tombstone cleanup. Only worth it if
you actually want phone↔PC sync; otherwise permanently out of scope.

## Risks & mitigations

| Risk | Mitigation |
|---|---|
| Compose Multiplatform desktop quirks (fonts, DPI) | Known-solved; Windows is the most tested desktop target of CMP |
| Room JVM edge cases (FTS4 on bundled driver) | The driver is the same one already used — `FTS4` works; test search first thing in Phase 1 |
| Koin annotation compiler on JVM target | Same plugin, same patterns as androidMain — DI modules just get `jvmMain` twins |
| Scope creep into re-UX | Phase 1 explicitly does NOT restyle: phone layout runs as-is in a window |
| Gemini Nano calls leaking into JVM code path | Only entry point is `GeminiNanoService` — JVM actual stubs it cleanly |

## Est. total effort
**5–8 focused sessions** to a packaged `.exe` with core features (Phases 1–4).
Reminders-as-true-notifications and sync (5) are separate follow-ups.

## Recommended order if user just wants "my notes + RAG on the PC"
Phase 1 alone delivers that. Everything after is polish breadth.
