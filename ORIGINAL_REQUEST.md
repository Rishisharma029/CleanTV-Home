# Original User Request

## Initial Request — 2026-10-26T17:42:02Z

This is a single self-contained fix; keep it small and focused.

Resolve the CleanTV Home mobile web remote connection failure where phones or PCs on the same Wi-Fi network receive "This site can't be reached" (connection reset or timeout) when connecting to the TV's web remote HTTP server.

Working directory: c:\Users\Rishi Sharma\Desktop\realme tv
Integrity mode: development

## Requirements

### R1. Deterministic Local Network Server Binding
Ensure the embedded HTTP server in `WebRemoteServer.java` binds correctly across local network interfaces (dual-stack IPv4 `0.0.0.0` and IPv6 `::` or explicit Wi-Fi/Ethernet socket) so that incoming HTTP requests from phones or PCs on the local subnet are reliably accepted and handled rather than dropped by the TV's kernel or firewall.

### R2. Robust Local IP Discovery & Resolution
The IP address and QR code displayed on the TV pairing screen and top HUD status bar must accurately reflect the reachable Wi-Fi (`wlan0`) or Ethernet (`eth0`) IPv4 address of the TV, gracefully prioritizing active physical interfaces over loopback, dummy, or virtual interfaces.

### R3. Resilient Request Parsing & HTTP Response Delivery
The embedded HTTP server must cleanly read HTTP request lines and headers without hanging indefinitely on unclosed client keep-alive streams, returning standard HTTP/1.1 200 responses for the HTML5 web remote and canvas synchronization endpoints.

## Acceptance Criteria

### Connectivity & Functional Verification
- [ ] Direct curl/HTTP request to `http://<tv-ip>:8080/` from the local network returns HTTP/1.1 200 OK with the full CleanTV Remote HTML within 500ms.
- [ ] Direct request to `http://<tv-ip>:8080/api/status` returns valid JSON with active profile, audio mode, and TV status.
- [ ] Mobile browser scanning the on-screen QR code loads the remote control interface and the "✏️ Note" drawing canvas immediately without "Site can't be reached" or timeout errors.
- [ ] All existing launcher unit tests pass (`./gradlew testDebugUnitTest`).


## 2026-10-26T17:48:14Z

Implement an on-device personal AI TV controller for the CleanTV launcher that parses natural voice/text commands to perform TV actions (opening applications, controlling settings, navigating system features) and automatically delegates unknown or conversational queries to the TV's native Google Assistant / Search AI.

Working directory: c:\Users\Rishi Sharma\Desktop\realme tv
Integrity mode: development

## Requirements

### R1. TV Command Intent Recognition & Action Execution
Implement a voice/text intent recognition engine capable of matching natural speech and written commands to direct TV actions, including opening installed applications (by label or package name), adjusting volume, toggling system settings, and controlling media playback.

### R2. Voice Overlay & Remote Control Integration
Integrate an interactive voice prompt/HUD overlay directly into the CleanTV Android TV launcher UI, as well as voice/text input handling within the local Web Remote interface, enabling users to trigger the personal AI from either their TV remote microphone or a connected phone browser.

### R3. Seamless Native Google AI Fallback
When a user query cannot be resolved to a local TV control action (e.g. general knowledge questions, conversational queries, or unrecognized commands), automatically and gracefully forward the transcribed query to the TV's native Google Assistant or Google Search intent so the TV's built-in Google AI handles the response.

## Acceptance Criteria

### Intent Recognition & Action Execution
- [ ] Automated test suite verifies natural command matching for opening applications (e.g., "open YouTube", "launch Netflix", "start Prime Video") correctly identifies the target app and generates the proper launch intent.
- [ ] Commands for system actions (e.g., volume control, mute, settings, return home) trigger their respective actions with appropriate feedback messages.

### Fallback Routing
- [ ] Automated tests verify that unrecognized, out-of-scope, or informational queries (e.g., "what's the weather", "who is the president", "tell me a joke") trigger the fallback dispatcher rather than throwing an unhandled exception or silent failure.
- [ ] Fallback dispatcher constructs and fires an `ACTION_VOICE_ASSIST` or Google Search intent containing the original query text.

### Web Remote & Launcher UI
- [ ] CleanTV launcher displays a responsive AI listening/processing HUD overlay when activated by the user.
- [ ] The Web Remote HTTP interface includes an AI command endpoint and web remote UI control that accepts voice transcription or typed input and returns the execution result.

### Build & Test Verification
- [ ] All unit and instrumentation tests pass cleanly via `./gradlew testDebugUnitTest`.
- [ ] The Android TV application builds cleanly without errors (`./gradlew assembleDebug`).


## 2026-10-27T18:53:41Z

The server was restarted. Please resume orchestrating the teamwork project from where you left off (Milestone 1 Iteration 2 remediation verification, gate evaluation, and advancing through Milestones 2, 3, 4 to completion).
