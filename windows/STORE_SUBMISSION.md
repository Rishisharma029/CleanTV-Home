# CleanTV Desktop Manager — Microsoft Store Submission Guide

This guide details the requirements, configuration, and verification procedures for publishing CleanTV Desktop Manager (`CleanTV-Desktop-Manager-v4.0.0.msix`) to the **Microsoft Store** via **Microsoft Partner Center**.

---

## 1. Application Identity Configuration

Before submitting the MSIX package to Partner Center, the package manifest identity attributes must be mapped to your registered Partner Center developer identity.

In [`windows/src/AppxManifest.xml`](file:///windows/src/AppxManifest.xml):

```xml
<Identity
    Name="RishiSharma.CleanTV"
    Publisher="CN=4C22920B-E5FC-4E8E-BFF1-7F166D6008F2"
    Version="4.0.0.0"
    ProcessorArchitecture="x64" />
```

### Steps to retrieve Identity values:
1. Sign in to [Microsoft Partner Center](https://partner.microsoft.com/dashboard).
2. Navigate to **Apps and games** > **Product management** > **Package identity**.
3. Copy the following values into `AppxManifest.xml`:
   - **Package Name (Identity Name)**: Must match your reserved name in Partner Center.
   - **Publisher ID (`CN=...`)**: Your official Microsoft Store publisher identifier.
   - **Publisher Display Name**: Your public publisher display name.

> [!NOTE]
> During local development, testing, and CI/CD runs, the package is signed with the automated self-signed certificate `CN=CleanTV`. For Store submission, Microsoft automatically signs packages with Microsoft's trusted root authority during ingestion.

---

## 2. Restricted Capabilities Justification (`runFullTrust`)

CleanTV Desktop Manager declares the `runFullTrust` restricted capability in `AppxManifest.xml`:

```xml
<Capabilities>
    <rescap:Capability Name="runFullTrust" />
</Capabilities>
```

### Partner Center Submission Justification Text
When prompted during the Store submission process under **Submission Options > Notes for Certification**, provide the following justification:

```text
Application Name: CleanTV Desktop Manager
Restricted Capability: runFullTrust

Reason for Capability:
CleanTV Desktop Manager is an Android TV configuration and management utility that communicates directly with Android TV devices over the local network and USB. Full trust is strictly required for the following system operations:

1. Process Management: Spawning and managing the official Google Android Debug Bridge (adb.exe) client daemon locally to execute device setup and configuration commands.
2. Local Network Communication: Establishing TCP connections to local Android TV endpoints on ports 5555 (ADB), 8080 (Launcher Remote), and 59822 (ADB Pair) across local subnets for TV discovery, health polling, and remote control.
3. Persistent Backup Verification: Reading and writing rollback snapshots (package disable states, system properties) to %LOCALAPPDATA%\CleanTV\backups to guarantee user data protection before any package is debloated.

The application does not run background tasks outside its main window, does not modify Windows system registry entries, collects zero user telemetry, and operates exclusively within the user's private local area network.
```

---

## 3. Pre-Flight Certification: Windows App Certification Kit (WACK)

Before uploading to Partner Center, validate the package locally using the official Windows App Certification Kit (WACK) included in the Windows SDK:

```powershell
# Run WACK automated validation test
& "C:\Program Files (x86)\Windows Kits\10\App Certification Kit\appcert.exe" test `
    -appxpackagepath "windows\dist\CleanTV-Desktop-Manager-v4.0.0.msix" `
    -reportoutputpath "windows\dist\wack_report.xml"
```

### Automated Checklist Tested by CI and WACK:
- [x] **AppxManifest Schema Compliance**: Tested in CI via `windows/tests/test-manifest.js`.
- [x] **Package Structure & Assets**: Tested in CI via `makeappx unpack` and square asset verification (Square44x44Logo, Square150x150Logo, StoreLogo).
- [x] **High-DPI Display Scaling**: Vector SVG and standard PNG resolutions included.
- [x] **Safe Uninstallation**: App does not leave orphaned files in Windows System folders; cleanly stores all user data in `%LOCALAPPDATA%\CleanTV`.
- [x] **No Hardcoded Credentials / Secrets**: Zero API keys or cloud tokens required; local session security tokens are ephemeral and generated on launch.

---

## 4. Package Artifacts to Upload

The release pipeline generates the complete, ready-to-upload package in:
- **MSIX Artifact**: `windows/dist/CleanTV-Desktop-Manager-v4.0.0.msix`
- **Architecture**: x64 (Native 64-bit Windows)
- **Minimum OS Version**: Windows 10 Build 17763 (Version 1809) or higher
- **Included Dependencies**: Bundled Google Platform Tools (`adb.exe`), OEM Presets, and CleanTV Launcher APK.

---

## 5. Store Listing & Metadata Recommendations

- **Category**: Utilities & Tools > Device Management
- **Age Rating**: Everyone / PEGI 3 (No sensitive content)
- **Prerequisites for End Users**:
  - Android TV / Google TV device connected to the same Wi-Fi network or connected via USB.
  - Developer Options > USB / Network Debugging enabled on the TV.
- **Privacy Policy**: 
  - CleanTV does not collect, track, or transmit any personal data or usage analytics to external servers. All operations are local between your Windows PC and your TV.
