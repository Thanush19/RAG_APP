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

