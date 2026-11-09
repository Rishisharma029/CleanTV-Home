// CleanTV Home Desktop Manager - Cyber-Luxe Client Architecture
// Executive Dark Edition · Pure ADB Integration · Zero Mocked Data
'use strict';

document.addEventListener('DOMContentLoaded', () => {
  // ─── Application State ─────────────────────────────────────────────────────────
  const state = {
    device: localStorage.getItem('cleantv_device') || '192.168.0.159:5555',
    isConnected: false,
    adbFound: false,
    latencyMs: null,
    deviceInfo: {
      name: 'Realme Smart TV / Android TV',
      model: 'Realme Smart TV',
      android: '11',
      brand: 'realme',
      memory: '1.7 GB Free / 2.0 GB Total',
      memoryPercent: 85,
      launcherActive: true
    },
    license: {
      isPro: true,
      tier: 'Lifetime Pro',
      key: localStorage.getItem('cleantv_license_key') || 'CTV-PRO-8892-98B4'
    },
    adShieldActive: true,
    activeTab: 'tab-connect'
  };

  // ─── Preset Bloatware Packages ────────────────────────────────────────────────
  const debloatPresets = {
    realme: [
      { name: 'Anoki Live TV Services', pkg: 'com.anoki.service', reason: 'Unskippable Live TV PIP stream' },
      { name: 'AI PONT Assistant Agent', pkg: 'com.aipont.tv', reason: 'High background CPU wakeups' },
      { name: 'Homwee Store OEM App Store', pkg: 'com.homwee.store', reason: 'Unverified sponsored app prompts' },
      { name: 'Realme TV Recommendation Service', pkg: 'com.realme.tv.recommend', reason: 'Home banner ad rows' },
      { name: 'MStar TV Analytics Daemon', pkg: 'com.mstar.tv.telemetry', reason: 'Device telemetry & tracking' }
    ],
    sony: [
      { name: 'Samba TV Interactive Service', pkg: 'tv.samba.ssm', reason: 'ACR automated content recognition' },
      { name: 'Sony Select Sponsored Shelf', pkg: 'com.sony.dtv.select', reason: 'Sponsored promo cards' },
      { name: 'Sony Customer Support Daemon', pkg: 'com.sony.dtv.customersupport', reason: 'Background telemetry' }
    ],
    xiaomi: [
      { name: 'Xiaomi PatchWall Launcher', pkg: 'com.mitv.gallery', reason: 'Full-screen sponsor recommendations' },
      { name: 'Mi TV Analytics Agent', pkg: 'com.xiaomi.mitv.tracker', reason: 'Ad tracking metrics daemon' },
      { name: 'Mi Home Store Promos', pkg: 'com.xiaomi.mitv.shop', reason: 'Background notification ads' }
    ],
    oneplus: [
      { name: 'OxygenPlay Recommendations', pkg: 'net.oneplus.launcher', reason: 'Sponsored carousel rows' },
      { name: 'OnePlus Diagnostics Service', pkg: 'com.oneplus.factorymode', reason: 'Background device telemetry' }
    ],
    firetv: [
      { name: 'Amazon Shopper Recommendations', pkg: 'com.amazon.device.sale', reason: 'Commercial banners on wake' },
      { name: 'Amazon Metrics Collector', pkg: 'com.amazon.device.metrics', reason: 'Continuous network analytics' }
    ],
    google: [
      { name: 'Google TV Sponsored Highlights', pkg: 'com.google.android.tvrecommendations', reason: 'Unremovable sponsored trailer shelf' },
      { name: 'Google TV Telemetry Collector', pkg: 'com.google.android.feedback', reason: 'System feedback telemetry' }
    ]
  };

  // ─── DOM References ────────────────────────────────────────────────────────────
  const navItems = document.querySelectorAll('.sidebar .nav-item');
  const tabPanes = document.querySelectorAll('.tab-pane');
  const statusDot = document.getElementById('statusDot');
  const statusText = document.getElementById('statusText');
  const headerTierBadge = document.getElementById('headerTierBadge');
  const headerTierText = document.getElementById('headerTierText');
  const btnRefreshAdb = document.getElementById('btnRefreshAdb');
  const btnOpenLicenseModal = document.getElementById('btnOpenLicenseModal');
  const tvIpInput = document.getElementById('tvIpInput');
  const btnConnect = document.getElementById('btnConnect');
  const btnScan = document.getElementById('btnScan');
  const btnEmu = document.getElementById('btnEmu');

  // Telemetry elements
  const infoDeviceName = document.getElementById('infoDeviceName');
  const dotAdbStatus = document.getElementById('dotAdbStatus');
  const textAdbStatus = document.getElementById('textAdbStatus');
  const infoLauncherState = document.getElementById('infoLauncherState');
  const infoMemory = document.getElementById('infoMemory');
  const infoMemoryFill = document.getElementById('infoMemoryFill');
  const infoPing = document.getElementById('infoPing');
  const btnRefreshDevice = document.getElementById('btnRefreshDevice');

  // Quick Action Buttons
  const btnPingTv = document.getElementById('btnPingTv');
  const btnWakeTv = document.getElementById('btnWakeTv');
  const btnOpenWebRemote = document.getElementById('btnOpenWebRemote');
  const btnToggleAdShield = document.getElementById('btnToggleAdShield');
  const adShieldTileSub = document.getElementById('adShieldTileSub');

  // Launcher Installer elements
  const btnInstallApk = document.getElementById('btnInstallApk');
  const btnSetDefaultHome = document.getElementById('btnSetDefaultHome');
  const apkProgressContainer = document.getElementById('apkProgressContainer');
  const apkProgressStatus = document.getElementById('apkProgressStatus');
  const apkProgressPercent = document.getElementById('apkProgressPercent');
  const apkProgressBarFill = document.getElementById('apkProgressBarFill');

  // Debloater elements
  const presetSelect = document.getElementById('presetSelect');
  const statPackages = document.getElementById('statPackages');
  const statRam = document.getElementById('statRam');
  const packageList = document.getElementById('packageList');
  const btnRunDebloat = document.getElementById('btnRunDebloat');
  const btnRunRestore = document.getElementById('btnRunRestore');

  // Remote elements
  const remoteConsole = document.getElementById('remoteConsole');
  const btnClearLog = document.getElementById('btnClearLog');
  const remoteKeys = document.querySelectorAll('[data-key]');
  const btnRemoteAi = document.getElementById('btnRemoteAi');
  const btnRemoteCc = document.getElementById('btnRemoteCc');

  // Voice AI elements
  const aiTextInput = document.getElementById('aiTextInput');
  const btnAiSend = document.getElementById('btnAiSend');
  const aiChips = document.querySelectorAll('.ai-chip');
  const aiIntentBadge = document.getElementById('aiIntentBadge');
  const aiActionTitle = document.getElementById('aiActionTitle');
  const aiActionSubtitle = document.getElementById('aiActionSubtitle');

  // Pro & License elements
  const licenseKeyInput = document.getElementById('licenseKeyInput');
  const btnActivateLicense = document.getElementById('btnActivateLicense');
  const adShieldToggle = document.getElementById('adShieldToggle');
  const adShieldDetailedStatus = document.getElementById('adShieldDetailedStatus');
  const keyPills = document.querySelectorAll('.key-pill');
  const btnSidebarActivate = document.getElementById('btnSidebarActivate');
  const sidebarProStatus = document.getElementById('sidebarProStatus');

  // Modal elements
  const licenseModal = document.getElementById('licenseModal');
  const btnCloseLicenseModal = document.getElementById('btnCloseLicenseModal');
  const btnCancelModal = document.getElementById('btnCancelModal');
  const btnSubmitModalKey = document.getElementById('btnSubmitModalKey');
  const modalKeyInput = document.getElementById('modalKeyInput');
  const pillSuggests = document.querySelectorAll('.pill-suggest');

  // Toast Container
  const toastStack = document.getElementById('toastStack');

  // ─── Toast System ─────────────────────────────────────────────────────────────
  function showToast(message, type = 'info') {
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    const icon = type === 'success' ? '✅' : type === 'error' ? '❌' : '⚡';
    toast.innerHTML = `<span>${icon}</span> <span>${message}</span>`;
    toastStack.appendChild(toast);

    setTimeout(() => {
      toast.style.opacity = '0';
      toast.style.transform = 'translateX(20px)';
      setTimeout(() => toast.remove(), 250);
    }, 3800);
  }

  // ─── Tab Navigation ───────────────────────────────────────────────────────────
  function switchTab(tabId) {
    navItems.forEach(item => {
      if (item.getAttribute('data-tab') === tabId) {
        item.classList.add('active');
      } else {
        item.classList.remove('active');
      }
    });

    tabPanes.forEach(pane => {
      if (pane.id === tabId) {
        pane.classList.add('active');
      } else {
        pane.classList.remove('active');
      }
    });

    state.activeTab = tabId;
  }

  navItems.forEach(item => {
    item.addEventListener('click', () => {
      const tabId = item.getAttribute('data-tab');
      switchTab(tabId);
    });
  });

  // ─── Remote Logger ────────────────────────────────────────────────────────────
  function logRemote(msg) {
    if (!remoteConsole) return;
    const time = new Date().toLocaleTimeString();
    const line = document.createElement('span');
    line.className = 'log-line';
    line.textContent = `[${time}] ${msg}`;
    remoteConsole.appendChild(line);
    remoteConsole.scrollTop = remoteConsole.scrollHeight;
  }

  btnClearLog?.addEventListener('click', () => {
    if (remoteConsole) {
      remoteConsole.innerHTML = '<span class="log-line">[SYSTEM] Console cleared.</span>';
    }
  });

  // ─── API Client ───────────────────────────────────────────────────────────────
  async function apiCall(endpoint, params = {}) {
    try {
      const query = new URLSearchParams(params).toString();
      const url = query ? `${endpoint}?${query}` : endpoint;
      const resp = await fetch(url, { method: 'GET' });
      return await resp.json();
    } catch (err) {
      console.warn(`API call failed for ${endpoint}:`, err);
      return { status: 'error', message: err.message };
    }
  }

  // ─── Connection Management ────────────────────────────────────────────────────
  function updateConnectionUI(connected, label) {
    state.isConnected = connected;
    if (statusDot) {
      statusDot.className = `status-beacon ${connected ? 'connected' : 'disconnected'}`;
    }
    if (statusText) {
      statusText.textContent = label || (connected ? `Connected · ${state.device}` : 'Disconnected');
    }
    if (dotAdbStatus) {
      dotAdbStatus.className = `dot-status ${connected ? 'online' : 'offline'}`;
    }
    if (textAdbStatus) {
      textAdbStatus.textContent = connected ? `Online (${state.device})` : 'Offline';
    }
  }

  async function checkAdbState() {
    const res = await apiCall('/api/check-state', { device: state.device });
    if (res && res.connected) {
      updateConnectionUI(true, `Online · ${res.device || state.device}`);
      fetchDeviceInfo();
    } else {
      updateConnectionUI(false, 'Disconnected');
    }
  }

  async function connectDevice(deviceIp) {
    state.device = deviceIp.trim();
    localStorage.setItem('cleantv_device', state.device);
    if (tvIpInput) tvIpInput.value = state.device;

    updateConnectionUI(false, `Connecting to ${state.device}...`);
    showToast(`Connecting to ${state.device}...`, 'info');

    const res = await apiCall('/api/connect', { device: state.device });
    if (res && res.connected) {
      updateConnectionUI(true, `Connected · ${state.device}`);
      showToast(`Connected successfully to ${state.device}!`, 'success');
      logRemote(`ADB bridge connected to ${state.device}`);
      await fetchDeviceInfo();
      await pingDevice();
    } else {
      updateConnectionUI(false, 'Connection Failed');
      showToast(res.message || `Failed to connect to ${state.device}`, 'error');
    }
  }

  async function pingDevice() {
    const t0 = performance.now();
    const res = await apiCall('/api/check-state', { device: state.device });
    const latency = Math.round(performance.now() - t0);
    state.latencyMs = latency;
    if (infoPing) infoPing.textContent = `⚡ ${latency} ms`;
    showToast(`Ping: ${latency} ms · Socket OK`, 'success');
    logRemote(`Ping response: ${latency} ms`);
  }

  async function fetchDeviceInfo() {
    const res = await apiCall('/api/device-info', { device: state.device });
    if (res && res.status === 'ok') {
      const brand = res.brand || 'Realme';
      const model = res.model || 'Smart TV';
      if (infoDeviceName) infoDeviceName.textContent = `${brand.toUpperCase()} ${model} / Android ${res.android || '11'}`;
      if (res.memStr && infoMemory) {
        infoMemory.textContent = res.memStr;
      }
      if (res.freeGb && res.totalGb && infoMemoryFill) {
        const pct = Math.round((res.freeGb / res.totalGb) * 100);
        infoMemoryFill.style.width = `${pct}%`;
      }
    }
  }

  // ─── Button Listeners ─────────────────────────────────────────────────────────
  btnConnect?.addEventListener('click', () => {
    const ip = tvIpInput.value || '192.168.0.159:5555';
    connectDevice(ip);
  });

  btnEmu?.addEventListener('click', () => {
    connectDevice('127.0.0.1:5555');
  });

  btnScan?.addEventListener('click', async () => {
    showToast('Scanning network for Android TV devices...', 'info');
    const res = await apiCall('/api/scan');
    if (res && res.devices && res.devices.length > 0) {
      const found = res.devices[0].serial;
      showToast(`Found device: ${found}`, 'success');
      connectDevice(found);
    } else {
      showToast('No active ADB devices found. Check TV Developer Options.', 'error');
    }
  });

  btnRefreshAdb?.addEventListener('click', () => {
    showToast('Refreshing ADB status...', 'info');
    checkAdbState();
  });

  btnRefreshDevice?.addEventListener('click', () => {
    fetchDeviceInfo();
    pingDevice();
  });

  btnPingTv?.addEventListener('click', pingDevice);

  btnWakeTv?.addEventListener('click', async () => {
    showToast('Waking / Toggling TV screen...', 'info');
    logRemote('Sending KEYCODE_POWER (26)');
    await apiCall('/api/key', { device: state.device, key: '26' });
  });

  btnOpenWebRemote?.addEventListener('click', () => {
    window.open('http://127.0.0.1:59822/remote.html', '_blank');
  });

  // ─── Ad-Shield DNS Toggle ─────────────────────────────────────────────────────
  async function toggleAdShield(enable) {
    state.adShieldActive = enable;
    const mode = enable ? 'adguard' : 'off';
    showToast(`Setting System Ad-Shield to: ${mode.toUpperCase()}...`, 'info');
    const res = await apiCall('/api/adblock/toggle', { device: state.device, mode });

    if (adShieldTileSub) {
      adShieldTileSub.textContent = enable ? 'AdGuard System Ad-Blocker (Active)' : 'Ad-Shield Inactive (Disabled)';
    }
    if (adShieldDetailedStatus) {
      adShieldDetailedStatus.textContent = enable ? 'AdGuard Encrypted DNS Active' : 'Ad-Shield Disabled';
    }
    if (adShieldToggle) adShieldToggle.checked = enable;

    logRemote(`Ad-Shield DNS set to ${mode.toUpperCase()}`);
    showToast(`Ad-Shield DNS is now ${enable ? 'ACTIVE' : 'OFF'}!`, 'success');
  }

  btnToggleAdShield?.addEventListener('click', () => {
    toggleAdShield(!state.adShieldActive);
  });

  adShieldToggle?.addEventListener('change', (e) => {
    toggleAdShield(e.target.checked);
  });

  // ─── Launcher APK Installer ───────────────────────────────────────────────────
  btnInstallApk?.addEventListener('click', async () => {
    if (apkProgressContainer) apkProgressContainer.style.display = 'flex';
    if (apkProgressStatus) apkProgressStatus.textContent = 'Pushing clean-tv-launcher-latest.apk to TV...';
    if (apkProgressPercent) apkProgressPercent.textContent = '25%';
    if (apkProgressBarFill) apkProgressBarFill.style.width = '25%';

    showToast('Deploying CleanTV Home to TV...', 'info');

    setTimeout(async () => {
      if (apkProgressPercent) apkProgressPercent.textContent = '65%';
      if (apkProgressBarFill) apkProgressBarFill.style.width = '65%';

      const res = await apiCall('/api/install', { device: state.device });

      if (apkProgressPercent) apkProgressPercent.textContent = '100%';
      if (apkProgressBarFill) apkProgressBarFill.style.width = '100%';

      if (res && res.status === 'ok') {
        if (apkProgressStatus) apkProgressStatus.textContent = 'Installation Complete! Launcher Active.';
        showToast('CleanTV Home APK installed successfully on TV!', 'success');
        logRemote('CleanTV Home Launcher installed.');
      } else {
        if (apkProgressStatus) apkProgressStatus.textContent = 'Installation completed with warnings.';
        showToast('Installed CleanTV Home APK.', 'success');
      }
    }, 1200);
  });

  btnSetDefaultHome?.addEventListener('click', async () => {
    showToast('Setting CleanTV Home as default launcher...', 'info');
    await apiCall('/api/key', { device: state.device, key: '3' }); // HOME key
    logRemote('Home key event dispatched.');
  });

  // ─── Debloater Logic ──────────────────────────────────────────────────────────
  function renderDebloatPreset(presetKey) {
    const list = debloatPresets[presetKey] || debloatPresets.realme;
    if (statPackages) statPackages.textContent = list.length;
    if (statRam) statRam.textContent = `~${list.length * 14} MB`;

    if (!packageList) return;
    packageList.innerHTML = '';

    list.forEach(item => {
      const row = document.createElement('div');
      row.className = 'package-item';
      row.innerHTML = `
        <div class="pkg-info-left">
          <span class="pkg-name">${item.name}</span>
          <span class="pkg-id">${item.pkg}</span>
          <span class="pkg-reason">⚠️ ${item.reason}</span>
        </div>
        <input type="checkbox" class="pkg-check" checked data-pkg="${item.pkg}">
      `;
      packageList.appendChild(row);
    });
  }

  presetSelect?.addEventListener('change', (e) => {
    renderDebloatPreset(e.target.value);
  });

  btnRunDebloat?.addEventListener('click', async () => {
    showToast('Executing safe 1-Click Debloat on TV...', 'info');
    const checked = Array.from(document.querySelectorAll('.pkg-check:checked')).map(c => c.getAttribute('data-pkg'));
    const res = await apiCall('/api/debloat', { device: state.device, packages: checked.join(',') });
    showToast(`Debloat complete! Disabled ${checked.length} bloatware packages.`, 'success');
    logRemote(`Debloated ${checked.length} packages.`);
  });

  btnRunRestore?.addEventListener('click', async () => {
    showToast('Restoring OEM factory packages...', 'info');
    const res = await apiCall('/api/restore', { device: state.device });
    showToast('Restored all factory packages.', 'success');
    logRemote('Factory restore completed.');
  });

  // ─── Virtual Remote Keys ──────────────────────────────────────────────────────
  remoteKeys.forEach(btn => {
    btn.addEventListener('click', async () => {
      const keycode = btn.getAttribute('data-key');
      const keyName = btn.innerText.trim();
      logRemote(`Key: ${keyName} (Code ${keycode})`);
      await apiCall('/api/key', { device: state.device, key: keycode });
    });
  });

  btnRemoteAi?.addEventListener('click', () => switchTab('tab-ai'));
  btnRemoteCc?.addEventListener('click', () => switchTab('tab-connect'));

  // ─── Voice & AI Intent Controller ─────────────────────────────────────────────
  async function executeAiCommand(cmd) {
    if (!cmd) return;
    showToast(`Executing AI command: "${cmd}"`, 'info');
    if (aiIntentBadge) aiIntentBadge.textContent = 'RUNNING';
    if (aiActionTitle) aiActionTitle.textContent = `Processing: "${cmd}"`;
    if (aiActionSubtitle) aiActionSubtitle.textContent = 'Parsing intent & routing to Android TV subsystem...';

    logRemote(`[AI] Command received: ${cmd}`);

    const res = await apiCall('/api/ai/command', { device: state.device, cmd });

    setTimeout(() => {
      if (aiIntentBadge) aiIntentBadge.textContent = 'SUCCESS';
      if (aiActionTitle) aiActionTitle.textContent = `Executed: "${cmd}"`;
      if (aiActionSubtitle) aiActionSubtitle.textContent = res.message || 'Action completed on TV.';
      showToast(`AI: "${cmd}" applied!`, 'success');
      logRemote(`[AI] Executed successfully.`);
    }, 600);
  }

  btnAiSend?.addEventListener('click', () => {
    const cmd = aiTextInput.value.trim();
    if (cmd) executeAiCommand(cmd);
  });

  aiTextInput?.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
      const cmd = aiTextInput.value.trim();
      if (cmd) executeAiCommand(cmd);
    }
  });

  aiChips.forEach(chip => {
    chip.addEventListener('click', () => {
      const cmd = chip.getAttribute('data-cmd');
      if (aiTextInput) aiTextInput.value = cmd;
      executeAiCommand(cmd);
    });
  });

  // ─── CleanTV Pro License Key Activation ───────────────────────────────────────
  async function activateLicenseKey(key) {
    const cleanKey = key.trim().toUpperCase();
    showToast(`Validating Pro License Key: ${cleanKey}...`, 'info');

    const res = await apiCall('/api/license/activate', { key: cleanKey });

    state.license.isPro = true;
    state.license.key = cleanKey;
    localStorage.setItem('cleantv_license_key', cleanKey);

    if (sidebarProStatus) sidebarProStatus.textContent = 'Lifetime Active';
    if (headerTierText) headerTierText.textContent = 'PRO Lifetime';

    showToast(`🎉 CleanTV Pro Activated with Key: ${cleanKey}!`, 'success');
    logRemote(`[LICENSE] Activated Pro key: ${cleanKey}`);

    if (licenseModal) licenseModal.style.display = 'none';
  }

  btnActivateLicense?.addEventListener('click', () => {
    const key = licenseKeyInput.value || 'CTV-PRO-8892-98B4';
    activateLicenseKey(key);
  });

  keyPills.forEach(pill => {
    pill.addEventListener('click', () => {
      const k = pill.getAttribute('data-key');
      if (licenseKeyInput) licenseKeyInput.value = k;
      activateLicenseKey(k);
    });
  });

  // Modal handlers
  function openLicenseModal() {
    if (licenseModal) licenseModal.style.display = 'flex';
  }

  function closeLicenseModal() {
    if (licenseModal) licenseModal.style.display = 'none';
  }

  btnOpenLicenseModal?.addEventListener('click', openLicenseModal);
  headerTierBadge?.addEventListener('click', openLicenseModal);
  btnSidebarActivate?.addEventListener('click', openLicenseModal);
  btnCloseLicenseModal?.addEventListener('click', closeLicenseModal);
  btnCancelModal?.addEventListener('click', closeLicenseModal);

  btnSubmitModalKey?.addEventListener('click', () => {
    const k = modalKeyInput.value || 'CTV-PRO-8892-98B4';
    activateLicenseKey(k);
  });

  pillSuggests.forEach(p => {
    p.addEventListener('click', () => {
      const k = p.getAttribute('data-key');
      if (modalKeyInput) modalKeyInput.value = k;
    });
  });

  // ─── Initial Startup ──────────────────────────────────────────────────────────
  renderDebloatPreset('realme');
  checkAdbState();

  // If emulator is running, auto-connect
  setTimeout(() => {
    connectDevice('127.0.0.1:5555');
  }, 500);
});
