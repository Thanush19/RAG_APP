# RAG ANDROID CLIENT — PLAN (M0–M4 BUILT; see 10 and 11 for records)

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

> **SUPERSEDED IN PART (owner decision, 2026-10-31 late session, D5):**
> the shell is now ChatGPT-mobile-shaped — a **left navigation drawer**
> (profile header, New chat, chat history, uploaded documents, search,
> profile, sign out) with **no bottom tab bar**, and a conversation
> thread as the chat surface. The typography, accent, source-pill and
> growing-prompt-bar rules below still apply inside that shell.

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

## 5.2 Screen inventory (as built — drawer shell, D5)

| Route | State highlights | Intents |
|---|---|---|
| sign-in gate (`RagRoot`) | session.isAuthenticated from `SessionStore` | — |
| login / register | mode, email, password, name, inFlight, error | ChangeField, SwitchMode, Submit, SignOut |
| `chat/{conversationId}?scope={documentId}` (start `chat/latest`) | messages[], prompt, isGenerating, streamingText, streamingSources, scopedDocumentId, error | ChangePrompt, Send, Stop, ScopeTo, ClearScope, DismissError |
| `viewer/{documentId}/{page}` | pageCount, initialPage, pages(index→bitmap), error | PageVisible, Retry |
| `search` | query, minScore, results[], isLoading, error | ChangeQuery, ChangeMinScore, Run, Clear |
| `profile` | session, document/chat counts, base URL | SignOut |
| drawer (`AppShell`) | conversations[], documents[], uploadInFlight, banner | Refresh, Upload(uri), DeleteConversation, DeleteDocument, RetryDocument, SignOut, BannerShown |

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
- M1: auth flow end-to-end with token refresh — **COMPLETE** (phase B, §11).
- M2: Library/profile — **COMPLETE as the drawer's Documents section**
  (list, upload, delete, retry, PdfRenderer viewer) + Profile screen
  (phase C/D, §11). Bottom tabs were dropped per D5, so adaptive
  list-detail is deferred, not delivered.
- M3: Ask with live SSE streaming + sources chips — **COMPLETE** as the
  conversation thread (phase C/E, §11).
- M4: Search + viewer deep-link to page — **COMPLETE** (phase F, §11);
  dynamic color + adaptive rails + motion polish still open.

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
- **Icons:** the glyph set is `br.com.devsrsouza.compose.icons:feather`
  behind `core/designsystem/AppIcons`, tinted `AppColors.Icon` (§27).
  Before §27 this line read "only the 63-icon `material-icons-core` set is on
  the classpath — `Icons.Filled.Stop` / `...Chat` do not resolve"; no `Icons.*`
  reference remains in `app/src/main`.

## 10.2 Environment constraints

- `ANDROID_HOME` is unset; SDK lives at
  `C:\Users\Thanush\AppData\Local\Android\Sdk` and `local.properties`
  points `sdk.dir` there.
- **No `cmdline-tools` / `sdkmanager`.** API 37 was installed manually:
  download `https://dl.google.com/android/repository/platform-37.0_r02.zip`
  and unpack to `Sdk/platforms/android-37`. (A `platform-37.2_r02.zip`
  revision also exists; keep it in a separate folder so it doesn't
  clobber the base platform.)
- `DebugConfig`/`BuildConfig`: `API_BASE_URL` — emulator→host alias at M0,
  now `http://127.0.0.1:8080/` plus an `adb reverse` tunnel (see section 12),
  `usesCleartextTraffic="true"` for debug.

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

Superseded — M1 shipped in the same session; see section 11.

# 11. PHASES A–F IMPLEMENTATION RECORD (2026-10-31) — COMPLETE

Owner brief for this batch: run the remaining milestones one by one
without stopping for approval, always preview the next phase, ship
**two product flavors** (dev + prod, same backend except the URL), and
match the **ChatGPT mobile** structure — left drawer holding chats and
uploaded documents, chat section, proper auth and profile, **no bottom
tab bar**.

## 11.1 Phase A — product flavors

`app/build.gradle.kts` gained `flavorDimensions += "environment"` with
`dev` and `prod`, each supplying its own `API_BASE_URL` and an
`IS_DEV_BUILD` boolean. Both default to a local origin today — dev uses
`http://127.0.0.1:8080/` (see section 12 for why that address and not
`10.0.2.2`); **prod's real origin is supplied at build time and the
placeholder is `https://api.example.invalid/`** — see section 13.
Cleartext http is granted to the dev flavor only (section 13.2).
`dev` carries `applicationIdSuffix = ".dev"` so
both APKs install side by side. Generated BuildConfig verified:
dev → `com.ragclient.android.dev` / `IS_DEV_BUILD=true`, prod →
`com.ragclient.android` / `IS_DEV_BUILD=false`. The dev login screen
prints the base URL + "dev" so a tester can never confuse the builds.
Variant tasks are `assembleDevDebug` / `assembleProdDebug` /
`assembleProdRelease`, unit tests `testDevDebugUnitTest`.

## 11.2 Phase B — auth

- `data/auth/AuthRepository`: login, register (backend register returns
  no token, so it signs in immediately afterwards), signOut
  (best-effort server revoke then local clear), `refreshAccessToken()`
  behind a `Mutex` so a burst of 401s triggers one refresh.
- `data/network/TokenAuthenticator` (OkHttp `Authenticator`): refresh-once
  replay, never for `/auth/*` paths, re-uses an already-refreshed token
  when several requests 401 at once, and marks the replay with a request
  tag (`RetryMarker`) because `Response.priorResponses()` is not public
  in OkHttp 5. A failed refresh — or a missing refresh token — clears the
  session so the gate sends the user back to sign-in.
- `data/network/AuthInterceptor` attaches the bearer header to
  everything except `/auth/*`.
- `data/network/ApiErrorMapper` turns the backend `ApiErrorResponse`
  envelope (plus `IOException`) into `ApiFailure(message, code)`; the
  body is read **once** because `ResponseBody.string()` is not re-readable.
- `data/storage/SessionStore` interface + `TokenStore` (DataStore
  Preferences) implementation, bound with `@Binds` in `StorageModule`
  per section 4.0. This is what makes the whole auth chain unit-testable
  without an Android context.
- `feature/auth` MVI: `AuthContract` (mode/email/password/name/inFlight/
  error/isSignedIn), `AuthReducer` with client-side validation mirroring
  the backend bean rules (`@Email`, password 8–72, name required for
  register), `AuthViewModel` that also mirrors the session flow (a token
  cleared anywhere flips the UI back to sign-in).
- **Session gate:** `navigation/RagRoot.kt` replaces `AppNavHost.kt`.
  Signed-in → drawer shell, otherwise → sign-in. Cold start with a valid
  token lands directly in the last chat.

## 11.3 Phase C — drawer shell + chat section

- `feature/shell/AppShell.kt`: `ModalNavigationDrawer` + custom light top
  bar (menu · wordmark · search · new chat) + `NavHost`. Drawer order is
  profile header → New chat → **Chats** (title from the first question,
  delete per row) → **Documents** (status dot + label, open / ask-this-doc
  / retry-when-failed / delete) → Search · Profile · Sign out. There is
  **no bottom NavigationBar anywhere.**
- `Routes`: `chat/{conversationId}?scope={documentId}`, `search`,
  `profile`, `viewer/{documentId}/{page}`. `chat/latest` is the start
  destination; `scope=none` means "ask across all documents" (sentinel
  handled by `Routes.scopeOf`).
- Chat history is local (Room `conversations` + `messages`, schema
  version 1 → **2** with an explicit `MIGRATION_1_2` — no destructive
  fallback). `ConversationRepository` owns threads, titles, scope and
  message persistence; sources are stored as JSON on the message row.
- `feature/chat` MVI replaces `feature/ask` (deleted, together with the
  library stubs): `ChatContract` + `ChatReducer` + `ChatViewModel`
  streaming from `RagStreamClient`, `ChatScreen` = message thread
  (user bubble right, serif answer left, `S1 · p3` pills), streaming
  caret row while generating, dismissible error banner, growing prompt
  bar whose action button becomes Stop while generating.
- Reducer rule carried over: **a failure never discards the transcript**
  — the partial answer is persisted as a message and the banner explains
  it (`UPSTREAM_UNAVAILABLE` → "The AI service is busy — please retry.").

## 11.4 Phase D — documents + viewer

- `DocumentRepository`: list/upload/remove/reprocess against the verified
  endpoints, mirrored into the Room `document_cache` so the drawer paints
  offline; `pdfBytes()` uses the new `GET /api/v1/documents/{id}/bytes`.
- `ShellViewModel` polls the list every 5 s while any document is
  `UPLOADED`/`PROCESSING`, every 30 s otherwise, and turns outcomes into a
  drawer banner.
- Upload: `ActivityResultContracts.OpenDocument` → `UploadStager` copies
  the `content://` uri into `cacheDir/uploads` → multipart `file` part.
- `data/pdf/PdfRepository` + `feature/viewer`: `android.graphics.pdf`
  renderer, pages rendered lazily into `ImageBitmap`s behind a `Mutex`
  (PdfRenderer allows one open page at a time), `HorizontalPager`, and
  `initialPage` from the deep-link so citations land on the right page.

## 11.5 Phase F — search

`feature/search` real MVI vertical slice: query field, minScore slider
(0–0.6, default 0.05), result cards (file · page · score · snippet) that
tap through to the viewer at that page, and an "ask this document" action
that opens a **new scoped conversation** (`chat/new?scope=<id>`).

## 11.6 Decisions added by this batch

- **D5 Shell**: ChatGPT-mobile style — left drawer + chat thread, bottom
  tab bar removed (owner instruction, overrides the M0 nav design).
- **D6 Token storage**: app-private DataStore Preferences, **not**
  `androidx.security:security-crypto` — the only artifact is
  `1.1.0-alpha06` and its Keystore assumptions are not stable against the
  current platform. The dependency was dropped. Revisit if a
  Keystore-backed store ships stable; call sites are behind
  `SessionStore`, so the migration is one class.
- **D7 Flavors**: `dev`/`prod` on the `environment` dimension, URL via
  `BuildConfig`, `@BaseUrl` qualifier injected into Retrofit and the SSE
  client (that is what made the MockWebServer SSE replay test possible).
- **D8 Chat history**: client-side Room threads; the backend stays
  one-question-at-a-time.

## 11.7 Verification evidence

- `./gradlew :app:assembleDevDebug :app:testDevDebugUnitTest` →
  `GRADLE_EXIT=0`, **BUILD SUCCESSFUL**, `app-dev-debug.apk` 15,643,175 B.
- `./gradlew :app:assembleProdDebug` → `app-prod-debug.apk` 15,564,701 B;
  `:app:assembleProdRelease` → BUILD SUCCESSFUL (needs network — the
  `lintVitalAnalyzeProdRelease` step cannot resolve
  `lint-gradle:32.3.0` with `--offline`; not a code failure).
- Unit tests, `app/build/test-results/testDevDebugUnitTest/`, all green
  (25 tests, 0 failures/errors):
  `AuthFlowTest` 5 (login, rejected login, 401→refresh→replay, refresh
  failure clears session and does not loop, auth endpoints carry no
  bearer), `RagStreamPlaybackTest` 3 (sources→tokens→done, mid-stream
  error keeps tokens, scope+minScore in the POST body),
  `AuthReducerTest` 7, `ChatReducerTest` 6, `SearchReducerTest` 4.
- **Not yet verified**: on-device behaviour. There is no emulator/AVD on
  this machine (no `cmdline-tools`); the app runs on a physical Samsung
  SM-S711B over USB, so drawer gesture, PdfRenderer rendering and live
  SSE against the running backend still need a device pass.

# 12. DEVICE CONNECTIVITY INCIDENT (2026-10-31) — RESOLVED

Symptom: login on the physical device showed *"Can't reach the server.
Check that the backend is running."* while the backend answered fine on
the host.

- That copy comes from `ApiErrorMapper` mapping `IOException` →
  `CONNECTION_FAILED`, i.e. the TCP request never completed. An HTTP
  status would have produced different copy, so this was never a
  credentials or backend-logic problem.
- Root cause: both flavors pointed at `http://10.0.2.2:8080/`, which is
  the **emulator-only** loopback alias. The device is a real phone.
- Secondary: the host Wi-Fi sits on `192.168.1.5/24` while the phone sits
  on `192.168.0.104/24`, so a LAN-IP base URL would not have worked
  either without re-networking.
- Fix: flavors now use `http://127.0.0.1:8080/`, which is valid on the
  emulator and on a USB device alike once the tunnel exists.

**Rule — every dev session must re-establish the tunnel** (it dies with
the device reconnect and with an adb server restart):

```
"<android-sdk>/platform-tools/adb.exe" reverse tcp:8080 tcp:8080
"<android-sdk>/platform-tools/adb.exe" reverse --list   # UsbFfs tcp:8080 tcp:8080
```

adb is not on PATH in Git Bash; full path is
`C:/Users/Thanush/AppData/Local/Android/Sdk/platform-tools/adb.exe`.
`./gradlew installDevDebug` works and installs on `SM-S711B - 16`.
Both `com.ragclient.android` (prod) and `com.ragclient.android.dev`
coexist because of the `.dev` applicationId suffix.

Diagnosis evidence from this pass: backend LISTENING on `0.0.0.0:8080`
(PID 4244), Neon reachable (`Test-NetConnection … :5432` → True),
`POST /auth/login` with an unknown email returned **500** — see below.

## 12.1 Backend status-code bug found by this incident

`InvalidCredentialsException` had no `@ExceptionHandler`, so a bad login
fell through `GlobalExceptionHandler`'s catch-all and returned
`500 INTERNAL_SERVER_ERROR` with "An unexpected error occurred". The
client would have shown server-failure copy for a wrong password.

- Fixed in `common/exception/GlobalExceptionHandler.java`: dedicated
  handler → `401` / `INVALID_CREDENTIALS` / "Invalid email or password",
  logged at WARN (credentials rejection is not a server error).
- Needs a backend restart to take effect; the running process still
  returns 500 for bad credentials.
- Same class of gap remains for `refresh`: `RefreshTokenService` throws
  bare `IllegalArgumentException("Invalid refresh token")`, which also
  lands in the catch-all (500 instead of 401). Left alone deliberately —
  mapping `IllegalArgumentException` globally would turn genuine
  programming errors into 401s. A typed `RefreshTokenRejectedException`
  is the right fix when someone touches that path. The Android client is
  unaffected either way: any refresh failure clears the session.

## 12.2 Chat will fail until the backend restarts — upstream LLM outage

With the tunnel in place, login, `/auth/me`, `/documents` and `/search`
all returned 200 for `test4@gmail.com`, but `/rag/ask` hung ~98s and
`/rag/ask/stream` returned a bare 403 / connection reset. Root cause is
upstream: the configured NVIDIA chat model `openai/gpt-oss-20b` stopped
answering at all (no response headers, stream and non-stream alike)
while embeddings kept working. `app.ai.nvidia.chat-model` is now
`nvidia/nemotron-3.5-lightning-30b-a3b` (verified 200, 99 stream chunks).

**So the expected device result right now: sign-in and the document
drawer work; sending a chat message fails until the backend is
restarted.** Full diagnosis in backend plan section 23.

**RESOLVED 2026-11-01** — the backend was restarted (now `./gradlew
bootRun`, pid 6692, terminal-owned) and chat works against the new model:
`/rag/ask` answered in 10.2 s with `[S1][S2]` citations and
`/rag/ask/stream` emitted `sources` + 60 `token` frames + `done`.
Probe table in backend plan 24.2.1.

# 13. PROD CONNECTIVITY — HOW THE APP REACHES A DEPLOYED BACKEND

Section 12's fix (`127.0.0.1` + `adb reverse`) is a **dev-only** answer:
the tunnel exists only over USB with adb running, so a prod build could
never use it. Before this change both flavors also carried
`usesCleartextTraffic="true"` from the shared manifest, which meant a
prod APK was permitted to talk plain http and had no way to be told
where the real server was short of editing `build.gradle.kts`.

## 13.1 Base URL is a build-time knob

`app/build.gradle.kts` reads the origin from Gradle properties, so
pointing prod at a deployment is a flag, not a source change:

| Property | Default | Notes |
| --- | --- | --- |
| `rag.devBaseUrl` | `http://127.0.0.1:8080/` | needs `adb reverse tcp:8080 tcp:8080`, or set to the PC's LAN ip for a phone on the same Wi-Fi |
| `rag.prodBaseUrl` | `https://api.example.invalid/` | RFC-2606 `.invalid` never resolves, so an unconfigured prod build fails with the app's own "can't reach the server" copy instead of silently hitting somebody's host |

```
./gradlew assembleProdRelease -Prag.prodBaseUrl=https://api.yourhost.example/
```

Both defaults are commented in `gradle.properties` for discoverability.

## 13.2 Cleartext is now a dev-only capability

- `app/src/main/AndroidManifest.xml`: `usesCleartextTraffic` **removed**.
- `app/src/dev/AndroidManifest.xml` (new): merged into dev variants only,
  sets `usesCleartextTraffic="true"` for the local http backend.
- prod has no equivalent file, so a release build cannot fall back to
  http even by mistake — API 28+ default applies and https is mandatory.

## 13.3 Verification

Merged manifests, `app/build/intermediates/merged_manifests/`:

| Variant | cleartext grant |
| --- | --- |
| `devDebug` | `usesCleartextTraffic="true"` |
| `prodDebug` | absent |
| `prodRelease` | absent (rebuilt after the change — the earlier 22:05 artifact was stale and still showed the old grant; always re-run the task before trusting a merged file) |

Generated `BuildConfig`: `devDebug` → `http://127.0.0.1:8080/` +
`IS_DEV_BUILD = true`; `prodRelease` → `https://api.example.invalid/`.
Knob check: `./gradlew :app:compileDevDebugJavaWithJavac
-Prag.devBaseUrl=http://192.168.1.5:8080/` → `KNOB_EXIT=0` and the
generated `BuildConfig` read `http://192.168.1.5:8080/`; the following
plain `installDevDebug` restored `127.0.0.1` and reinstalled on the
device.

Builds: `assembleDevDebug assembleProdDebug testDevDebugUnitTest` →
`GRADLE_EXIT=0`, BUILD SUCCESSFUL 1m25s (25 unit tests unaffected —
`AuthFlowTest` and `RagStreamPlaybackTest` inject their own base URLs);
`assembleProdRelease` → BUILD SUCCESSFUL 1m20s.

## 13.4 When the backend actually deploys

1. Build prod with `-Prag.prodBaseUrl=https://<host>/` (trailing slash is
   required — Retrofit rejects a base URL without one).
2. The backend must sit behind TLS with a publicly trusted cert. A
   self-signed cert needs `src/prod/res/xml/network_security_config.xml`
   pinning the CA, not a cleartext grant.
3. CORS is still not needed (section 12 note; browsers only).
4. Nothing in `data/network` reads the URL at runtime, so no code change
   is required for a new host — only the property.

# 14. PHASE G — BACKGROUND UPLOADS (WorkManager) + "Thagaval"

Requested: *"if network is slow, if file is big it may take time, so if
user comes out of the app the file upload should continue, use
workmanagers, also show the notification on device regarding the
progress. Name of my app is Thagaval."*

Before this phase the upload was `DocumentRepository.upload()` awaited
inside `ShellViewModel`'s `viewModelScope`, with `ShellState.uploadInFlight`
as the only progress signal. `viewModelScope` dies with the ViewModel, so
swiping the app away killed the transfer mid-stream and the file was
re-staged on the next launch. There was no notification and no way to
cancel.

## 14.1 Shape of the new flow

```
SAF picker → UploadStager.stage(uri) → filesDir/uploads/<ts>/<name>
           → UploadQueue.enqueue(file)  [WorkManager, unique per uploadKey]
           → UploadWorker (CoroutineWorker) streams the bytes
           → UploadNotifier posts progress; ShellState.uploads mirrors the queue
```

| File | Role |
| --- | --- |
| `data/upload/FileProgressRequestBody.kt` | `RequestBody` over a `File`, 16 KiB chunks, reports once per whole percent; `contentLength()` = `file.length()` so OkHttp sends a real length |
| `data/upload/UploadWorker.kt` | `@HiltWorker CoroutineWorker`; owns retry, notification, staging cleanup, `getForegroundInfo()` |
| `data/upload/UploadQueue.kt` | enqueue / cancel / `observe(): Flow<List<UploadJob>>`; the only place WorkManager is touched from the UI layer |
| `data/upload/UploadNotifier.kt` | channel, group, deterministic notification ids |
| `data/repository/UploadStager.kt` | stages to **filesDir**, not cacheDir (see 14.4) |
| `RagApp.kt` | `Configuration.Provider` + injected `HiltWorkerFactory` |

New deps (`gradle/libs.versions.toml`): `androidx.work:work-runtime-ktx
2.12.0`, `androidx.hilt:hilt-work 1.4.0`, KSP `androidx.hilt:hilt-compiler
1.4.0`.

## 14.2 WorkManager 2.12 API traps hit while building this

Both cost real compile cycles; future sessions should not re-derive them.

1. **`WorkInfo.inputData` does not exist.** Reading the file name off the
   input data (the obvious way to render a queue row) fails to compile.
   `UploadQueue.observe()` reads `info.progress` instead, and
   `UploadWorker` writes the name into every `setProgress` payload. Rows
   that predate the first tick show the `PREPARING = "Preparing upload"`
   fallback.
2. **`setProgress` is a suspend function.** It cannot be called from
   OkHttp's synchronous body callback. `UploadWorker.transfer()` runs a
   `Channel<Int>(Channel.CONFLATED)` fed by the callback and a reporter
   coroutine that awaits it, then `ticks.close(); reporter.join()` before
   returning — conflated because only the newest percent matters.
3. Cancellation is by `WorkInfo.id` (a UUID): `cancelWorkById`. With no
   `inputData` there is nothing else stable to key on, so `UploadJob.workId`
   is the id string and is also the `key {}` for the drawer's `items`.

## 14.3 On-demand initialization (the crash that would otherwise happen)

`AndroidManifest.xml` removes the startup provider entry:

```xml
<provider android:name="androidx.startup.InitializationProvider" …>
    <meta-data android:name="androidx.work.WorkManagerInitializer"
        android:value="androidx.startup" tools:node="remove" />
</provider>
```

Without that removal, Startup initialises WorkManager before Hilt injects
`RagApp`, and the first `@HiltWorker` resolution throws
`IllegalStateException: WorkManager is not initialized properly`. Paired
requirement: `RagApp` implements `Configuration.Provider` — in 2.12 that
means `override val workManagerConfiguration: Configuration`, not the old
`getWorkManagerConfiguration()` override.

## 14.4 Notification model

- Channel `document-uploads` ("Uploads"), group `document-uploads-group`.
- **Id is derived, not random**: `2000 + |uploadKey.hashCode()| % 1000`, so
  a worker restarted in a new process re-posts into the same row instead of
  leaving an orphan behind. `uploadKey` is `"<enqueueMillis>-<name>"` and
  travels in the input data.
- Ongoing while `percent < 100`, then `markFinished` / `markFailed` replaces
  it; `markFailed` keeps the row so the reason is readable.
- `setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)` plus
  `getForegroundInfo()` — expedited is what lets a transfer keep running
  after the UI is gone; the fallback policy is what keeps it from throwing
  when the app is out of quota.
- `POST_NOTIFICATIONS` is requested from `AppShell` before opening the
  picker; a denial still opens the picker (the upload works, only the row
  is invisible). `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` are
  declared for the expedited path.
- Staging moved `cacheDir/uploads/<name>` → `filesDir/uploads/<ts>/<name>`:
  cache files can be evicted while a job is merely queued, which would
  surface as `MISSING_FILE`. The worker deletes the whole `<ts>` directory
  after a successful transfer.

## 14.5 Retry policy

`Constraints … setRequiredNetworkType(CONNECTED)` means a job picked on
mobile data with no signal sits `BLOCKED` rather than failing. Backoff is
exponential from 30 s. `UploadWorker.handleFailure` retries **only** when
`outcome.code == ApiErrorMapper.CONNECTION_FAILED` and
`params.runAttemptCount < 3`; a 4xx/5xx from the server is permanent —
retrying an unsupported file type 3 more times is noise. Cancel is
`cancelWorkById`, and the worker's `CancellationException` branch dismisses
the notification before rethrowing so a cancelled job leaves no stale row.

## 14.6 Drawer UI + MVI changes

`ShellState.uploadInFlight: Boolean` → `uploads: List<UploadJob>`.
`ShellResult.UploadStarted` / `UploadFinished` are deleted; the queue is
the single source of truth, mirrored by `ShellResult.UploadsChanged`.
`ShellIntent.CancelUpload(workId)` added. The reducer copies the list
verbatim — WorkManager owns that state, so the reducer must not invent it —
and a `Banner` result explicitly does **not** clear `uploads` (a row that
disappears because a toast was shown is a lie about the transfer).

Drawer gained an "Uploading" section: file name, `AppType.Label` percent
(or "Queued"), a 3.dp `LinearProgressIndicator`, and a Close button per
row. Top bar title is now `stringResource(R.string.app_name)`.

## 14.7 Rename to Thagaval

`app_name` = **Thagaval** in `strings.xml`; that single string drives the
launcher label, the top bar, and the notification. `applicationId` stays
`com.ragclient.android` (`.dev` suffix for the dev flavor) — renaming the
package would orphan the installed app and its stored session.

## 14.8 Verification (2026-11-01)

| Check | Result |
| --- | --- |
| `assembleProdDebug assembleProdRelease` | `GRADLE_EXIT=0`, BUILD SUCCESSFUL 1m9s |
| `assembleDevDebug testDevDebugUnitTest` | `GRADLE_EXIT=0`; 31 tests / 0 failures from `app/build/test-results/…xml` — `FileProgressRequestBodyTest` 3, `ShellReducerTest` 3 (new) |
| merged `prodRelease` manifest | `WorkManagerInitializer` absent, cleartext absent |
| merged `devDebug` manifest | `WorkManagerInitializer` absent, `usesCleartextTraffic="true"` |
| `installDevDebug` + cold start on `RZCXB0FWFNW` | process alive (pid 1287), no `AndroidRuntime`/`FATAL` and no "not initialized properly" in logcat |
| live backend, same session | `POST /auth/login` 200 with tokens; unknown email **and** wrong password both `401 INVALID_CREDENTIALS` — so the section 12.1 fix is deployed (the user restarted the backend); `POST /documents` with a 382-byte PDF → `201 READY`, then `DELETE` → `204` (test artifact cleaned up) |

**Not yet verified on device.** The phone was `mWakefulness=Dozing` behind
a secure lock (screencap returns a black frame), so no upload was actually
enqueued and WorkManager's own database had not been created yet —
`run-as … ls databases` showed only `rag-client.db`. On-demand init is
therefore proven by construction and by the absence of the startup
provider, not by a live job.

Manual pass, once unlocked (backend on 8080 + `adb reverse tcp:8080
tcp:8080`): log in → drawer → "Add document" → allow notifications → pick a
PDF → **immediately swipe the app away**. Expect a "Thagaval" progress
notification climbing to 100, then "uploaded — indexing now", the row
vanishing from the drawer's Uploading section, and the document appearing
in the library as PROCESSING → READY. Re-picking the same file while it is
still in flight must not start a second transfer (`enqueueUniqueWork` +
`ExistingWorkPolicy.KEEP`).

## 14.9 Incident found by this phase — non-PDF uploads returned 500

Probing the upload endpoint to validate the worker's success path:
a 382-byte PDF → `201 READY`, but a 2300-byte `text/plain` →
`500 INTERNAL_SERVER_ERROR`. Two bugs, one on each side:

1. The picker offered `text/plain` while `DocumentService.validateFile` is
   PDF-only and signals rejection with a bare `IllegalArgumentException`,
   which had no handler. Fixed: `DOCUMENT_MIME_TYPES` is now
   `arrayOf("application/pdf")`, and backend
   `GlobalExceptionHandler.handleIllegalArgument` → `400 VALIDATION_ERROR`
   with the real message. **Restarted 2026-11-01 and verified live** — a
   `text/plain` part now returns `400 VALIDATION_ERROR` / "Only PDF files
   are supported". See backend plan 24.2.1 for the full probe table.
   This reverses the "deliberately not mapped globally" note in the
   backend plan (23.2) — see backend plan 24 for the tradeoff.
2. The server caps uploads at 10 MB (`DocumentService.MAX_FILE_SIZE`) and
   nothing checked that client-side, which is precisely the "big file on
   slow network" case: minutes of transfer, then a rejection.
   `UploadStager.stage()` now returns `StagedUpload`
   (`Ready` / `TooLarge` / `Unreadable`) and reads
   `OpenableColumns.SIZE` **before** copying, so an oversized PDF fails in
   one query with "Files must be 10 MB or smaller." It re-checks
   `target.length()` after copying because some providers report no size.
   `MAX_BYTES` in `UploadStager` mirrors the server cap — change both or
   neither.

## 14.10 Open follow-ups from this phase

- Run the 14.8 manual pass on the unlocked device; that is the only
  remaining evidence gap for phase G.
- Real byte-level resume is not implemented — a killed worker restarts the
  transfer from 0. Needs a server `Upload-Length`/`Upload-Offset` endpoint
  (tus-style); the current design only guarantees the upload *continues*
  without the app in the foreground.
- 10 MB is low for scanned PDFs. Raising it is a one-line change on each
  side, but chunking/embedding cost grows with page count, so pair it with
  the async-ingestion item in backend plan 19.
- Deferred UI items are unchanged: adaptive list-detail + NavigationRail,
  Material You dynamic color, motion polish.

# 15. EDGE-TO-EDGE INSETS (2026-11-01)

`MainActivity` has called `enableEdgeToEdge()` since M0, but nothing in the
composition ever asked for the insets it exposes, so the status bar drew on
top of the app bar.

## 15.1 Library facts worth keeping (read from the resolved sources, not memory)

Verified against `material3-android:1.4.0` and
`foundation-layout-android:1.12.1` sources in the Gradle cache — the BOM
resolves these, don't assume another version's behaviour:

- `ScaffoldDefaults.contentWindowInsets = WindowInsets.systemBarsForVisualComponents`,
  and the content `PaddingValues` bottom is `insets.calculateBottomPadding()`
  **whenever there is no `bottomBar`**. So every NavHost destination here is
  already inset on left/right/bottom.
- `TopAppBarDefaults.windowInsets = systemBarsForVisualComponents.only(Horizontal + Top)` —
  the convention a custom bar should copy. `systemBarsForVisualComponents`
  itself is `androidx.compose.material3.internal`, i.e. **not public API**, so
  a hand-rolled bar composes the same sides from `statusBars` + `displayCutout`.
- `DrawerDefaults.windowInsets = systemBarsForVisualComponents.only(Vertical + Start)`
  and `ModalDrawerSheet` applies it → **the drawer needed no change**.
- `imePadding()` is plain `windowInsetsPadding(WindowInsets.ime)`.
- Key gotcha: `Modifier.padding(paddingValues)` from a Scaffold does **not**
  consume insets. Insets-aware modifiers inside the content (`navigationBarsPadding`)
  therefore re-apply the same bars → double padding. Only
  `windowInsetsPadding` / `consumeWindowInsets` participate in consumption.

## 15.2 What was wrong and what changed

| Screen | Problem | Fix |
| --- | --- | --- |
| `feature/shell/AppShell.kt` `ShellTopBar` | plain `Row`, no insets → title and icons rendered under the status bar | `.windowInsetsPadding(statusBars.only(Top))` + `.windowInsetsPadding(displayCutout.only(Horizontal))`, placed **after** `.background()` so the bar colour still paints behind the status bar |
| `feature/chat/ChatScreen.kt` composer | `.navigationBarsPadding()` on top of the Scaffold's own bottom inset → the input bar floated ~2× the nav bar above the gesture pill | padding removed (comment left in place); `.imePadding()` kept |
| `feature/auth/AuthScreen.kt` | root when signed out, so **no** Scaffold hands it insets — it had `navigationBarsPadding` + `imePadding` but nothing for the top | `.statusBarsPadding()` added |
| Drawer, viewer, search, profile | — | untouched; already covered by `ModalDrawerSheet` / Scaffold content padding |

Because the top bar's insets are consumed inside it, the `topBarPlaceable.height`
the Scaffold reports already includes the status bar, so the banner overlay
(`padding.calculateTopPadding()`) and the NavHost both shift down for free.

## 15.3 Device numbers and verification

From `dumpsys window` on `RZCXB0FWFNW` (SM-S711B, 1080×2340):
`statusBars frame=[0,0][1080,80]`, `navigationBars` bottom inset **42 px**.
So the composer was sitting ~84 px up, not 42.

- `assembleDevDebug testDevDebugUnitTest` → `CLIENT_EXIT=0`, BUILD SUCCESSFUL 40s,
  31 tests / 0 failures (no reducer changed — insets are layout-only).
- Before/after screenshots taken over adb with the app on the chat screen:
  before shows "Thagaval" overlapping the 09:32 clock and a wide dead band
  under the input bar; after shows the title fully below the status bar and
  the composer resting just above the gesture pill.
- Chat itself was exercised live in the same session: a streamed answer
  rendered with `[S1][S2]` markers and tappable `S1 · p1` / `S2 · p1` chips.

## 15.4 Known remaining inset nit

With the keyboard open, the composer is pushed up by the Scaffold's 42 px
nav-bar padding **plus** the full IME height, so there is a small gap above
the keyboard. The precise fix is
`windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))`
instead of `imePadding()` — deliberately not done here because it changes
keyboard behaviour, which is a separate thing to verify on-device.

# 16. SEARCH: WHAT `minScore` ACTUALLY DOES (2026-11-01)

The user asked what the SearchScreen slider does. Answering it required
reading the backend filter order and measuring real scores, and uncovered two
genuine defects: the slider was a **dead control**, and its range was mostly
a dead zone.

## 16.1 The semantics (from code, not assumption)

- Qdrant collection is created with `Distance.Cosine`
  (`backend/.../qdrant/QdrantCollectionInitializer.java:42`), so the score is
  **cosine similarity** between the query embedding and the passage embedding
  — a meaning-similarity in 0..1 for normalized vectors, not a keyword overlap.
- `RetrievalService.retrieve` applies `matches.filter { it.score >= minScore }`
  **after** `searchSimilar(topK)`. Consequence: raising `minScore` can only
  *remove* results from a fixed pool of `topK`; it never brings a better match
  in. Lowering it past the pool's floor does nothing either.
- Numbers: backend `DEFAULT_TOP_K = 5`, but the client sends
  `topK = 12` (`SearchViewModel.RESULTS_LIMIT`), and the client default
  threshold is `SearchState.DEFAULT_MIN_SCORE = 0.05f`.
- Endpoint is `/api/v1/search` (`SearchController @RequestMapping`) —
  **not** `/api/v1/rag/search`; probing the wrong path silently returns 0
  hits for every threshold, which is how the first measurement round fooled us.

## 16.2 Measured score distribution (live index, test4 account)

Queried the running backend over `adb reverse tcp:8080` with the account's own
documents, reading the top-2 score of each query:

| Query | top scores |
| --- | --- |
| "docker" | 0.0878 / 0.0794 |
| "years of experience" | 0.1498 / 0.0723 |
| "partner management portal" | 0.2306 / 0.1520 |
| "nginx" | 0.2270 / 0.2033 |

At `minScore = 0.15`, two of the four queries return **zero** results. At
`0.30`, **every** query returns zero. So on this index, real matches live in
~0.05–0.25 and nothing clears 0.30 — which is why the old `0f..0.6f` track
put 60% of its travel in a region that always yields an empty list.

## 16.3 What changed in `feature/search/SearchScreen.kt`

| Change | Why |
| --- | --- |
| `LaunchedEffect(state.minScore)` + `delay(SETTLE_MS = 350)` re-dispatches `SearchIntent.Run` when `hasSearched && !isLoading && query.isNotBlank()` | the filter is server-side, so without a re-run the slider did nothing until the user pressed Search again |
| Slider range narrowed `0f..0.6f` → `0f..0.4f` | measured ceiling is ~0.23; wider track is dead zone |
| Label renamed to "Minimum match score" + explanatory caption | plain meaning ("how close a passage must be to your question — measured by meaning, not shared keywords") plus the monotonic rule ("raising this only ever removes results") |
| Dynamic `Float.interpretation()` line in accent colour | tells the user what the *current* position does, in 5 bands from "Broadest" to "Very strict — expect no results" |
| Empty state names the threshold: "Nothing scored above 0.xx. Drag the slider down…" | the previous "No matches" gave no hint that the control they just moved caused it |
| Result card header now `file · pN · match 0.xx` | surfaces the score per hit so the bands are learnable by watching real results |

`interpretation()` bands: `<0.05` broadest · `<0.12` broad (where useful
matches actually land) · `<0.20` balanced · `<0.30` strict (results thin out) ·
else very strict (almost nothing scores this high).

## 16.4 Verification

- `assembleDevDebug testDevDebugUnitTest installDevDebug` → `CLIENT_EXIT=0`,
  BUILD SUCCESSFUL 11s, 31 tests / 0 failures, "Installed on 1 device".
- The backend evidence in 16.1/16.2 came from live HTTP calls, so the copy is
  grounded in this index, not in a generic RAG description.
- **Outstanding**: on-device visual check of the new slider block. The phone
  went to a secure lock mid-verification (`dumpsys trust` →
  `trustState=UNTRUSTED deviceLocked=1`, `dreaming=true`), so screencap
  returns an all-black frame. This blocks §14's G5 upload/notification smoke
  test too — both need the device unlocked.

## 16.5 Notes for a future session

- `AppType` has Display / AnswerBody / Label / Body — there is **no**
  `AppType.Caption`.
- Auto re-search on `minScore` means every settled drag costs one request. If
  the backend gets rate-limited, gate on `state.results.isNotEmpty()` or raise
  `SETTLE_MS` rather than removing the behaviour.
- `minScore` is not a relevance *ranking* knob; if the product eventually wants
  "more results" that is `topK`, currently fixed at 12 in the ViewModel.

## 16.6 REVERTED the same day — the slider is gone

User's call after seeing it explained: the control is not worth a user
understanding cosine at all. So the whole slider block was deleted rather than
left in with better copy.

- Removed: `SearchState.minScore` + `DEFAULT_MIN_SCORE`,
  `SearchIntent.ChangeMinScore`, `SearchResult.MinScoreChanged` and its reducer
  branch, the debounced `LaunchedEffect(state.minScore)` re-search,
  `Float.interpretation()`, `SETTLE_MS`, and the `Slider`/`SliderDefaults`
  imports. Empty-state copy no longer names a threshold the user cannot move.
- `SearchViewModel` now sends a fixed `MIN_SCORE = 0.05f` (companion const,
  with the measured 0.07–0.23 rationale in its comment). **§16.1 and §16.2 are
  still the reason that number is 0.05** — keep them.
- What survived from §16.3: the per-result `match 0.xx` score on each card, so
  the ranking is still visible even though the filter is not.
- Verified live after the change: `minScore=0.05` returns every useful hit for
  all four probe queries (0.088/0.079, 0.150/0.072, 0.231/0.152, 0.227/0.203).
  `assembleDevDebug testDevDebugUnitTest installDevDebug` → `CLIENT_EXIT=0`,
  31 tests / 0 failures (the reducer test dropped its `minScore` argument),
  "Installed on 1 device".
- If this ever comes back it should be a two-state control (e.g. "show fewer
  matches"), not a raw 0–0.4 float track.

# 17. BRAND ASSETS: LAUNCHER ICON + SPLASH LOGO (2026-11-01)

User supplied `thagaval_logo.png` (1408×768 RGBA) and
`thagaval_splash_animation.gif` (2.3 MB) and asked for the first to become the
app logo and the second to be the splash animation. Both had been dropped
loosely into `app/src/main/res/`, which is not a valid resource folder — every
asset now lives in a qualified directory.

**Same-day revision:** after the GIF splash was built and installed, the user
asked to drop it and use the logo itself on the splash instead. The GIF is
**gone from the repo** (`res/raw/` deleted; the source file still sits in
`~/Downloads` if it is ever wanted again) and the splash is a fade-in/hold/
fade-out of the wordmark. §17.4–17.6 describe the shipped logo version; the
GIF measurements stay in §17.1 only as history.

## 17.1 What the assets actually are (measured, not assumed)

- **Logo PNG**: fully **opaque** — the "white" background is real pixels
  (`#FBF9FA`/`#FFFFFF`, alpha 255 everywhere), and the ink is
  `#119F79`, i.e. effectively `AppColors.Accent` (`#16A37A`). Using it directly
  would paint a white rectangle on the warm `#F7F6F2` app surface.
- **Layout**: document+graph glyph on top, "Thagaval" wordmark below, with a
  blank row gap between them. The glyph is 275×344 of the 1408×768 canvas.
- **GIF**: GIF89a, 1080×1080, **47 frames, 2160 ms per loop, NETSCAPE
  infinite-loop block, no transparency flag, white canvas**, max frame delay
  200 ms. Frame 0 is blank white (the animation draws the mark in).

## 17.2 Asset pipeline (`client-android/tools/MakeIcon.java`)

No ImageMagick/Python/ffmpeg on this box — JDK 21 ImageIO is the only image
tool, and `java File.java` runs single-file programs directly. Re-run after any
logo change:

```bash
cd client-android
java tools/MakeIcon.java <src.png> <out-wordmark.png> 432 0.56 <out-foreground.png>
# then:
cp <out-wordmark.png>    app/src/main/res/drawable-nodpi/thagaval_logo.png
cp <out-foreground.png>  app/src/main/res/drawable-nodpi/ic_launcher_foreground.png
```

What it does, and why:

1. **Knockout**: alpha = f(near-white ∧ low-saturation). Chroma > 28 keeps
   88% of the pixel so anti-aliased green edges survive instead of halos.
2. **Glyph crop**: cuts at the first run of ≥12 empty rows below the mark
   rather than a hard-coded fraction — the first attempt used `0.68 * height`
   and dragged the tops of the wordmark's letters into the icon.
3. **Icon canvas**: 432×432 with the glyph at **56%** of the canvas.
   Adaptive-icon geometry: the 108dp canvas is scaled into the launcher's 72dp
   display box, so the 66dp "safe zone" is only 61% of the canvas — the
   corners of a portrait mark cross that ring at 56%, but the *mask* (circle /
   squircle, 100% of the display box) still contains the whole glyph.
   46% was provably un-clipped but looked small; 62% was the first guess and
   wasted canvas. Verified by rendering the foreground under
   square/circle/squircle masks with the safe ring overlaid.

## 17.3 Files

| Path | What |
| --- | --- |
| `res/drawable-nodpi/thagaval_logo.png` | wordmark, white knocked out to alpha; **this is the splash art** |
| `res/drawable-nodpi/ic_launcher_foreground.png` | 432² transparent glyph, centred |
| `res/mipmap-anydpi-v26/ic_launcher.xml` + `ic_launcher_round.xml` | `<adaptive-icon>` = white background + foreground + `<monochrome>` (themed icons on Android 13+) |
| `res/values/colors.xml` | `ic_launcher_background` `#FFFFFFFF`, `splash_background` `#FFF7F6F2` (= `AppColors.Surface`) |
| `AndroidManifest.xml` | `android:icon="@mipmap/ic_launcher"`, new `android:roundIcon` |
| `feature/splash/SplashScreen.kt` | new `SplashGate` (below) |
| `MainActivity.kt` | `SplashGate { RagRoot() }` |
| `res/values/themes.xml` | `android:windowBackground` → `@color/splash_background` |

`res/drawable/ic_app_icon.xml` stays: it is still
`UploadNotifier.setSmallIcon` (notification small icons must be simple
monochrome silhouettes, the brand glyph is too detailed).

**No adaptive-icon PNG densities are needed**: `minSdk = 26` means
`mipmap-anydpi-v26` always matches, so no `mipmap-*dpi` fallbacks.

## 17.4 How the splash works (logo version, shipped)

`SplashGate` composes the real app **underneath** the logo, so the session gate
(auth check) resolves while it is on screen instead of after — no double flash.
`MainActivity` is just `SplashGate { RagRoot() }`.

- One `Animatable<Float>` alpha on the logo `Image`:
  fade in 220 ms → hold 650 ms → fade out 260 ms ≈ **1.1 s** total. The GIF was
  2.2 s, so this also halves the wait on every cold start.
- `painterResource(R.drawable.thagaval_logo)` + `ContentScale.Fit`, sized
  `fillMaxWidth(0.66f).widthIn(max = 300.dp)` so the 1.83:1 wordmark does not
  blow up on a tablet.
- No `AndroidView`, no `ImageDecoder`, no watchdog: a static drawable either
  loads or the app has bigger problems, so the timer is unconditional.
- **Layering bug found on device (2026-11-01):** the paper background was
  originally on the *root* `Box`, i.e. behind `content()`. On a cold start the
  app underneath has not painted yet so it looked fine, but on a warm start
  (activity recreated from recents) the chat screen was already drawn and the
  logo appeared to float on top of it. The background now belongs to a
  `matchParentSize()` overlay panel, and the fade is split in two:
  `logoAlpha` 0→1 while the panel is already opaque, then `panelAlpha` 1→0 so
  the whole branded surface dissolves into the app.
- Background is `AppColors.Surface`, and `android:windowBackground` is the same
  colour via `@color/splash_background`, so the starting window and the
  Compose surface match.

The GIF-era design (`AnimatedImageDrawable` through `AndroidView`,
`repeatCount = 0`, a `@RequiresApi(28)` decode split because API 26–27 has no
`AnimatedImageDrawable`, a timer instead of a loop-finished callback since
`AnimatedImageDrawable` exposes none) is gone, but it is the recipe to reach
for if an animated asset comes back.

## 17.5 Consequences worth knowing

- **The splash is always the light paper surface, including in dark mode.** The
  wordmark's lettering is black, so it would be invisible on `DarkSurface`.
  A dark-mode brand asset needs a light-coloured export.
- Dropping the GIF took the dev-debug APK from **17.05 MB to 14.46 MB**.
- On Android 12+ the *system* splash (which now shows the new adaptive icon)
  runs before this overlay, so a cold start is icon → logo fade → app. On
  API 26–30 the `windowBackground` colour covers the gap instead.
- Samsung's launcher caches icons; if the home-screen icon looks stale after
  this change, remove and re-add it or reboot the launcher.

## 17.6 Verification

- `assembleDevDebug testDevDebugUnitTest installDevDebug` after the logo
  switch → `CLIENT_EXIT=0`, BUILD SUCCESSFUL 21s, 31 tests / 0 failures,
  "Installed on 1 device" (SM-S711B, **Android 16**).
- `aapt2 dump badging` → `application: label='Thagaval'
  icon='res/mipmap-anydpi-v26/ic_launcher.xml'`; `dump xmltree` confirms
  background/foreground/monochrome resolve to real resource ids.
- APK contents: both PNGs and both adaptive XMLs present, `res/raw/` gone.
- Cold start over adb (`am start`) → process alive, **no** `AndroidRuntime` /
  `FATAL` lines in logcat.
- **Verified on device 2026-11-01** (phone finally unlocked): burst screencaps
  caught the panel at full opacity — background samples read exactly
  `#F7F6F2` = `AppColors.Surface`, and a pixel diff of two splash frames showed
  the only changed pixels inside the logo's bounding box, i.e. nothing from the
  chat screen bleeds through. The launcher icon and the focused composer were
  likewise confirmed by screenshot.

# 18. CHAT COMPOSER: ONE OUTLINE AROUND FIELD + SEND (2026-11-01)

## 18.1 Incident first — a hand edit that could not compile

The user pasted `Row(..., modifier = Modifier.color(colors = OutlinedTextFieldDefaults.colors(...)))`
and asked what was wrong. Three separate mistakes in one line, and only the
first is reported:

1. `Modifier.color(...)` does not exist (paint modifiers are `background`,
   `drawBehind`, `blur`, …) → `Unresolved reference: color`.
2. `OutlinedTextFieldDefaults.colors(...)` returns a **`TextFieldColors`**, not
   a `Color`. It is only valid as the `colors =` argument of a text field; a
   `Row` has no border, cursor or focus state to apply it to.
3. Moving it also *deleted* the styling: `focusedBorderColor` /
   `unfocusedBorderColor` were cut out of the field's own `colors` block, so the
   field silently reverted to theme defaults. A change like this compiles and
   simply looks wrong.

An unused `import androidx.compose.ui.draw.blur` came with the edit and was
removed too.

## 18.2 The actual request, and how it is done

Goal: when the prompt is focused, the active border should also enclose the
send button, which previously sat *outside* the field.

Fix in `feature/chat/ChatScreen.kt` `PromptBar`: the `IconButton` moved from
being a sibling in a `Row(verticalAlignment = Alignment.Bottom)` into the
field's own **`trailingIcon` slot**, and the `Row` wrapper went away (the field
is now `fillMaxWidth()`). Material draws the outline around the whole field
including its icon slots, so the focus ring covers the button for free — no
custom border, no hoisted focus state.

- `uiautomator dump` confirms it geometrically: the `EditText` spans
  `[39,2095][1041,2270]` and the `content-desc="Send"` node is
  `[935,2156][991,2212]` — inside the field's box.
- Behaviour change to know about: the trailing icon is **vertically centred**
  by Material, so as the field grows toward its 160 dp max the button floats to
  the middle instead of hugging the bottom (the old `Alignment.Bottom` +
  `bottom = 8.dp` padding did that). Accept it, or move back to a container
  border if bottom anchoring ever matters more than the single outline.
- The same pattern already existed on SearchScreen's query field (magnifier
  inside the outline), so the two bars now match.

## 18.3 Verification

- `assembleDevDebug testDevDebugUnitTest installDevDebug` → `CLIENT_EXIT=0`,
  BUILD SUCCESSFUL, 31 tests / 0 failures, "Installed on 1 device".
- On-device screenshots after the change: focused composer shows one continuous
  emerald outline around the pill *and* the send circle; the search screen
  shows no slider (§16.6); the splash is a full opaque paper surface with the
  wordmark (§17.4).
- Still outstanding: §14's G5 upload + notification smoke test — the device is
  unlocked now, so nothing blocks it any more.

# 19. STREAMING CARET BLINKS INSTEAD OF SITTING THERE (2026-11-01)

## 19.1 Request

"in chat screen, when i ask for something, it shwoing things and some green
line, make it as blink, so that user experience will be better."

The "green line" is the `▍` block the streaming row appended to the answer. It
was a static `Text` in accent emerald, so it read as a rendering artefact rather
than as "the model is still writing".

## 19.2 Implementation — `feature/chat/ChatScreen.kt`

`StreamingRow` now calls a private `BlinkingCaret()` composable instead of the
static `Text(" ▍", color = AppColors.Accent)`:

- `rememberInfiniteTransition` + `animateFloat(0f → 1f)` with
  `infiniteRepeatable(tween(CaretCycleMillis, easing = LinearEasing))`.
- Hard on/off via `color = AppColors.Accent.copy(alpha = if (phase < 0.5f) 1f else 0f)`
  — a fade would look like a loading shimmer; text editors snap.
- `CaretCycleMillis = 1_060` → 530 ms on / 530 ms off, the interval the platform
  text cursor uses, so the two carets (this one and the composer's) feel related.
- **Why its own composable:** the animation's per-frame state read happens inside
  `BlinkingCaret`, so only that `Text` recomposes ~60×/s. Left in `StreamingRow`
  it would recompose the whole answer paragraph — including the markdown-ish text
  — on every frame of the blink while tokens are also arriving.
- The caret only exists while `StreamingRow` is on screen; when the stream
  finishes the row is replaced by the settled message, so the blink stops by
  itself. No manual cleanup, no coroutine to cancel.

## 19.3 Verification (device `RZCXB0FWFNW`, 2026-11-01)

Burst-captured 22 `screencap` frames (~250 ms apart) while an answer streamed,
then analysed them with `CaretDiff.java` (JDK 21 ImageIO, chat scratch dir — no
ImageMagick/Python here): count pixels within tolerance of `#16A37A` and diff
each consecutive pair, restricted to `y < 2050` so the composer band is excluded.

- Accent count alternates **882 ↔ 0** across the frames; the diff for every
  toggle is one small box, `293,1954 → 307,2018` (≈15×65 px = the block glyph
  plus antialiasing, 975 changed px).
- Frames where text actually arrived are the only ones with a wide diff box
  (`f09`, `f13`, `f19`); between token bursts the rest of the answer is
  pixel-identical, which is the proof that the blink is not recomposing the
  paragraph.
- Crops `verified_caret_on.png` / `verified_caret_off.png` show the block after
  the word "inking" in one frame and nothing in the other.

**Trap worth remembering:** the first measurement run appeared to prove the
blink but was sampling the *composer's* own blinking text cursor (a 5×85 px bar
at `x=84`), which is a different widget with a similar cadence. Two things fixed
the experiment — restricting the pixel band to `y < 2050`, and reading the diff
box coordinates instead of trusting the count alone. Always locate the thing you
are measuring before measuring it.

Side effect of the test session: pressing `KEYCODE_BACK` to dismiss the IME a
second time (with no keyboard up) exits the app, and the installed package is
**`com.ragclient.android.dev`** (the `.dev` applicationIdSuffix from §Phase A),
so `am start -n com.ragclient.android/.MainActivity` fails with "Activity class
does not exist" — use the `.dev` package name for adb launches.


## 20. Phase G5 — uploads on a real device found four defects (2026-11-01)

### 20.1 What was supposed to happen
Upload a PDF from the drawer, watch the notification, swipe the app away, and
confirm WorkManager finishes the job. Every step of that produced a defect, so
this section is mostly the record of what was wrong and what fixed it.

### 20.2 Backend: the 10 MB limit was unreachable
`DocumentService.MAX_FILE_SIZE` said 10 MB, but Tomcat's default multipart cap
is **1 MB per file**, so anything real was rejected before the service saw it —
and the rejection did not look like our error envelope.

- `common/config/UploadSizeConfig.java` (new) registers a
  `jakarta.servlet.MultipartConfigElement(tmpdir, 10 MB, 11 MB, 0)`. Spring Boot
  4 **removed** `MultipartConfigFactory`, so the element is built with its own
  constructor. The request cap is deliberately 1 MB above the file cap so a
  multipart with its boundary overhead still reaches the file check.
- `GlobalExceptionHandler` now handles `MaxUploadSizeExceededException` →
  `413 FILE_TOO_LARGE` with the limit interpolated from `UploadSizeConfig`.
  `HttpStatus.PAYLOAD_TOO_LARGE` is deprecated in Spring 7; `CONTENT_TOO_LARGE`
  is the same code.
- The caps live in Java, **not** in `application.yaml`, because that file holds
  live Neon/NVIDIA/Qdrant/Cloudinary credentials and is already in git history —
  never print it and never push this repo to a public remote.
- Verified with curl against the running backend: 9,442,104-byte PDF → `201`,
  12,583,789-byte PDF → `413` + `FILE_TOO_LARGE`.

### 20.3 Client fixes
1. **The dedupe key was fictional.** `enqueue()` used `System.currentTimeMillis()`
   as the unique-work name, so `ExistingWorkPolicy.KEEP` could never match
   anything. The name is now the staged file's absolute path, which is unique
   per pick and stable per job.
2. **Notifications are keyed on the work id, not the upload key.**
   `getForegroundInfo()` posts the ongoing row *before* the first progress tick,
   and `WorkInfo` never exposes input data — so a UI holding only the work id
   had no way to name the row it needed to clear. `UploadNotifier` methods now
   take `jobId` (`params.id.toString()`, stable across process death) and
   `UploadQueue.cancel()` dismisses by that id. `KEY_UPLOAD_KEY` is gone.
3. **`runCatching` was swallowing `CancellationException`.** `DocumentRepository.failed()`
   now rethrows it, so coroutine cancellation stops masquerading as a network
   failure.
4. **Staged copies leaked.** Cancelling or failing an upload left the whole file
   in `filesDir/uploads/`, which the system never trims — 36 MB after four test
   picks. Three paths now clean up: the `CancellationException` catch in
   `UploadWorker.doWork()` (`CoroutineWorker.onStopped()` is **final**, so it
   cannot be overridden — that was a compile error, not a design choice),
   `handleFailure()` for terminal failures, and `pruneOrphanStagedFiles()` on
   every enqueue for jobs cancelled while parked in backoff, which have no live
   worker at all. Pruning needs to know which copies a live job owns: the staged
   path is added as a second **tag** (`addTag(uniqueName)`), because tags *are*
   visible on `WorkInfo`. `UploadJob.stagedPath` reads it back.
   The lookup uses `getWorkInfosByTagFlow(...).first()` inside
   `withContext(Dispatchers.IO)` — `getWorkInfosByTagBlocking` no longer exists.

### 20.4 Verification (device `RZCXB0FWFNW`)
- Orphan sweep: `files/uploads` held 4 directories / 36 MB → after one enqueue,
  only the live `1793512611279` (9.2 MB) → empty after success.
- Cancel while RUNNING (drawer row `100% uploaded`, `content-desc="Cancel
  upload"` at `[762,1048][813,1099]`): after the tap, `dumpsys notification
  --noredact | grep NotificationRecord.*ragclient` returns **nothing ongoing**
  and `files/uploads` is empty. Terminal rows keep `AUTO_CANCEL` and no
  `ONGOING_EVENT`.
- Failure path proved itself by accident: the test account hit the backend's
  50 MB per-user storage quota, the row ended as a failed notification reading
  "Storage limit exceeded. Maximum storage per user is 50 MB.", and the staged
  copy was deleted by the new `handleFailure()` cleanup.
- Logcat confirms the tag wiring: `tags={ …UploadWorker, document-upload,
  /data/user/0/com.ragclient.android.dev/files/uploads/1793512746398/thagaval_death_test.pdf }`.
- Earlier in the phase: WorkManager survived a hard process kill (new pid within
  1.5 s, "Upload complete" posted from the fresh process). `am kill` is a no-op
  while an expedited job holds the process important — use
  `adb shell run-as com.ragclient.android.dev kill -9 <pid>`.

### 20.5 Still open after G5
- **Retry creates duplicates.** A retried upload after process inserted the same
  file twice (2 × `READY` rows); the backend has no idempotency key per upload.
- The app **fell back to the login screen** after a backend restart, with no JWT
  errors in the server log — the client's refresh path deserves its own look.
- Test documents (7 × `thagaval_*.pdf`, ~47.8 MB) were deleted from the account
  to free quota. The user's own stuck rows were left alone:
  `b28f9360-92ba-48cf-8d5b-5c7b4d1ccf93` and three other `UPLOADED` documents
  still need a reprocess-or-delete decision.
- DocumentsUI automation: `Add document` row at `(464,1068)`, the picker needs
  ~8 swipes to reach `thagaval_*`, its tile taps at `(200,2050)`, and a
  MediaStore row pushed with `adb push` stays invisible until
  `content call --uri content://media/external/file --method scan_file --arg <path>`.

## 21. Preview of a file that is still uploading (2026-11-01)

### 21.1 Request
"in AppShell, add preview for this file" — the only drawer row without a preview
was the in-progress upload row (library documents already open the viewer).

### 21.2 Implementation
- `UploadJob.stagedPath` (from the tag described in §20.3.4) carries the staged
  file to the UI.
- `Routes.STAGED_VIEWER_PATTERN = "viewerStaged/{directory}/{name}"` and
  `Routes.stagedViewer(path)` split the staging layout
  `uploads/<millis>/<sanitised name>`. Those two segments are generated by
  `UploadStager.sanitize()` plus `System.currentTimeMillis()`, so they only ever
  contain `[A-Za-z0-9._-]` and digits — no percent-encoding of `/` is needed,
  which is the usual way navigation breaks on file paths.
- `ViewerViewModel` gained `ARG_DIRECTORY`/`ARG_NAME`; when they are present it
  calls `PdfRepository.openStaged(directory, name)` instead of the
  server-backed `open(documentId)`.
- `PdfRepository.openStaged()` canonicalises and requires
  `file.parentFile == uploadsRoot && file.isFile` — the arguments arrive
  through a deep link, i.e. untrusted input, even though our own staging code
  wrote them. `rendererFor(file)` is now shared with `open()`.
- `UploadRow`'s left column is `clip(RoundedCornerShape(8.dp)).clickable`, the
  same tap-to-open shape as `DocumentRow`; no new visible control, the Cancel
  button is untouched.
- `RoutesTest` pins the path→route split and the blank-name case.

### 21.3 Verification status
~~On-device check outstanding~~ — **superseded by §24**: the tap was exercised
on the SM-S711B, which is how the broken containment check in `openStaged()`
was found. That section also covers the render proof and the cancel cleanup.

## 22. "Can't reach the server" — removed the adb tunnel dependency (2026-11-01)

### 22.1 Why it kept happening
The dev build pointed at `http://127.0.0.1:8080/`, which on a USB phone only
works while `adb reverse tcp:8080 tcp:8080` exists. That tunnel is wiped by
every replug, every adb-server restart and every `adb kill-server`, and nothing
re-creates it — so a healthy backend looked like a dead one. The screenshot the
user sent was exactly this: `adb reverse --list` returned nothing.

### 22.2 The fix
`gradle.properties` now carries an active `rag.devBaseUrl=http://192.168.1.5:8080/`,
so the dev build talks to the PC over Wi-Fi and needs no host-side tunnel at
all. Nothing else had to change: Tomcat already binds `0.0.0.0:8080`, the dev
flavor already sets `usesCleartextTraffic="true"` for any host, and the Windows
firewall already lets inbound 8080 through.

The earlier note in this plan and in memory said a LAN URL could not work
because the PC was on `192.168.1.5/24` and the phone on `192.168.0.104/24`.
That was true on 2026-10-31 and is **not** true now — the phone is
`192.168.1.12/24`, the same subnet. Networks here move, so re-check both
addresses (`ipconfig`, `adb shell ip -4 addr show wlan0`) before concluding.
Note that `adb shell ping <pc-ip>` fails even when HTTP works, because Windows
drops inbound ICMP; test L4 instead:

```
adb shell "printf 'GET /api/v1/documents HTTP/1.1\r\nHost: 192.168.1.5\r\nConnection: close\r\n\r\n' | nc 192.168.1.5 8080 | head -1"
```

### 22.3 Better copy for the next occurrence
`ApiErrorMapper` gained a top-level `unreachableMessage()` and the `IOException`
branch now prints the origin it tried — "Can't reach http://…:8080/. Is this
device on the same network as that address, and is the backend running?" —
instead of "Check that the backend is running.", which was misleading whenever
the backend was in fact up. `ChatViewModel.copyFor()` had a duplicate of the old
sentence and now calls the same function.

### 22.4 Verification
- `assembleDevDebug testDevDebugUnitTest installDevDebug` → `CLIENT_EXIT=0`.
- `adb reverse --list` **empty** before and after the test.
- Raw HTTP from the phone to `192.168.1.5:8080` → `HTTP/1.1 403` (Tomcat
  answered; 403 is the expected unauthenticated response).
- The login screen footer reads `http://192.168.1.5:8080/  •  dev`, and signing
  in as `test4@gmail.com` succeeded and loaded the document list over Wi-Fi.

### 22.5 What is still not permanent
The PC's address is DHCP-assigned. If the router hands out something else, the
baked URL breaks in the same way — the fix is a **DHCP reservation for
`192.168.1.5` in the router** (one-time, user action, outside this repo). Until
that is done, the recovery is: read `ipconfig`, edit the single
`rag.devBaseUrl` line, rebuild. The USB tunnel remains available as a fallback
via `-Prag.devBaseUrl=http://127.0.0.1:8080/` plus `adb reverse`, but that
reintroduces the manual step.

---

## 23. "Ask this document" icon: pen → speech bubble (2026-11-07)

> **Superseded the same day by §27.** `ic_chat.xml` and every `Icons.Filled.*`
> reference are gone; the speech bubble is now `AppIcons.Ask`
> (`FeatherIcons.MessageCircle`). The reasoning below is kept because it
> records why the icon changed shape, not where it came from.

User request: the per-PDF ask affordance used a pen, which reads as "edit"; it
must read as "ask about this document".

### 23.1 Why a hand-drawn vector instead of a library icon
`gradle/libs.versions.toml` pulls **`material-icons-core` only**. Its whole set
is 63 icons (`Icons.Filled.{Add, ArrowBack, ArrowDropDown, Check, Close, Create,
Delete, Done, Email, Menu, Search, Settings, …}`) — verified by extracting
`material-icons-core-android-1.7.8-sources.jar` and listing
`commonMain/.../icons/filled/`. There is **no chat / comment / bubble glyph**
(`Chat`, `ModeComment`, `Message`, `Forum` are all absent; the nearest are
`Email`, `MailOutline`, `Send`). `Icons.AutoMirrored.Filled.Chat` therefore does
not compile, and `material-icons-extended` is not an option: release keeps
`isMinifyEnabled = false`, so it would ship ~1 MB of unused vectors.

So: `app/src/main/res/drawable/ic_chat.xml` (24dp viewport, black fill,
`fillType="evenOdd"` so the three text bars knock out of the bubble) rendered
with `Icon(painter = painterResource(R.drawable.ic_chat), tint = …)`. `Icon`'s
Painter overload calls `applyTint`, so the drawable stays colour-agnostic and
still picks up `onSurfaceVariant`. This matches the existing precedent of
hand-rolled art (`ic_app_icon.xml`, `thagaval_logo` via `painterResource`).

### 23.2 Call sites changed (2 of the 4 `Icons.Filled.Create` uses)
- `feature/shell/AppShell.kt` — drawer document row, `contentDescription = "Ask this document"`.
- `feature/search/SearchScreen.kt` — search-result row, same description; its
  now-unused `Icons.Filled.Create` import was removed.
- **Left alone:** the two "New chat" pens (`AppShell.kt` top bar + drawer
  button), which are a compose/edit action and correctly use the pen.

### 23.3 Verification (physical SM-S711B)
- `assembleDevDebug testDevDebugUnitTest installDevDebug` → `CLIENT_EXIT=0`,
  `BUILD SUCCESSFUL in 23s`, installed on `SM-S711B - 16`.
- Drawer open → `uiautomator dump` lists six `content-desc="Ask this document"`
  nodes; screencrop of the first one shows the bubble with tail + three bars.
- Search for `resume` → two result rows each carry the same bubble node;
  cropped screenshot confirms it renders identically at 18dp.
- Tapping a drawer bubble closed the drawer and landed on a new chat screen
  (`Routes.chat("new", documentId)`), so the navigation is unchanged.
- Tooling note: no ImageMagick/Python on this machine, so screenshots were
  cropped/upscaled with a single-file JDK program (`Crop.java`, `java Crop.java
  in.png out.png x y w h scale`). Git Bash `/tmp` paths are invisible to the
  JDK — pass `cygpath -w` paths.

---

## 24. Staged preview proven on device — and the bug it found (2026-11-07)

### 24.1 How the window was made deterministic
Previewing a staged upload only makes sense while the bytes are still moving,
and a 0.5 MB file over the LAN finishes in well under a second, so the row was
gone before it could be tapped. The reliable way to hold a job in a live state:
`adb shell svc wifi disable`, pick the file, and the worker fails its first
attempt and parks in 30 s retry backoff (`ENQUEUED` is a live state, the staged
copy stays). The row then reads "Preparing upload / Queued" indefinitely.

### 24.2 The bug: the containment check rejected every valid path
The first attempt opened the viewer but showed *"This PDF could not be opened."*
even though `files/uploads/1794062150979/sample5.pdf` existed with a valid
`%PDF-1.4` header and `%%EOF` trailer.

`PdfRepository.openStaged()` built `File(root, "<directory>/<name>")` and then
required `file.parentFile == root` — but the staged layout is
`uploads/<batch>/<file>`, so `file.parentFile` is `uploads/<batch>`, never
`uploads`. The guard failed for **every** legitimate path; the preview had never
worked, on any build.

Fix: the containment rule is now a canonical path prefix test in a pure,
testable helper `stagedFile(root, directory, name)` (`PdfRepository.kt`), which
also refuses `..` escapes. Covered by `app/src/test/.../data/pdf/StagedFileTest.kt`
(happy path, traversal, missing file).

### 24.3 Second gap found: cancel left the staged copy behind
`UploadWorker` deletes the staged copy when it unwinds, but a job cancelled
**before it ever ran** has no `doWork()` to clean up, so `files/uploads/<batch>/`
survived — a silent storage leak against the 50 MB per-user quota. `UploadQueue.cancel()`
now takes the whole `UploadJob` (`ShellIntent.CancelUpload(job)`) and deletes the
staged batch itself when `job.running == false`. A running job is deliberately
left to the worker, because deleting the file out from under an in-flight
`FileProgressRequestBody` would turn a clean cancel into a `MISSING_FILE` failure.

### 24.4 Verification (physical SM-S711B)
- `assembleDevDebug testDevDebugUnitTest installDevDebug` → `CLIENT_EXIT=0`,
  `BUILD SUCCESSFUL`, twice (fix 24.2, then fix 24.3).
- With Wi-Fi off: pick `sample5.pdf` → row "Preparing upload / Queued" → tap it →
  viewer renders **"5 pages"** and page 1 of the Lorem-ipsum document
  (`content-desc="Document page"`, screenshot verified).
- Reinstall does not lose the job: WorkManager restored it and the row was still
  previewable afterwards.
- Cancel of the live row: `run-as … ls -R files/uploads` → **empty**, no
  `Cancel upload` node left in the drawer, and no
  `FOREGROUND_SERVICE|ONGOING` notification for the package.
- The parked job also proved the retry path end-to-end in reverse: after the
  cancel, nothing re-appeared.

### 24.5 Environment side effect the user must undo
To create the offline window, Wi-Fi was toggled with `svc wifi disable/enable`.
The phone re-joined **SSID `Rkarthik` (192.168.0.104)** instead of
**`Airtel_thanush`**, and `192.168.0.0/24 → 192.168.1.5:8080` does not route
(`nc` from the phone times out), so the app now shows the new
"Can't reach http://192.168.1.5:8080/…" banner. **Action: reconnect the phone to
`Airtel_thanush`.** `cmd wifi connect-network`/`enable-network` cannot be driven
from adb (shell uid gets a SecurityException, and the saved network needs its
password), so this is a user-side step.

### 24.6 Still open
- Duplicate document on retry (no idempotency key) — unchanged from §20.5.
- Session drop after a backend restart — unchanged from §20.5.
- Empty batch directories are only pruned at the next `enqueue()`; harmless, but
  a cold-start prune would be tidier.

## 25. Citation UI hidden, logic untouched (2026-11-07)

### 25.1 The ask
"i dont want to those [s1] and some bublles withs s1 p1 and more yo user, just
hide the ui as of now, dont remove that logic" — the answer text ended with a
literal `[S1]` and a row of `S1 · p1` / `S2 · p1` / `S3 · p1` chips sat under it.

### 25.2 What changed (presentation only, one file)
`feature/chat/ChatScreen.kt`:
- New `private const val ShowCitations = false`. Both `SourceRow(...)` call
  sites (finished message and streaming row) are wrapped in `if (ShowCitations)`.
  `SourceRow`, `RagSourceModel`, `ChatMessage.sources`, the SSE `sources` event,
  `ChatReducer` and the `onOpenSource` deep-link into the viewer are all still
  there — flip the constant back to `true` and the chips return with working
  taps. Nothing was deleted, and the sources are still persisted in Room.
- New `internal fun displayAnswer(text: String)`, applied to both the stored
  answer and the streaming buffer. It strips `[S1]`, `[S1, S3]` and
  `[S1][S2]`-style markers and any space left in front of the punctuation that
  followed them, then trims. The marker comes from the model's own answer text
  (the backend prompt asks for it), so it is removed at render time only — the
  raw text in the database is unchanged.

### 25.3 Tests
`app/src/test/java/com/ragclient/android/feature/chat/DisplayAnswerTest.kt`,
4 cases: marker before punctuation, mid-sentence + multi-index brackets, plain
text with a line break and a non-citation `[ b ]` left byte-for-byte intact, and
a half-arrived `[S` during streaming (still shown that one frame — accepted, it
completes on the next chunk).

### 25.4 Verification (physical SM-S711B)
`assembleDevDebug testDevDebugUnitTest` → `CLIENT_EXIT=0`, `BUILD SUCCESSFUL`,
`DisplayAnswerTest` executed. Then the live path:
- The phone was still on `Rkarthik` (§24.5), so `192.168.1.5:8080` was
  unreachable. Instead of waiting on the SSID fix, the build was installed with
  `-Prag.devBaseUrl=http://127.0.0.1:8080/` plus `adb reverse tcp:8080 tcp:8080`
  — the tunnel is fine for a verification run, it just cannot be the shipped
  default (§22).
- Asked "what is the email id of him". `uiautomator` dump of the answer node:
  `text="The email ID is `writetorahul3125@gmail.com`."` — **no `[S1]`, and no
  chip nodes anywhere in the tree**. Screenshot confirmed; reopening the app
  after a cold start showed the same stored message rendered the same way,
  which proves the strip works on Room-persisted text, not just live SSE.
- Restored afterwards: plain `installDevDebug` (gradle.properties puts
  `rag.devBaseUrl` back to `http://192.168.1.5:8080/`, verified in the generated
  `BuildConfig.java`) and `adb reverse --remove-all`.

### 25.5 Two things this exposed
- **Markdown is shown literally.** The answer keeps its backticks, so the user
  sees `` `writetorahul3125@gmail.com` ``. Out of scope for this change, but it
  is the same class of problem as the `[S1]` marker: model output is being
  painted straight into a `Text`. Either strip inline code/bold in
  `displayAnswer` or add a real markdown renderer.
- **Drawer chat rows do not open.** Tapping the "what is the email id of him"
  row in the drawer closed the drawer and left the empty "Ask your documents."
  state; `AppDrawer.onOpenChat` → `Routes.chat(id)` navigation is not landing.
  Unreproducible since (the answer above was reached by asking again), so it is
  a fresh suspect rather than a confirmed defect. Needs a look before the
  conversation history is considered usable.
  → Resolved the same day in §26.5.1: `launchSingleTop`, and it was never
  unreproducible, just unnoticed. The markdown point above was closed on
  2026-11-07 by §28.

## 26. Per-document chats: continuity, context, and three ways to delete (2026-11-07)

### 26.1 The ask
"when i tap on pre uploaded doc to message on it, its opening the screen i
have uploaded in second image, to give user more context, can we have some
ui improvement for this case, like please ask question from this story_aims
file, something like, also preserve the chat we had with specific document,
also give a option for user to delete the chat alone for the specific chat
and all the chat with specific doc, and also the option to delete specific
document"

Four separate demands: (a) say plainly which file you are talking to,
(b) one thread per document that survives leaving the app, (c) delete a
single chat / every chat of one document, (d) delete the document.

### 26.2 One thread per document
`ConversationDao` gained `latestScopedTo(documentId)`, `idsScopedTo(documentId)`,
`exists(id)` and `deleteByIds(ids)`; `MessageDao` gained
`clearForConversations(conversationIds)`.

`data/repository/ConversationRepository.kt`:
- `openForDocument(id)` → the newest conversation whose `scopedDocumentId`
  matches, or a new one. Tapping the same PDF twice returns to the
  conversation you were already having with it.
- `openExisting(id)` → that exact thread; a stale id (row deleted elsewhere)
  starts fresh instead of opening a blank dead screen.
- `deleteAllForDocument(id)` → messages then conversations, returns the
  count for the banner. **Guards the empty list** because Room binds
  `IN (:ids)` literally and `IN ()` is not valid SQL.

`navigation/Routes.kt`: `chatForDocument(id)` = `chat/scoped?scope=<id>` and
`newChatInDocument(id)` = `chat/new?scope=<id>`. `ChatViewModel` resolves the
four route sentinels (`new`, `scoped`, `latest`, explicit id) against the
repository.

### 26.3 Saying which file you are in
`ChatState` now carries `documents: List<LibraryDocument>` and
`scopedDocumentName: String?`; `ChatResult.LibraryChanged` feeds the library
flow into the chat reducer so the name resolves no matter whether the
conversation or the library arrived first (`nameOf()` is re-run on both
emissions).

- Empty state, scoped: **"Ask questions from"** / the file name at
  `AppType.Display` / "Only this document is searched, and this thread stays
  with it." Unscoped keeps "Ask your documents."
- Prompt bar chip: **"Asking <file>.pdf"**, plus the existing
  "Ask all documents" button that drops the scope.
- `ChatResult.LibraryChanged` also **widens a dead scope**: if the scoped
  document left the library, `scopedDocumentId` becomes null so the thread
  falls back to the whole library instead of asking a nonexistent id and
  getting nothing back.

### 26.4 Drawer controls
`DocumentRow` shows `Ready · 2 chats` (`pluralize()` handles the 1 case) and a
`MoreVert` menu — "Start a new chat in this document" / "Delete N chats" /
"Delete document". The delete-chats entry is **not rendered at all when the
count is zero**; a greyed-out "Delete 0 chats" is just noise. Both destructive
entries route through a local `ConfirmAction(title, body, confirmLabel, run)`
+ `ConfirmDialog` before anything is touched.

Chat rows print the scoped file name as a second line, so "New chat" in the
history list says which document it belongs to.

`ShellIntent.DeleteChatsForDocument` → banner "Chats deleted." /
"No chats in that document.". `ShellIntent.DeleteDocument` now **cascades**:
it calls `deleteAllForDocument` before `documents.remove(id)`, because a
thread scoped to a gone document can never retrieve again. Confirmation copy
changed from "Its chats stay until you delete them too." to
"Chats about this file are deleted too." to match.

### 26.5 Three shipped defects found on the way
1. **Every chat switch was a silent no-op.** `navigate(...) {
   launchSingleTop = true }` matches the *destination*, and all threads
   share the `chat/{conversationId}` pattern, so tapping another chat or
   another document kept whatever was already on screen. This is what
   §25.5 recorded as "unreproducible" — it reproduced the moment a second
   entry point was added. Replaced with one `openChat(route)` helper that
   does `popUpTo(findStartDestination().id) { inclusive = true }`; all five
   chat navigation sites go through it.
2. **Document scoping never reached the request.** `CHAT_PATTERN` declared
   its placeholder as `{documentId}` while `navArgument(ARG_SCOPE)` is named
   `scope`, so the bundle key differed from the key the view model read and
   `savedStateHandle` handed back the `"none"` default forever. Placeholder
   renamed to `{scope}`; a route placeholder name and its `navArgument` name
   must be identical.
3. **The server ignored the scope even when the client sent it.** Fixing (2)
   still produced an answer pulled from the *wrong* PDF. The client had been
   posting `documentId` all along, but `AskRequest` had no such field, so
   `RagService.ask`/`askStream` hard-coded `null` into the retrieval filter
   that `POST /rag/search` already used. Backend work is recorded in the
   project plan, section 25.

### 26.6 Tests
`testDevDebugUnitTest` → `CLIENT_EXIT=0`, `BUILD SUCCESSFUL`, **46** cases.
New since §25:
- `DocumentChatRoutesTest` (3): `chatForDocument("doc-1") ==
  "chat/scoped?scope=doc-1"`, `newChatInDocument`, plain and id routes.
- `ScopedDocumentNameTest` (3): name resolves in either emission order,
  unscoped/deleted give null, and a departed document widens
  `scopedDocumentId` back to null while a live one keeps it.
- `DisplayAnswerTest` (4) from §25 still green.

### 26.7 Verification (physical SM-S711B, tunnel build)
Phone still on the unroutable `Rkarthik` SSID (§24.5), so the build was
installed with `-Prag.devBaseUrl=http://127.0.0.1:8080/` + `adb reverse`,
and a temporary backend with the §26.5.3 fix was run on **8081** so the
user's IDE session on 8080 was never restarted.
- Cold start resumed the latest thread; the scoped document row's Ask bubble
  opened "Ask questions from / thagaval_death_test.pdf" with the
  "Asking thagaval_death_test.pdf" chip.
- Re-tapping the same document returned to the thread holding the previous
  question and answer — continuity proven, not just asserted.
- Tapping an older drawer chat row opened exactly that thread (the §25.5
  bug, now fixed), showing the stored answer with its `[S1]` stripped.
- Menu on a document with two threads: "Delete 2 chats" → confirm dialog →
  both threads gone and the row reverts to plain `Ready`.
- Menu on a document with zero threads: no delete-chats entry at all.
- "Delete document" dialog shows the cascade copy; **Cancel pressed**, no
  user data destroyed.
- Scope proof, same question two ways —
  scoped to `thagaval_death_test.pdf`: "The provided documents do not
  contain an answer to this question."
  unscoped, second later: "writetorahul3125@gmail.com".
- Restored: plain `installDevDebug` (`gradle.properties` line 17 =
  `http://192.168.1.5:8080/` again), `adb reverse --remove-all`, 8081
  instance killed, device and working-directory scratch files deleted.

### 26.8 Still open
- §25.5's markdown point stands: the unscoped answer above arrived as bare
  text this time but answers containing `` `code` `` or `**bold` ` still
  render literally (task #34).
  → Closed the same day by §28: `answerMarkup` renders bold / italic / inline
  code / bullets / headings, and is streaming-aware by re-parsing the buffer.
- The cascade on document delete is proven only for its Room half
  (`deleteAllForDocument` on device); the destructive document path itself
  was exercised up to the confirmation dialog and cancelled.
- Phone must rejoin SSID `Airtel_thanush` before `192.168.1.5:8080` is
  reachable again from the shipped build.

## 27. Icons: compose-icons Feather pack, emerald everywhere (2026-11-07)

### 27.1 The ask
"instead of creating basic icons and using android icons use the icons from
this library https://github.com/DevSrSouza/compose-icons for our use case,
use emerald colour for icons which is our theme" — i.e. stop hand-drawing
vector drawables, stop using the 63-glyph Android core set, and make every
glyph the theme's emerald.

### 27.2 Pack choice
`br.com.devsrsouza.compose.icons` publishes 10 packs at **1.1.1** on Maven
Central (`css-gg, erikflowers-weather-icons, eva-icons, feather,
font-awesome, line-awesome, linea, octicons, simple-icons, tabler-icons`).
Two candidates were measured from Maven Central before choosing:

| Pack | `-android` AAR | Coverage of our 12 glyphs |
| --- | --- | --- |
| **feather** | 0.83 MB | complete |
| tabler-icons | 3.87 MB | complete |

Feather won on size and because its 24dp/2px stroke matches the serif
answer-first look. Note the library has **no Material Design Icons pack**
any more, and `isMinifyEnabled = false` in release means an unused-heavy
pack is pure APK weight — the same argument §23.1 used against
`material-icons-extended`.

### 27.3 Wiring
- `gradle/libs.versions.toml`: new `composeIcons = "1.1.1"` and
  `compose-icons-feather = { group = "br.com.devsrsouza.compose.icons", name =
  "feather", version.ref = "composeIcons" }`. The
  `androidx-compose-material-icons` (material-icons-core) entry was **deleted**.
- `app/build.gradle.kts`: `implementation(libs.compose.icons.feather)`
  replaces the material-icons line. `settings.gradle.kts` already had
  `mavenCentral()`, so no repository change. Resolution confirmed with
  `./gradlew :app:dependencies --configuration devDebugCompileClasspath` →
  `feather:1.1.1` → `feather-android:1.1.1`.
- material3 still drags `material-icons-core` in transitively; that is
  harmless, and nothing in `app/src/main` references `Icons.*` any more
  (`grep -rn "material.icons" app/src/main` → empty).
- The generated API is an empty marker object plus extension properties, so
  **each glyph needs two imports**: `compose.icons.FeatherIcons` *and*
  `compose.icons.feathericons.<Name>`. Forgetting the second one gives
  `Unresolved reference`.
- Gotcha hit during this change: Feather names the cross **`X`**, not `Close`
  (and `Edit2`/`Trash2`/`RotateCw`/`MoreVertical` are the suffixed variants).
  `AppIcons.Close = FeatherIcons.X` keeps the call sites readable.

### 27.4 `core/designsystem/AppIcons.kt` (new)
Semantic names, not shape names, so a screen never imports an icon pack and
the whole set can be re-skinned in one file:

`Menu, Search, NewChat(Edit2), Ask(MessageCircle), Send, Close(X), Delete(Trash2),
More(MoreVertical), Upload, Retry(RotateCw), Settings, Back(ArrowLeft)`

### 27.5 Tint rule
`AppColors.Icon = Accent` (`0xFF16A37A`) added to the token object with the
exception list written next to it:

- **Emerald** — top bar (menu, search, new chat), drawer chat-row delete,
  drawer nav items (Add document → `Upload`, Search inside documents,
  Profile → `Settings`), upload-row cancel, document row retry + ask +
  overflow, scoped-chip "ask all documents" cross, search-field magnifier,
  search-result ask bubble, viewer back arrow.
- **`onPrimary`** (dark ink on an emerald fill) — the send disc and the
  drawer's "New chat" button. Emerald on emerald would be invisible.
- **`onErrorContainer`** — the error banner cross, so a failure control
  does not read as the success colour.

Destructive glyphs are emerald too (delete chat, cancel upload): the user
asked for emerald icons, not for emerald-safe icons, and the confirm dialog
still paints its action label `AppColors.Error`.

### 27.6 Removed
`app/src/main/res/drawable/ic_chat.xml` deleted (unreferenced after the
swap). `ic_app_icon.xml` (notification small icon) and `thagaval_logo`
(splash) stay — those are brand marks, not UI glyphs.

### 27.7 Verification
`assembleDevDebug testDevDebugUnitTest` → `BUILD SUCCESSFUL`,
**46** unit tests still green (icon work is presentation-only, nothing to
assert). Then the tunnel build (`-Prag.devBaseUrl=http://127.0.0.1:8080/` +
`adb reverse`) on the SM-S711B, screenshotted region by region:
- Chat screen: emerald hamburger / magnifier / pencil in the top bar;
  Feather plane in the send disc (grey while the field is empty = disabled
  state, unchanged).
- Drawer: pencil on the emerald "New chat" fill, `Trash2` on all seven chat
  rows, `Upload` on Add document, `MessageCircle` + `DotsVertical` on every
  document row.
- Drawer footer: `Search` and `Settings` emerald.
- Scoped chat: "Asking preview_test_cert.pdf" chip with the emerald `X`.
- Viewer: emerald `ArrowLeft` beside "1 page".
- Search results: emerald `MessageCircle` on a hit row (query "What is this
  document about" → `thagaval_death_test.pdf · p1 · match 0.12`).
- Restored: plain `installDevDebug` (LAN URL back on gradle.properties
  line 17), `adb reverse --remove-all`, device dumps and local screenshots
  deleted.

### 27.8 Incidental observation, not touched
Several library rows render **Queued** forever (`apa-research-paper-template.pdf`,
two `Aayush Atul Chandak Resume.pdf` copies) — the stale `UPLOADED` status
already recorded in the project plan §24.2.1. A `POST /documents/{id}/reprocess`
or a delete clears them; left alone because it is the user's data.

## 28. Answers are bold, not asterisks: markdown-lite without breaking the stream (2026-11-07)

### 28.1 The ask
A screenshot of "what are his skills" came back as

```
- **Languages:** Java, Python, JavaScript, TypeScript, SQL, HTML/CSS
- **Frameworks:** Spring Boot, React.js, Angular, Node.js, Hibernate, JPA
```

— the user's question was *why it is not bolded, is this due to streaming,
and fix it without breaking streaming*.

### 28.2 Diagnosis: streaming was not the cause
Nothing in the app ever interpreted markdown. `MessageRow` and `StreamingRow`
painted `displayAnswer(text)` — a plain `String` — straight into `Text`, and
Compose only styles what an `AnnotatedString` tells it to. So the `**` was
already doomed before the first SSE token; streaming only makes the *fix*
harder, because the parser sees a buffer that is always mid-sentence.

### 28.3 Implementation — `feature/chat/AnswerMarkup.kt` (new)
`internal fun answerMarkup(text: String, streaming: Boolean = false): AnnotatedString`

- **Re-parsed from the whole buffer on every token**, never incrementally.
  `ChatReducer.TokenReceived` appends to `state.streamingText`, so each frame
  re-runs the scanner over the complete partial answer. That is what makes the
  streaming case tractable: there is no parser state to get stuck in, and an
  answer is a few KB, so the cost is irrelevant next to a recomposition.
- **`streaming` is the only extra argument** and it answers one question: is an
  unmatched marker still arriving, or is it the text? While `true`, an open run
  is styled as if it closed at the end of the buffer and its marker is dropped,
  so `**Languages:` shows as bold `Languages:` and a lone trailing `*` (the
  first half of a `**` that has not landed yet) shows as nothing. With `false` —
  the Room transcript, a stopped stream, a failed one — an unmatched marker is
  content the model wrote, so it stays visible.
- Scanner, not regex: `indexOfDelimiter` finds the next `**`, `*` or `` ` ``,
  then looks for the same token later in the line. Found → style the span; not
  found and not at the end → emit the marker literally, which is what keeps
  `2 * 3 * 4` and `resume_srivastava_rahul.pdf` untouched.
- Single `*` emphasis additionally requires CommonMark flanking (no space just
  inside either delimiter), otherwise "3 * 4 * 5" would italicise the middle.
  `_` is deliberately **not** an emphasis delimiter at all — this app prints
  file names full of underscores.
- Block pass before the inline pass: `- ` / `* ` / `+ ` become `• `, `## …`
  becomes a bold line with its hashes dropped, and a ```` ``` ```` fence renders
  its lines monospace with the fence lines dropped.
- Deliberately narrow: no nested emphasis, no links, no tables. Anything
  unrecognised is painted literally rather than half-parsed.

### 28.4 Wiring — `feature/chat/ChatScreen.kt`
Two call sites, and the flag is the only difference:
- `MessageRow` (assistant): `answerMarkup(displayAnswer(message.text))`.
- `StreamingRow`: `answerMarkup(displayAnswer(…), streaming = true)`.
`displayAnswer` still runs first, so `[S1]` markers are gone before the
markdown pass. User bubbles are untouched — that text is typed, not generated.

### 28.5 Tests
`AnswerMarkupTest.kt` (new), 12 cases: bold, plain text untouched, the exact
device answer shape (bullets + bold labels), inline code, fenced block,
heading, flanking (`2 * 3 * 4` stays literal, `*emphasis*` becomes italic),
underscore file names, line breaks survive, an open run styled as closed, a
finished answer keeping its unclosed marker, and the frame sequence
`*` → `**` → `**L` → … → `**Languages:**` asserting **no frame contains a
literal `*`**.

`testDevDebugUnitTest` → `BUILD SUCCESSFUL`, **58** unit tests green (46 + 12).
Two fixes on the way: `AnnotatedString.Range` exposes `item`/`start`/`end`, not
`style`/`range`; and my own first assertion expected the markers to survive a
successful italic parse.

### 28.6 Verification (device `RZCXB0FWFNW`, 2026-11-07)
`testDevDebugUnitTest` → `BUILD SUCCESSFUL`, **59** unit tests green. Then the
plain `installDevDebug` build on the phone, on the LAN URL — the phone is back
on `192.168.1.0/24`, so no `adb reverse` tunnel was needed this time.
- "what are his skills" streamed and landed as
  `Rahul Srivastava's skills, as listed in the context, include:` then
  `• Languages: Java, Python, …` — the crop shows genuine bold on each label,
  `•` instead of `- `, and **no `*` anywhere** in the `uiautomator` text node.
- "what projects has he worked on" produced the harder shape,
  `**Partner Management Portal – Vendor Onboarding** (Spring Boot, Angular,
  PostgreSQL) –` : the name is bold, the parenthesised stack is untouched.
  That frame is the proof the scanner pairs runs without eating words.
- **A scare, resolved:** the first answer rendered `Vendor Onboarding ():`,
  which looked like the renderer swallowing the text between two adjacent
  `**` runs. Fetched the raw model answer with a live `POST /api/v1/rag/ask`
  and the parens are empty in the model's own output. By construction the
  renderer can only ever drop `*` and `` ` `` — every other character is
  appended, including a marker it decides is prose. A regression test now pins
  the adjacent-run shape anyway.
- **Mid-marker frames are not reachable through this backend.** The client sat
  on `Thinking` + caret for ~20 s and then the whole answer appeared in a
  single 400 ms screenshot frame: the reasoning model buffers, so
  `TokenReceived` delivers one large chunk rather than a dribble. The
  frame-by-frame guarantee is therefore covered by the unit test
  (`*` → `**` → `**L` → … → `**Languages:**`, no frame may contain a literal
  `*`), not by a screenshot. Streaming itself is unchanged — the caret, the
  placeholder and the `Done` handover all behaved.
- Tooling note: **`uiautomator dump` fails with "could not get idle state"
  while the caret blinks**, so a streaming screen has to be captured with
  screenshots; dumps only work once generation stops.
- One later ask returned "The AI service is busy — please retry." — the known
  transient NVIDIA limit after several live calls in a row.
- Left behind: four test chats in the account ("what are his skills", "what
  projects has he worked on" ×2, "describe every project skill and job entry
  in full detail"). Not deleted — deleting is the user's call, and the drawer
  per-chat delete is the way to do it.

### 28.7 Still open
- Markdown inside *search results* and the viewer still comes from raw chunk
  text and is painted literally; only the chat answer was made markdown-aware.
- Links (`[text](url)`) and tables are still literal by design (§28.3).

## 29. Phase K — de-duplicated top bar, paged drawer, guided new-chat canvas

### 29.1 The ask
Three screenshots-driven requests, one round:
1. "lets remove the top navbar search pen icon, its already in drawer right" —
   the chat top bar repeated Search and New chat, which the drawer already
   offers.
2. "in drawer, lets show 5 chats and see more … next 5 … similar logics apply
   to document also" — the drawer listed every conversation and every document
   in one unbounded column.
3. "when user selects new chat … user should shown with pin icon, on top of it
   … 2 option upload new file, check existing file … (add upload animation) …
   if existing file is chosen, the chat related that specific document should
   be shown" — the blank canvas gave a new user no way in.

### 29.2 Top bar (`feature/shell/AppShell.kt:309`)
`ShellTopBar` now takes only `onMenu`. The Search and New chat `IconButton`s
and their parameters are deleted, not hidden — the drawer is the single home
for both, per the standing "fewer controls" preference. The bar is hamburger +
"Thagaval" and nothing else.

### 29.3 Drawer pagination (`AppShell.kt:366-367, 390, 434-436, 480, 515-517`)
Two independent counters, `chatsShown` and `documentsShown`, both start at
`DrawerPageSize = 5` (`AppShell.kt:602`) and live in `remember`, so they reset
when the drawer leaves composition but survive scrolling inside it.
`items(state.conversations.take(chatsShown))` and
`items(state.documents.take(documentsShown))`; after each list, only when
`size > shown`, a `SeeMoreRow` (`AppShell.kt:593`) — a full-width `TextButton`
in `AppColors.Accent` — adds another 5. No server change: the shell already
holds the full lists, this is a window over them. The row disappears once
everything is shown, so the last page ends clean.

### 29.4 New-chat empty canvas (`feature/chat/ChatScreen.kt:158-230`)
`EmptyCanvas` keeps its scoped branch (names the file, §25) and redesigns the
unscoped one: a 40 dp attach glyph, then two `OutlinedButton`s —
**Upload new file** and **Check existing file**.
- *Upload new file* calls `onUploadDocument`, which the shell wires to the
  existing SAF picker (`pickDocuments()`), so staging, WorkManager and the
  notification path are reused unchanged.
- *Check existing file* toggles a local `showDocuments` flag and lists every
  library document as a `DocumentPickRow` (`ChatScreen.kt:232`); tapping one
  calls `onOpenDocumentChat(document.id)`, which the shell routes through
  `Routes.chatForDocument(id)` — the document's **resumed** thread, not a fresh
  one (§24/§33 behaviour). Empty library shows "Nothing uploaded yet."
- `ChatScreen`/`ChatContent` signatures gained the two callbacks
  (`ChatScreen.kt:63-64, 83-84`); both previews pass no-ops.

### 29.5 Upload animation (`AppShell.kt:657`)
The Scaffold content is now a `Column`: an `UploadStrip` for
`state.uploads.firstOrNull()` above a `Box(weight = 1f)` holding the NavHost
and the banner, so a running upload never covers the prompt bar.
`UploadStrip` pulses its upload glyph with `rememberInfiniteTransition` +
`animateFloat(0.35f → 1f, tween(700, LinearEasing), RepeatMode.Reverse)` on the
tint alpha, shows the file name, a `LinearProgressIndicator` driven by
`job.percent` while running (indeterminate when queued), a `NN%` / "Queued"
label and a cancel `IconButton` wired to `ShellIntent.CancelUpload`. The strip
is transient: it vanishes when the job leaves `state.uploads`, which for a
small file is under a second.

### 29.6 The "pin" icon — Feather has no pushpin
The request said "pin icon". The Feather pack (§27) contains no pushpin/thumbtack
glyph; the closest "attach a file" meaning is `Paperclip`, exposed as
`AppIcons.Attach` (`core/designsystem/AppIcons.kt:40`) so the facade stays the
only place an icon pack is named. Swapping to a real pushpin later means
adding one glyph to `AppIcons`, nothing else. **Flagged to the user.**

### 29.7 Build and tests
`./gradlew assembleDevDebug testDevDebugUnitTest` → exit 0, `BUILD SUCCESSFUL`,
59 unit tests green (no new tests: this round is layout/navigation only, and
the reducers it touches were already covered). `installDevDebug` → exit 0,
installed on `RZCXB0FWFNW`.

### 29.8 Device verification (2026-11-07, LAN URL, no tunnel)
- **Top bar:** dump shows exactly one actionable node, `Open menu`, plus the
  "Thagaval" title. Search/pen gone.
- **Drawer chats:** 5 rows + `See more`; tap → 10 rows + `See more` (account
  holds >10 threads). Screenshot confirms the emerald "See more" row.
- **Drawer documents:** with only 3 docs no row appeared (correct: `size >
  shown` is false). After the test upload brought the library to 8, the section
  showed 5 + `See more`; tap → all 8 rows, documents `See more` gone while the
  chats one remains. Both branches of the pagination exercised.
- **New chat canvas:** paperclip + the two outlined buttons, nothing else.
  *Check existing file* expanded all 7 library documents; tapping
  `resume_srivastava_rahul.pdf` opened the scoped chat with the
  "Asking resume_srivastava_rahul.pdf" chip **and the prior thread resumed**
  ("what are his skills" + its answer), proving `chatForDocument` reuse.
- **Upload:** *Upload new file* opened the system picker; chose
  `Download/sample5.pdf` (5.8 kB). Frame 1 after selection shows the strip —
  pale-emerald pulsing glyph (mid-alpha, visibly lighter than the full-accent
  progress bar in the same frame), name, 100% bar, cancel X — plus the
  "Added to the upload queue" banner. A frame ~1 s later shows the strip gone
  and the canvas shifted up: completion removes the job, as designed.
  Drawer then lists `sample5.pdf` as **Ready**, i.e. ingestion completed.
- Tooling: the picker sorts by date descending with folders first, so a file
  touched today sits at the top; two blind swipes overshot it and had to be
  reversed. `screencap` immediately after the picker tap still catches the
  picker itself — the return transition takes one frame.

### 29.9 Left behind / still open
- `sample5.pdf` is now a real document in the test account (the upload proof).
  Delete it from the drawer overflow if unwanted; same for the four §28 test
  chats.
- The empty-canvas document list is intentionally unpaginated — it is a
  picker, not a browsing surface; the drawer is where pagination lives.
- Pushpin swap remains a one-line `AppIcons` change if the paperclip is not
  the intended glyph (§29.6).

## 30. Phase L — full-screen Chats and Documents managers (CRUD)

### 30.1 The ask
Two screenshots: the Profile page (Documents 7 / Chats 14 / Backend / Sign out)
and a Google-Photos-style card grid. "when user clicks on chats, he should be
redirected to full screen, where he should be able to list of chats he made
and also crud operation should be doable … similarly … documents … list of
files with preview with ui something similar to 2nd image … crud operations
here". The drawer stays the five-row shortcut (§29); these are the managers.

### 30.2 Entry points and a navigation bug found on device
`Routes.CHATS` / `Routes.DOCUMENTS`; the Profile `StatLine`s for Documents and
Chats are now clickable rows with a Feather chevron (`AppIcons.Forward`),
Backend stays plain text. Both managers take the shell's `ShellViewModel`
directly, like Profile, so lists, banners and intents stay single-source.
**Bug:** the first build wired the managers' chat opens through `openChat`,
whose `popUpTo(startDestination, inclusive = true)` empties the back stack —
on device, BACK from the opened thread landed on the launcher. Managers now
*push* (`navController.navigate`) so BACK returns to the list; the drawer
keeps the root-replacing behaviour (`AppShell.kt`, CHATS/DOCUMENTS
composables).

### 30.3 Chats manager (`feature/chats/ChatsScreen.kt`)
Header: Back, "Chats", New chat action (create). One card per thread: title,
then `scopedName · relativeTime` (new `internal fun relativeTime`, unit-tested:
just now / m / h / d / `d MMM yyyy` beyond a week, future clamped). Row tap
opens the thread (read). Overflow: **Rename** (AlertDialog + OutlinedTextField,
confirm disabled while blank) and **Delete** (shared confirm dialog).
Rename is `ShellIntent.RenameConversation` → `ConversationRepository.rename`,
which trims to `MAX_TITLE`, ignores blank, and deliberately does **not** touch
`updatedAt` — renaming must not reorder recency. `ConfirmAction`/`ConfirmDialog`
moved out of `AppShell` into `core/designsystem/ConfirmDialog.kt` so all three
surfaces (drawer, chats, documents) ask before destroying.

### 30.4 Documents manager (`feature/documents/DocumentsScreen.kt`)
Card per file, in the reference image's order: header (FileText glyph, name,
overflow), a 200 dp **first-page preview**, footer (status dot + label +
chat count, then size + added date). Preview = `PdfRepository.thumbnail`,
which reuses the viewer's `cacheDir/pdf-<id>.pdf` when present (a card opened
once costs no network), renders page 0 at 720 px and caches the bitmap in an
8 MB `LruCache` keyed by id; failure leaves the muted FileText placeholder.
Crop is `ContentScale.Crop` with `Alignment.TopCenter` — the first build
centered the crop and showed mid-body lorem ipsum instead of the title.
Overflow: Open, Ask this document, Start a new chat in this document,
Reprocess (FAILED only, §30.5), Delete N chats (only when N > 0), Delete
document — the destructive pair behind the shared confirm dialog. Header
upload icon and an empty-state "Upload new file" button reuse `pickDocuments`.
`DocumentStatus.color()/label()` moved from AppShell privates to
`core/designsystem/StatusStyle.kt`; `AppIcons.File` = Feather FileText.

### 30.5 What CRUD means against this server
- Chats: create (header), read (list/open), update (rename, local Room title),
  delete (confirm; messages cleared with the thread).
- Documents: create (upload), read (preview + viewer), update (**reprocess**),
  delete (confirm; cascades to scoped chats, §26).
- **Reprocess is a retry, not a refresh.** Tapping it on a READY file on device
  produced the banner "Document is already processed": `DocumentService`
  throws `ConflictException` for READY (and guards PROCESSING). The menu item
  is therefore shown only for FAILED, exactly like the drawer's Retry icon —
  a control that always 409s is a dead control. The 409 round-trip is itself
  the proof the intent → API → error-mapper → banner path works.
- **File rename is deliberately absent**: the backend has no rename endpoint,
  and adding one would need a restart of the backend the user runs from
  IntelliJ (pid 9740), which this session must not touch. Offered as a
  follow-up instead of shipping half-dead UI.

### 30.6 Build and tests
`assembleDevDebug testDevDebugUnitTest installDevDebug` → exit 0,
`BUILD SUCCESSFUL`, **63** unit tests (59 + 4 `RelativeTimeTest`), installed on
`RZCXB0FWFNW`. Three install cycles: initial, back-stack fix, crop/gating fix.

### 30.7 Device verification (2026-11-07, LAN URL)
- Profile rows show counts and chevrons; Chats row → manager with all 16
  threads, relative times and scoped file names.
- **Chat CRUD cycle on a thread of my own:** header New chat created it
  (`CHAT_NEW` creates on open), BACK returned to the manager (post-fix),
  overflow → Rename → typed "rename probe" → row retitled; overflow → Delete →
  confirm → row gone + "Chat deleted." banner. No user thread was touched.
- **Tooling:** the Samsung Keyboard "predicted text" promo (Not now /
  Download) pops on first text entry and ate the confirm tap; dismiss with
  "Not now", then BACK to hide the IME, then re-dump — dialog buttons move
  when the IME closes.
- Documents row → card grid; previews rendered for every file (certificate
  header, resume, lorem page). Card tap → viewer ("5 pages" for sample5).
- Reprocess on READY sample5 → 409 banner (then gated out of the menu).
- Delete document → confirm naming the file and the cascade → row gone +
  "Document deleted." banner. This also cleaned up the Phase K `sample5.pdf`
  test artifact.
- Header upload icon → system `PickActivity` focused; BACK returns to the grid.
- Final screenshot confirms top-anchored previews and the footer
  `Ready · 1 chat` / `561 kB · 7 Nov` shape.

### 30.8 Still open / notes
- The shell banner overlays the manager's header title for its lifetime
  (pre-existing overlay placement from §29); it is tap-to-dismiss and
  transient. Moving it below the header is a one-line Scaffold change if it
  bothers anyone.
- File rename awaits a backend endpoint + a user-owned restart (§30.5).
- The empty-canvas document picker (§29) and the drawer remain unpaginated /
  five-row respectively by design; the managers are the full lists.

## 31. Phase M — Profile "Backend" row is staging-only

### 31.1 The ask
> "similarly in profile , show the backend url only for staging environment ,
> dont show that row for production app"

The LAN address (`http://192.168.1.5:8080/`) is a debugging aid for the dev
build; a shipped production app should not advertise which host it talks to.

### 31.2 The gate
Reuses the flavor field that already exists in `app/build.gradle.kts`
(`dev` → `IS_DEV_BUILD=true`, `prod` → `false`) rather than adding a new
flavor-name comparison:

```kotlin
// ProfileScreen.kt:55
backendUrl = BuildConfig.API_BASE_URL.takeIf { BuildConfig.IS_DEV_BUILD }
```

`ProfileContent` takes `backendUrl: String? = null` and renders the row
conditionally, so the production build has no row at all — not a hidden or
greyed-out control (the "fewer controls, no dead UI" rule):

```kotlin
// ProfileScreen.kt:109
backendUrl?.let { StatLine(label = "Backend", value = it) }
```

The `@Preview` passes a sample URL so the dev layout stays visible in Android
Studio. `StatLine` keeps its optional `onClick` + chevron for the Documents and
Chats rows; Backend has neither because it is read-only information.

### 31.3 Build and tests
`assembleDevDebug assembleProdDebug testDevDebugUnitTest installDevDebug` →
exit 0, `BUILD SUCCESSFUL`, **63** tests, 0 failures, installed on
`RZCXB0FWFNW`. Both flavors compile, which is the check that matters here.

### 31.4 Verification, and its honest limit
- **dev (staging):** re-launched, drawer → Profile, `uiautomator dump` still
  shows `Documents / Chats / Backend / http://192.168.1.5:8080/ / Sign out`.
- **prod:** hiding is guaranteed at compile time (`IS_DEV_BUILD=false` ⇒
  `backendUrl == null` ⇒ the `let` block never runs), but it was **not**
  observed on the device: the prod flavor points at the placeholder
  `https://api.example.invalid/` (RFC-2606, never resolves) and cleartext HTTP
  is only permitted in `src/dev`, so a prod build cannot sign in and therefore
  cannot reach Profile. Verifying it on-device needs a real prod HTTPS
  endpoint, which is out of scope for this session.

## 32. Phase N — composer add button, upload toasts, Profile storage meter

### 32.1 The ask
Three parts, from three screenshots (ChatGPT-style composer, the current
new-chat canvas, and the "Added to the upload queue" banner):
1. The new-chat canvas buttons were wrong: the attach entry belongs **in the
   text field** (a "+" disc like ChatGPT's), and tapping it opens a **dialog**
   with "upload a new file" / "ask from an existing file", each doing its
   respective action.
2. The "Added to the upload queue — it continues if you close the app." banner
   must go, in chat **and** in Profile; upload feedback should be a **toast**
   ("Uploading…", "Uploaded").
3. Profile needs a storage indicator under the Documents row against the
   per-user 50 MB cap: last 10% of free space red, last 30% orange, else
   emerald.

### 32.2 Composer add button + dialog (`feature/chat/ChatScreen.kt`)
- `PromptBar` gained `onAttach` and a `leadingIcon`: a 44dp
  `surfaceVariant` disc with `AppIcons.Add` (Feather `Plus`, new facade entry),
  mirroring the send disc in the trailing slot so the focus outline wraps both.
- `AttachDialog` is one `AlertDialog` with two steps: the choice rows
  (`AttachOption`: Upload new file / Ask from an existing file), then a
  `LazyColumn` of `DocumentPickRow` (max 320dp) or "Nothing uploaded yet.".
  Upload closes the dialog and launches the SAF picker; picking a file closes
  it and opens that document's scoped thread. No confirm button — tapping
  outside dismisses.
- `EmptyCanvas` is now scoped-only (names the file). An unscoped new chat is a
  blank canvas above the composer, exactly like the reference screenshot; the
  two `OutlinedButton`s and the inline expandable list are gone.

### 32.3 Upload feedback is a toast, everywhere
- `ShellState.toast` + `ShellResult.Toast/ToastCleared` + `ShellIntent.ToastShown`;
  `AppShell` shows it in a `LaunchedEffect(state.toast)` via
  `Toast.makeText(..., LENGTH_SHORT)` and immediately clears it. One path
  serves chat, Profile and both managers because they all send
  `ShellIntent.Upload`.
- Enqueue now toasts `Uploading <name>…`; the `QUEUED` banner constant is
  deleted, so the queue sentence no longer appears anywhere. Error banners
  (too large / unreadable / delete / reprocess) are unchanged.
- Completion: `UploadQueue.outcomes()` is a new flow over the **terminal**
  WorkInfo states (SUCCEEDED/FAILED), kept separate from `observe()` so
  finished jobs never become drawer rows. `ShellViewModel` toasts once per
  outcome, and only for ids it saw live in this process (`liveUploadIds` +
  `toasted` sets) — WorkManager retains finished work for days, so without
  that guard every launch would re-toast old uploads.
- **Bug found on device:** the first completion toast read "Uploaded
  Document". WorkManager clears `progress` when work finishes, so the name
  lookup fell back. `UploadWorker` now returns
  `Result.success(workDataOf(KEY_FILE_NAME to fileName))` and `outcomes()`
  reads output data first. Re-verified: "Uploaded phaseN.pdf".
- The animated `UploadStrip` (progress + cancel) stays; it is the only
  place a running upload can be cancelled, and the ask was about the banner.

### 32.4 Profile storage meter (`feature/profile/StorageMeter.kt`)
- The cap is real server-side: `DocumentService.MAX_USER_STORAGE =
  50 * 1024 * 1024`. `USER_STORAGE_BYTES` mirrors it; used bytes are
  `state.documents.sumOf { it.fileSize }`, so the meter needs no new endpoint.
- `storageLevel(used, quota)`: free ≤ 10% → `Critical` (red `AppColors.Error`),
  free ≤ 30% → `Warning` (new `AppColors.Warning` 0xFFD97706), else `Normal`
  (emerald `Accent`). Bar and caption share the tier colour.
- Caption reads whole binary MB ("Used 22% · 39 MB free of 50 MB") because the
  cap is 50 MiB; the decimal `formatSize` would have said "52.4 MB" and
  contradicted the server's own 50 MB wording. `formatSize` moved to
  `core/designsystem/Format.kt` and is shared with the documents footers.
- `drawStopIndicator = {}`: Material3's default end dot is drawn in the bar
  colour at the 100% end and read as "full" at 22% (caught in a screenshot).
- Placement: Documents `StatLine` + meter in one 6dp-spaced column, so the
  meter hugs its row instead of floating 16dp away.

### 32.5 Build and tests
`assembleDevDebug assembleProdDebug testDevDebugUnitTest installDevDebug` →
exit 0, `BUILD SUCCESSFUL`, **69** tests (63 + 6 `StorageLevelTest` covering
0/69.9/70/89.9/90/100/120% of a 1000-byte quota and the default cap),
0 failures. Four install cycles: initial, toast-name fix, stop-dot fix
(the meter/stop-dot change rode the last two).

### 32.6 Device verification (2026-11-08, LAN URL)
- New chat: blank canvas, "+" disc leading in the composer (dump
  `content-desc="Add a document"`). Tap → dialog "Add to this chat" with both
  rows; "Ask from an existing file" → "Ask from a file" list; picking
  `preview_test_cert.pdf` opened its scoped thread ("Ask questions from …" +
  "Asking preview_test_cert.pdf" chip).
- Upload via dialog → SAF picker (its search box found the pushed test file
  faster than scrolling the date-sorted list) → toast **"Uploading
  phaseN.pdf…"** captured on screen with the strip at 100% and **no banner**
  (`grep -c "upload queue"` on the dump = 0); ~5s later toast **"Uploaded
  phaseN.pdf"** captured, strip gone.
- Profile: meter under Documents, emerald, "Used 22% · 39 MB free of 50 MB";
  final screenshot confirms the stop dot is gone. Orange/red tiers are
  unit-tested only — reaching them on device would mean uploading ≥35 MB.
- Cleanup of my own artifacts: three `phaseN.pdf` uploads deleted through the
  Documents manager (menu correctly showed no Reprocess for READY), the five
  empty "New chat" threads this verification created (every New chat open
  persists a thread) deleted through the Chats manager, and the pushed
  `/sdcard/Download/phaseN.pdf` removed. Older empty threads (28m/7h) predate
  this phase and were left alone.

### 32.7 Still open / notes
- The delete/reprocess **banner** still overlays manager headers (§30.8);
  only upload feedback moved to toasts.
- `sample5.pdf` still appears twice in the library — the pre-existing
  duplicate-on-retry suspect (§20.5, task #31), untouched by this phase.
- Idempotency key (#31) and session drop after backend restart (#32) remain
  the next backend-side candidates.

## 33. Phase O — canvas taglines and back-only headers (2026-11-08)

### 33.1 The ask
Two screenshots: the new-chat canvas is empty before *and* after a file is
uploaded or picked, and the managers/viewer stack the shell's hamburger +
"Thagaval" bar on top of their own back header. Wanted: a tagline before any
file and a file-context tagline after; no hamburger/title on Documents,
viewer and Chats — just their back button; titles "Chat history",
"Documents uploaded", and the filename in the viewer.

### 33.2 Canvas taglines (`ChatScreen.kt`, `EmptyCanvas`)
- Unscoped: Display "Ask your documents anything." + Body line that names the
  newest library file (`documents.maxByOrNull { it.createdAt }`):
  "`<file>` is in your library — tap + to ask from it, or ask across
  everything." With an empty library it falls back to "Tap + to upload a PDF
  and start asking." so the canvas reacts to the first upload without any
  auto-scoping (upload behaviour is unchanged, taglines only).
- Scoped: "Ask questions from" + the filename in Display, then a status-aware
  line — READY: "Every answer in this thread comes from this file alone.";
  UPLOADED/PROCESSING: "Still indexing — this thread answers from it the
  moment it is ready."; FAILED: "Indexing failed — retry it from Documents
  uploaded." The scoped document is looked up from `state.documents` by
  `scopedDocumentId`, so the line tracks reprocess/delete live.

### 33.3 One header per screen (`AppShell.kt`)
`navController.currentBackStackEntryAsState()` route ∈ {CHATS, DOCUMENTS,
VIEWER_PATTERN, STAGED_VIEWER_PATTERN} → the Scaffold `topBar` becomes an
insets-only `Box` (statusBars top + displayCutout horizontal) instead of
`ShellTopBar`. The screens' own back headers then sit directly under the
status bar with no per-screen inset changes; chat/search/profile keep the
hamburger + title. Drawer navigation is unchanged (the drawer still opens
from chat).

### 33.4 Titles and the header type scale
- Chats → "Chat history", Documents → "Documents uploaded", viewer → the
  filename (`ViewerState.fileName`, filled by `ViewerResult.Opened` from the
  staged name or a one-shot `observeLibrary().first()` lookup) with the page
  count moved to a quiet trailing label.
- First device pass showed "Documents uploaded" wrapping to two lines at
  `AppType.Display` (26sp serif) and crowding the status bar. Added
  `AppType.Title` (serif 22sp/30sp) and used it, single-line + ellipsis, for
  all three back-only headers; long filenames now ellipsize instead of
  wrapping. Display stays the hero style for answers and the canvas.

### 33.5 Build and tests
`assembleDevDebug assembleProdDebug testDevDebugUnitTest installDevDebug` →
exit 0, `BUILD SUCCESSFUL`, **69** tests, 0 failures (no new tests: the
taglines are pure presentation over existing state). Two install cycles:
initial Phase O build, then the Title-scale fix.

### 33.6 Device verification (2026-11-08, LAN URL)
- New chat: "Ask your documents anything." + "sample5.pdf is in your library
  — tap + to ask from it, or ask across everything."; chat keeps the shell
  bar ("Thagaval" present in the dump, as intended).
- "+" → "Ask from an existing file" → `sample5.pdf`: scoped canvas shows
  "Ask questions from / sample5.pdf / Every answer in this thread comes from
  this file alone." plus the "Asking sample5.pdf" chip.
- Chat history: Back + "Chat history" + new-chat pen, `grep -c
  'Thagaval\|Open menu'` on the dump = 0. Documents uploaded: Back + title +
  upload icon, same grep = 0, single line after the Title fix (title bounds
  height 72px, aligned with the 68px back button). Viewer: Back +
  "sample5.pdf" + "5 pages", grep = 0.
- Cleanup: the two empty threads this verification created (the unscoped
  New chat and the scoped pick) deleted through the Chat history manager;
  list now starts at the pre-existing 43m/7h rows. Proof screenshots kept as
  `verified_phaseO_*.png` in the session scratch dir.

### 33.7 Still open / notes
- Indexing/failed tagline branches are code-verified only; reaching them on
  device needs a fresh upload caught mid-ingest or a forced failure.
- The delete/reprocess banner still overlays manager headers (§30.8/§32.7).
- #31 (upload idempotency, duplicate `sample5.pdf`) and #32 (session drop
  after backend restart) remain the next backend-side candidates.
