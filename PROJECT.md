# Project: CleanTV On-Device Personal AI TV Controller

## Architecture
CleanTV is an ultra-lean, 100% pure Java 17 Android TV launcher (`compileSdk 34`, `targetSdk 34`, `minSdk 26`).
The AI TV controller adds on-device voice and text intent recognition, execution of TV actions (apps, volume, settings, media, launcher features), a responsive TV HUD overlay, Web Remote integration, and a seamless 5-tier fallback ladder to native Google Assistant / Search AI.

### Architectural Layers
1. **Core Intent Engine & Classification (`com.gathin.tvhome.ai`)**:
   - `TvIntent`: Immutable representation of a parsed user command (`IntentType`, action string, parameter map, raw query).
   - `TvAppLookup`: Interface for abstracting installed application name resolution and package mapping.
   - `TvIntentClassifier`: High-speed, on-device regex, token normalization, and fuzzy string distance parser. Classifies input into `LAUNCH_APP`, `VOLUME_CONTROL`, `SYSTEM_ACTION`, `MEDIA_CONTROL`, `LAUNCHER_FEATURE`, or `GOOGLE_AI_FALLBACK`.
   - `GoogleAiFallbackDispatcher`: Multi-tier fallback ladder building `Intent.ACTION_VOICE_ASSIST`, `Intent.ACTION_ASSIST`, `Intent.ACTION_SEARCH` (Katniss), `Intent.ACTION_WEB_SEARCH`, and browser intent with `SearchManager.QUERY`, `USER_QUERY`, and `EXTRA_TEXT`.
2. **Action Execution & Platform Bridge (`com.gathin.tvhome.ai`)**:
   - `TvActionExecutor`: Bridges parsed `TvIntent` to Android platform services (`MainActivity.launchAppByPackage`, `AudioManager` for discrete steps, mute, and 0-100% volume, `SystemSettings` for settings pages, `KeyEvent` for media controls) while enforcing Kids Guard Mode and Bedtime limits.
3. **TV UI HUD Overlay (`com.gathin.tvhome.ai.ui`)**:
   - `VoiceAiHUD`: Attached to `MainActivity.root` (`FrameLayout`) following the established `ControlCenterHUD` pattern. Manages 5 visual states (`LISTENING`, `PROCESSING`, `EXECUTING`, `FALLBACK`, `ERROR`), traps D-pad and Back keys, and restores prior focus upon dismissal.
   - Remote mic key interception: `MainActivity.dispatchKeyEvent()` intercepts `KEYCODE_VOICE_ASSIST` (231), `KEYCODE_SEARCH` (84), and `KEYCODE_ASSIST` (219).
4. **Web Remote Integration (`com.gathin.tvhome`)**:
   - `WebRemoteServer`: Implements `/api/ai/command?q=...` using `CountDownLatch` UI thread synchronization.
   - `buildRemoteHtml()`: Adds voice mic button (HTML5 Web Speech API `webkitSpeechRecognition`), text input box, and execution status card.
5. **E2E Testing Track**:
   - Independent test harness validating requirement-driven acceptance criteria across Tiers 1-4 and adversarial Tier 5.

---

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | Natural App Launch | Opens installed TV app by spoken/written name ("open YouTube", "launch Netflix") | M1 | Survey Spec Miner 3 |
| 2 | Direct App Name | Launches app when user speaks just the app title ("YouTube", "Spotify") | M1 | Survey Spec Miner 3 |
| 3 | App Package Launch | Launches app when specified by exact package name ("com.google.android.youtube.tv") | M1 | Survey Spec Miner 3 |
| 4 | Relative Volume Step | Increments/decrements media volume by 1 discrete step ("volume up", "louder") | M1 | Survey Spec Miner 3 |
| 5 | Mute / Unmute | Mutes or restores audio output ("mute", "unmute", "silence") | M1 | Survey Spec Miner 3 |
| 6 | Absolute Percentage Volume | Sets volume to exact percentage ("set volume to 20%", "volume 50") | M1 | Survey Spec Miner 3 |
| 7 | Home Navigation | Returns TV to CleanTV home launcher screen ("go home", "return home") | M1 | Survey Spec Miner 3 |
| 8 | General Settings | Opens TV system settings menu ("open settings", "system settings") | M1 | Survey Spec Miner 3 |
| 9 | Wi-Fi Settings | Opens Wi-Fi configuration screen ("wifi settings", "open wifi") | M1 | Survey Spec Miner 3 |
| 10 | Bluetooth Settings | Opens Bluetooth pairing screen ("bluetooth settings", "pair remote") | M1 | Survey Spec Miner 3 |
| 11 | TV Inputs | Opens TV input switcher ("inputs", "tv inputs", "switch hdmi") | M1 | Survey Spec Miner 3 |
| 12 | Play / Pause / Stop | Controls background media playback ("play", "pause", "stop", "resume") | M1 | Survey Spec Miner 3 |
| 13 | Skip / Rewind | Advances or rewinds media track ("next track", "skip", "rewind") | M1 | Survey Spec Miner 3 |
| 14 | Control Center | Opens translucent frosted glass Control Center HUD ("control center") | M1 | Survey Spec Miner 3 |
| 15 | Add-on Hub | Opens CleanTV Add-on package manager ("addon hub", "open addons") | M1 | Survey Spec Miner 3 |
| 16 | Screensaver / Aerial | Starts 4K Apple TV-style aerial artwork screensaver ("screensaver") | M1 | Survey Spec Miner 3 |
| 17 | Sleep Timer | Cycles or sets deep standby sleep countdown ("sleep timer") | M1 | Survey Spec Miner 3 |
| 18 | Game Mode | Toggles low-latency TV game picture mode ("game mode") | M1 | Survey Spec Miner 3 |
| 19 | Profile Switcher | Opens Profile selection dialog ("switch profile", "kids mode") | M1 | Survey Spec Miner 3 |
| 20 | Web Remote AI HTTP Endpoint | HTTP API accepting voice/text queries (/api/ai/command?q=...) | M4 | Survey Spec Miner 3 |
| 21 | Web Remote Voice Mic Button | Speech-to-text input via Web Speech API in phone browser | M4 | Survey Spec Miner 3 |
| 22 | TV Voice AI HUD Overlay | Frosted glass listening/processing overlay on TV screen | M3 | Survey Spec Miner 3 |
| 23 | Remote Mic Key Interception | Intercepts remote mic buttons (KEYCODE_VOICE_ASSIST, KEYCODE_SEARCH) | M3 | Survey Spec Miner 3 |
| 24 | Tier 1: Voice Assist Intent | Dispatches query to system voice assist activity (ACTION_VOICE_ASSIST) | M1 | Survey Spec Miner 3 |
| 25 | Tier 2: Assist Intent | Dispatches query to system assistant activity (ACTION_ASSIST) | M1 | Survey Spec Miner 3 |
| 26 | Tier 3: Katniss / Search | Explicitly targets Android TV Google Search (ACTION_SEARCH Katniss) | M1 | Survey Spec Miner 3 |
| 27 | Tier 4: Web Search Intent | Dispatches query to web search activity (ACTION_WEB_SEARCH) | M1 | Survey Spec Miner 3 |
| 28 | Tier 5: Browser URI Search | Dispatches query to web browser via search URL (ACTION_VIEW) | M1 | Survey Spec Miner 3 |
| 29 | Tier 6: Graceful Degradation | Cleanly handles devices where no assistant exists (status: unavailable) | M1 | Survey Spec Miner 3 |
| 30 | Kids Guard Enforcement | Blocks unauthorized apps when voice-launched in Kids Mode | M2 | Survey Spec Miner 3 |
| 31 | Bedtime Limit Enforcement | Prevents app launches when bedtime limit has expired | M2 | Survey Spec Miner 3 |

---

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| M1 | Intent Recognition Engine & Fallback Core | `TvIntent`, `TvIntentClassifier`, `TvAppLookup`, `GoogleAiFallbackDispatcher`, `<queries>` in `AndroidManifest.xml`, unit tests | None | DONE |
| M2 | System Action Executor & Platform Bridge | `TvActionExecutor`, app launch delegation with Kids Guard/Bedtime, `AudioManager` volume/mute/media, `SystemSettings`, launcher actions | M1 | DONE |
| M3 | CleanTV Voice AI HUD Overlay & Remote Keys | `VoiceAiHUD` on `MainActivity.root`, 5 visual states, D-pad/Back key trapping, remote mic key event handling (`KEYCODE_VOICE_ASSIST`), top bar pill | M1, M2 | DONE |
| M4 | Web Remote AI Endpoint & UI Controls | `WebRemoteServer` `/api/ai/command`, UI thread synchronization, HTML5 Web Speech API mic button, text input, execution feedback card | M1, M2 | IN_PROGRESS |
| M5 | Final Milestone: E2E Test Pass & Adversarial Hardening | Phase 1: Pass 100% of E2E test suite (Tiers 1-4 from `TEST_READY.md`). Phase 2: Tier 5 adversarial coverage hardening & forensic integrity audit. | M1, M2, M3, M4, E2E Track | PLANNED |

---

## Interface Contracts

### 1. `TvIntentClassifier` ↔ `TvActionExecutor`
```java
package com.gathin.tvhome.ai;

public enum IntentType {
    LAUNCH_APP,
    VOLUME_CONTROL,
    SYSTEM_ACTION,
    MEDIA_CONTROL,
    LAUNCHER_FEATURE,
    GOOGLE_AI_FALLBACK,
    UNKNOWN
}

public class TvIntent {
    public final IntentType type;
    public final String action;
    public final Map<String, Object> params;
    public final String rawQuery;
    ...
}

public interface TvAppLookup {
    String findPackageForLabel(String query);
    String getLabelForPackage(String pkg);
}

public class TvIntentClassifier {
    public static TvIntent classify(String query, TvAppLookup appLookup);
}
```

### 2. `GoogleAiFallbackDispatcher` Contract
```java
package com.gathin.tvhome.ai;

import android.content.Context;
import android.content.Intent;

public class GoogleAiFallbackDispatcher {
    public static Intent buildVoiceAssistIntent(String query);
    public static Intent buildSystemAssistIntent(String query);
    public static Intent buildKatnissSearchIntent(String query);
    public static Intent buildWebSearchIntent(String query);
    public static Intent buildBrowserSearchIntent(String query);
    public static ExecutionResult dispatch(Context context, String query);
}
```

### 3. `TvActionExecutor` ↔ Launcher & Web Remote
```java
package com.gathin.tvhome.ai;

public class ExecutionResult {
    public final String status;      // "ok", "error", "blocked", "unavailable"
    public final String action;      // "open_app", "volume_set", "google_fallback", etc.
    public final String target;      // package name or target parameter
    public final String label;       // human readable label
    public final String message;     // UI feedback message
    public final String query;       // original query
    public final boolean isFallback; // true if handled by Google AI fallback
    ...
}

public class TvActionExecutor {
    public static ExecutionResult execute(MainActivity activity, TvIntent intent);
}
```

### 4. Web Remote AI Endpoint `/api/ai/command`
- Endpoint: `GET /api/ai/command?q=<query>` and `POST /api/ai/command` with body `{"query": "..."}` or form `q=...`
- Response: `200 OK`
- JSON Payload:
```json
{
  "status": "ok",
  "action": "open_app",
  "target": "com.google.android.youtube.tv",
  "label": "YouTube",
  "message": "Opening YouTube on TV",
  "query": "open youtube",
  "isFallback": false
}
```

---

## Code Layout
- Package: `com.gathin.tvhome.ai`
  - `TvIntent.java`
  - `TvIntentClassifier.java`
  - `TvAppLookup.java`
  - `TvActionExecutor.java`
  - `GoogleAiFallbackDispatcher.java`
  - `ExecutionResult.java`
- Package: `com.gathin.tvhome.ai.ui`
  - `VoiceAiHUD.java`
- Layouts: `baron-inspired-tv-home-codebase/launcher/app/src/main/res/layout/voice_ai_hud.xml`
- Integrations:
  - `baron-inspired-tv-home-codebase/launcher/app/src/main/AndroidManifest.xml` (package `<queries>` declarations)
  - `baron-inspired-tv-home-codebase/launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java` (key event interception, top bar pill, HUD lifecycle)
  - `baron-inspired-tv-home-codebase/launcher/app/src/main/java/com/gathin/tvhome/WebRemoteServer.java` (`/api/ai/command` endpoint and Web Remote HTML/JS)
- Tests:
  - `baron-inspired-tv-home-codebase/launcher/app/src/test/java/com/gathin/tvhome/ai/TvIntentClassifierTest.java`
  - `baron-inspired-tv-home-codebase/launcher/app/src/test/java/com/gathin/tvhome/ai/GoogleAiFallbackDispatcherTest.java`
  - `baron-inspired-tv-home-codebase/launcher/app/src/test/java/com/gathin/tvhome/ai/TvActionExecutorTest.java`
  - `baron-inspired-tv-home-codebase/launcher/app/src/test/java/com/gathin/tvhome/ai/WebRemoteAiEndpointTest.java`
