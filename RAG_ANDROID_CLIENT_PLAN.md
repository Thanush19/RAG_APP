# RAG ANDROID CLIENT — PLAN (PROPOSAL, NOT YET STARTED)

Status: proposed 2026-10-31. This file is the living plan for the
Android client, mirroring how
`RAG_PROJECT_PLAN_UPDATED_CURRENT_STATUS (1).md` works for the backend.
The backend is DONE (see that file, sections 10–22); the client
consumes it as-is, no new backend endpoints are required.

---

# 1. USE CASES (what the app must do)

1. Auth
   - Register, Login (`POST /api/v1/auth/register|login`)
   - JWT access + refresh tokens (`POST /api/v1/auth/refresh`),
     logout, "who am I" (`GET /api/v1/auth/me`)
2. Profile / Library page (the page described by the product owner):
   - List ALL PDFs the user added (`GET /api/v1/documents`)
   - Each card: fileName, size, date, STATUS badge
     (PROCESSING spinner / READY / FAILED)
   - VIEW doc → fetch `GET /api/v1/documents/{id}/bytes` as blob,
     render in-app PDF viewer
   - DELETE doc → `DELETE /api/v1/documents/{documentId}`
     (backend also cleans Qdrant vectors + Cloudinary file)
   - FAILED docs get a RETRY button →
     `POST /api/v1/documents/{id}/reprocess`
   - UPLOAD new PDF (multipart, 10 MB + PDF-only + 50 MB quota
     errors are already mapped server-side)
3. Ask (the hero feature):
   - `POST /api/v1/rag/ask/stream` (SSE): render tokens live,
     show sources (document + page + score) from the `sources` event,
     handle `error` events (`UPSTREAM_UNAVAILABLE` / `INTERNAL_ERROR`)
   - Optional per-question scope: pick one document
     (`documentId`) and a `minScore` slider
4. Search:
   - `POST /api/v1/search` → chunk cards with page + score,
     tap → open that document's viewer at the page
5. Session errors:
   - 401 → silent refresh attempt once, else back to Login
   - 503 `UPSTREAM_UNAVAILABLE` → friendly retry banner

# 2. NON-GOALS (for v1)

- No offline sync / local vector cache.
- No chat-history persistence beyond in-memory session.
- No account management beyond logout (profile edit API exists but
  is out of scope for v1).

# 3. TECH STACK

| Concern | Choice | Notes |
|---|---|---|
| Language | Kotlin 2.x | |
| UI | Jetpack Compose (single Activity) | BOM latest stable |
| Pattern | MVI (hand-rolled, no library) | see section 4 |
| Navigation | androidx.navigation-compose | |
| Networking | Retrofit + OkHttp + kotlinx.serialization | SSE via `okhttp-sse` EventSource → bridged to a Kotlin `Flow` |
| DI | Hilt | |
| Local storage | **DataStore (Preferences)** for key-value; **Room** for structured tables | both provisioned up-front as the local-storage mechanism "in case of need": DataStore = session tokens/flags/settings; Room = cached document list, ask/search history should we later persist it |
| Token storage | DataStore Preferences + `security-crypto` encrypted provider | access + refresh |
| Async | coroutines + StateFlow | one StateFlow per screen |
| Images/PDF render | `android.graphics.pdf.PdfRenderer` Compose pager (recommended) OR barteksc/PdfViewer lib | decision D2 |
| Min/target SDK | min 26, target 35+ | targetSdk 35+ required for adaptive/large-screen behavior |

# 4. MVI ARCHITECTURE (binding convention)

## 4.0 DI = Hilt (binding convention, skill for every session)

- All injection goes through Hilt; no manual service locators, no
  hand-wired singletons outside Hilt modules.
- ViewModels: `@HiltViewModel class X @Inject constructor(...)`,
  consumed via `hiltViewModel()` in composables.
- Singleton graph (`@InstallIn(SingletonComponent)`): `NetworkModule`
  (Json/OkHttp/Retrofit + the 3 API interfaces), `DatabaseModule`
  (Room), `TokenStore`/`RagStreamClient` as `@Singleton`.
- `@HiltAndroidApp` on Application, `@AndroidEntryPoint` on the single
  Activity. Fragments/Compose nav entries use `androidx.hilt:hilt-
  navigation-compose`.
- Future sessions MUST register new dependencies in a Hilt module
  rather than constructing them in features.


Per feature/screen, exactly three concepts:

```text
Intent (user action)  →  Actor/Reducer  →  State (immutable)
```

- `State`: `@Immutable data class` per screen
  (`content: ..., isLoading, error: ErrorMessage?, ...`).
  Streaming screens add an append-only `answerTokens: ImmutableList<String>`
  (or a single accumulating String + derived UI chunks).
- `Intent`/`Action`: sealed interface (`object SubmitQuestion`,
  `data class DeleteDocument(val id: UUID)`, ...).
- `Result`: sealed interface — internal events produced by the
  "actor" (network); the ViewModel reduces `Result → State`.
- ViewModel = MVI store:
  `private val _state = MutableStateFlow(XState())`
  `val state: StateFlow<XState>`
  `fun onIntent(intent: XIntent)` → launches actor → emits Results →
  `reduce(result)` produces the next state.
- NO business logic in composables; composables only render state and
  send intents. One state per screen; screens never talk to the API
  layer directly.
- SSE reducer rule: each `token` event appends to state → Compose
  recomposes progressively; `done` flips `isGenerating=false`;
  `error` keeps partial tokens and surfaces the banner (never wipes
  streamed content).

Package sketch:

```text
app/
  core/            design system (tokens, theme, components), network, storage
  data/            Retrofit services, SSE client, Room DAOs + DB,
                   DataStore (tokens/settings), repos (auth/docs/rag/search)
  domain/          models mirroring backend DTOs (Lombok-style plain data)
  feature/
    auth/          (mvi: AuthIntent/AuthState/AuthViewModel)
    library/      profile+library screen (list/view/delete/upload/retry)
    ask/          streaming chat-style screen
    search/       retrieval results screen
    viewer/       PdfRenderer pager screen
  navigation/      NavHost + routes + bottom/nav-rail switching
```

# 5. UI DIRECTION — "modern, NOT a generic chatbot"

Design principles (the product owner explicitly asked for this):

- Answer-first canvas, not bubbles: the streaming answer is a large
  typographic block (serif display for the answer, sans for chrome);
  sources appear as numbered pill chips `[S1]` anchored under it —
  tapping a chip slides in the source excerpt + "open in document".
- Prompt bar at the BOTTOM is a single growing field with inline
  scope controls (document chip, minScore dial) — no boxed
  "type a message" chat chrome, no avatar bubbles, no timestamps.
- Motion: Material 3 Expressive — springy enter/exit, animated
  state badges (PROCESSING morphs into READY), shared-axis nav
  between Library → Viewer.
- Color: dynamic color (Material You) with a hand-picked fallback
  palette; near-neutral surfaces, ONE accent; generous whitespace;
  edge-to-edge.

## 5.1 Skills / docs to apply (explicit requirement)

1. **Adaptive apps** —
   https://developer.android.com/develop/ui/compose/build-adaptive-apps
   - `calculateWindowSizeClass()` drives layout mode.
   - Compact (phone): stacked screens + bottom nav.
   - Medium/Expanded (foldable/tablet/desktop):
     **List-Detail canonical layout** — Library list + document
     detail side-by-side (`ListDetailScaffold` from Material3
     adaptive), Ask screen as two-pane (conversation + sources
     rail), `NavigationRail` instead of bottom bar.
   - Support multi-window + desktop windowing (resizable,
     no orientation assumptions, `WindowSizeClass` re-flow on drag).
   - Install the official Android CLI skill when starting UI work:
     `android skills add adaptive`
     (repo: github.com/android/skills → jetpack-compose/adaptive).
2. **Compose Styles** —
   https://developer.android.com/develop/ui/compose/styles/styles-update
   - IMPORTANT (verified 2026-10-31): the initial Styles API was
     TEMPORARILY REMOVED and is being redesigned
     (`stylePropertyOf`/`styleLocalOf` direction; still
     `@Experimental` in Compose 1.13). Do NOT build on it yet.
   - Instead: our own design-token layer in `core/designsystem`
     (typed `AppColors`, `AppType`, `AppShapes`, `AppMotion`
     provided via CompositionLocals) shaped so it can migrate to
     the re-evaluated Styles API later with minimal churn.
   - Use stable **Material 3 Expressive components** from the
     current material3 release (expressive shapes,
     `FloatingActionButton` variants, spring motion specs).

3. **Hilt DI** — binding convention in section 4.0; every feature
   wires its dependencies through Hilt modules, ViewModels via
   `@HiltViewModel` + `hiltViewModel()`. (Applied from M0 onward.)

## 5.2 Screen inventory

| Route | State highlights | Intents |
|---|---|---|
| login / register | email, password, inFlight, error | Submit, OpenRegister |
| library (profile) | documents[], uploadProgress?, error | Load, Upload(file), View(id), Delete(id), Retry(id) |
| viewer | pageCount, currentPage, bytes-state | Open(id, page?), Swipe(next/prev) |
| ask | tokens, isGenerating, sources[], scopedDocId?, minScore | Ask(question), ScopeToDocument, SetMinScore, Stop(abort SSE) |
| search | query, results[chunk+score], minScore | Search, OpenResult(chunk→viewer@page) |
| root shell | windowSizeClass → nav bar/rail | — |

# 6. API INTEGRATION NOTES (backend contract, already built)

- Auth header: `Authorization: Bearer <access>`; on 401 refresh once.
- Backend error envelope is `ApiErrorResponse` (code/message/field
  errors) → map `UPSTREAM_UNAVAILABLE`→"AI service busy, retry",
  `DOCUMENT_PROCESSING_FAILED`→ show RETRY CTA on the doc card.
- SSE contract (backend plan section 22): events `sources` →
  `token`(`{"t":"..."}`) → `done`(`{"answer":...}`) / `error`
  (`{"code","message"}`). POST body — EventSource is GET-only, so
  implement the stream with OkHttp `Request(POST)` + a manual
  `data:`/`event:` line parser exposed as `Flow<StreamEvent>`
  (or use a POST-capable SSE lib). Timeout: honor ~300 s emitter.
- Upload: multipart field name must match backend
  (`file`), enforce client-side 10 MB pre-check for nicer UX.
- Base URL configurable per build flavor (debug emulator → host LAN
  IP; the Spring app runs on 8080).

# 7. TESTING

- MVI reducers are pure functions → unit-test `reduce(state, result)`
  directly (fast, no Android).
- Repo/API layer: MockWebServer (incl. an SSE playback test:
  sources→tokens→done ordering + mid-stream error keeps tokens).
- ViewModel tests with Turbine; Compose UI smoke tests for library
  status badges + streaming text append.

# 8. MILESTONES

- M0: project scaffold — **COMPLETE 2026-10-31** (see 10).
- M1: auth flow end-to-end with token refresh. — NEXT
- M2: Library/profile page — list, upload, delete, view (PdfRenderer),
  retry; adaptive list-detail at medium+.
- M3: Ask with live SSE streaming + sources chips.
- M4: Search + viewer deep-link to page; polish motion, dynamic color,
  adaptive rails.

# 9. DECISIONS (settled with owner 2026-10-31)

- D1 Networking: **Retrofit + OkHttp** (manual POST-SSE parser
  exposed as Flow, per section 6).
- D2 PDF view: **in-house `PdfRenderer` Compose pager** (no deps,
  page targeting for search deep-links).
- D3 Scope: **phone-first; adaptive (list-detail, nav rail) from
  milestone M2 onward**.
- D4 Location: **`C:\Users\Thanush\projects\TODO\client-android`**
  (git strategy decided later).

# 10. M0 IMPLEMENTATION RECORD (2026-10-31) — COMPLETE

Location: `C:\Users\Thanush\projects\TODO\client-android`
(module `:app`, namespace `com.ragclient.android`).

## 10.1 Settled toolchain (do not re-derive)

- **AGP 9.3.0** — HARD CEILING: the owner's Android Studio supports
  at most 9.3.0 (9.5.0 was tried and rejected by the IDE). Never bump
  past 9.3.0 without confirming the Studio version.
- **Gradle 9.7.1** wrapper (jar + properties copied from `backend/`;
  distribution already cached locally).
- **compileSdk 37 / targetSdk 37 / minSdk 26.**
- **Kotlin via AGP 9 built-in support.** `org.jetbrains.kotlin.android`
  MUST NOT be applied — AGP 9 fails the build if it is. Plugins in
  `app/build.gradle.kts`: `com.android.application`,
  `org.jetbrains.kotlin.compose`, `org.jetbrains.kotlin.serialization`,
  `com.google.devtools.ksp`, `com.google.dagger.hilt.android`.
  JVM target set with `android { kotlin { jvmToolchain(17) … } }`,
  plus `compilerOptions { freeCompilerArgs.add("-Xskip-prerelease-check") }`.
- **Hilt 2.60.1** (2.58 breaks with AGP 9 — "Could not find the AGP
  base extension"). Needs the KSP compiler artifact
  `com.google.dagger:hilt-compiler` (catalog alias `hilt-compiler`;
  do NOT map it to `androidx.hilt:hilt-compiler`, which is a different
  library and triggers "no com.google.dagger:hilt-compiler dependency
  was found"). KSP 2.3.13, Kotlin catalog version 2.3.21.
- **Compose BOM 2026.09.00**, Material 3, navigation-compose 2.10.2,
  hilt-navigation-compose 1.4.0, lifecycle 2.11.0.
- **Retrofit 3.0.0 + OkHttp/okhttp-sse 5.5.1**, kotlinx-serialization
  1.12.0, coroutines 1.11.0, Room 2.8.5 (+ KSP, schema exported to
  `app/schemas`), DataStore-preferences 1.3.0, security-crypto
  1.1.0-alpha06, Turbine 1.2.1, MockWebServer 5.5.1.
- **Retrofit 3 converter gotcha (cost hours):** the kotlinx-serialization
  converter's public API is the top-level extension function, NOT the
  factory class — `KotlinSerializationConverterFactory` is an internal
  synthesized facade (`javap -v -p` shows `k=2`). Correct usage:
  `import retrofit2.converter.kotlinx.serialization.asConverterFactory`
  then `.addConverterFactory(json.asConverterFactory("application/json".toMediaType()))`.
- **Icons:** only the core icon set is on the classpath —
  `Icons.Filled.Stop` and `Icons.AutoMirrored.Filled.Chat` do NOT
  resolve. Use `Icons.Filled.Close` / `Icons.Filled.Info` / Home /
  Search, or add `androidx.compose.material:material-icons-extended`
  deliberately (APK size).

## 10.2 Environment constraints

- `ANDROID_HOME` is unset; SDK lives at
  `C:\Users\Thanush\AppData\Local\Android\Sdk` and `local.properties`
  points `sdk.dir` there.
- **No `cmdline-tools` / `sdkmanager`.** API 37 was installed manually:
  download `https://dl.google.com/android/repository/platform-37.0_r02.zip`
  and unpack to `Sdk/platforms/android-37`. (A `platform-37.2_r02.zip`
  revision also exists; keep it in a separate folder so it doesn't
  clobber the base platform.)
- `DebugConfig`/`BuildConfig`: `API_BASE_URL = "http://10.0.2.2:8080/"`
  (emulator → host), `usesCleartextTraffic="true"` for debug.

## 10.3 What exists

- `core/designsystem`: typed tokens `AppColors`/`AppType`/`AppShapes`
  + `RagClientTheme` (emerald-on-paper, serif answer display type,
  28dp prompt-bar shape). CompositionLocal-based by design so it can
  migrate to the redesigned Styles API later. Dynamic color deferred
  to M4.
- `core/mvi/BaseViewModel<State, Intent, Result>`: `onIntent` →
  abstract `handle` → `reduceResult` → pure `reduce`. `setState` takes
  a **receiver** lambda (`State.() -> State`) — a plain
  `(State) -> State` param breaks `state.copy(…)` at call sites.
- `domain/model`: `DocumentStatus`, `RagSourceModel`, `StreamEvent`
  (Sources/Token/Done/Error) mirroring backend plan §22.
- `data`: DTOs + Retrofit interfaces for auth/documents/search/rag
  (paths verified against the controllers), `AuthInterceptor`,
  `NetworkModule`, `RagStreamClient` (okhttp-sse POST →
  `callbackFlow<StreamEvent>`, named-event parsing, `awaitClose`
  cancel), `TokenStore` (DataStore prefs; encrypted-provider migration
  is M1), Room `document_cache` + `DatabaseModule`.
- `feature/ask`: full MVI vertical slice (contract, reducer,
  view model, answer-first `AskScreen` with `S1 · p<n>` source pills).
  `feature/auth|library|search|viewer` are compilable stubs.
- `navigation/AppNavHost`: phone-first bottom bar (Library/Ask/Search);
  rail + list-detail land in M2 via `calculateWindowSizeClass`.
- Tests: `AskReducerTest` (3 pure reducer cases incl. the
  "error preserves streamed tokens" rule).

## 10.4 Verification evidence

- `./gradlew :app:assembleDebug` → `GRADLE_EXIT=0`,
  **BUILD SUCCESSFUL in 36s**, artifact
  `app/build/outputs/apk/debug/app-debug.apk` (15,322,038 bytes).
- `./gradlew :app:testDebugUnitTest` → `AskReducerTest`
  `tests="3" skipped="0" failures="0" errors="0"`
  (`app/build/test-results/testDebugUnitTest/`).
- Rule re-applied from backend plan 21.7: never trust a piped
  command's exit code — capture `GRADLE_EXIT=$?` and grep the log for
  `BUILD SUCCESSFUL|FAILED` plus the XML results.
- The temporary `printCompileCp` diagnostic task used to prove the
  converter jar was on `debugCompileClasspath` has been REMOVED.

## 10.5 M1 scope (next)

Login/register screens on the existing `AuthApi`, 401 → refresh-once
interceptor, migrate `TokenStore` to the security-crypto encrypted
DataStore provider (call sites unchanged), session-gated
`startDestination`, and a MockWebServer auth round-trip test.
