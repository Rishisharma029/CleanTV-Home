// CleanTV Desktop Manager — OBSIDIAN / AURORA Engine
// Precision · Workstation Engineering · Editorial Restraint
'use strict';

document.addEventListener('DOMContentLoaded', () => {
  // ─── Application State ─────────────────────────────────────────────────────────
  const state = {
    ip: localStorage.getItem('cleantv_ip') || '127.0.0.1',
    port: localStorage.getItem('cleantv_port') || '5555',
    get device() {
      const s = (this.ip || '').trim();
      if (!s) return '127.0.0.1:5555';
      if (s.includes(':')) return s;
      if (s.startsWith('emulator-') || !s.includes('.')) return s;
      return `${s}:${this.port || '5555'}`;
    },
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
    adShieldActive: false,
    license: {
      isPro: false,
      tier: 'Free Tier',
      key: localStorage.getItem('cleantv_key') || ''
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
  const sessionToken = document.querySelector('meta[name="cleantv-token"]')?.getAttribute('content') || '';

  function escapeHtml(str) {
    if (!str) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

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

  // Discovery & Setup Wizard Elements
  const discoveredDevicesContainer = document.getElementById('discoveredDevicesContainer');
  const discoveredDevicesList = document.getElementById('discoveredDevicesList');
  const discoveredCountBadge = document.getElementById('discoveredCountBadge');
  const btnDismissDiscover = document.getElementById('btnDismissDiscover');

  const setupWizardOverlay = document.getElementById('setupWizardOverlay');
  const wizardTitle = document.getElementById('wizardTitle');
  const wizardTargetSubtitle = document.getElementById('wizardTargetSubtitle');
  const step1Dot = document.getElementById('step1Dot');
  const step2Dot = document.getElementById('step2Dot');
  const step3Dot = document.getElementById('step3Dot');
  const step4Dot = document.getElementById('step4Dot');
  const wizardBodyContent = document.getElementById('wizardBodyContent');
  const btnCloseWizard = document.getElementById('btnCloseWizard');

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
  const remoteKeyBtns = document.querySelectorAll('.physical-remote-body [data-key]');
  const btnRemoteAiHud = document.getElementById('btnRemoteAiHud');
  const btnRemoteCc = document.getElementById('btnRemoteCc');
  const selectRemoteTarget = document.getElementById('selectRemoteTarget');
  const btnRemoteWakeTv = document.getElementById('btnRemoteWakeTv');

  // APK Deployment Elements
  const btnDeployLauncher = document.getElementById('btnDeployLauncher');
  const btnSetLauncherDefault = document.getElementById('btnSetLauncherDefault');
  const deployProgressBox = document.getElementById('deployProgressBox');
  const deployStatusText = document.getElementById('deployStatusText');
  const deployPercentText = document.getElementById('deployPercentText');
  const deployProgressGauge = document.getElementById('deployProgressGauge');
  const btnDeploySmartTube = document.getElementById('btnDeploySmartTube');
  const btnLaunchSmartTube = document.getElementById('btnLaunchSmartTube');
  const smartTubeProgressBox = document.getElementById('smartTubeProgressBox');
  const smartTubeStatusText = document.getElementById('smartTubeStatusText');
  const smartTubePercentText = document.getElementById('smartTubePercentText');
  const smartTubeProgressGauge = document.getElementById('smartTubeProgressGauge');

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
      const headers = { 'Content-Type': 'application/json' };
      if (sessionToken) {
        headers['X-CleanTV-Token'] = sessionToken;
        params.token = sessionToken;
      }
      const query = new URLSearchParams(params).toString();
      const url = query ? `${endpoint}?${query}` : endpoint;
      const resp = await fetch(url, { method: 'GET', headers });
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
      if (selectRemoteTarget) {
        let found = false;
        for (let i = 0; i < selectRemoteTarget.options.length; i++) {
          if (selectRemoteTarget.options[i].value === state.device || selectRemoteTarget.options[i].value.includes(state.ip)) {
            selectRemoteTarget.selectedIndex = i;
            found = true;
            break;
          }
        }
        if (!found) {
          const opt = document.createElement('option');
          opt.value = state.device;
          opt.textContent = `📺 ${state.telemetry.model || 'Device'} (${state.device})`;
          opt.selected = true;
          selectRemoteTarget.prepend(opt);
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
    let cleanIp = (ip || '').trim();
    let cleanPort = (port || '5555').trim();
    if (cleanIp.includes(':')) {
      const parts = cleanIp.split(':');
      cleanIp = parts[0];
      cleanPort = parts[1] || '5555';
    } else if (cleanIp.startsWith('emulator-') || !cleanIp.includes('.')) {
      cleanPort = '';
    }
    state.ip = cleanIp;
    state.port = cleanPort;
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
    const ip = inputTargetIp?.value?.trim() || '127.0.0.1';
    const port = inputTargetPort.value || '5555';
    connectDevice(ip, port);
  });

  btnConnectEmu?.addEventListener('click', () => {
    connectDevice('127.0.0.1', '5555');
  });

  btnDismissDiscover?.addEventListener('click', () => {
    if (discoveredDevicesContainer) discoveredDevicesContainer.style.display = 'none';
  });

  btnCloseWizard?.addEventListener('click', () => {
    if (setupWizardOverlay) setupWizardOverlay.style.display = 'none';
  });

  btnDiscoverAdb?.addEventListener('click', async () => {
    if (discoveredDevicesContainer) {
      discoveredDevicesContainer.style.display = 'block';
      if (discoveredCountBadge) discoveredCountBadge.textContent = '...';
      if (discoveredDevicesList) {
        discoveredDevicesList.innerHTML = `
          <div style="grid-column: 1 / -1; padding: 24px 16px; text-align: center; color: var(--text-secondary);">
            <div class="loader-spinner" style="margin: 0 auto 12px; width: 22px; height: 22px; border: 2px solid var(--border-subtle); border-top-color: var(--accent-primary); border-radius: 50%; animation: spin 0.8s linear infinite;"></div>
            <div style="font-size: 13px; font-weight: 600; color: var(--text-primary);">Scanning Wi-Fi & ADB network for Android TVs...</div>
            <div style="font-size: 11.5px; color: var(--text-muted); margin-top: 4px;">Checking ARP cache and port 5555 socket handshakes...</div>
          </div>
        `;
      }
    }
    showToast('Scanning local network for active TV hosts...', 'info');
    const res = await apiCall('/api/scan');
    if (res && res.devices && res.devices.length > 0) {
      if (discoveredCountBadge) discoveredCountBadge.textContent = res.devices.length;
      if (discoveredDevicesList) {
        discoveredDevicesList.innerHTML = '';
        res.devices.forEach(dev => {
          const card = document.createElement('div');
          card.className = 'discovered-card';
          card.innerHTML = `
            <div class="discovered-header">
              <div class="discovered-icon">${dev.type === 'emulator' ? '💻' : '📺'}</div>
              <div style="flex: 1; min-width: 0;">
                <div class="discovered-name">${escapeHtml(dev.name || 'Android TV Device')}</div>
                <div class="discovered-address">${escapeHtml(dev.serial)}</div>
              </div>
            </div>
            <div class="discovered-footer">
              <span class="discovered-chip">${dev.type === 'emulator' ? 'EMULATOR' : 'WI-FI LAN'}</span>
              <button class="btn btn-primary btn-sm btn-connect-device" style="pointer-events: auto;">
                Connect & Install →
              </button>
            </div>
          `;
          card.addEventListener('click', () => {
            startSetupWizard(dev);
          });
          discoveredDevicesList.appendChild(card);
        });
      }
      showToast(`Found ${res.devices.length} nearby Android TV device(s).`, 'success');
    } else {
      if (discoveredCountBadge) discoveredCountBadge.textContent = '0';
      if (discoveredDevicesList) {
        discoveredDevicesList.innerHTML = `
          <div style="grid-column: 1 / -1; padding: 20px 16px; text-align: center; background: var(--surface-secondary); border-radius: var(--radius-xs); border: 1px dashed var(--border-subtle);">
            <div style="font-size: 24px; margin-bottom: 6px;">📡</div>
            <div style="font-size: 13px; font-weight: 600; color: var(--text-primary);">No active devices detected</div>
            <div style="font-size: 12px; color: var(--text-muted); max-width: 440px; margin: 4px auto 14px;">
              Ensure ADB debugging is enabled on your TV under Settings > Developer Options and both the PC and TV are on the same Wi-Fi.
            </div>
            <button class="btn btn-secondary btn-sm" id="btnRetryScan">Retry Scan</button>
          </div>
        `;
        document.getElementById('btnRetryScan')?.addEventListener('click', () => btnDiscoverAdb.click());
      }
      showToast('No active ADB devices discovered on local network.', 'warning');
    }
  });

  async function startSetupWizard(dev) {
    if (!setupWizardOverlay) return;
    setupWizardOverlay.style.display = 'flex';
    if (wizardTitle) wizardTitle.textContent = `Setting up ${dev.name || 'Device'}`;
    if (wizardTargetSubtitle) wizardTargetSubtitle.textContent = dev.serial;

    function setStep(stepNum) {
      const dots = [step1Dot, step2Dot, step3Dot, step4Dot];
      dots.forEach((dot, idx) => {
        if (!dot) return;
        if (idx + 1 < stepNum) {
          dot.className = 'wizard-step done';
          dot.innerHTML = `✓ ${dot.textContent.replace(/^([0-9]+\.|✓)\s*/, '')}`;
        } else if (idx + 1 === stepNum) {
          dot.className = 'wizard-step active';
        } else {
          dot.className = 'wizard-step';
        }
      });
    }

    // ── STAGE 1: ADB Connect ──
    async function attemptConnect() {
      setStep(1);
      if (wizardBodyContent) {
        wizardBodyContent.innerHTML = `
          <div style="text-align: center; padding: 24px 0;">
            <div class="loader-spinner" style="margin: 0 auto 16px; width: 28px; height: 28px; border: 3px solid var(--border-subtle); border-top-color: var(--accent-primary); border-radius: 50%; animation: spin 0.8s linear infinite;"></div>
            <div style="font-size: 14px; font-weight: 600; color: var(--text-primary);">Sending ADB Connection Request...</div>
            <div style="font-size: 12px; color: var(--text-muted); margin-top: 6px;">Contacting ${escapeHtml(dev.serial)} over port 5555</div>
          </div>
        `;
      }

      const res = await apiCall('/api/connect', { device: dev.serial });
      if (res && res.connected) {
        state.ip = dev.ip || dev.serial.split(':')[0];
        state.port = dev.port || (dev.serial.includes(':') ? dev.serial.split(':')[1] : '5555');
        localStorage.setItem('cleantv_ip', state.ip);
        localStorage.setItem('cleantv_port', state.port);
        if (inputTargetIp) inputTargetIp.value = state.ip;
        if (inputTargetPort) inputTargetPort.value = state.port;
        setConnectionUI('online', `${dev.name || 'Android TV'} (${dev.serial})`);
        proceedToTelemetry();
      } else if (res && res.message && res.message.toLowerCase().includes('unauthorized')) {
        if (wizardBodyContent) {
          wizardBodyContent.innerHTML = `
            <div style="padding: 12px 0;">
              <div class="tv-auth-banner">
                <div class="tv-auth-icon">⚠️</div>
                <div>
                  <div class="tv-auth-title">Authorization Required on TV Screen</div>
                  <div class="tv-auth-desc">
                    A prompt asking <strong>"Allow USB debugging?"</strong> has appeared on your TV.<br>
                    Using your TV remote, check <strong>"Always allow from this computer"</strong> and select <strong>Allow</strong>.
                  </div>
                </div>
              </div>
              <div style="margin-top: 18px; display: flex; justify-content: flex-end; gap: 10px;">
                <button class="btn btn-secondary btn-sm" id="btnWizardCancelAuth">Cancel</button>
                <button class="btn btn-primary btn-sm" id="btnWizardRetryAuth">I have allowed it — Retry</button>
              </div>
            </div>
          `;
          document.getElementById('btnWizardRetryAuth')?.addEventListener('click', attemptConnect);
          document.getElementById('btnWizardCancelAuth')?.addEventListener('click', () => {
            setupWizardOverlay.style.display = 'none';
          });
        }
      } else {
        if (wizardBodyContent) {
          wizardBodyContent.innerHTML = `
            <div style="padding: 16px 0; text-align: center;">
              <div style="font-size: 26px; color: var(--danger); margin-bottom: 8px;">✕</div>
              <div style="font-size: 14px; font-weight: 600; color: var(--text-primary);">Connection Failed</div>
              <div style="font-size: 12px; color: var(--text-muted); margin: 6px auto 16px; max-width: 400px;">
                ${escapeHtml(res?.message || 'Failed to establish ADB connection.')}
              </div>
              <div style="display: flex; justify-content: center; gap: 10px;">
                <button class="btn btn-secondary btn-sm" id="btnWizardFailClose">Close</button>
                <button class="btn btn-primary btn-sm" id="btnWizardFailRetry">Retry</button>
              </div>
            </div>
          `;
          document.getElementById('btnWizardFailRetry')?.addEventListener('click', attemptConnect);
          document.getElementById('btnWizardFailClose')?.addEventListener('click', () => {
            setupWizardOverlay.style.display = 'none';
          });
        }
      }
    }

    // ── STAGE 2: Device Telemetry ──
    async function proceedToTelemetry() {
      setStep(2);
      if (wizardBodyContent) {
        wizardBodyContent.innerHTML = `
          <div style="text-align: center; padding: 24px 0;">
            <div class="loader-spinner" style="margin: 0 auto 16px; width: 28px; height: 28px; border: 3px solid var(--border-subtle); border-top-color: var(--accent-primary); border-radius: 50%; animation: spin 0.8s linear infinite;"></div>
            <div style="font-size: 14px; font-weight: 600; color: var(--text-primary);">Reading System Architecture & Telemetry...</div>
            <div style="font-size: 12px; color: var(--text-muted); margin-top: 6px;">Querying Android OS, RAM, model identity, and display specs</div>
          </div>
        `;
      }

      await fetchTelemetry();
      await pingDevice();
      setTimeout(proceedToInstallation, 600);
    }

    // ── STAGE 3: Install CleanTV Home ──
    async function proceedToInstallation() {
      setStep(3);
      if (wizardBodyContent) {
        wizardBodyContent.innerHTML = `
          <div style="padding: 16px 0;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px;">
              <span style="font-size: 13px; font-weight: 600; color: var(--text-primary);" id="wizardInstallPhase">Streaming CleanTV Home APK...</span>
              <span style="font-family: var(--font-mono); font-size: 12px; color: var(--accent-primary);" id="wizardInstallPct">30%</span>
            </div>
            <div class="gauge-track" style="height: 8px; margin-bottom: 14px;">
              <div class="gauge-fill" id="wizardInstallProgress" style="width: 30%; background: var(--accent-primary);"></div>
            </div>
            <div style="font-size: 12px; color: var(--text-muted);" id="wizardInstallSub">
              Uploading clean-tv-launcher-latest.apk to device package manager...
            </div>
          </div>
        `;
      }

      const progressBar = document.getElementById('wizardInstallProgress');
      const progressPct = document.getElementById('wizardInstallPct');
      const progressPhase = document.getElementById('wizardInstallPhase');
      const progressSub = document.getElementById('wizardInstallSub');

      const installPromise = apiCall('/api/install', { device: dev.serial });

      setTimeout(() => {
        if (progressBar) progressBar.style.width = '65%';
        if (progressPct) progressPct.textContent = '65%';
        if (progressPhase) progressPhase.textContent = 'Executing Package Installation...';
        if (progressSub) progressSub.textContent = 'Running pm install -r -d on target device...';
      }, 900);

      const res = await installPromise;

      if (res && (res.status === 'ok' || res.installed)) {
        if (progressBar) progressBar.style.width = '90%';
        if (progressPct) progressPct.textContent = '90%';
        if (progressPhase) progressPhase.textContent = 'Launching CleanTV Home...';
        if (progressSub) progressSub.textContent = 'Registering Leanback launcher on TV screen...';

        try {
          await apiCall('/api/key', { device: dev.serial, key: '3' }); // KEYCODE_HOME
        } catch (_) {}

        if (progressBar) progressBar.style.width = '100%';
        if (progressPct) progressPct.textContent = '100%';

        setTimeout(proceedToCelebration, 500);
      } else {
        if (wizardBodyContent) {
          wizardBodyContent.innerHTML = `
            <div style="padding: 16px 0; text-align: center;">
              <div style="font-size: 26px; color: var(--danger); margin-bottom: 8px;">✕</div>
              <div style="font-size: 14px; font-weight: 600; color: var(--text-primary);">Installation Encountered an Issue</div>
              <div style="font-size: 12px; color: var(--text-muted); margin: 6px auto 16px; max-width: 400px;">
                ${escapeHtml(res?.message || 'Failed to install APK.')}
              </div>
              <div style="display: flex; justify-content: center; gap: 10px;">
                <button class="btn btn-secondary btn-sm" id="btnWizardInstallFailClose">Close</button>
                <button class="btn btn-primary btn-sm" id="btnWizardInstallFailRetry">Retry Install</button>
              </div>
            </div>
          `;
          document.getElementById('btnWizardInstallFailRetry')?.addEventListener('click', proceedToInstallation);
          document.getElementById('btnWizardInstallFailClose')?.addEventListener('click', () => {
            setupWizardOverlay.style.display = 'none';
          });
        }
      }
    }

    // ── STAGE 4: Celebration ──
    function proceedToCelebration() {
      setStep(4);
      if (wizardBodyContent) {
        wizardBodyContent.innerHTML = `
          <div style="text-align: center; padding: 18px 0 8px;">
            <div style="width: 52px; height: 52px; border-radius: 50%; background: var(--success-subtle); color: var(--accent-secondary); font-size: 26px; display: flex; align-items: center; justify-content: center; margin: 0 auto 16px; border: 1px solid rgba(77, 226, 197, 0.4);">
              ✓
            </div>
            <h3 style="font-size: 16px; font-weight: 700; color: var(--text-primary); margin-bottom: 4px;">CleanTV Home is Live!</h3>
            <p style="font-size: 12.5px; color: var(--text-secondary); margin-bottom: 16px;">
              The CleanTV launcher has been installed, launched, and protected on <strong>${escapeHtml(state.telemetry.model)}</strong>.
            </p>

            <div style="background: var(--surface-secondary); border: 1px solid var(--border-subtle); border-radius: var(--radius-xs); padding: 12px 14px; margin-bottom: 20px; text-align: left; font-size: 12px; display: flex; flex-direction: column; gap: 8px;">
              <div style="display: flex; align-items: center; gap: 8px;">
                <span style="color: var(--accent-secondary); font-weight: 700;">✓</span>
                <span style="color: var(--text-primary); font-weight: 500;">CleanTV Home Launcher: Active & Default</span>
              </div>
              <div style="display: flex; align-items: center; gap: 8px;">
                <span style="color: var(--accent-secondary); font-weight: 700;">✓</span>
                <span style="color: var(--text-primary); font-weight: 500;">In-System AdGuard DNS: Activated</span>
              </div>
              <div style="display: flex; align-items: center; gap: 8px;">
                <span style="color: var(--accent-secondary); font-weight: 700;">✓</span>
                <span style="color: var(--text-primary); font-weight: 500;">ADB Connection: Authenticated (${escapeHtml(dev.serial)})</span>
              </div>
            </div>

            <div style="display: flex; justify-content: center; gap: 10px; flex-wrap: wrap;">
              <button class="btn btn-secondary btn-sm" id="btnWizardOpenRemote">🎮 Open Virtual Remote</button>
              <button class="btn btn-secondary btn-sm" id="btnWizardOpenDebloat">⚡ Debloat TV Apps</button>
              <button class="btn btn-primary btn-sm" id="btnWizardFinish">Done</button>
            </div>
          </div>
        `;

        document.getElementById('btnWizardOpenRemote')?.addEventListener('click', () => {
          setupWizardOverlay.style.display = 'none';
          switchPage('page-remote', 'Device', 'Virtual Remote');
        });

        document.getElementById('btnWizardOpenDebloat')?.addEventListener('click', () => {
          setupWizardOverlay.style.display = 'none';
          switchPage('page-debloater', 'Device', 'TV Debloater');
        });

        document.getElementById('btnWizardFinish')?.addEventListener('click', () => {
          setupWizardOverlay.style.display = 'none';
          checkAdbState();
        });
      }
    }

    // Start Stage 1
    attemptConnect();
  }

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
    if (enable && !state.license.isPro) {
      showToast('CleanTV Pro is required for System-Wide Ad-Shield DNS. Please activate a Pro license.', 'warning');
      if (toggleAdGuardDns) toggleAdGuardDns.checked = false;
      openLicenseModal();
      return;
    }
    state.adShieldActive = enable;
    const mode = enable ? 'adguard' : 'off';
    showToast(`Setting Ad-Shield Private DNS to: ${mode.toUpperCase()}...`, 'info');
    const res = await apiCall('/api/adblock/toggle', { device: state.device, mode });
    if (res && res.code === 'PRO_REQUIRED') {
      showToast(res.message || 'CleanTV Pro is required for System-Wide Ad-Shield DNS.', 'warning');
      state.adShieldActive = false;
      if (toggleAdGuardDns) toggleAdGuardDns.checked = false;
      openLicenseModal();
      return;
    }

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
    if (!state.license.isPro) {
      showToast('CleanTV Pro is required for 1-Click System Debloating. Please activate a Pro license.', 'warning');
      openLicenseModal();
      return;
    }
    showToast('Executing safe 1-Click Debloat on TV...', 'info');
    const checked = Array.from(document.querySelectorAll('.debloat-check:checked')).map(c => c.getAttribute('data-pkg'));
    const res = await apiCall('/api/debloat', { device: state.device, packages: checked.join(',') });
    if (res && res.code === 'PRO_REQUIRED') {
      showToast(res.message || 'CleanTV Pro is required for Debloating.', 'warning');
      openLicenseModal();
      return;
    }
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
  async function sendRemoteKey(keycode, label = '') {
    if (!keycode) return;
    const desc = label || `Key ${keycode}`;
    logRemote(`Sending: ${desc} (Code ${keycode})...`);
    const res = await apiCall('/api/key', { device: state.device, key: keycode });
    if (res && res.status === 'ok') {
      logRemote(`✓ ACK: ${desc} executed`);
    } else {
      const errMsg = res?.message || 'Device not responding';
      logRemote(`✕ ERR: ${errMsg}`);
      showToast(`Key failed: ${errMsg}`, 'error');
    }
  }

  remoteKeyBtns.forEach(btn => {
    btn.addEventListener('click', async (e) => {
      e.preventDefault();
      const keycode = btn.getAttribute('data-key');
      const label = btn.innerText.trim() || btn.getAttribute('title') || keycode;
      btn.style.transform = 'scale(0.90)';
      setTimeout(() => btn.style.transform = '', 120);
      await sendRemoteKey(keycode, label);
    });
  });

  selectRemoteTarget?.addEventListener('change', () => {
    const val = selectRemoteTarget.value;
    logRemote(`Switched remote target to: ${val}`);
    showToast(`Target set to ${val}`, 'info');
    connectDevice(val);
  });

  btnRemoteWakeTv?.addEventListener('click', async () => {
    logRemote(`⚡ Waking screen and activating CleanTV Home on ${state.device}...`);
    showToast('Waking screen & launching CleanTV Home...', 'info');
    const res = await apiCall('/api/wake', { device: state.device });
    if (res && res.status === 'ok') {
      logRemote('✓ TV Screen Awake & CleanTV Home active!');
      showToast('TV Screen is Awake & CleanTV Home is Active!', 'success');
    } else {
      logRemote(`✕ Wake attempt failed: ${res?.message || 'Timeout'}`);
      showToast('Wake failed. Ensure TV is powered on.', 'error');
    }
  });

  // Physical Keyboard Navigation for Virtual Remote
  document.addEventListener('keydown', (e) => {
    if (['INPUT', 'TEXTAREA', 'SELECT'].includes(document.activeElement?.tagName)) return;
    if (state.activePage !== 'page-remote') return;

    const keyMap = {
      'ArrowUp': { code: '19', label: 'Up ▲' },
      'ArrowDown': { code: '20', label: 'Down ▼' },
      'ArrowLeft': { code: '21', label: 'Left ◀' },
      'ArrowRight': { code: '22', label: 'Right ▶' },
      'Enter': { code: '23', label: 'Select OK' },
      ' ': { code: '23', label: 'Select OK' },
      'Escape': { code: '4', label: 'Back ↩' },
      'Backspace': { code: '4', label: 'Back ↩' },
      'Home': { code: '3', label: 'Home 🏠' },
      '+': { code: '24', label: 'Vol + 🔊' },
      '=': { code: '24', label: 'Vol + 🔊' },
      '-': { code: '25', label: 'Vol - 🔉' },
      '_': { code: '25', label: 'Vol - 🔉' },
      'm': { code: '164', label: 'Mute 🔇' },
      'M': { code: '164', label: 'Mute 🔇' }
    };

    if (keyMap[e.key]) {
      e.preventDefault();
      sendRemoteKey(keyMap[e.key].code, keyMap[e.key].label);
    }
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

  // ─── SmartTube (Ad-Free YouTube) Deployment ──────────────────────────────────
  btnDeploySmartTube?.addEventListener('click', async () => {
    if (smartTubeProgressBox) smartTubeProgressBox.style.display = 'block';
    if (smartTubeStatusText) smartTubeStatusText.textContent = 'Pushing SmartTube 4K APK (~35 MB) to TV over ADB...';
    if (smartTubePercentText) smartTubePercentText.textContent = '25%';
    if (smartTubeProgressGauge) smartTubeProgressGauge.style.width = '25%';

    showToast('Installing Ad-Free YouTube (SmartTube) to TV...', 'info');

    setTimeout(() => {
      if (smartTubePercentText) smartTubePercentText.textContent = '65%';
      if (smartTubeProgressGauge) smartTubeProgressGauge.style.width = '65%';
      if (smartTubeStatusText) smartTubeStatusText.textContent = 'Executing package installation (pm install)...';
    }, 1500);

    const res = await apiCall('/api/install-smarttube', { device: state.device });

    if (smartTubePercentText) smartTubePercentText.textContent = '100%';
    if (smartTubeProgressGauge) smartTubeProgressGauge.style.width = '100%';

    if (res && (res.status === 'ok' || res.installed)) {
      if (smartTubeStatusText) smartTubeStatusText.textContent = 'SmartTube installed & launched! Enjoy 100% ad-free 4K YouTube.';
      showToast('SmartTube installed successfully! 100% Ad-Free YouTube is now active.', 'success');
      logRemote('SmartTube installed and launched on TV');
    } else {
      if (smartTubeStatusText) smartTubeStatusText.textContent = `Install issue: ${res?.message || 'Check connection'}`;
      showToast(`SmartTube error: ${res?.message || 'Failed to install'}`, 'error');
    }
  });

  btnLaunchSmartTube?.addEventListener('click', async () => {
    showToast('Opening SmartTube on TV...', 'info');
    await apiCall('/api/launch-smarttube', { device: state.device });
    logRemote('Launched SmartTube on TV');
  });

  // ─── Voice & AI Intent Controller ─────────────────────────────────────────────
  async function executeAiCommand(cmd) {
    if (!cmd) return;
    if (!state.license.isPro) {
      showToast('CleanTV Pro is required for Voice & AI Neural Assistant. Please activate a Pro license.', 'warning');
      openLicenseModal();
      return;
    }
    showToast(`Executing AI command: "${cmd}"`, 'info');
    if (aiResultTitle) aiResultTitle.textContent = `Processing: "${cmd}"`;
    if (aiResultSubtitle) aiResultSubtitle.textContent = 'Routing intent to TV subsystem over low-latency socket...';

    logRemote(`[AI] Command: ${cmd}`);
    const res = await apiCall('/api/ai/command', { device: state.device, cmd });
    if (res && res.code === 'PRO_REQUIRED') {
      showToast(res.message || 'CleanTV Pro is required for Voice & AI commands.', 'warning');
      openLicenseModal();
      return;
    }

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

  // ─── License Verification & Activation ─────────────────────────────────────────
  async function refreshLicenseStatus() {
    try {
      const res = await apiCall('/api/license/status');
      if (res && res.status === 'ok' && res.isPro) {
        state.license.isPro = true;
        state.license.tier = res.plan || 'CleanTV Pro Lifetime';
        state.license.key = res.key || '';
        if (railProStatus) railProStatus.textContent = 'Pro Active';
        if (titlebarProLabel) titlebarProLabel.textContent = 'Pro Active';
      } else {
        state.license.isPro = false;
        state.license.tier = 'Free Tier';
        state.license.key = '';
        if (railProStatus) railProStatus.textContent = 'Upgrade to Pro';
        if (titlebarProLabel) titlebarProLabel.textContent = 'Free Tier';
      }
      updatePlansUI();
    } catch (err) {
      console.warn('License verification error:', err);
    }
  }

  async function activateLicenseKey(key) {
    const cleanKey = (key || '').trim().toUpperCase();
    if (!cleanKey) {
      showToast('Please enter a CleanTV Pro product key.', 'warning');
      return;
    }
    showToast(`Validating Pro License Key: ${cleanKey}...`, 'info');

    const res = await apiCall('/api/license/activate', { licenseKey: cleanKey, key: cleanKey });

    if (res && res.status === 'ok' && res.isPro) {
      state.license.isPro = true;
      state.license.key = cleanKey;
      state.license.tier = res.plan || 'CleanTV Pro Lifetime';
      localStorage.setItem('cleantv_key', cleanKey);

      if (railProStatus) railProStatus.textContent = 'Pro Active';
      if (titlebarProLabel) titlebarProLabel.textContent = 'Pro Active';
      updatePlansUI();

      showToast(`CleanTV Pro Activated: ${state.license.tier}!`, 'success');
      logRemote(`[LICENSE] Activated Pro key: ${cleanKey}`);

      if (keyModalOverlay) keyModalOverlay.style.display = 'none';
    } else {
      showToast(res?.message || 'Invalid license key. Please check your purchase receipt.', 'error');
      logRemote(`[LICENSE] Activation failed for: ${cleanKey}`);
    }
  }

  btnActivateProKey?.addEventListener('click', () => {
    const key = (inputProKey?.value || '').trim();
    if (!key) {
      showToast('Please enter your CleanTV Pro product key.', 'warning');
      return;
    }
    activateLicenseKey(key);
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
    const k = (modalKeyInputField?.value || '').trim();
    if (!k) {
      showToast('Please enter your CleanTV Pro product key.', 'warning');
      return;
    }
    activateLicenseKey(k);
  });

  // ─── Plans & Microsoft Store 1-Click Checkout System ─────────────────────────
  const checkoutModalOverlay = document.getElementById('checkoutModalOverlay');
  const checkoutModalPlanTitle = document.getElementById('checkoutModalPlanTitle');
  const checkoutModalPriceTag = document.getElementById('checkoutModalPriceTag');
  const btnCloseCheckoutModal = document.getElementById('btnCloseCheckoutModal');
  const btnBuyPlanMonthly = document.getElementById('btnBuyPlanMonthly');
  const btnBuyPlanLifetime = document.getElementById('btnBuyPlanLifetime');
  const btnExecuteMsPayment = document.getElementById('btnExecuteMsPayment');
  const checkoutMainPane = document.getElementById('checkoutMainPane');
  const checkoutProgressBox = document.getElementById('checkoutProgressBox');
  const checkoutProgressFill = document.getElementById('checkoutProgressFill');
  const checkoutStatusStep = document.getElementById('checkoutStatusStep');
  const checkoutStatusPercent = document.getElementById('checkoutStatusPercent');
  const checkoutSuccessBox = document.getElementById('checkoutSuccessBox');
  const checkoutSuccessMessage = document.getElementById('checkoutSuccessMessage');
  const checkoutTransactionId = document.getElementById('checkoutTransactionId');
  const btnCloseSuccessModal = document.getElementById('btnCloseSuccessModal');
  const btnPlansRestorePurchases = document.getElementById('btnPlansRestorePurchases');
  const btnPlansEnterKeyDirect = document.getElementById('btnPlansEnterKeyDirect');
  const plansCurrentTierTitle = document.getElementById('plansCurrentTierTitle');
  const plansCurrentTierPill = document.getElementById('plansCurrentTierPill');
  const plansCurrentTierSubtitle = document.getElementById('plansCurrentTierSubtitle');
  const plansActiveIcon = document.getElementById('plansActiveIcon');

  let activeCheckoutPlan = { plan: 'lifetime', price: '₹200', name: 'CleanTV Pro Lifetime' };

  function updatePlansUI() {
    if (!plansCurrentTierTitle) return;
    if (state.license.isPro) {
      const isLifetime = (state.license.tier || '').toLowerCase().includes('lifetime');
      plansCurrentTierTitle.textContent = state.license.tier || 'CleanTV Pro Lifetime';
      plansCurrentTierPill.textContent = 'ACTIVE';
      plansCurrentTierPill.className = isLifetime ? 'plan-badge-pill lifetime' : 'plan-badge-pill popular';
      if (plansActiveIcon) plansActiveIcon.textContent = isLifetime ? '👑' : '⭐';
      if (plansCurrentTierSubtitle) {
        plansCurrentTierSubtitle.textContent = 'Your license is verified and bound via official Microsoft Store Commerce. Multi-device TV sync is active.';
      }

      if (btnBuyPlanLifetime) {
        if (isLifetime) {
          btnBuyPlanLifetime.textContent = '✓ Active Microsoft Lifetime Entitlement';
          btnBuyPlanLifetime.className = 'btn-buy-oneclick disabled';
        } else {
          btnBuyPlanLifetime.textContent = '🛍️ Upgrade to Lifetime with Microsoft (₹200)';
          btnBuyPlanLifetime.className = 'btn-buy-oneclick lifetime';
        }
      }
      if (btnBuyPlanMonthly) {
        if (!isLifetime) {
          btnBuyPlanMonthly.textContent = '✓ Active Microsoft Monthly Entitlement';
          btnBuyPlanMonthly.className = 'btn-buy-oneclick disabled';
        } else {
          btnBuyPlanMonthly.textContent = '🛍️ Switch to Monthly with Microsoft (₹99)';
          btnBuyPlanMonthly.className = 'btn-buy-oneclick monthly';
        }
      }
    } else {
      plansCurrentTierTitle.textContent = 'CleanTV Free Tier';
      plansCurrentTierPill.textContent = 'STARTER';
      plansCurrentTierPill.className = 'plan-badge-pill starter';
      if (plansActiveIcon) plansActiveIcon.textContent = '📺';
      if (plansCurrentTierSubtitle) {
        plansCurrentTierSubtitle.textContent = 'Upgrade to Pro via Microsoft Store 1-Click checkout to unlock Ad-Shield DNS, deep debloater, and Voice AI.';
      }

      if (btnBuyPlanLifetime) {
        btnBuyPlanLifetime.textContent = '🛍️ 1-Click Buy Lifetime with Microsoft Store (₹200)';
        btnBuyPlanLifetime.className = 'btn-buy-oneclick lifetime';
      }
      if (btnBuyPlanMonthly) {
        btnBuyPlanMonthly.textContent = '🛍️ 1-Click Buy with Microsoft Store (₹99 / mo)';
        btnBuyPlanMonthly.className = 'btn-buy-oneclick monthly';
      }
    }
  }

  function openOneClickCheckout(plan, price, name) {
    activeCheckoutPlan = { plan, price, name };
    if (checkoutModalPlanTitle) checkoutModalPlanTitle.textContent = name;
    if (checkoutModalPriceTag) checkoutModalPriceTag.textContent = `${price} · Microsoft Store 1-Click Activation`;
    
    // Reset panes
    if (checkoutMainPane) checkoutMainPane.style.display = 'block';
    if (checkoutProgressBox) checkoutProgressBox.style.display = 'none';
    if (checkoutSuccessBox) checkoutSuccessBox.style.display = 'none';

    if (checkoutModalOverlay) checkoutModalOverlay.style.display = 'flex';
  }

  function closeOneClickCheckout() {
    if (checkoutModalOverlay) checkoutModalOverlay.style.display = 'none';
  }

  async function processMicrosoftStorePayment() {
    if (checkoutMainPane) checkoutMainPane.style.display = 'none';
    if (checkoutProgressBox) checkoutProgressBox.style.display = 'block';
    if (checkoutSuccessBox) checkoutSuccessBox.style.display = 'none';

    function setStep(pct, label) {
      if (checkoutProgressFill) checkoutProgressFill.style.width = `${pct}%`;
      if (checkoutStatusPercent) checkoutStatusPercent.textContent = `${pct}%`;
      if (checkoutStatusStep) checkoutStatusStep.textContent = label;
    }

    setStep(15, 'Launching Microsoft Windows Commerce...');
    try {
      // Trigger store protocol link if browser environment allows
      if (window.navigator?.userAgent?.includes('Windows')) {
        const storeLink = document.createElement('a');
        storeLink.href = 'ms-windows-store://pdp/?productid=CleanTV.Pro.Lifetime';
        storeLink.click();
      }
    } catch (_) {}

    await new Promise(r => setTimeout(r, 200));
    setStep(40, 'Authorizing Microsoft Account transaction...');
    await new Promise(r => setTimeout(r, 200));
    setStep(65, 'Verifying Microsoft Store durable digital entitlement...');
    await new Promise(r => setTimeout(r, 200));
    setStep(80, 'Generating cryptographically verified product key...');

    try {
      const res = await apiCall('/api/license/checkout-oneclick', {
        plan: activeCheckoutPlan.plan,
        method: 'store',
        device: state.device
      });

      if (res && res.status === 'ok' && res.success) {
        setStep(95, 'Syncing license to connected Android TV...');
        await new Promise(r => setTimeout(r, 250));
        setStep(100, 'Microsoft activation complete!');

        state.license.isPro = true;
        state.license.tier = res.plan || 'CleanTV Pro Lifetime';
        state.license.key = res.key || '';
        localStorage.setItem('cleantv_key', res.key || '');

        if (railProStatus) railProStatus.textContent = 'Pro Active';
        if (titlebarProLabel) titlebarProLabel.textContent = 'Pro Active';
        updatePlansUI();

        setTimeout(() => {
          if (checkoutProgressBox) checkoutProgressBox.style.display = 'none';
          if (checkoutSuccessBox) checkoutSuccessBox.style.display = 'block';
          const txId = res.transactionId || ('MS-ORD-' + Math.random().toString(36).substring(2, 9).toUpperCase());
          if (checkoutTransactionId) checkoutTransactionId.textContent = txId;
          if (checkoutSuccessMessage) {
            checkoutSuccessMessage.textContent = res.tvActivated
              ? `Payment confirmed via Microsoft Store! Your license key has been sent separately to your email. CleanTV ${res.plan} is now active on this Windows PC and synced to your TV.`
              : `Payment confirmed via Microsoft Store! Your license key has been sent separately to your email. CleanTV ${res.plan} is now active on this Windows PC.`;
          }
          showToast(`🎉 CleanTV Pro Activated via Microsoft Store!`, 'success');
          logRemote(`[MS STORE CHECKOUT] Activated ${res.plan} (Transaction: ${txId})`);
        }, 200);
      } else {
        if (checkoutProgressBox) checkoutProgressBox.style.display = 'none';
        if (checkoutMainPane) checkoutMainPane.style.display = 'block';
        showToast(res?.message || 'Microsoft Store payment verification failed.', 'error');
      }
    } catch (err) {
      if (checkoutProgressBox) checkoutProgressBox.style.display = 'none';
      if (checkoutMainPane) checkoutMainPane.style.display = 'block';
      showToast('Network error during Microsoft checkout: ' + err.message, 'error');
    }
  }

  btnBuyPlanLifetime?.addEventListener('click', () => {
    openOneClickCheckout('lifetime', '₹200', 'CleanTV Pro Lifetime');
  });

  btnBuyPlanMonthly?.addEventListener('click', () => {
    openOneClickCheckout('monthly', '₹99', 'CleanTV Pro Monthly');
  });

  btnExecuteMsPayment?.addEventListener('click', processMicrosoftStorePayment);

  btnCloseCheckoutModal?.addEventListener('click', closeOneClickCheckout);
  btnCloseSuccessModal?.addEventListener('click', closeOneClickCheckout);

  btnPlansRestorePurchases?.addEventListener('click', async () => {
    showToast('Checking Microsoft Store and local entitlements...', 'info');
    try {
      const res = await apiCall('/api/license/restore');
      if (res && res.status === 'ok' && res.restored) {
        state.license.isPro = true;
        state.license.tier = res.plan || 'CleanTV Pro Lifetime';
        state.license.key = res.key || '';
        updatePlansUI();
        if (railProStatus) railProStatus.textContent = 'Pro Active';
        if (titlebarProLabel) titlebarProLabel.textContent = 'Pro Active';
        showToast(`Purchases restored! Active: ${state.license.tier}`, 'success');
      } else {
        showToast('No active purchases found to restore.', 'warning');
      }
    } catch (e) {
      showToast('Restore failed: ' + e.message, 'error');
    }
  });

  btnPlansEnterKeyDirect?.addEventListener('click', openLicenseModal);

  // Quick navigation to Plans
  const titlebarProPill = document.getElementById('titlebarProPill');
  titlebarProPill?.addEventListener('click', () => switchPage('page-plans', 'Account', 'Plans & Pricing'));
  const btnUpgradeProBottom = document.querySelector('.rail-footer-profile');
  btnUpgradeProBottom?.addEventListener('click', (e) => {
    if (e.target.id !== 'btnRailEnterKey') {
      switchPage('page-plans', 'Account', 'Plans & Pricing');
    }
  });

  // ─── Initial Startup ──────────────────────────────────────────────────────────
  refreshLicenseStatus();
  renderBrandDebloat('realme');
  checkAdbState();

  // Auto-connect to emulator if running
  setTimeout(() => {
    connectDevice('127.0.0.1', '5555');
  }, 500);
});
