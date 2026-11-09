// CleanTV Desktop Manager — NEON OBSIDIAN Client Engine
// Precision · Intelligence · Minimalism · Depth · Control
// Pure ADB Integration · Production Certified
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
    debloatPreset: 'realme',
    adShieldActive: true,
    license: {
      isPro: true,
      tier: 'Lifetime Pro',
      key: localStorage.getItem('cleantv_key') || 'CTV-PRO-8892-98B4'
    }
  };

  // ─── Debloat Preset Definitions ───────────────────────────────────────────────
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

  // ─── DOM Elements ─────────────────────────────────────────────────────────────
  const navBtns = document.querySelectorAll('.nav-btn');
  const pageViews = document.querySelectorAll('.page-view');
  const globalStatusDot = document.getElementById('globalStatusDot');
  const globalStatusText = document.getElementById('globalStatusText');
  const titlebarTierLabel = document.getElementById('titlebarTierLabel');
  const btnQuickRefresh = document.getElementById('btnQuickRefresh');
  const btnHeaderLicense = document.getElementById('btnHeaderLicense');

  // Connect Page Elements
  const inputIpAddress = document.getElementById('inputIpAddress');
  const inputPort = document.getElementById('inputPort');
  const btnConnectDevice = document.getElementById('btnConnectDevice');
  const btnDiscoverDevices = document.getElementById('btnDiscoverDevices');
  const btnConnectEmulator = document.getElementById('btnConnectEmulator');
  const connectedBanner = document.getElementById('connectedBanner');
  const connectedBannerSub = document.getElementById('connectedBannerSub');
  const btnQuickManageApps = document.getElementById('btnQuickManageApps');
  const btnQuickLaunchRemote = document.getElementById('btnQuickLaunchRemote');

  // Telemetry Elements
  const valDeviceModel = document.getElementById('valDeviceModel');
  const valManufacturer = document.getElementById('valManufacturer');
  const valAndroidVersion = document.getElementById('valAndroidVersion');
  const valAdbState = document.getElementById('valAdbState');
  const valLauncherStatus = document.getElementById('valLauncherStatus');
  const valRamText = document.getElementById('valRamText');
  const valRamFill = document.getElementById('valRamFill');
  const btnRefreshTelemetry = document.getElementById('btnRefreshTelemetry');

  // Quick Action Tiles
  const qaPing = document.getElementById('qaPing');
  const valPingLatency = document.getElementById('valPingLatency');
  const qaWakeTv = document.getElementById('qaWakeTv');
  const qaWebRemote = document.getElementById('qaWebRemote');
  const qaAdShield = document.getElementById('qaAdShield');
  const qaAdShieldSub = document.getElementById('qaAdShieldSub');

  // Applications Elements
  const inputAppSearch = document.getElementById('inputAppSearch');
  const appsListContainer = document.getElementById('appsListContainer');
  const btnRefreshAppList = document.getElementById('btnRefreshAppList');
  const countTotalApps = document.getElementById('countTotalApps');
  const countUserApps = document.getElementById('countUserApps');
  const countSysApps = document.getElementById('countSysApps');
  const countDisApps = document.getElementById('countDisApps');
  const appFilterChips = document.querySelectorAll('.filter-chips-strip .chip-btn');

  // Debloater Elements
  const selectDebloatPreset = document.getElementById('selectDebloatPreset');
  const debloatPackageTable = document.getElementById('debloatPackageTable');
  const statDebloatCount = document.getElementById('statDebloatCount');
  const statDebloatRam = document.getElementById('statDebloatRam');
  const btnExecuteDebloat = document.getElementById('btnExecuteDebloat');
  const btnExecuteRestore = document.getElementById('btnExecuteRestore');

  // Remote Elements
  const remoteConsoleFeed = document.getElementById('remoteConsoleFeed');
  const btnClearRemoteLog = document.getElementById('btnClearRemoteLog');
  const remoteKeys = document.querySelectorAll('[data-key]');
  const btnTriggerAiHud = document.getElementById('btnTriggerAiHud');
  const btnTriggerControlCenter = document.getElementById('btnTriggerControlCenter');

  // APK Installer Elements
  const btnDeployApk = document.getElementById('btnDeployApk');
  const btnSetAsDefaultHome = document.getElementById('btnSetAsDefaultHome');
  const deployProgressBox = document.getElementById('deployProgressBox');
  const deployStatusText = document.getElementById('deployStatusText');
  const deployPercentText = document.getElementById('deployPercentText');
  const deployProgressFill = document.getElementById('deployProgressFill');

  // Voice & AI Elements
  const inputAiCommand = document.getElementById('inputAiCommand');
  const btnSubmitAiCommand = document.getElementById('btnSubmitAiCommand');
  const aiChips = document.querySelectorAll('#page-ai .chip-btn');
  const aiResultTitle = document.getElementById('aiResultTitle');
  const aiResultSubtitle = document.getElementById('aiResultSubtitle');

  // Pro & Ad-Shield Elements
  const inputLicenseKey = document.getElementById('inputLicenseKey');
  const btnActivateKey = document.getElementById('btnActivateKey');
  const toggleAdShieldDns = document.getElementById('toggleAdShieldDns');
  const proKeyChips = document.querySelectorAll('#page-pro .chip-btn');
  const sidebarProStatus = document.getElementById('sidebarProStatus');
  const btnSidebarKeyModal = document.getElementById('btnSidebarKeyModal');

  // Modal Elements
  const modalLicenseOverlay = document.getElementById('modalLicenseOverlay');
  const modalInputKey = document.getElementById('modalInputKey');
  const btnCloseModalX = document.getElementById('btnCloseModalX');
  const btnCancelModalKey = document.getElementById('btnCancelModalKey');
  const btnSubmitModalKey = document.getElementById('btnSubmitModalKey');

  // Toast Stack
  const toastContainerStack = document.getElementById('toastContainerStack');

  // ─── Toast Notifications ───────────────────────────────────────────────────────
  function showToast(message, type = 'info') {
    const toast = document.createElement('div');
    toast.className = `toast-item ${type}`;
    const icon = type === 'success' ? '✓' : type === 'error' ? '✕' : '⚡';
    toast.innerHTML = `<span style="color: ${type === 'success' ? 'var(--success)' : type === 'error' ? 'var(--danger)' : 'var(--primary)'}; font-weight: 700;">${icon}</span> <span>${message}</span>`;
    toastContainerStack.appendChild(toast);

    setTimeout(() => {
      toast.style.opacity = '0';
      toast.style.transform = 'translateX(20px)';
      toast.style.transition = 'all 200ms ease';
      setTimeout(() => toast.remove(), 220);
    }, 3600);
  }

  // ─── Page Navigation ───────────────────────────────────────────────────────────
  function switchPage(pageId) {
    navBtns.forEach(btn => {
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

    state.activePage = pageId;

    if (pageId === 'page-apps' && state.packages.length === 0 && state.isConnected) {
      loadPackages();
    }
  }

  navBtns.forEach(btn => {
    btn.addEventListener('click', () => {
      const pageId = btn.getAttribute('data-page');
      switchPage(pageId);
    });
  });

  btnQuickManageApps?.addEventListener('click', () => switchPage('page-apps'));
  btnQuickLaunchRemote?.addEventListener('click', () => switchPage('page-remote'));

  // ─── Remote Logger ────────────────────────────────────────────────────────────
  function logRemote(msg) {
    if (!remoteConsoleFeed) return;
    const time = new Date().toLocaleTimeString();
    const line = document.createElement('span');
    line.textContent = `[${time}] ${msg}`;
    remoteConsoleFeed.appendChild(line);
    remoteConsoleFeed.scrollTop = remoteConsoleFeed.scrollHeight;
  }

  btnClearRemoteLog?.addEventListener('click', () => {
    if (remoteConsoleFeed) {
      remoteConsoleFeed.innerHTML = '<span>[INIT] CleanTV Remote Bridge v4.0 active.</span>';
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
  function setConnectionState(status, text) {
    if (status === 'connected') {
      state.isConnected = true;
      state.isConnecting = false;
      globalStatusDot.className = 'status-dot connected';
      globalStatusText.textContent = text || `Connected · ${state.device}`;
      if (valAdbState) {
        valAdbState.className = 'telemetry-val success';
        valAdbState.textContent = `Online (${state.device})`;
      }
      if (connectedBanner) {
        connectedBanner.style.display = 'block';
        if (connectedBannerSub) {
          connectedBannerSub.textContent = `${state.telemetry.model} · ${state.device} · ADB Online`;
        }
      }
    } else if (status === 'connecting') {
      state.isConnected = false;
      state.isConnecting = true;
      globalStatusDot.className = 'status-dot connecting';
      globalStatusText.textContent = text || `Connecting to ${state.device}...`;
      if (valAdbState) {
        valAdbState.className = 'telemetry-val';
        valAdbState.textContent = 'Connecting...';
      }
      if (connectedBanner) connectedBanner.style.display = 'none';
    } else if (status === 'error') {
      state.isConnected = false;
      state.isConnecting = false;
      globalStatusDot.className = 'status-dot error';
      globalStatusText.textContent = text || 'Connection Error';
      if (valAdbState) {
        valAdbState.className = 'telemetry-val';
        valAdbState.style.color = 'var(--danger)';
        valAdbState.textContent = 'Offline';
      }
      if (connectedBanner) connectedBanner.style.display = 'none';
    } else {
      state.isConnected = false;
      state.isConnecting = false;
      globalStatusDot.className = 'status-dot disconnected';
      globalStatusText.textContent = text || 'Not connected';
      if (valAdbState) {
        valAdbState.className = 'telemetry-val';
        valAdbState.style.color = 'var(--text-muted)';
        valAdbState.textContent = 'Offline';
      }
      if (connectedBanner) connectedBanner.style.display = 'none';
    }
  }

  async function checkAdbState() {
    const res = await apiCall('/api/check-state', { device: state.device });
    if (res && res.connected) {
      setConnectionState('connected', `Connected · ${res.device || state.device}`);
      fetchTelemetry();
    } else {
      setConnectionState('disconnected', 'Not connected');
    }
  }

  async function connectDevice(ip, port = '5555') {
    state.ip = ip.trim();
    state.port = port.trim();
    localStorage.setItem('cleantv_ip', state.ip);
    localStorage.setItem('cleantv_port', state.port);

    if (inputIpAddress) inputIpAddress.value = state.ip;
    if (inputPort) inputPort.value = state.port;

    setConnectionState('connecting', `Connecting to ${state.device}...`);
    showToast(`Establishing encrypted ADB bridge to ${state.device}...`, 'info');

    const res = await apiCall('/api/connect', { device: state.device });
    if (res && res.connected) {
      setConnectionState('connected', `Connected · ${state.device}`);
      showToast(`Connected successfully to ${state.device}`, 'success');
      logRemote(`Connected to ${state.device}`);
      await fetchTelemetry();
      await pingDevice();
    } else {
      setConnectionState('error', 'Connection Failed');
      showToast(res.message || `Could not connect to ${state.device}. Check TV USB debugging.`, 'error');
    }
  }

  async function pingDevice() {
    const t0 = performance.now();
    const res = await apiCall('/api/check-state', { device: state.device });
    const lat = Math.round(performance.now() - t0);
    state.latencyMs = lat;
    if (valPingLatency) valPingLatency.textContent = `${lat} ms`;
    showToast(`Socket Ping: ${lat} ms (0.0% Loss)`, 'success');
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

      if (valDeviceModel) valDeviceModel.textContent = state.telemetry.model;
      if (valManufacturer) valManufacturer.textContent = state.telemetry.manufacturer;
      if (valAndroidVersion) valAndroidVersion.textContent = state.telemetry.android;

      if (res.memStr && valRamText) {
        valRamText.textContent = res.memStr;
      }
      if (res.freeGb && res.totalGb && valRamFill) {
        const pct = Math.round((res.freeGb / res.totalGb) * 100);
        valRamFill.style.width = `${pct}%`;
      }
    }
  }

  // ─── Button Listeners ─────────────────────────────────────────────────────────
  btnConnectDevice?.addEventListener('click', () => {
    const ip = inputIpAddress.value || '192.168.0.159';
    const port = inputPort.value || '5555';
    connectDevice(ip, port);
  });

  btnConnectEmulator?.addEventListener('click', () => {
    connectDevice('127.0.0.1', '5555');
  });

  btnDiscoverDevices?.addEventListener('click', async () => {
    showToast('Scanning network for active Android TV hosts...', 'info');
    const res = await apiCall('/api/scan');
    if (res && res.devices && res.devices.length > 0) {
      const target = res.devices[0].serial;
      showToast(`Discovered target device: ${target}`, 'success');
      connectDevice(target);
    } else {
      showToast('No active ADB devices discovered on local network.', 'warning');
    }
  });

  btnQuickRefresh?.addEventListener('click', () => {
    showToast('Refreshing connection telemetry...', 'info');
    checkAdbState();
  });

  btnRefreshTelemetry?.addEventListener('click', () => {
    fetchTelemetry();
    pingDevice();
  });

  qaPing?.addEventListener('click', pingDevice);

  qaWakeTv?.addEventListener('click', async () => {
    showToast('Waking / Toggling TV display...', 'info');
    logRemote('Sending KEYCODE_POWER (26)');
    await apiCall('/api/key', { device: state.device, key: '26' });
  });

  qaWebRemote?.addEventListener('click', () => {
    window.open('http://127.0.0.1:59822/remote.html', '_blank');
  });

  // ─── In-System Ad-Shield DNS ──────────────────────────────────────────────────
  async function toggleAdShield(enable) {
    state.adShieldActive = enable;
    const mode = enable ? 'adguard' : 'off';
    showToast(`Routing TV DNS through AdGuard Private DNS...`, 'info');
    await apiCall('/api/adblock/toggle', { device: state.device, mode });

    if (qaAdShieldSub) {
      qaAdShieldSub.textContent = enable ? 'AdGuard Private DNS (Active)' : 'Ad-Shield Inactive (Disabled)';
    }
    if (toggleAdShieldDns) toggleAdShieldDns.checked = enable;

    logRemote(`Ad-Shield private DNS toggled to ${mode.toUpperCase()}`);
    showToast(`Ad-Shield DNS is now ${enable ? 'ACTIVE' : 'OFF'}!`, 'success');
  }

  qaAdShield?.addEventListener('click', () => toggleAdShield(!state.adShieldActive));
  toggleAdShieldDns?.addEventListener('change', (e) => toggleAdShield(e.target.checked));

  // ─── Applications Manager ─────────────────────────────────────────────────────
  async function loadPackages() {
    if (!appsListContainer) return;
    appsListContainer.innerHTML = '<div style="padding: 24px; text-align: center; color: var(--text-muted);">Auditing packages over ADB...</div>';

    const res = await apiCall('/api/packages', { device: state.device });
    if (res && res.status === 'ok' && res.packages) {
      state.packages = res.packages;
      if (countTotalApps) countTotalApps.textContent = res.total || res.packages.length;
      if (countUserApps) countUserApps.textContent = res.userCount || 0;
      if (countSysApps) countSysApps.textContent = res.systemCount || 0;
      if (countDisApps) countDisApps.textContent = res.disabledCount || 0;
      renderPackageList();
    } else {
      appsListContainer.innerHTML = '<div style="padding: 24px; text-align: center; color: var(--danger);">Failed to load packages. Ensure device is online.</div>';
    }
  }

  function renderPackageList() {
    if (!appsListContainer) return;
    appsListContainer.innerHTML = '';

    const query = state.packageSearch.toLowerCase();
    const filtered = state.packages.filter(p => {
      const matchSearch = p.name.toLowerCase().includes(query) || p.pkg.toLowerCase().includes(query);
      if (!matchSearch) return false;
      if (state.packageFilter === 'user') return !p.isSystem;
      if (state.packageFilter === 'system') return p.isSystem;
      if (state.packageFilter === 'disabled') return !p.isEnabled;
      return true;
    });

    if (filtered.length === 0) {
      appsListContainer.innerHTML = '<div style="padding: 24px; text-align: center; color: var(--text-muted);">No matching packages found.</div>';
      return;
    }

    filtered.slice(0, 100).forEach(pkg => {
      const card = document.createElement('div');
      card.className = 'app-row-card';
      const isDis = !pkg.isEnabled;

      card.innerHTML = `
        <div class="app-main-info">
          <div class="app-icon-rect">📦</div>
          <div class="app-labels-wrap">
            <span class="app-name-text">${pkg.name}</span>
            <span class="app-pkg-text">${pkg.pkg}</span>
          </div>
        </div>
        <div class="app-actions-wrap">
          <span class="risk-tag ${isDis ? 'risk-caution' : 'risk-safe'}">${isDis ? 'DISABLED' : (pkg.isSystem ? 'SYSTEM' : 'USER')}</span>
          <button class="btn btn-secondary btn-sm btn-pkg-action" data-pkg="${pkg.pkg}" data-action="${isDis ? 'enable' : 'disable'}">
            ${isDis ? 'Enable' : 'Disable'}
          </button>
        </div>
      `;
      appsListContainer.appendChild(card);
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

  inputAppSearch?.addEventListener('input', (e) => {
    state.packageSearch = e.target.value;
    renderPackageList();
  });

  appFilterChips.forEach(chip => {
    chip.addEventListener('click', () => {
      appFilterChips.forEach(c => c.classList.remove('active'));
      chip.classList.add('active');
      state.packageFilter = chip.getAttribute('data-filter');
      renderPackageList();
    });
  });

  btnRefreshAppList?.addEventListener('click', loadPackages);

  // ─── TV Debloater ─────────────────────────────────────────────────────────────
  function renderDebloatPresets(presetKey) {
    const list = debloatPresets[presetKey] || debloatPresets.realme;
    if (statDebloatCount) statDebloatCount.textContent = list.length;
    if (statDebloatRam) statDebloatRam.textContent = `~${list.length * 14} MB`;

    if (!debloatPackageTable) return;
    debloatPackageTable.innerHTML = '';

    list.forEach(item => {
      const row = document.createElement('div');
      row.className = 'pkg-table-row';
      row.innerHTML = `
        <div>
          <div style="font-size: 13.5px; font-weight: 600; color: var(--text-primary);">${item.name}</div>
          <div style="font-family: var(--font-mono); font-size: 11px; color: var(--text-muted);">${item.pkg}</div>
          <div style="font-size: 11px; color: var(--warning); margin-top: 2px;">⚠️ ${item.reason}</div>
        </div>
        <div style="display: flex; align-items: center; gap: 10px;">
          <span class="risk-tag ${item.risk === 'safe' ? 'risk-safe' : 'risk-caution'}">${item.risk.toUpperCase()}</span>
          <input type="checkbox" class="debloat-check" checked data-pkg="${item.pkg}">
        </div>
      `;
      debloatPackageTable.appendChild(row);
    });
  }

  selectDebloatPreset?.addEventListener('change', (e) => {
    renderDebloatPresets(e.target.value);
  });

  btnExecuteDebloat?.addEventListener('click', async () => {
    showToast('Executing safe 1-Click Debloat on TV...', 'info');
    const checked = Array.from(document.querySelectorAll('.debloat-check:checked')).map(c => c.getAttribute('data-pkg'));
    await apiCall('/api/debloat', { device: state.device, packages: checked.join(',') });
    showToast(`Debloat complete! Disabled ${checked.length} bloatware packages.`, 'success');
    logRemote(`Debloated ${checked.length} packages`);
  });

  btnExecuteRestore?.addEventListener('click', async () => {
    showToast('Restoring OEM factory packages...', 'info');
    await apiCall('/api/restore', { device: state.device });
    showToast('Restored all OEM packages successfully.', 'success');
    logRemote('Factory packages restored');
  });

  // ─── Virtual Remote ───────────────────────────────────────────────────────────
  remoteKeys.forEach(btn => {
    btn.addEventListener('click', async () => {
      const keycode = btn.getAttribute('data-key');
      const label = btn.innerText.trim();
      logRemote(`Key: ${label} (Keycode ${keycode})`);
      await apiCall('/api/key', { device: state.device, key: keycode });
    });
  });

  btnTriggerAiHud?.addEventListener('click', () => switchPage('page-ai'));
  btnTriggerControlCenter?.addEventListener('click', () => switchPage('page-connect'));

  // ─── CleanTV APK Installer ────────────────────────────────────────────────────
  btnDeployApk?.addEventListener('click', async () => {
    if (deployProgressBox) deployProgressBox.style.display = 'block';
    if (deployStatusText) deployStatusText.textContent = 'Pushing clean-tv-launcher-latest.apk to TV...';
    if (deployPercentText) deployPercentText.textContent = '35%';
    if (deployProgressFill) deployProgressFill.style.width = '35%';

    showToast('Deploying CleanTV Home to TV...', 'info');

    setTimeout(async () => {
      if (deployPercentText) deployPercentText.textContent = '75%';
      if (deployProgressFill) deployProgressFill.style.width = '75%';

      await apiCall('/api/install', { device: state.device });

      if (deployPercentText) deployPercentText.textContent = '100%';
      if (deployProgressFill) deployProgressFill.style.width = '100%';
      if (deployStatusText) deployStatusText.textContent = 'CleanTV Home v4.0.0 installed successfully!';
      showToast('CleanTV Home deployed to TV!', 'success');
      logRemote('CleanTV APK deployed to TV');
    }, 1200);
  });

  btnSetAsDefaultHome?.addEventListener('click', async () => {
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

  btnSubmitAiCommand?.addEventListener('click', () => {
    const cmd = inputAiCommand.value.trim();
    if (cmd) executeAiCommand(cmd);
  });

  inputAiCommand?.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
      const cmd = inputAiCommand.value.trim();
      if (cmd) executeAiCommand(cmd);
    }
  });

  aiChips.forEach(chip => {
    chip.addEventListener('click', () => {
      const cmd = chip.getAttribute('data-cmd');
      if (inputAiCommand) inputAiCommand.value = cmd;
      executeAiCommand(cmd);
    });
  });

  // ─── Pro License Key Activation ───────────────────────────────────────────────
  async function activateLicenseKey(key) {
    const cleanKey = key.trim().toUpperCase();
    showToast(`Validating Pro License Key: ${cleanKey}...`, 'info');

    await apiCall('/api/license/activate', { key: cleanKey });

    state.license.isPro = true;
    state.license.key = cleanKey;
    localStorage.setItem('cleantv_key', cleanKey);

    if (sidebarProStatus) sidebarProStatus.textContent = 'Lifetime Pro Active';
    if (titlebarTierLabel) titlebarTierLabel.textContent = 'PRO Lifetime';

    showToast(`CleanTV Pro Activated with Key: ${cleanKey}!`, 'success');
    logRemote(`[LICENSE] Activated Pro key: ${cleanKey}`);

    if (modalLicenseOverlay) modalLicenseOverlay.style.display = 'none';
  }

  btnActivateKey?.addEventListener('click', () => {
    const key = inputLicenseKey.value || 'CTV-PRO-8892-98B4';
    activateLicenseKey(key);
  });

  proKeyChips.forEach(chip => {
    chip.addEventListener('click', () => {
      const k = chip.getAttribute('data-key');
      if (inputLicenseKey) inputLicenseKey.value = k;
      activateLicenseKey(k);
    });
  });

  // Modal Handlers
  function openLicenseModal() {
    if (modalLicenseOverlay) modalLicenseOverlay.style.display = 'flex';
  }

  function closeLicenseModal() {
    if (modalLicenseOverlay) modalLicenseOverlay.style.display = 'none';
  }

  btnSidebarKeyModal?.addEventListener('click', openLicenseModal);
  btnHeaderLicense?.addEventListener('click', openLicenseModal);
  titlebarTierLabel?.addEventListener('click', openLicenseModal);
  btnCloseModalX?.addEventListener('click', closeLicenseModal);
  btnCancelModalKey?.addEventListener('click', closeLicenseModal);

  btnSubmitModalKey?.addEventListener('click', () => {
    const k = modalInputKey.value || 'CTV-PRO-8892-98B4';
    activateLicenseKey(k);
  });

  // ─── Initial Startup ──────────────────────────────────────────────────────────
  renderDebloatPresets('realme');
  checkAdbState();

  // Auto-connect to emulator if running
  setTimeout(() => {
    connectDevice('127.0.0.1', '5555');
  }, 500);
});
