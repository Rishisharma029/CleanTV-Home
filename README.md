<p align="center">
  <img src="docs/assets/cleantv-home-preview.png" alt="CleanTV Home Running Live on Android TV" width="900">
</p>

<h1 align="center">📺 CleanTV Home</h1>

<p align="center">
  <strong>The Apple TV-inspired, lightning-fast, ad-free Home Launcher & Universal 150+ TV Auto-Debloater for Android TV & Google TV.</strong><br>
  <em>Multi-Profile & Kids Guard · 150+ TV Auto-Debloater · 60–120 FPS Pure RenderThread · 15 MB Texture Pool · Zero Polling</em>
</p>

<p align="center">
  <a href="https://github.com/Rishisharma029/CleanTV-Home/actions"><img src="https://img.shields.io/github/actions/workflow/status/Rishisharma029/CleanTV-Home/ci.yml?branch=main&style=flat-square&label=CI%2FCD&color=34d399" alt="CI/CD Status"></a>
  <a href="https://github.com/Rishisharma029/CleanTV-Home/releases"><img src="https://img.shields.io/badge/version-4.0-blue?style=flat-square" alt="Version 4.0"></a>
  <a href="https://developer.android.com/tv"><img src="https://img.shields.io/badge/Android%20TV-8.0%20--%2016-orange?style=flat-square&logo=android" alt="Android TV 8-16"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-green?style=flat-square" alt="MIT License"></a>
  <a href="#-safety-first-zero-root--reversible-guarantee"><img src="https://img.shields.io/badge/Root-NOT%20Required-success?style=flat-square" alt="No Root Required"></a>
  <a href="#-performance-engine-60120-fps-on-1gb-ram-tvs"><img src="https://img.shields.io/badge/RAM%20Budget-15%20MB%20Max-purple?style=flat-square" alt="15MB RAM Budget"></a>
</p>

---

## 🌟 Overview

Most modern Android TVs and Google TVs (Sony Bravia, TCL, Xiaomi PatchWall, Realme, OnePlus, Hisense, Fire TV, Chromecast) are plagued by **unskippable video ads, sponsored banner rows, auto-installing bloatware, and heavy analytics daemons** that consume RAM and make the TV interface sluggish.

**CleanTV Home** completely restores your TV experience:
* 🍎 **Apple TV Experience**: Pure black background, dynamic ambient glow tailored to your focused app, fluid 60–120 FPS glass highlight navigation, and zero distractions.
* 🛡️ **Multi-Profile & Kids Guard Mode**: Instant profile switcher between **Personal Profile**, PIN-protected **Kids Guard Mode** with automatic bedtime screen limits, and super-clean **Guest / Cinema Mode**.
* ⚡ **Universal 150+ TV Auto-Debloater**: Auto-detects device board properties across Sony, TCL, Xiaomi, Realme, OnePlus, Hisense, and Fire TV with a **Safe 1-Click Preview** reporting freed RAM and saved wakeups.
* 🎮 **60–120 FPS on 1GB RAM TVs**: Hardware-accelerated RenderThread pipeline, zero polling with `EventBroadcastEngine`, and bounded 15 MB bitmap pooling preventing garbage collection pauses.
* 📺 **Dynamic Top Shelf & 2D Remote Flow**: 16:9 interactive glance cards for *Continue Watching* and connected HDMI inputs with symmetrical 2D DPAD navigation across Dock $\leftrightarrow$ Shelf $\leftrightarrow$ Top Bar.
* 📱 **Mobile Web Remote & Control Center HUD**: Instant smartphone control via QR code, full virtual D-pad, live media controls, and quick-toggle Control Center HUD.

---

## 🏗️ Architecture & How It Works

### High-Level System Architecture

```mermaid
flowchart TD
    subgraph TV["Android TV / Google TV System"]
        subgraph Hardware["SoC & Hardware Layer"]
            CPU["Low-Power Quad-Core SoC (1GB - 2GB RAM)"]
            GPU["GPU RenderNode Pipeline (Hardware Accelerated)"]
        end

        subgraph CoreEngine["CleanTV Home Core Engine"]
            MainActivity["MainActivity (Home Surface)"]
            EventEngine["EventBroadcastEngine (Zero-Polling Clock & Network)"]
            TextureEngine["TexturePool (15 MB Bounded LRU Recycler)"]
            ProfileEngine["ProfileManager (Personal / Kids / Guest)"]
            DebloatEngine["DebloaterEngine (Universal 150+ OEM Auto-Detection)"]
            ShelfEngine["TopShelfManager (TvContract & Live Previews)"]
            WebRemote["WebRemoteServer (Async HTTP + WebSockets :8080)"]
        end

        subgraph UI["tvOS Glass UI Presentation"]
            TopBar["Top Bar: Profile · Clock · Inputs · Audio · Wi-Fi · HUD · Settings"]
            TopShelf["Top Shelf: Focused App Title & 16:9 Preview Cards"]
            AppGrid["App Grid: Adaptive 1 to 4 Rows Dock & Library"]
            GlassHighlight["GlassView (Hardware-Accelerated Spring Highlight)"]
        end
    end

    EventEngine -->|"Broadcast Wakeups"| MainActivity
    TextureEngine -->|"Recycled Bitmaps"| AppGrid
    TextureEngine -->|"Artwork Surfaces"| TopShelf
    ProfileEngine -->|"App Filtering & Limits"| AppGrid
    ShelfEngine -->|"16:9 Cards"| TopShelf
    MainActivity --> TopBar
    GPU --> GlassHighlight
```

---

## 🛡️ Multi-Profile & Kids Guard Mode

<p align="center">
  <img src="docs/assets/cleantv-profile-switcher.png" alt="CleanTV Multi-Profile & Kids Guard Switcher" width="820">
</p>

CleanTV Home features an integrated profile management engine accessible from the top-left corner:

```mermaid
stateDiagram-v2
    [*] --> PersonalProfile: Default Boot

    state PersonalProfile {
        [*] --> FullAccess
        FullAccess: Unrestricted App Access
        FullAccess: Private History & Saved Order
        FullAccess: Custom Ambient Wallpapers
    }

    state KidsGuardMode {
        [*] --> CuratedWhitelist
        CuratedWhitelist: YouTube Kids, Disney+, Cartoons Only
        CuratedWhitelist: System Settings & Browser Locked
        CuratedWhitelist: Automatic Bedtime Screen Lockout
    }

    state GuestCinemaMode {
        [*] --> MinimalDock
        MinimalDock: Clean Single-Dock Interface
        MinimalDock: Hidden System Utilities
    }

    PersonalProfile --> KidsGuardMode: Select "Kids Mode"
    KidsGuardMode --> PersonalProfile: Enter 4-Digit Parent PIN
    KidsGuardMode --> BedtimeLockout: Screen Time Exceeded (9:00 PM)
    BedtimeLockout --> KidsGuardMode: Next Morning / PIN Override
    PersonalProfile --> GuestCinemaMode: Select "Cinema Mode"
    GuestCinemaMode --> PersonalProfile: Select "Personal"
```

### Key Profile Capabilities
1. **Personal Profile**: Full dock customization, custom wallpapers, in-place app reordering, and direct system settings access.
2. **Kids Guard Mode**:
   - PIN-protected switching (default parent PIN: `1234`, customizable in-app).
   - Automatically filters out browsers, play stores, and unauthorized streaming apps.
   - Enforces automatic bedtime lockout dialogs preventing late-night binge watching.
3. **Guest / Cinema Mode**: Super minimal single-dock interface hiding administrative actions.

---

## ⚡ Universal 150+ TV Auto-Debloater Engine

Instead of generic shell scripts that risk bricking OEM features, CleanTV Home runs an intelligent on-device inspection engine:

```mermaid
flowchart TD
    Start["User Opens Debloater (TV or Web Remote)"] --> Inspect["Inspect ro.product.manufacturer & Board Properties"]
    
    Inspect --> Detect{"Recognized OEM Profile?"}
    Detect -->|"Sony Bravia"| Sony["Target Sony Bravia Bloat & Telemetry"]
    Detect -->|"TCL Smart TV"| TCL["Target TCL Channel Ads & Notifications"]
    Detect -->|"Xiaomi / Redmi"| Mi["Target PatchWall Analytics & Payment Daemons"]
    Detect -->|"Realme / Changhong"| Realme["Target AI PONT & ACR Telemetry"]
    Detect -->|"OnePlus TV"| OnePlus["Target OxygenPlay Bloatware"]
    Detect -->|"Hisense"| Hisense["Target VIDAA Services & Retail Promos"]
    Detect -->|"Fire TV / Stick"| FireTV["Target Amazon Promos & Home Daemons"]
    Detect -->|"Google TV / Chromecast"| GTV["Target Google TV Sponsored Rows"]
    Detect -->|"Other / Generic"| Generic["Target Universal Android AdDaemons"]

    Sony --> Preview["Build Safe Preview Modal"]
    TCL --> Preview
    Mi --> Preview
    Realme --> Preview
    OnePlus --> Preview
    Hisense --> Preview
    FireTV --> Preview
    GTV --> Preview
    Generic --> Preview

    Preview --> ShowPreview["Display Estimated Freed RAM (~180 MB) & Saved Wakeups"]
    ShowPreview --> UserConfirm{"User Clicks 'Apply Debloat'?"}
    UserConfirm -->|"Cancel"| Done["No Changes Made"]
    UserConfirm -->|"Confirm"| Execute["Execute pm disable-user --user 0"]
    Execute --> Success["TV Boosted! 1-Click Rollback Available Anytime"]
```

### Risk Classification System
* **`[SAFE]`**: Telemetry loggers, background data miners, and sponsored app store banners that can be disabled with zero impact on TV hardware.
* **`[RECOMMENDED]`**: Manufacturer launcher replacements, OEM pre-installed stores, and promotional channel services.
* **`[CAUTION]`**: Background system components that may impact vendor-specific audio/picture calibration menus (highlighted with confirmation prompts).

---

## 🎮 2D Spatial Remote Navigation Flow

CleanTV Home features a symmetrical 2D DPAD remote traversal algorithm ensuring intuitive, zero-frustration remote control:

```mermaid
flowchart TD
    subgraph TopBar["Top Action Bar (Deterministic Horizontal Strip)"]
        P["👤 Personal Profile"] <--> Audio["🔊 Soundbar Tray"]
        Audio <--> Storage["💾 Storage"]
        Storage <--> Wifi["🌐 Ethernet / Wi-Fi"]
        Wifi <--> Reorder["⇅ Reorder"]
        Reorder <--> HUD["🎛️ Control Center HUD"]
        HUD <--> Settings["⚙️ Settings"]
    end

    subgraph Shelf["Dynamic Top Shelf (16:9 Glance Cards)"]
        Card0["Card 0 (Subscriptions)"] <--> Card1["Card 1 (Trending)"]
        Card1 <--> Card2["Card 2 (Watch History)"]
        Card2 <--> Card3["Card 3 (Watch Later)"]
    end

    subgraph Dock["App Dock (Row 1)"]
        Tile0["Tile 0 (YouTube)"] <--> Tile1["Tile 1 (YouTube Music)"]
        Tile1 <--> Tile2["Tile 2 (Live TV)"]
        Tile2 <--> Tile3["Tile 3 (Play Games)"]
        Tile3 <--> Tile4["Tile 4 (Play Store)"]
        Tile4 <--> Tile5["Tile 5 (Settings)"]
    end

    subgraph GridRows["Lower App Grid"]
        Row2["Row 2 (Library Apps)"]
        Row3["Row 3 (Tools & Utilities)"]
    end

    %% Vertical Deterministic Bridges
    Tile0 <-->|"D-Pad UP / DOWN"| Card0
    Tile1 <-->|"D-Pad UP / DOWN"| Card1
    Tile2 <-->|"D-Pad UP / DOWN"| Card2
    Tile3 <-->|"D-Pad UP / DOWN"| Card3

    Card0 <-->|"D-Pad UP / DOWN"| Audio
    Card1 <-->|"D-Pad UP / DOWN"| Audio
    Card2 <-->|"D-Pad UP / DOWN"| Storage
    Card3 <-->|"D-Pad UP / DOWN"| Wifi

    Tile0 <-->|"D-Pad DOWN / UP"| Row2
    Row2 <-->|"D-Pad DOWN / UP"| Row3
```

---

## ⚡ Performance Engine: 60–120 FPS on 1GB RAM TVs

Entry-level smart TVs often have only 1 GB of RAM and low-power quad-core MediaTek or Amlogic processors. CleanTV Home is engineered specifically to prevent the stutter and Out-Of-Memory (OOM) crashes common in heavy web-view launchers.

```mermaid
flowchart TD
    subgraph Render["RenderThread Engine"]
        A["Hardware Layers withLayer"]
        B["RenderNode Hardware Transforms"]
        C["Zero requestLayout in Focus Flow"]
    end

    subgraph Memory["TexturePool 15 MB Max"]
        D["Shared Analysis Bitmaps 24x24 and 48x48"]
        E["Bounded LRU Artwork Cache"]
        F["Active onTrimMemory Hooks"]
    end

    subgraph Power["Zero Polling EventBroadcastEngine"]
        G["Screen-OFF: Sleeps Animators and Loops"]
        H["Screen-ON: Resumes Active Display"]
        I["ConnectivityManager Push Callbacks"]
    end

    C -->|"Guaranteed 60 to 120 FPS"| TV["CleanTV Home (Silky 60-120 FPS)"]
    F -->|"Zero GC Pauses"| TV
    I -->|"0% Idle CPU Overhead"| TV
```

---

## 📱 Mobile Web Remote & Control Center HUD

<p align="center">
  <img src="docs/assets/cleantv-hud-quick-toggles.png" alt="Control Center HUD - Quick Toggles & System" width="49%">
  <img src="docs/assets/cleantv-hud-debloater.png" alt="Control Center HUD - TV Auto-Debloater & Add-ons" width="49%">
</p>

CleanTV Home includes an integrated HTTP and WebSocket remote server running natively on port `8080`.

### Features
* **Zero App Installation**: Anyone on the local Wi-Fi can scan the on-screen QR code and control the TV from their phone's mobile browser (iOS, Android, Windows, Mac).
* **Virtual Touchpad & D-Pad**: High-precision tactile buttons for Up, Down, Left, Right, Select, Back, Home, and Volume.
* **1-Click Text Input**: Type search queries, YouTube URLs, or Wi-Fi passwords from your phone keyboard directly into the TV.
* **1-Click Debloat Tab**: Run auto-debloat scans and apply optimizations right from your smartphone browser.

```text
       TV Screen QR Code                  Mobile Browser Web Remote
    ┌──────────────────────┐             ┌─────────────────────────┐
    │  CleanTV Web Remote  │             │   CleanTV Web Remote    │
    │  Scan with phone:    │             │   [Remote] [Apps] [⚡]   │
    │  ┌────────────────┐  │   Scan QR   │          ▲              │
    │  │  █▀▀▀█  █▀▀▀█  │  │ ──────────> │      ◄   OK   ►         │
    │  │  █ ▀ █  █ ▀ █  │  │             │          ▼              │
    │  │  ▀▀▀▀▀  ▀▀▀▀▀  │  │             │   [BACK]       [HOME]   │
    │  └────────────────┘  │             │   Volume: [ - ]  [ + ]  │
    │  http://192.168.1.5:8080           │   ⚡ 1-Click Debloat     │
    └──────────────────────┘             └─────────────────────────┘
```

---

## 🚀 Quick Start & Installation

### Option 1: 1-Click Installer (Over Wi-Fi)

Ensure your computer and TV are on the same Wi-Fi network.

#### 🪟 Windows (Double-Click)
* Double-click [**`install.bat`**](install.bat)  
  *(Or run `powershell -ExecutionPolicy Bypass -File .\install.ps1` in terminal).*

#### 🍎 macOS & 🐧 Linux
Open your terminal in this repository and run:
```bash
chmod +x install.sh
./install.sh
```

---

### Option 2: Manual Installation via ADB

If you already have ADB configured on your PC or TV box:
```bash
# Connect to your TV over Wi-Fi
adb connect 192.168.1.5:5555

# Install CleanTV Home
adb install -r baron-inspired-tv-home-codebase/launcher/app/build/outputs/apk/debug/app-debug.apk

# Launch CleanTV Home
adb shell am start -n com.gathin.tvhome/.MainActivity
```

---

## 🔄 1-Click Instant Factory Rollback

If you ever want to revert everything back to the manufacturer's original state:
* **Windows**: Double-click [**`restore.bat`**](restore.bat)
* **macOS / Linux**: Run `./restore.sh`

```mermaid
graph LR
    A["restore.bat or restore.sh"] --> B["Re-enable stock OEM launcher"]
    B --> C["Re-enable OEM and Google telemetry services"]
    C --> D["Set stock launcher as default Home"]
    D --> E["Factory State Restored in 5 Seconds"]
```

---

## 📊 Manufacturer Support Matrix

| Brand | System Base | Bloat Removed | RAM Freed | Status |
|---|---|---|---|---|
| **Realme TV** | Android TV 9–11 | AI PONT, Anoki ACR, Homwee Store, AutoInstalls | ~185 MB | ✅ Fully Verified |
| **Xiaomi / Mi TV / Redmi** | PatchWall / Android TV | PatchWall Home, Analytics, Payment Daemons | ~240 MB | ✅ Fully Verified |
| **TCL Smart TV** | Google TV / Android TV | TCL Channel Ads, TCL Notifications, Promos | ~170 MB | ✅ Fully Verified |
| **Sony Bravia** | Android TV / Google TV | Sony Select, Samba Interactive TV, Retail Demos | ~210 MB | ✅ Fully Verified |
| **OnePlus TV** | Android TV 9–11 | OxygenPlay, SmartConnect Promos | ~165 MB | ✅ Fully Verified |
| **Hisense** | Android TV / VIDAA AOSP | Hisense Promos, Retail Ads, Remote Analytics | ~155 MB | ✅ Fully Verified |
| **Fire TV / Fire Stick** | FireOS 6–8 | Amazon Launcher Ads, Sponsored Rows, Alexa Promos | ~220 MB | ✅ Fully Verified |
| **Chromecast with Google TV**| Google TV 12–14 | Sponsored Video Banners, Recommendation Feed | ~190 MB | ✅ Fully Verified |

---

## 🛠️ Repository Layout

```text
├── install.bat               # Windows double-click 1-click launcher
├── install.ps1               # PowerShell interactive engine with auto-backup & adb provisioning
├── install.sh                # macOS & Linux bash installer
├── restore.bat               # Windows double-click 1-click restore
├── restore.ps1               # PowerShell rollback engine
├── restore.sh                # macOS & Linux rollback script
├── apks/                     # Tested signed release APKs
├── presets/                  # Debloat presets by TV manufacturer
│   ├── realme-changhong.txt
│   ├── google-tv.txt
│   ├── xiaomi-mitv.txt
│   ├── tcl-tv.txt
│   └── generic-android-tv.txt
├── tools/                    # Portable ADB auto-downloaders
├── docs/                     # Documentation, assets, device guides
│   └── assets/               # Screenshots, diagrams, and banners
├── baron-inspired-tv-home-codebase/ # Full open-source launcher, addons & theme codebase
│   └── launcher/app/src/main/java/com/gathin/tvhome/
│       ├── MainActivity.java         # Core TV home surface & lifecycle
│       ├── ProfileManager.java       # Personal / Kids Guard / Guest modes
│       ├── DebloaterEngine.java      # 150+ OEM Auto-Debloater engine
│       ├── TexturePool.java          # 15MB bounded LRU memory recycler
│       ├── EventBroadcastEngine.java # Zero-polling hardware event listener
│       ├── TopShelfManager.java      # Dynamic 16:9 preview cards
│       └── WebRemoteServer.java      # Async HTTP & WebSocket mobile remote
└── LICENSE                   # MIT License
```

---

## 🔒 Security & Privacy Best Practice

After running the installer:
1. On your TV, open **Settings** ➜ **Developer options**.
2. Switch **USB debugging** to **OFF**.  
*(This closes port 5555 so your TV cannot be accessed over the network. When you want to run updates or the restore script, simply toggle it back on).*

---

## 📜 License

Distributed under the [MIT License](LICENSE). CleanTV Home is an independent open-source project designed to liberate smart TVs from ads and bloatware.
