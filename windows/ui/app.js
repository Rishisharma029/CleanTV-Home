// CleanTV Home Desktop Manager — Frontend Client
// Real ADB-backed operations. No mock success. No simulated fake progress.
'use strict';

document.addEventListener('DOMContentLoaded', () => {
  // ── State ──────────────────────────────────────────────────────────────────
  let currentDevice = '';
  let isConnected = false;
  let adbAvailable = false;

  // ── DOM References ─────────────────────────────────────────────────────────
  const statusDot = document.getElementById('statusDot');
  const statusText = document.getElementById('statusText');
  const tvIpInput = document.getElementById('tvIpInput');
  const remoteConsole = document.getElementById('remoteConsole');

  const btnConnect = document.getElementById('btnConnect');
  const btnScan = document.getElementById('btnScan');
  const btnEmu = document.getElementById('btnEmu');
  const btnPingTv = document.getElementById('btnPingTv');
  const btnWakeTv = document.getElementById('btnWakeTv');
  const btnOpenWebRemote = document.getElementById('btnOpenWebRemote');

  const btnDeployApk = document.getElementById('btnDeployApk');
  const installProgressBox = document.getElementById('installProgressBox');
  const progressFill = document.getElementById('progressFill');
  const progressLabel = document.getElementById('progressLabel');

  const presetSelect = document.getElementById('presetSelect');
  const btnRunDebloat = document.getElementById('btnRunDebloat');
  const btnRunRestore = document.getElementById('btnRunRestore');
  const packageList = document.getElementById('packageList');
  const statPackages = document.getElementById('statPackages');

  const aiTextInput = document.getElementById('aiTextInput');
  const btnAiSend = document.getElementById('btnAiSend');
  const aiIntentBadge = document.getElementById('aiIntentBadge');
  const aiActionTitle = document.getElementById('aiActionTitle');
  const aiActionSubtitle = document.getElementById('aiActionSubtitle');

  // ── Tab Switching ──────────────────────────────────────────────────────────
  document.querySelectorAll('.nav-item').forEach(item => {
    item.addEventListener('click', () => {
      document.querySelectorAll('.nav-item').forEach(n => n.classList.remove('active'));
      document.querySelectorAll('.tab-pane').forEach(p => p.classList.remove('active'));
      item.classList.add('active');
      const pane = document.getElementById(item.getAttribute('data-tab'));
      if (pane) pane.classList.add('active');
    });
  });

  // ── Logging ────────────────────────────────────────────────────────────────
  function log(msg, level) {
    if (!remoteConsole) return;
    const colors = { error: '#f87171', warn: '#fbbf24', ok: '#34d399', info: '#94a3b8' };
    const color = colors[level] || colors.info;
    const time = new Date().toLocaleTimeString();
    remoteConsole.innerHTML += `<br><span style="color:${color}">[${time}] ${escHtml(msg)}</span>`;
    remoteConsole.scrollTop = remoteConsole.scrollHeight;
  }

  function escHtml(s) {
    return String(s || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  }

  // ── Connection UI State ────────────────────────────────────────────────────
  function setConnectionState(connected, label) {
    isConnected = connected;
    if (statusDot) {
      statusDot.className = 'status-dot ' + (connected ? 'connected' : 'disconnected');
    }
    if (statusText) {
      statusText.textContent = label || (connected ? `Online · ${currentDevice}` : 'Disconnected');
    }
    const adbVal = document.getElementById('infoAdbStatus');
    if (adbVal) adbVal.textContent = connected ? `Online (${currentDevice})` : 'Offline';
  }

  function setButtonLoading(btn, loading, defaultText) {
    if (!btn) return;
    btn.disabled = loading;
    if (loading) {
      btn.dataset.origText = btn.textContent;
      btn.textContent = '...';
    } else {
      btn.textContent = defaultText || btn.dataset.origText || 'OK';
    }
  }

  // ── Real API Request Helper ────────────────────────────────────────────────
  function api(endpoint, data) {
    return fetch(endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data || {})
    }).then(r => {
      return r.json().then(j => {
        if (!r.ok || j.status === 'error') {
          return Promise.reject(new Error(j.message || `HTTP ${r.status}`));
        }
        return j;
      });
    });
  }

  // ── 1. ADB Availability Check ──────────────────────────────────────────────
  function checkAdbStatus() {
    api('/api/adb-status', {}).then(res => {
      adbAvailable = res.adbFound;
      if (res.adbFound) {
        log(`ADB Engine: ${res.bundled ? 'Bundled (Google Platform Tools)' : 'System PATH (' + res.adbPath + ')'}`, 'ok');
      } else {
        log('WARNING: ADB not found. Sideloading and remote functions will be unavailable.', 'error');
      }
    }).catch(err => {
      log('Could not connect to CleanTV local background host: ' + err.message, 'error');
    });
  }

  // ── 2. Device Connection ───────────────────────────────────────────────────
  function connectDevice(deviceAddress) {
    const dev = (deviceAddress || (tvIpInput ? tvIpInput.value : '') || '').trim();
    if (!dev) {
      log('Please enter an IP address or hostname for the TV.', 'warn');
      return;
    }

    currentDevice = dev.includes(':') ? dev : `${dev}:5555`;
    setConnectionState(false, `Connecting to ${currentDevice}...`);
    log(`Connecting to ${currentDevice}...`, 'info');
    setButtonLoading(btnConnect, true);

    api('/api/connect', { device: currentDevice }).then(res => {
      setButtonLoading(btnConnect, false, 'Connect to TV');
      setConnectionState(true, `Connected · ${currentDevice}`);
      log(`Connected successfully: ${res.output || 'Device ready'}`, 'ok');
      loadDeviceInfo();
    }).catch(err => {
      setButtonLoading(btnConnect, false, 'Connect to TV');
      setConnectionState(false, 'Connection Failed');
      log(`Connection to ${currentDevice} failed: ${err.message}`, 'error');
    });
  }

  if (btnConnect) {
    btnConnect.addEventListener('click', () => connectDevice());
  }

  if (btnEmu) {
    btnEmu.addEventListener('click', () => {
      if (tvIpInput) tvIpInput.value = '127.0.0.1:5555';
      connectDevice('127.0.0.1:5555');
    });
  }

  // ── 3. Real Device Auto-Scan ───────────────────────────────────────────────
  if (btnScan) {
    btnScan.addEventListener('click', () => {
      setButtonLoading(btnScan, true, 'Auto-Scan');
      log('Scanning for connected ADB devices and emulators...', 'info');

      api('/api/scan', {}).then(res => {
        setButtonLoading(btnScan, false, 'Auto-Scan');
        const devices = res.devices || [];
        if (devices.length === 0) {
          log('Scan finished: No ADB devices detected. Ensure USB/Network Debugging is ON.', 'warn');
        } else {
          log(`Scan finished: Found ${devices.length} device(s):`, 'ok');
          devices.forEach(d => {
            log(` -> ${d.serial} [${d.state}] ${d.info || ''}`, d.state === 'device' ? 'ok' : 'warn');
          });
          // Auto-fill first active device
          const activeDev = devices.find(d => d.state === 'device') || devices[0];
          if (activeDev && tvIpInput) {
            tvIpInput.value = activeDev.serial;
            connectDevice(activeDev.serial);
          }
        }
      }).catch(err => {
        setButtonLoading(btnScan, false, 'Auto-Scan');
        log('Device scan error: ' + err.message, 'error');
      });
    });
  }

  // ── 4. Device Information Fetcher ──────────────────────────────────────────
  function loadDeviceInfo() {
    if (!currentDevice || !isConnected) return;

    api('/api/device-info', { device: currentDevice }).then(res => {
      const elDevName = document.getElementById('infoDeviceName');
      const elAdb = document.getElementById('infoAdbStatus');
      const elLauncher = document.getElementById('infoLauncherState');
      const elMem = document.getElementById('infoMemory');

      if (elDevName) elDevName.textContent = `${res.brand || ''} ${res.model || 'Android TV'} (Android ${res.androidVersion || '?'})`.trim();
      if (elAdb) elAdb.textContent = `Online (${currentDevice})`;
      if (elLauncher) elLauncher.textContent = res.launcherState || 'Active';
      if (elMem) elMem.textContent = res.memory || 'Active';

      // Auto-select matching preset based on brand
      const b = (res.brand || '').toLowerCase();
      if (presetSelect) {
        if (b.includes('realme') || b.includes('changhong')) presetSelect.value = 'realme';
        else if (b.includes('sony') || b.includes('bravia')) presetSelect.value = 'sony';
        else if (b.includes('xiaomi') || b.includes('redmi')) presetSelect.value = 'xiaomi';
        else if (b.includes('tcl')) presetSelect.value = 'tcl';
        else if (b.includes('google') || (res.model || '').toLowerCase().includes('chromecast')) presetSelect.value = 'google';
        else if (b.includes('hisense')) presetSelect.value = 'hisense';
        loadPresetPackages(presetSelect.value);
      }

      log(`Device verified: ${res.brand} ${res.model} · Android ${res.androidVersion}`, 'ok');
    }).catch(err => {
      log('Could not retrieve full device info: ' + err.message, 'warn');
    });
  }

  // Quick action buttons
  if (btnPingTv) {
    btnPingTv.addEventListener('click', () => {
      if (!currentDevice) { log('No device specified to ping.', 'warn'); return; }
      log(`Pinging device state for ${currentDevice}...`, 'info');
      api('/api/check-state', { device: currentDevice }).then(res => {
        setConnectionState(res.connected, res.connected ? `Online · ${currentDevice}` : 'Offline');
        log(`Device state: ${res.state} (Online: ${res.connected})`, res.connected ? 'ok' : 'error');
      }).catch(err => log('Ping error: ' + err.message, 'error'));
    });
  }

  if (btnWakeTv) {
    btnWakeTv.addEventListener('click', () => {
      if (!isConnected) { log('Connect to TV first.', 'warn'); return; }
      log('Sending Wakeup keyevent (KEYCODE_WAKEUP)...', 'info');
      api('/api/key', { device: currentDevice, key: '224' }).then(() => {
        log('Wakeup keyevent transmitted.', 'ok');
      }).catch(err => log('Wake error: ' + err.message, 'error'));
    });
  }

  if (btnOpenWebRemote) {
    btnOpenWebRemote.addEventListener('click', () => {
      const ipOnly = currentDevice.split(':')[0];
      const remoteUrl = `http://${ipOnly}:8080/`;
      log(`Opening Web Remote at ${remoteUrl}...`, 'info');
      window.open(remoteUrl, '_blank');
    });
  }

  // ── 5. Real Presets Listing ────────────────────────────────────────────────
  function loadPresetPackages(presetName) {
    api('/api/preset-list', { preset: presetName }).then(res => {
      const pkgs = res.packages || [];
      if (packageList) {
        packageList.innerHTML = '';
        pkgs.forEach(pkg => {
          const item = document.createElement('div');
          item.className = 'pkg-item';
          item.innerHTML = `
            <div>
              <strong style="font-family:monospace;font-size:12px;">${escHtml(pkg)}</strong>
              <div style="font-size:11px;color:#94a3b8;">Preset: ${escHtml(res.file)}</div>
            </div>
            <span class="pkg-tag">SAFE TO DISABLE</span>
          `;
          packageList.appendChild(item);
        });
      }
      if (statPackages) statPackages.textContent = pkgs.length;
    }).catch(err => {
      log('Could not load preset list: ' + err.message, 'warn');
    });
  }

  if (presetSelect) {
    presetSelect.addEventListener('change', () => loadPresetPackages(presetSelect.value));
    loadPresetPackages(presetSelect.value);
  }

  // ── 6. Real Debloat Execution ──────────────────────────────────────────────
  if (btnRunDebloat) {
    btnRunDebloat.addEventListener('click', () => {
      if (!isConnected) {
        log('Not connected to a TV. Connect first.', 'error');
        alert('Please connect to your TV first.');
        return;
      }

      const preset = presetSelect ? presetSelect.value : 'realme';
      if (!confirm(`Are you sure you want to run 1-Click Safe Debloat for preset "${preset}"?\n\nA full backup of disabled packages will be created automatically before disabling anything.`)) {
        return;
      }

      setButtonLoading(btnRunDebloat, true, '⚡ 1-Click Safe Debloat');
      log(`Executing real Safe Debloat for preset [${preset}] on ${currentDevice}...`, 'info');

      api('/api/debloat', { device: currentDevice, preset: preset }).then(res => {
        setButtonLoading(btnRunDebloat, false, '⚡ 1-Click Safe Debloat');
        log(`Debloat finished: ${res.disabled} disabled, ${res.skipped} skipped (not on TV), ${res.failed} failed.`, 'ok');
        log(`Pre-debloat backup saved: ${res.backupDir}`, 'ok');
        alert(`Debloat Complete!\n\n${res.disabled} package(s) safely disabled.\n${res.skipped} package(s) not installed on TV.\nBackup saved to:\n${res.backupDir}`);
      }).catch(err => {
        setButtonLoading(btnRunDebloat, false, '⚡ 1-Click Safe Debloat');
        log('Debloat execution failed: ' + err.message, 'error');
        alert('Debloat failed: ' + err.message);
      });
    });
  }

  // ── 7. Real Backup-Driven Restore ──────────────────────────────────────────
  if (btnRunRestore) {
    btnRunRestore.addEventListener('click', () => {
      if (!isConnected) {
        log('Not connected to a TV. Connect first.', 'error');
        alert('Please connect to your TV first.');
        return;
      }

      if (!confirm('Re-enable all disabled packages from backup and restore stock TV launcher?')) {
        return;
      }

      setButtonLoading(btnRunRestore, true, '🔄 Factory Restore All');
      log(`Executing backup-driven restore on ${currentDevice}...`, 'info');

      api('/api/restore', { device: currentDevice }).then(res => {
        setButtonLoading(btnRunRestore, false, '🔄 Factory Restore All');
        log(`Restore complete: Re-enabled ${res.restored} package(s). Stock launcher restored.`, 'ok');
        alert(`Factory Restore Complete!\n\nRe-enabled ${res.restored} package(s).\nStock TV launcher restored as default Home.`);
      }).catch(err => {
        setButtonLoading(btnRunRestore, false, '🔄 Factory Restore All');
        log('Restore execution failed: ' + err.message, 'error');
        alert('Restore failed: ' + err.message);
      });
    });
  }

  // ── 8. Real APK Deployment ─────────────────────────────────────────────────
  if (btnDeployApk) {
    btnDeployApk.addEventListener('click', () => {
      if (!isConnected) {
        log('Not connected to TV. Connect first.', 'error');
        alert('Please connect to your TV first.');
        return;
      }

      setButtonLoading(btnDeployApk, true, '🚀 Push & Update TV Now');
      if (installProgressBox) installProgressBox.style.display = 'block';
      if (progressFill) progressFill.style.width = '30%';
      if (progressLabel) progressLabel.textContent = `Streaming CleanTV APK to ${currentDevice}...`;
      log(`Initiating APK sideload to ${currentDevice}...`, 'info');

      api('/api/install', { device: currentDevice }).then(res => {
        setButtonLoading(btnDeployApk, false, '🚀 Push & Update TV Now');
        if (progressFill) progressFill.style.width = '100%';
        if (progressLabel) progressLabel.textContent = 'Success! CleanTV Launcher is running live on the TV.';
        log(`APK Installation Succeeded: ${res.output || 'Installed'}`, 'ok');
        loadDeviceInfo();
      }).catch(err => {
        setButtonLoading(btnDeployApk, false, '🚀 Push & Update TV Now');
        if (progressFill) progressFill.style.width = '0%';
        if (progressLabel) progressLabel.textContent = 'Installation failed: ' + err.message;
        log('APK installation error: ' + err.message, 'error');
      });
    });
  }

  // ── 9. Virtual Remote Controls ─────────────────────────────────────────────
  document.querySelectorAll('.remote-btn, .dpad-dir, .dpad-center').forEach(btn => {
    btn.addEventListener('click', () => {
      const key = btn.getAttribute('data-key');
      if (!key) return;

      if (!isConnected) {
        log(`Keyevent ${key} dropped: TV is not connected.`, 'warn');
        return;
      }

      log(`Remote Key: KEYCODE_${key}`, 'info');
      api('/api/key', { device: currentDevice, key: key }).catch(err => {
        log(`Keyevent ${key} failed: ` + err.message, 'error');
      });
    });
  });

  const btnRemoteAi = document.getElementById('btnRemoteAi');
  if (btnRemoteAi) {
    btnRemoteAi.addEventListener('click', () => {
      if (!isConnected) return;
      log('Remote: Triggering Voice AI HUD on TV...', 'info');
      api('/api/ai/command', { device: currentDevice, query: 'voice ai hud' });
    });
  }

  const btnRemoteCc = document.getElementById('btnRemoteCc');
  if (btnRemoteCc) {
    btnRemoteCc.addEventListener('click', () => {
      if (!isConnected) return;
      log('Remote: Triggering Control Center (KEYCODE_MENU)...', 'info');
      api('/api/key', { device: currentDevice, key: '82' });
    });
  }

  // ── 10. Voice & AI Controller ──────────────────────────────────────────────
  function executeAiCommand(cmd) {
    const query = (cmd || '').trim();
    if (!query) return;

    if (!isConnected) {
      log('AI Command dropped: TV is not connected.', 'warn');
      alert('Please connect to your TV first.');
      return;
    }

    log(`AI Query: "${query}"`, 'info');
    if (aiIntentBadge) aiIntentBadge.textContent = 'EXECUTING';
    if (aiActionTitle) aiActionTitle.textContent = `"${query}"`;
    if (aiActionSubtitle) aiActionSubtitle.textContent = 'Processing intent locally...';

    api('/api/ai/command', { device: currentDevice, query: query }).then(res => {
      if (aiIntentBadge) aiIntentBadge.textContent = 'EXECUTED';
      if (aiActionSubtitle) aiActionSubtitle.textContent = res.message || 'Action executed on TV.';
      log(`AI Result: ${res.message || 'Done'}`, 'ok');
    }).catch(err => {
      if (aiIntentBadge) aiIntentBadge.textContent = 'ERROR';
      if (aiActionSubtitle) aiActionSubtitle.textContent = err.message;
      log(`AI Execution Error: ${err.message}`, 'error');
    });
  }

  if (btnAiSend && aiTextInput) {
    btnAiSend.addEventListener('click', () => {
      executeAiCommand(aiTextInput.value);
    });
    aiTextInput.addEventListener('keydown', e => {
      if (e.key === 'Enter') executeAiCommand(aiTextInput.value);
    });
  }

  document.querySelectorAll('.chip').forEach(chip => {
    chip.addEventListener('click', () => {
      const cmd = chip.getAttribute('data-cmd');
      if (cmd) {
        if (aiTextInput) aiTextInput.value = cmd;
        executeAiCommand(cmd);
      }
    });
  });

  // ── Initialization ─────────────────────────────────────────────────────────
  setConnectionState(false, 'Disconnected');
  checkAdbStatus();
  log('CleanTV Home Desktop Manager Initialized. Offline until connected.', 'info');
});
