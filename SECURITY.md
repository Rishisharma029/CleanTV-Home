# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 3.5.x   | :white_check_mark: |
| 3.4.x   | :white_check_mark: |
| < 3.0   | :x:                |

## Zero-Root & Reversibility Guarantee

CleanTV Home adheres to strict safety principles:
1. **Zero-Root Required**: Operates strictly within standard Android User permissions via ADB (`user 0`).
2. **Non-Destructive Operations**: We exclusively use `pm disable-user --user 0`, **never** `pm uninstall`. System APKs remain present in the TV's read-only system partitions and can be restored instantly via `restore.bat` or factory reset.
3. **Automatic Safety Snapshot**: Prior to changing any package or setting, a complete snapshot of installed packages, properties, and global settings is exported into `backups/`.
4. **No Telemetry / No Tracking**: The installer, launcher, and scripts make **no** external analytics calls or tracking queries.

## Network & Debugging Security

When USB or Network Debugging is active on your TV, port 5555 is listening on your local Wi-Fi:
* Always ensure your TV is on a secured, trusted private network.
* **Immediately after installation or maintenance, switch USB Debugging OFF in TV Developer Options.**
* If your TV's port 5555 is probed while debugging is off, it will return `ConnectionRefused`.

## Reporting a Vulnerability

If you discover a potential security vulnerability or an edge-case package that causes an issue on a specific TV board, please report it:
* Open an issue or email: `i.rishisharma2007@gmail.com`
* Please include your TV model, Android version, and logs if safe to share.
* Vulnerability reports are handled promptly.
