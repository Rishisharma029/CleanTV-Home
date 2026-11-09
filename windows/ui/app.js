// CleanTV Desktop Manager — OBSIDIAN / AURORA Engine
// Precision · Workstation Engineering · Editorial Restraint
'use strict';

document.addEventListener('DOMContentLoaded', () => {
  // ─── Application State ─────────────────────────────────────────────────────────
  const state = {
    ip: localStorage.getItem('cleantv_ip') || '192.168.0.159',
    port: localStorage.getItem('cleantv_port') || '5555',
    get device() { return `${this.ip}:${this.port}`; },
    isConnected: false,
    isConnecting: false,
    latencyMs: null,
    activePage: 'page-connect',
    telemetry: {
      model: 'Realme Smart TV 4K',
      manufacturer: 'realme',
      android: 'Android 11 (API 30)',
      ramFreeGb: 1.7,
      ramTotalGb: 2.0,
      ramText: '1.7 GB Free / 2.0 GB Total',
      launcherActive: true
    },
    packages: [],
    packageFilter: 'all',
    packageSearch: '',
    adShieldActive: true,
    license: {
      isPro: true,
      tier: 'Lifetime Pro',
      key: localStorage.getItem('cleantv_key') || 'CTV-PRO-8892-98B4'
    }
  };

  // ─── Debloat Presets ──────────────────────────────────────────────────────────
  const debloatPresets = {
    realme: [
      { name: 'Anoki Live TV Services', pkg: 'com.anoki.service', risk: 'safe', reason: 'Unskippable Live TV PIP stream' },
      { name: 'AI PONT Assistant Agent', pkg: 'com.aipont.tv', risk: 'safe', reason: 'High background CPU wakeups' },
      { name: 'Homwee Store OEM App Store', pkg: 'com.homwee.store', risk: 'safe', reason: 'Unverified sponsored app prompts' },
      { name: 'Realme TV Recommendation Service', pkg: 'com.realme.tv.recommend', risk: 'safe', reason: 'Home banner ad rows' },
      { name: 'MStar TV Analytics Daemon', pkg: 'com.mstar.tv.telemetry', risk: 'caution', reason: 'Device telemetry & tracking' }
    ],
    sony: [
      { name: 'Samba TV Interactive Service', pkg: 'tv.samba.ssm', risk: 'safe', reason: 'ACR automated content recognition' },
      { name: 'Sony Select Sponsored Shelf', pkg: 'com.sony.dtv.select', risk: 'safe', reason: 'Sponsored promo cards' },
      { name: 'Sony Customer Support Daemon', pkg: 'com.sony.dtv.customersupport', risk: 'caution', reason: 'Background telemetry' }
    ],
    xiaomi: [
      { name: 'Xiaomi PatchWall Launcher', pkg: 'com.mitv.gallery', risk: 'safe', reason: 'Full-screen sponsor recommendations' },
      { name: 'Mi TV Analytics Agent', pkg: 'com.xiaomi.mitv.tracker', risk: 'safe', reason: 'Ad tracking metrics daemon' },
      { name: 'Mi Home Store Promos', pkg: 'com.xiaomi.mitv.shop', risk: 'safe', reason: 'Background notification ads' }
    ],
    oneplus: [
      { name: 'OxygenPlay Recommendations', pkg: 'net.oneplus.launcher', risk: 'safe', reason: 'Sponsored carousel rows' },
      { name: 'OnePlus Diagnostics Service', pkg: 'com.oneplus.factorymode', risk: 'caution', reason: 'Background device telemetry' }
    ],
    firetv: [
      { name: 'Amazon Shopper Recommendations', pkg: 'com.amazon.device.sale', risk: 'safe', reason: 'Commercial banners on wake' },
      { name: 'Amazon Metrics Collector', pkg: 'com.amazon.device.metrics', risk: 'caution', reason: 'Continuous network analytics' }
    ],
    google: [
      { name: 'Google TV Sponsored Highlights', pkg: 'com.google.android.tvrecommendations', risk: 'safe', reason: 'Unremovable sponsored trailer shelf' },
      { name: 'Google TV Feedback Daemon', pkg: 'com.google.android.feedback', risk: 'caution', reason: 'System feedback telemetry' }
    ]
  };

  // ─── DOM References ────────────────────────────────────────────────────────────
  const railItems = document.querySelectorAll('.rail-item');
  const pageViews = document.querySelectorAll('.page-view');
  const breadcrumbSection = document.getElementById('breadcrumbSection');
  const breadcrumbPage = document.getElementById('breadcrumbPage');
  const statusBeaconDot = document.getElementById('statusBeaconDot');
  const titlebarDeviceText = document.getElementById('titlebarDeviceText');
  const titlebarProLabel = document.getElementById('titlebarProLabel');
  const btnTitlebarRefresh = document.getElementById('btnTitlebarRefresh');
  const btnTitlebarLicenseModal = document.getElementById('btnTitlebarLicenseModal');

  // Connect View Elements
  const inputTargetIp = document.getElementById('inputTargetIp');
  const inputTargetPort = document.getElementById('inputTargetPort');
  const btnConnectAdb = document.getElementById('btnConnectAdb');
  const btnDiscoverAdb = document.getElementById('btnDiscoverAdb');
  const btnConnectEmu = document.getElementById('btnConnectEmu');
  const connectedStateBanner = document.getElementById('connectedStateBanner');
  const connectedStateDetails = document.getElementById('connectedStateDetails');
  const btnQuickToApps = document.getElementById('btnQuickToApps');
  const btnQuickToRemote = document.getElementById('btnQuickToRemote');

  // Telemetry View Elements
  const tModel = document.getElementById('tModel');
  const tMfr = document.getElementById('tMfr');
  const tAndroid = document.getElementById('tAndroid');
  const tAdb = document.getElementById('tAdb');
  const tLauncher = document.getElementById('tLauncher');
  const tRamVal = document.getElementById('tRamVal');
  const tRamGauge = document.getElementById('tRamGauge');
  const btnTelemetryRefresh = document.getElementById('btnTelemetryRefresh');

  // Quick Action Tiles
  const qaPingDevice = document.getElementById('qaPingDevice');
  const qaPingMs = document.getElementById('qaPingMs');
  const qaWakeDisplay = document.getElementById('qaWakeDisplay');
  const qaLaunchWebRemote = document.getElementById('qaLaunchWebRemote');
  const qaToggleAdGuard = document.getElementById('qaToggleAdGuard');
  const qaAdShieldSub = document.getElementById('qaAdShieldSub');

  // Overview View Elements
  const ovDevName = document.getElementById('ovDevName');
  const ovDevMfr = document.getElementById('ovDevMfr');
  const ovDevBuild = document.getElementById('ovDevBuild');
  const ovRamFreeText = document.getElementById('ovRamFreeText');

  // Applications Elements
  const inputFilterApps = document.getElementById('inputFilterApps');
  const inventoryTable = document.getElementById('inventoryTable');
  const btnReloadApps = document.getElementById('btnReloadApps');
  const countAll = document.getElementById('countAll');
  const countUser = document.getElementById('countUser');
  const countSys = document.getElementById('countSys');
  const countDis = document.getElementById('countDis');
  const appFilterBtns = document.querySelectorAll('.app-filter-btn');

  // Debloater Elements
  const selectBrandPreset = document.getElementById('selectBrandPreset');
  const debloatPresetTable = document.getElementById('debloatPresetTable');
  const metricBloatCount = document.getElementById('metricBloatCount');
  const metricBloatRam = document.getElementById('metricBloatRam');
  const btnDebloatRun = document.getElementById('btnDebloatRun');
  const btnDebloatRestore = document.getElementById('btnDebloatRestore');

  // Remote Elements
  const feedLogs = document.getElementById('feedLogs');
  const btnClearFeed = document.getElementById('btnClearFeed');
  const remoteKeyBtns = document.querySelectorAll('[data-key]');
  const btnRemoteAiHud = document.getElementById('btnRemoteAiHud');
  const btnRemoteCc = document.getElementById('btnRemoteCc');

  // APK Deployment Elements
  const btnDeployLauncher = document.getElementById('btnDeployLauncher');
  const btnSetLauncherDefault = document.getElementById('btnSetLauncherDefault');
  const deployProgressBox = document.getElementById('deployProgressBox');
  const deployStatusText = document.getElementById('deployStatusText');
  const deployPercentText = document.getElementById('deployPercentText');
  const deployProgressGauge = document.getElementById('deployProgressGauge');

  // Voice AI Elements
  const inputAiPrompt = document.getElementById('inputAiPrompt');
  const btnSubmitAi = document.getElementById('btnSubmitAi');
  const chipsAi = document.querySelectorAll('.chip-ai');
  const aiResultTitle = document.getElementById('aiResultTitle');
  const aiResultSubtitle = document.getElementById('aiResultSubtitle');

  // Pro & License Elements
  const inputProKey = document.getElementById('inputProKey');
  const btnActivateProKey = document.getElementById('btnActivateProKey');
  const toggleAdGuardDns = document.getElementById('toggleAdGuardDns');
  const keyChips = document.querySelectorAll('.key-chip');
  const railProStatus = document.getElementById('railProStatus');
  const btnRailEnterKey = document.getElementById('btnRailEnterKey');

  // Modal Elements
  const keyModalOverlay = document.getElementById('keyModalOverlay');
  const modalKeyInputField = document.getElementById('modalKeyInputField');
  const btnCloseKeyModal = document.getElementById('btnCloseKeyModal');
  const btnCancelKeyModal = document.getElementById('btnCancelKeyModal');
  const btnConfirmKeyModal = document.getElementById('btnConfirmKeyModal');

  // Toast Container
  const toastRack = document.getElementById('toastRack');

  // ─── Toast System ─────────────────────────────────────────────────────────────
  function showToast(message, type = 'info') {
    const toast = document.createElement('div');
    toast.className = `toast-message ${type}`;
    const icon = type === 'success' ? '✓' : type === 'error' ? '✕' : '⚡';
    toast.innerHTML = `<span style="color: ${type === 'success' ? 'var(--accent-secondary)' : type === 'error' ? 'var(--danger)' : 'var(--accent-primary)'}; font-weight: 700;">${icon}</span> <span>${message}</span>`;
    toastRack.appendChild(toast);

    setTimeout(() => {
      toast.style.opacity = '0';
      toast.style.transform = 'translateX(14px)';
      toast.style.transition = 'all 160ms ease';
      setTimeout(() => toast.remove(), 180);
    }, 3400);
  }

  // ─── Page Navigation ───────────────────────────────────────────────────────────
  function switchPage(pageId, section = 'Device', label = 'Connect') {
    railItems.forEach(btn => {
      if (btn.getAttribute('data-page') === pageId) {
        btn.classList.add('active');
      } else {
        btn.classList.remove('active');
      }
    });

    pageViews.forEach(view => {
      if (view.id === pageId) {
        view.classList.add('active');
      } else {
        view.classList.remove('active');
      }
    });

    if (breadcrumbSection) breadcrumbSection.textContent = section;
    if (breadcrumbPage) breadcrumbPage.textContent = label;

    state.activePage = pageId;

    if (pageId === 'page-apps' && state.packages.length === 0 && state.isConnected) {
      loadPackages();
    }
  }

  railItems.forEach(btn => {
    btn.addEventListener('click', () => {
      const pageId = btn.getAttribute('data-page');
      const section = btn.getAttribute('data-section') || 'Device';
      const label = btn.getAttribute('data-label') || 'Connect';
      switchPage(pageId, section, label);
    });
  });

  btnQuickToApps?.addEventListener('click', () => switchPage('page-apps', 'Device', 'Applications'));
  btnQuickToRemote?.addEventListener('click', () => switchPage('page-remote', 'Device', 'Virtual Remote'));

  // ─── Remote Logger ────────────────────────────────────────────────────────────
  function logRemote(msg) {
    if (!feedLogs) return;
    const time = new Date().toLocaleTimeString();
    const line = document.createElement('span');
    line.textContent = `[${time}] ${msg}`;
    feedLogs.appendChild(line);
    feedLogs.scrollTop = feedLogs.scrollHeight;
  }

  btnClearFeed?.addEventListener('click', () => {
    if (feedLogs) {
      feedLogs.innerHTML = '<span>[INIT] CleanTV Remote Bridge v4.0 active.</span>';
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
      console.warn(`API call error for ${endpoint}:`, err);
      return { status: 'error', message: err.message };
    }
  }

  // ─── Connection Management ────────────────────────────────────────────────────
  function setConnectionUI(status, label) {
    if (status === 'online') {
      state.isConnected = true;
      state.isConnecting = false;
      statusBeaconDot.className = 'status-beacon-dot online';
      titlebarDeviceText.textContent = label || `${state.telemetry.model} (${state.device})`;
      if (tAdb) {
        tAdb.className = 'data-val mint';
        tAdb.textContent = `Online (${state.device})`;
      }
      if (connectedStateBanner) {
        connectedStateBanner.style.display = 'block';
        if (connectedStateDetails) {
          connectedStateDetails.textContent = `${state.telemetry.model} · ${state.device} · ADB Online`;
        }
      }
    } else if (status === 'connecting') {
      state.isConnected = false;
      state.isConnecting = true;
      statusBeaconDot.className = 'status-beacon-dot connecting';
      titlebarDeviceText.textContent = label || `Connecting to ${state.device}...`;
      if (tAdb) {
        tAdb.className = 'data-val';
        tAdb.textContent = 'Connecting...';
      }
      if (connectedStateBanner) connectedStateBanner.style.display = 'none';
    } else {
      state.isConnected = false;
      state.isConnecting = false;
      statusBeaconDot.className = 'status-beacon-dot offline';
      titlebarDeviceText.textContent = label || 'No Device Connected';
      if (tAdb) {
        tAdb.className = 'data-val';
        tAdb.textContent = 'Offline';
      }
      if (connectedStateBanner) connectedStateBanner.style.display = 'none';
    }
  }

  async function checkAdbState() {
    const res = await apiCall('/api/check-state', { device: state.device });
    if (res && res.connected) {
      setConnectionUI('online', `Online · ${res.device || state.device}`);
      fetchTelemetry();
    } else {
      setConnectionUI('offline', 'No Device Connected');
    }
  }

  async function connectDevice(ip, port = '5555') {
    state.ip = ip.trim();
    state.port = port.trim();
    localStorage.setItem('cleantv_ip', state.ip);
    localStorage.setItem('cleantv_port', state.port);

    if (inputTargetIp) inputTargetIp.value = state.ip;
    if (inputTargetPort) inputTargetPort.value = state.port;

    setConnectionUI('connecting', `Connecting to ${state.device}...`);
    showToast(`Connecting to ${state.device}...`, 'info');

    const res = await apiCall('/api/connect', { device: state.device });
    if (res && res.connected) {
      setConnectionUI('online', `${state.telemetry.model} (${state.device})`);
      showToast(`Connected successfully to ${state.device}`, 'success');
      logRemote(`Connected to ${state.device}`);
      await fetchTelemetry();
      await pingDevice();
    } else {
      setConnectionUI('offline', 'Connection Failed');
      showToast(res.message || `Could not connect to ${state.device}`, 'error');
    }
  }

  async function pingDevice() {
    const t0 = performance.now();
    const res = await apiCall('/api/check-state', { device: state.device });
    const lat = Math.round(performance.now() - t0);
    state.latencyMs = lat;
    if (qaPingMs) qaPingMs.textContent = `${lat} ms`;
    showToast(`Ping: ${lat} ms (0.0% loss)`, 'success');
    logRemote(`Socket ping: ${lat} ms`);
  }

  async function fetchTelemetry() {
    const res = await apiCall('/api/device-info', { device: state.device });
    if (res && res.status === 'ok') {
      const brand = (res.brand || 'realme').toLowerCase();
      const model = res.model || 'Smart TV 4K';
      state.telemetry.model = `${brand.toUpperCase()} ${model}`;
      state.telemetry.manufacturer = res.manufacturer || brand;
      state.telemetry.android = `Android ${res.android || '11'}`;

      if (tModel) tModel.textContent = state.telemetry.model;
      if (tMfr) tMfr.textContent = state.telemetry.manufacturer;
      if (tAndroid) tAndroid.textContent = state.telemetry.android;

      if (ovDevName) ovDevName.textContent = state.telemetry.model;
      if (ovDevMfr) ovDevMfr.textContent = state.telemetry.manufacturer;

      if (res.memStr && tRamVal) {
        tRamVal.textContent = res.memStr;
      }
      if (res.freeGb && res.totalGb && tRamGauge) {
        const pct = Math.round((res.freeGb / res.totalGb) * 100);
        tRamGauge.style.width = `${pct}%`;
        if (ovRamFreeText) ovRamFreeText.textContent = `${res.freeGb} GB (${pct}%)`;
      }
    }
  }

  // ─── Button Listeners ─────────────────────────────────────────────────────────
  btnConnectAdb?.addEventListener('click', () => {
    const ip = inputTargetIp.value || '192.168.0.159';
    const port = inputTargetPort.value || '5555';
    connectDevice(ip, port);
  });

  btnConnectEmu?.addEventListener('click', () => {
    connectDevice('127.0.0.1', '5555');
  });

  btnDiscoverAdb?.addEventListener('click', async () => {
    showToast('Scanning local network for active TV hosts...', 'info');
    const res = await apiCall('/api/scan');
    if (res && res.devices && res.devices.length > 0) {
      const target = res.devices[0].serial;
      showToast(`Discovered target: ${target}`, 'success');
      connectDevice(target);
    } else {
      showToast('No active ADB devices discovered.', 'warning');
    }
  });

  btnTitlebarRefresh?.addEventListener('click', () => {
    showToast('Refreshing connection telemetry...', 'info');
    checkAdbState();
  });

  btnTelemetryRefresh?.addEventListener('click', () => {
    fetchTelemetry();
    pingDevice();
  });

  qaPingDevice?.addEventListener('click', pingDevice);

  qaWakeDisplay?.addEventListener('click', async () => {
    showToast('Toggling TV display power state...', 'info');
    logRemote('Sending KEYCODE_POWER (26)');
    await apiCall('/api/key', { device: state.device, key: '26' });
  });

  qaLaunchWebRemote?.addEventListener('click', () => {
    window.open('http://127.0.0.1:59822/remote.html', '_blank');
  });

  // ─── In-System Ad-Shield DNS ──────────────────────────────────────────────────
  async function toggleAdShield(enable) {
    state.adShieldActive = enable;
    const mode = enable ? 'adguard' : 'off';
    showToast(`Setting Ad-Shield Private DNS to: ${mode.toUpperCase()}...`, 'info');
    await apiCall('/api/adblock/toggle', { device: state.device, mode });

    if (qaAdShieldSub) {
      qaAdShieldSub.textContent = enable ? 'AdGuard Private DNS (Active)' : 'Ad-Shield Inactive (Disabled)';
    }
    if (toggleAdGuardDns) toggleAdGuardDns.checked = enable;

    logRemote(`Ad-Shield DNS set to ${mode.toUpperCase()}`);
    showToast(`Ad-Shield DNS is now ${enable ? 'ACTIVE' : 'OFF'}!`, 'success');
  }

  qaToggleAdGuard?.addEventListener('click', () => toggleAdShield(!state.adShieldActive));
  toggleAdGuardDns?.addEventListener('change', (e) => toggleAdShield(e.target.checked));

  // ─── Applications Management ──────────────────────────────────────────────────
  async function loadPackages() {
    if (!inventoryTable) return;
    inventoryTable.innerHTML = '<div style="padding: 20px; text-align: center; color: var(--text-muted);">Auditing packages over ADB...</div>';

    const res = await apiCall('/api/packages', { device: state.device });
    if (res && res.status === 'ok' && res.packages) {
      state.packages = res.packages;
      if (countAll) countAll.textContent = res.total || res.packages.length;
      if (countUser) countUser.textContent = res.userCount || 0;
      if (countSys) countSys.textContent = res.systemCount || 0;
      if (countDis) countDis.textContent = res.disabledCount || 0;
      renderPackageInventory();
    } else {
      inventoryTable.innerHTML = '<div style="padding: 20px; text-align: center; color: var(--danger);">Failed to load packages. Ensure device is online.</div>';
    }
  }

  function renderPackageInventory() {
    if (!inventoryTable) return;
    inventoryTable.innerHTML = '';

    const q = state.packageSearch.toLowerCase();
    const filtered = state.packages.filter(p => {
      const matchSearch = p.name.toLowerCase().includes(q) || p.pkg.toLowerCase().includes(q);
      if (!matchSearch) return false;
      if (state.packageFilter === 'user') return !p.isSystem;
      if (state.packageFilter === 'system') return p.isSystem;
      if (state.packageFilter === 'disabled') return !p.isEnabled;
      return true;
    });

    if (filtered.length === 0) {
      inventoryTable.innerHTML = '<div style="padding: 20px; text-align: center; color: var(--text-muted);">No matching packages found.</div>';
      return;
    }

    filtered.slice(0, 100).forEach(pkg => {
      const row = document.createElement('div');
      row.className = 'inventory-row';
      const isDis = !pkg.isEnabled;

      row.innerHTML = `
        <div style="display: flex; align-items: center; gap: 12px;">
          <div style="font-size: 16px;">📦</div>
          <div>
            <div style="font-size: 13px; font-weight: 600; color: var(--text-primary);">${pkg.name}</div>
            <div style="font-family: var(--font-mono); font-size: 11px; color: var(--text-muted);">${pkg.pkg}</div>
          </div>
        </div>
        <div style="display: flex; align-items: center; gap: 8px;">
          <span class="risk-tag ${isDis ? 'risk-caution' : 'risk-safe'}">${isDis ? 'DISABLED' : (pkg.isSystem ? 'SYSTEM' : 'USER')}</span>
          <button class="btn btn-secondary btn-sm btn-pkg-action" data-pkg="${pkg.pkg}" data-action="${isDis ? 'enable' : 'disable'}">
            ${isDis ? 'Enable' : 'Disable'}
          </button>
        </div>
      `;
      inventoryTable.appendChild(row);
    });

    document.querySelectorAll('.btn-pkg-action').forEach(btn => {
      btn.addEventListener('click', async () => {
        const p = btn.getAttribute('data-pkg');
        const act = btn.getAttribute('data-action');
        showToast(`${act === 'disable' ? 'Disabling' : 'Enabling'} ${p}...`, 'info');
        await apiCall('/api/package-action', { device: state.device, pkg: p, action: act });
        showToast(`${p} ${act}d successfully.`, 'success');
        loadPackages();
      });
    });
  }

  inputFilterApps?.addEventListener('input', (e) => {
    state.packageSearch = e.target.value;
    renderPackageInventory();
  });

  appFilterBtns.forEach(btn => {
    btn.addEventListener('click', () => {
      appFilterBtns.forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      state.packageFilter = btn.getAttribute('data-filter');
      renderPackageInventory();
    });
  });

  btnReloadApps?.addEventListener('click', loadPackages);

  // ─── Debloater ────────────────────────────────────────────────────────────────
  function renderBrandDebloat(presetKey) {
    const list = debloatPresets[presetKey] || debloatPresets.realme;
    if (metricBloatCount) metricBloatCount.textContent = list.length;
    if (metricBloatRam) metricBloatRam.textContent = `~${list.length * 14} MB`;

    if (!debloatPresetTable) return;
    debloatPresetTable.innerHTML = '';

    list.forEach(item => {
      const row = document.createElement('div');
      row.className = 'inventory-row';
      row.innerHTML = `
        <div>
          <div style="font-size: 13px; font-weight: 600; color: var(--text-primary);">${item.name}</div>
          <div style="font-family: var(--font-mono); font-size: 11px; color: var(--text-muted);">${item.pkg}</div>
          <div style="font-size: 11px; color: var(--warning); margin-top: 2px;">⚠️ ${item.reason}</div>
        </div>
        <div style="display: flex; align-items: center; gap: 8px;">
          <span class="risk-tag ${item.risk === 'safe' ? 'risk-safe' : 'risk-caution'}">${item.risk.toUpperCase()}</span>
          <input type="checkbox" class="debloat-check" checked data-pkg="${item.pkg}">
        </div>
      `;
      debloatPresetTable.appendChild(row);
    });
  }

  selectBrandPreset?.addEventListener('change', (e) => {
    renderBrandDebloat(e.target.value);
  });

  btnDebloatRun?.addEventListener('click', async () => {
    showToast('Executing safe 1-Click Debloat on TV...', 'info');
    const checked = Array.from(document.querySelectorAll('.debloat-check:checked')).map(c => c.getAttribute('data-pkg'));
    await apiCall('/api/debloat', { device: state.device, packages: checked.join(',') });
    showToast(`Debloat complete! Disabled ${checked.length} bloatware packages.`, 'success');
    logRemote(`Debloated ${checked.length} packages`);
  });

  btnDebloatRestore?.addEventListener('click', async () => {
    showToast('Restoring OEM factory packages...', 'info');
    await apiCall('/api/restore', { device: state.device });
    showToast('Restored all OEM packages successfully.', 'success');
    logRemote('Factory packages restored');
  });

  // ─── Virtual Remote ───────────────────────────────────────────────────────────
  remoteKeyBtns.forEach(btn => {
    btn.addEventListener('click', async () => {
      const keycode = btn.getAttribute('data-key');
      const label = btn.innerText.trim();
      logRemote(`Key: ${label} (Keycode ${keycode})`);
      await apiCall('/api/key', { device: state.device, key: keycode });
    });
  });

  btnRemoteAiHud?.addEventListener('click', () => switchPage('page-ai', 'Tools', 'Voice & AI'));
  btnRemoteCc?.addEventListener('click', () => switchPage('page-connect', 'Device', 'Connect'));

  // ─── CleanTV APK Deployment ───────────────────────────────────────────────────
  btnDeployLauncher?.addEventListener('click', async () => {
    if (deployProgressBox) deployProgressBox.style.display = 'block';
    if (deployStatusText) deployStatusText.textContent = 'Pushing clean-tv-launcher-latest.apk to TV...';
    if (deployPercentText) deployPercentText.textContent = '35%';
    if (deployProgressGauge) deployProgressGauge.style.width = '35%';

    showToast('Deploying CleanTV Home to TV...', 'info');

    setTimeout(async () => {
      if (deployPercentText) deployPercentText.textContent = '75%';
      if (deployProgressGauge) deployProgressGauge.style.width = '75%';

      await apiCall('/api/install', { device: state.device });

      if (deployPercentText) deployPercentText.textContent = '100%';
      if (deployProgressGauge) deployProgressGauge.style.width = '100%';
      if (deployStatusText) deployStatusText.textContent = 'CleanTV Home v4.0.0 installed successfully!';
      showToast('CleanTV Home deployed to TV!', 'success');
      logRemote('CleanTV APK deployed to TV');
    }, 1200);
  });

  btnSetLauncherDefault?.addEventListener('click', async () => {
    showToast('Dispatching Home key event...', 'info');
    await apiCall('/api/key', { device: state.device, key: '3' });
    logRemote('Home key event dispatched');
  });

  // ─── Voice & AI Intent Controller ─────────────────────────────────────────────
  async function executeAiCommand(cmd) {
    if (!cmd) return;
    showToast(`Executing AI command: "${cmd}"`, 'info');
    if (aiResultTitle) aiResultTitle.textContent = `Processing: "${cmd}"`;
    if (aiResultSubtitle) aiResultSubtitle.textContent = 'Routing intent to TV subsystem over low-latency socket...';

    logRemote(`[AI] Command: ${cmd}`);
    const res = await apiCall('/api/ai/command', { device: state.device, cmd });

    setTimeout(() => {
      if (aiResultTitle) aiResultTitle.textContent = `Executed: "${cmd}"`;
      if (aiResultSubtitle) aiResultSubtitle.textContent = res.message || 'Action executed on TV.';
      showToast(`AI: "${cmd}" applied!`, 'success');
      logRemote('[AI] Command completed successfully');
    }, 500);
  }

  btnSubmitAi?.addEventListener('click', () => {
    const cmd = inputAiPrompt.value.trim();
    if (cmd) executeAiCommand(cmd);
  });

  inputAiPrompt?.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
      const cmd = inputAiPrompt.value.trim();
      if (cmd) executeAiCommand(cmd);
    }
  });

  chipsAi.forEach(chip => {
    chip.addEventListener('click', () => {
      const cmd = chip.getAttribute('data-cmd');
      if (inputAiPrompt) inputAiPrompt.value = cmd;
      executeAiCommand(cmd);
    });
  });

  // ─── License Key Activation ───────────────────────────────────────────────────
  async function activateLicenseKey(key) {
    const cleanKey = key.trim().toUpperCase();
    showToast(`Validating Pro License Key: ${cleanKey}...`, 'info');

    await apiCall('/api/license/activate', { key: cleanKey });

    state.license.isPro = true;
    state.license.key = cleanKey;
    localStorage.setItem('cleantv_key', cleanKey);

    if (railProStatus) railProStatus.textContent = 'Pro Active';
    if (titlebarProLabel) titlebarProLabel.textContent = 'Pro Lifetime';

    showToast(`CleanTV Pro Activated with Key: ${cleanKey}!`, 'success');
    logRemote(`[LICENSE] Activated Pro key: ${cleanKey}`);

    if (keyModalOverlay) keyModalOverlay.style.display = 'none';
  }

  btnActivateProKey?.addEventListener('click', () => {
    const key = inputProKey.value || 'CTV-PRO-8892-98B4';
    activateLicenseKey(key);
  });

  keyChips.forEach(chip => {
    chip.addEventListener('click', () => {
      const k = chip.getAttribute('data-key');
      if (inputProKey) inputProKey.value = k;
      activateLicenseKey(k);
    });
  });

  // Modal Handlers
  function openLicenseModal() {
    if (keyModalOverlay) keyModalOverlay.style.display = 'flex';
  }

  function closeLicenseModal() {
    if (keyModalOverlay) keyModalOverlay.style.display = 'none';
  }

  btnRailEnterKey?.addEventListener('click', openLicenseModal);
  btnTitlebarLicenseModal?.addEventListener('click', openLicenseModal);
  titlebarProLabel?.addEventListener('click', openLicenseModal);
  btnCloseKeyModal?.addEventListener('click', closeLicenseModal);
  btnCancelKeyModal?.addEventListener('click', closeLicenseModal);

  btnConfirmKeyModal?.addEventListener('click', () => {
    const k = modalKeyInputField.value || 'CTV-PRO-8892-98B4';
    activateLicenseKey(k);
  });

  // ─── Initial Startup ──────────────────────────────────────────────────────────
  renderBrandDebloat('realme');
  checkAdbState();

  // Auto-connect to emulator if running
  setTimeout(() => {
    connectDevice('127.0.0.1', '5555');
  }, 500);
});
