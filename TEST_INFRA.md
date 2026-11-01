# TEST_INFRA: CleanTV On-Device Personal AI TV Controller

## 1. Testing Philosophy

The CleanTV Personal AI TV Controller testing infrastructure is designed around five core principles:

1. **Opaque-Box & Requirement-Driven**:
   - Tests validate observable behavior, intent classifications, parameter extraction, fallback routing, and execution outcomes against `ORIGINAL_REQUEST.md` and `PROJECT.md` specifications.
   - Tests do not bind to private implementation details or fragile internal state.

2. **Zero-Facade & Forensic Integrity**:
   - Tests strictly exercise real logic and real assertions (`assertEquals`, `assertTrue`, `assertNotNull`, `assertFalse`).
   - No mock facades that unconditionally pass; every test checks specific invariants and failure modes.

3. **Fast JVM Execution**:
   - Following CleanTV's existing 48 JUnit 4 test suites, all AI test suites execute directly on the local Java 17 JVM in milliseconds without requiring heavy Android emulators or Robolectric overhead.
   - Execution time for the entire AI test suite is under 5 seconds.

4. **Progressive Testability & Decoupled Execution (`AiDriver`)**:
   - The test infrastructure employs a decoupled test harness (`AiDriver`) utilizing reflection and dynamic proxies.
   - This allows independent dual-track development: tests can compile and run via `./gradlew testDebugUnitTest` across all milestones, seamlessly exercising production code as each module is implemented and committed.

5. **Exhaustive Multi-Tier Coverage**:
   - Every one of the 31 inventoried features is covered across four rigorous tiers:
     - **Tier 1**: Feature Coverage (>=5 test cases per feature in isolation; 155+ test cases)
     - **Tier 2**: Boundary & Corner Cases (>=5 test cases per feature for edge inputs, whitespace, punctuation, clamping, missing params, uninstalled apps; 100+ test cases)
     - **Tier 3**: Cross-Feature Combinations (pairwise interactions across apps, volume, settings, media, launcher features, and profiles)
     - **Tier 4**: Real-World Application Scenarios (realistic end-to-end voice interaction sessions)

---

## 2. Test Architecture & Directory Layout

### 2.1 Test Directory Structure
```
baron-inspired-tv-home-codebase/launcher/app/src/test/java/com/gathin/tvhome/ai/
├── AiDriver.java                         # Opaque-box test execution bridge & reflection harness
├── Tier1FeatureCoverageTest.java         # Tier 1: Isolated tests for all 31 features (>=5 per feature)
├── Tier2BoundaryCornerCaseTest.java      # Tier 2: Edge cases, boundaries, clamping, empty input
├── Tier3CrossFeatureCombinationTest.java # Tier 3: Pairwise cross-feature interactions
├── Tier4RealWorldScenarioTest.java       # Tier 4: Multi-step realistic user interaction sessions
├── TvIntentClassifierTest.java           # Intent classification & regex normalization tests
├── GoogleAiFallbackDispatcherTest.java   # 5-tier fallback ladder & intent construction tests
├── TvActionExecutorTest.java             # Action execution, volume clamping, Kids Guard, Bedtime
└── WebRemoteAiEndpointTest.java          # Web Remote HTTP API & HTML speech controls
```

### 2.2 Core Test Infrastructure Components

1. **`AiDriver`**:
   - Provides a unified test driver interface for invoking intent classification, fallback intent construction, action execution, and Web Remote HTTP queries.
   - Dynamically binds to `com.gathin.tvhome.ai.TvIntentClassifier`, `GoogleAiFallbackDispatcher`, and `TvActionExecutor`.
   - Generates dynamic proxies for `TvAppLookup` so tests can define custom installed app catalogs without Android `PackageManager` dependencies.
   - Provides safe HTTP client methods to query `http://127.0.0.1:<port>/api/ai/command?q=...`.
   - Utilizes `org.junit.Assume` to dynamically verify milestone capability presence, ensuring test suite passes cleanly throughout progressive development milestones.

2. **Authoritative Output Derivation**:
   - Expected outputs are derived directly from the specifications in `ORIGINAL_REQUEST.md` (R1, R2, R3) and `PROJECT.md` (§ Interface Contracts & Feature Inventory).
   - Volume clamping is verified against mathematical boundaries $[0, 100]$.
   - Intent extras are verified against Android SDK constants (`SearchManager.QUERY`, `SearchManager.USER_QUERY`, `Intent.EXTRA_TEXT`).

---

## 3. Feature Inventory Test Mapping (All 31 Features)

| Feature # | Feature Name | Primary Test Class | Tier 1 (Isolation) | Tier 2 (Boundary/Edge) | Tier 3 (Cross-Feature) | Tier 4 (Scenario) |
|---|---|---|---|---|---|---|
| F1 | Natural App Launch | `Tier1FeatureCoverageTest`, `TvIntentClassifierTest` | >= 5 test cases | >= 5 test cases | Included | Included |
| F2 | Direct App Name | `Tier1FeatureCoverageTest`, `TvIntentClassifierTest` | >= 5 test cases | >= 5 test cases | Included | Included |
| F3 | App Package Launch | `Tier1FeatureCoverageTest`, `TvIntentClassifierTest` | >= 5 test cases | >= 5 test cases | Included | Included |
| F4 | Relative Volume Step | `Tier1FeatureCoverageTest`, `TvIntentClassifierTest` | >= 5 test cases | >= 5 test cases | Included | Included |
| F5 | Mute / Unmute | `Tier1FeatureCoverageTest`, `TvIntentClassifierTest` | >= 5 test cases | >= 5 test cases | Included | Included |
| F6 | Absolute Percentage Volume | `Tier1FeatureCoverageTest`, `TvIntentClassifierTest` | >= 5 test cases | >= 5 test cases | Included | Included |
| F7 | Home Navigation | `Tier1FeatureCoverageTest`, `TvIntentClassifierTest` | >= 5 test cases | >= 5 test cases | Included | Included |
| F8 | General Settings | `Tier1FeatureCoverageTest`, `TvIntentClassifierTest` | >= 5 test cases | >= 5 test cases | Included | Included |
| F9 | Wi-Fi Settings | `Tier1FeatureCoverageTest`, `TvIntentClassifierTest` | >= 5 test cases | >= 5 test cases | Included | Included |
| F10 | Bluetooth Settings | `Tier1FeatureCoverageTest`, `TvIntentClassifierTest` | >= 5 test cases | >= 5 test cases | Included | Included |
| F11 | TV Inputs | `Tier1FeatureCoverageTest`, `TvIntentClassifierTest` | >= 5 test cases | >= 5 test cases | Included | Included |
| F12 | Play / Pause / Stop | `Tier1FeatureCoverageTest`, `TvIntentClassifierTest` | >= 5 test cases | >= 5 test cases | Included | Included |
| F13 | Skip / Rewind | `Tier1FeatureCoverageTest`, `TvIntentClassifierTest` | >= 5 test cases | >= 5 test cases | Included | Included |
| F14 | Control Center | `Tier1FeatureCoverageTest`, `TvIntentClassifierTest` | >= 5 test cases | >= 5 test cases | Included | Included |
| F15 | Add-on Hub | `Tier1FeatureCoverageTest`, `TvIntentClassifierTest` | >= 5 test cases | >= 5 test cases | Included | Included |
| F16 | Screensaver / Aerial | `Tier1FeatureCoverageTest`, `TvIntentClassifierTest` | >= 5 test cases | >= 5 test cases | Included | Included |
| F17 | Sleep Timer | `Tier1FeatureCoverageTest`, `TvIntentClassifierTest` | >= 5 test cases | >= 5 test cases | Included | Included |
| F18 | Game Mode | `Tier1FeatureCoverageTest`, `TvIntentClassifierTest` | >= 5 test cases | >= 5 test cases | Included | Included |
| F19 | Profile Switcher | `Tier1FeatureCoverageTest`, `TvIntentClassifierTest` | >= 5 test cases | >= 5 test cases | Included | Included |
| F20 | Web Remote AI HTTP Endpoint | `Tier1FeatureCoverageTest`, `WebRemoteAiEndpointTest` | >= 5 test cases | >= 5 test cases | Included | Included |
| F21 | Web Remote Voice Mic Button | `Tier1FeatureCoverageTest`, `WebRemoteAiEndpointTest` | >= 5 test cases | >= 5 test cases | Included | Included |
| F22 | TV Voice AI HUD Overlay | `Tier1FeatureCoverageTest`, `TvActionExecutorTest` | >= 5 test cases | >= 5 test cases | Included | Included |
| F23 | Remote Mic Key Interception | `Tier1FeatureCoverageTest`, `TvActionExecutorTest` | >= 5 test cases | >= 5 test cases | Included | Included |
| F24 | Tier 1: Voice Assist Intent | `Tier1FeatureCoverageTest`, `GoogleAiFallbackDispatcherTest` | >= 5 test cases | >= 5 test cases | Included | Included |
| F25 | Tier 2: Assist Intent | `Tier1FeatureCoverageTest`, `GoogleAiFallbackDispatcherTest` | >= 5 test cases | >= 5 test cases | Included | Included |
| F26 | Tier 3: Katniss / Search | `Tier1FeatureCoverageTest`, `GoogleAiFallbackDispatcherTest` | >= 5 test cases | >= 5 test cases | Included | Included |
| F27 | Tier 4: Web Search Intent | `Tier1FeatureCoverageTest`, `GoogleAiFallbackDispatcherTest` | >= 5 test cases | >= 5 test cases | Included | Included |
| F28 | Tier 5: Browser URI Search | `Tier1FeatureCoverageTest`, `GoogleAiFallbackDispatcherTest` | >= 5 test cases | >= 5 test cases | Included | Included |
| F29 | Tier 6: Graceful Degradation | `Tier1FeatureCoverageTest`, `GoogleAiFallbackDispatcherTest` | >= 5 test cases | >= 5 test cases | Included | Included |
| F30 | Kids Guard Enforcement | `Tier1FeatureCoverageTest`, `TvActionExecutorTest` | >= 5 test cases | >= 5 test cases | Included | Included |
| F31 | Bedtime Limit Enforcement | `Tier1FeatureCoverageTest`, `TvActionExecutorTest` | >= 5 test cases | >= 5 test cases | Included | Included |

---

## 4. Coverage Goals Across Tiers

### Tier 1: Feature Coverage (Isolation)
- **Goal**: Verify every single feature in complete isolation with >= 5 distinct test cases.
- **Scope**:
  - Application launching variants ("open", "launch", "start", "run", direct title, package name).
  - Volume controls (raise, lower, louder, softer, mute, unmute, silence, set 0%, set 25%, set 50%, set 75%, set 100%).
  - System settings navigation (home, settings, wifi, bluetooth, inputs).
  - Media playback (play, pause, stop, resume, next, skip, previous, rewind).
  - CleanTV features (control center, addons, screensaver, sleep timer, game mode, profile switcher).
  - Web Remote AI endpoints (`GET /api/ai/command?q=...`, `POST /api/ai/command`).
  - Google AI Fallback ladder (Tiers 1 through 6 intent builders and degradation).
  - Parental controls (Kids Guard blocked status, Bedtime limit blocked status).
- **Minimum Test Count**: 155 test cases.

### Tier 2: Boundary & Corner Cases
- **Goal**: Stress test input sanitization, boundaries, error handling, and corner cases with >= 5 test cases per feature where applicable.
- **Scope**:
  - Empty input (`""`, `"   "`, `null`) returning `EMPTY_INPUT` error.
  - Missing parameters (`"open"`, `"set volume to"`) returning `MISSING_PARAMETER` error.
  - Numeric volume clamping (clamping 150 -> 100, clamping -20 -> 0, non-numeric values).
  - Special characters and punctuation stripping (`"Can you please open YouTube?!?!"`, `"What's the weather???"`).
  - Mixed-case normalization (`"oPeN yOuTuBe"`, `"VOLUME UP"`, `"sEt VoLuMe To 50%"`).
  - Uninstalled applications (`"open Disney Plus"` returning `APP_NOT_INSTALLED` error without silent web search).
  - Whitespace tolerance (leading/trailing tabs, multiple internal spaces).
- **Minimum Test Count**: 100 test cases.

### Tier 3: Cross-Feature Combinations
- **Goal**: Verify pairwise interactions and state transitions between major features.
- **Scope**:
  - Volume adjustment during media playback.
  - Launching an application, then returning Home.
  - Navigating settings, then launching an application.
  - Toggling Kids Guard mode, verifying app restrictions, then switching to Personal profile and launching the app.
  - Cycling sleep timer, then toggling game mode.
  - Muting volume, then setting absolute volume.
- **Minimum Test Count**: 30 test cases.

### Tier 4: Real-World Application Scenarios
- **Goal**: Validate multi-step conversational and operational user journeys.
- **Scope**:
  - Morning News & TV Routine.
  - Evening Movie Session with Volume Clamping & Sleep Timer.
  - Kids Safe Watching Transition.
  - Google Assistant Conversational & Knowledge Exploration Journey.
  - Multi-Client Web Remote Command Stream.
  - System Connectivity & Audio Diagnostic Routine.
- **Minimum Test Count**: 20 test cases.

---

## 5. Verification Commands

To run all unit and AI E2E test suites:
```powershell
.\gradlew.bat testDebugUnitTest
```

To run only the AI test suites:
```powershell
.\gradlew.bat testDebugUnitTest --tests "com.gathin.tvhome.ai.*"
```

To run a specific tier:
```powershell
.\gradlew.bat testDebugUnitTest --tests "com.gathin.tvhome.ai.Tier1FeatureCoverageTest"
.\gradlew.bat testDebugUnitTest --tests "com.gathin.tvhome.ai.Tier2BoundaryCornerCaseTest"
.\gradlew.bat testDebugUnitTest --tests "com.gathin.tvhome.ai.Tier3CrossFeatureCombinationTest"
.\gradlew.bat testDebugUnitTest --tests "com.gathin.tvhome.ai.Tier4RealWorldScenarioTest"
```
