# Contributing to CleanTV Home

Thank you for helping make Android TV and smart TVs clean, fast, and ad-free!

## How to Contribute

### 1. Adding Device Profiles & Debloat Presets
If you test CleanTV Home on a new TV model (Sony, TCL, Hisense, OnePlus, Philips, etc.):
1. Create a debloat preset file in `presets/<brand>-<model>.txt`.
2. Document any unique vendor services in `docs/devices/<brand>-<model>.md`.
3. Submit a Pull Request.

### 2. Improving the Launcher or Addons
The native launcher source code is located in `baron-inspired-tv-home-codebase/launcher`.
* Requires JDK 17+ and Android SDK 34+.
* Follow lean design principles: minimal APK footprint, zero background telemetry, fluid 60fps D-pad animations.

### 3. Submitting PRs
1. Fork the repository.
2. Create your feature branch (`git checkout -b feature/cool-feature`).
3. Commit your changes (`git commit -m 'Add some feature'`).
4. Push to the branch (`git push origin feature/cool-feature`).
5. Open a Pull Request.
