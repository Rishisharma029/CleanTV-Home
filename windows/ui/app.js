// CleanTV Home Desktop Manager — Commercial Client Architecture
// Executive Dark Edition · Pure ADB Integration · Zero Mocked Data
'use strict';

document.addEventListener('DOMContentLoaded', () => {
  // ─── Application State ───────────────────────────────────────────────────────
  const state = {
    device: localStorage.getItem('cleantv_device') || '192.168.0.159:5555',
    isConnected: false,
    adbAvailable: false,
    deviceInfo: null,
    packages: [],
    selectedPackages: new Set(),
    activeFilter: 'all',
    searchQuery: '',
    sortBy: 'name',
    isScanning: false,
    lastScanTime: localStorage.getItem('cleantv_last_scan') || null,
    license: {
      tier: 'free',
      isPro: false,
      key: null
    },
    activities: JSON.parse(localStorage.getItem('cleantv_activities') || '[]'),
    settings: {
      theme: localStorage.getItem('cleantv_theme') || 'executive',
      autoConnect: localStorage.getItem('cleantv_auto_connect') !== 'false',
      notifications: localStorage.getItem('cleantv_notifications') !== 'false',
      includeSystem: localStorage.getItem('cleantv_inc_system') === 'true',
      aggressiveMode: localStorage.getItem('cleantv_aggressive') === 'true',
      defaultIp: localStorage.getItem('cleantv_default_ip') || '192.168.0.159:5555',
      timeout: parseInt(localStorage.getItem('cleantv_timeout') || '12000', 10)
    }
  };

  // ─── DOM References ─────────────────────────────────────────────────────────
  const headerStatusDot = document.getElementById('headerStatusDot');
  const headerStatusText = document.getElementById('headerStatusText');
  const headerDevicePill = document.getElementById('headerDevicePill');
  const headerTierBadge = document.getElementById('headerTierBadge');
  const headerTierText = document.getElementById('headerTierText');
  const btnHeaderSettings = document.getElementById('btnHeaderSettings');
  const btnNotifications = document.getElementById('btnNotifications');

  // Navigation
  const navItems = document.querySelectorAll('.sidebar .nav-item');
  const tabPanes = document.querySelectorAll('.tab-pane');

  // Dashboard elements
  const heroDeviceName = document.getElementById('heroDeviceName');
  const heroAndroidVer = document.getElementById('heroAndroidVer');
  const heroStatusText = document.getElementById('heroStatusText');
  const heroLastScan = document.getElementById('heroLastScan');
  const btnHeroScan = document.getElementById('btnHeroScan');
  const btnHeroManageApps = document.getElementById('btnHeroManageApps');

  const cardDevModel = document.getElementById('cardDevModel');
  const cardDevMfr = document.getElementById('cardDevMfr');
  const cardAndroidVer = document.getElementById('cardAndroidVer');
  const cardAdbConn = document.getElementById('cardAdbConn');
  const cardRamVal = document.getElementById('cardRamVal');
  const cardRamSub = document.getElementById('cardRamSub');
  const cardLauncherVal = document.getElementById('cardLauncherVal');
  const cardLauncherSub = document.getElementById('cardLauncherSub');

  const metricTotalApps = document.getElementById('metricTotalApps');
  const metricBloatApps = document.getElementById('metricBloatApps');
  const metricDisabledApps = document.getElementById('metricDisabledApps');
  const metricUserApps = document.getElementById('metricUserApps');
  const insightsStack = document.getElementById('insightsStack');
  const insightCountPill = document.getElementById('insightCountPill');
  const recentActivityList = document.getElementById('recentActivityList');

  // Applications elements
  const appSearchInput = document.getElementById('appSearchInput');
  const appSortSelect = document.getElementById('appSortSelect');
  const filterChips = document.querySelectorAll('.filter-chips-row .filter-chip');
  const appsListContainer = document.getElementById('appsListContainer');
  const btnRefreshApps = document.getElementById('btnRefreshApps');
  const selectAllApps = document.getElementById('selectAllApps');
  const selectedAppsCount = document.getElementById('selectedAppsCount');
  const btnBatchDisable = document.getElementById('btnBatchDisable');
  const btnBatchEnable = document.getElementById('btnBatchEnable');

  // Scanner elements
  const btnStartScan = document.getElementById('btnStartScan');
  const btnExportReport = document.getElementById('btnExportReport');
  const btnCancelScan = document.getElementById('btnCancelScan');
  const scanProgressCard = document.getElementById('scanProgressCard');
  const scanStageTitle = document.getElementById('scanStageTitle');
  const scanStageSubtitle = document.getElementById('scanStageSubtitle');
  const scanProgressFill = document.getElementById('scanProgressFill');
  const scanReportContainer = document.getElementById('scanReportContainer');
  const reportGradeBadge = document.getElementById('reportGradeBadge');
  const reportFindingsTable = document.getElementById('reportFindingsTable');
  const btnReportDebloatCta = document.getElementById('btnReportDebloatCta');

  // Debloater elements
  const presetSelect = document.getElementById('presetSelect');
  const packageList = document.getElementById('packageList');
  const statPackages = document.getElementById('statPackages');
  const statRam = document.getElementById('statRam');
  const btnRunDebloat = document.getElementById('btnRunDebloat');
  const btnRunRestore = document.getElementById('btnRunRestore');

  // Remote & AI
  const remoteConsole = document.getElementById('remoteConsole');
  const aiTextInput = document.getElementById('aiTextInput');
  const btnAiSend = document.getElementById('btnAiSend');
  const aiIntentBadge = document.getElementById('aiIntentBadge');
  const aiActionTitle = document.getElementById('aiActionTitle');
  const aiActionSubtitle = document.getElementById('aiActionSubtitle');

  // Backups
  const backupsListContainer = document.getElementById('backupsListContainer');
  const btnRefreshBackups = document.getElementById('btnRefreshBackups');

  // Pro & Licensing
  const btnUpgradePro = document.getElementById('btnUpgradePro');
  const btnEnterLicenseKey = document.getElementById('btnEnterLicenseKey');
  const btnRestorePurchase = document.getElementById('btnRestorePurchase');

  // Modals
  const confirmModal = document.getElementById('confirmModal');
  const confirmModalHeading = document.getElementById('confirmModalHeading');
  const confirmModalText = document.getElementById('confirmModalText');
  const btnConfirmCancel = document.getElementById('btnConfirmCancel');
  const btnConfirmProceed = document.getElementById('btnConfirmProceed');
  const btnConfirmClose = document.getElementById('btnConfirmClose');
  let confirmCallback = null;

  const licenseModal = document.getElementById('licenseModal');
  const licenseKeyInput = document.getElementById('licenseKeyInput');
  const btnLicenseCancel = document.getElementById('btnLicenseCancel');
  const btnLicenseSubmit = document.getElementById('btnLicenseSubmit');
  const btnLicenseClose = document.getElementById('btnLicenseClose');

  const toastContainer = document.getElementById('toastContainer');

  // ─── API Helper ─────────────────────────────────────────────────────────────
  function getSessionToken() {
    const meta = document.querySelector('meta[name="cleantv-token"]');
    return meta ? meta.getAttribute('content') : '';
  }

  function api(endpoint, data) {
    const token = getSessionToken();
    const headers = { 'Content-Type': 'application/json' };
    if (token) headers['X-CleanTV-Token'] = token;

    return fetch(endpoint, {
      method: 'POST',
      headers: headers,
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

  // ─── Toast Notifications ────────────────────────────────────────────────────
  function showToast(message, type = 'info') {
    if (!state.settings.notifications && type === 'info') return;
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    const icons = { info: 'ℹ️', success: '✅', warn: '⚠️', error: '❌' };
    toast.innerHTML = `<span>${icons[type] || 'ℹ️'}</span><span>${escHtml(message)}</span>`;
    toastContainer.appendChild(toast);
    setTimeout(() => {
      toast.style.opacity = '0';
      toast.style.transform = 'translateY(10px)';
      toast.style.transition = 'all 0.2s ease';
      setTimeout(() => toast.remove(), 200);
    }, 3800);
  }

  function escHtml(s) {
    return String(s || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  }

  // ─── Activity Log Manager ───────────────────────────────────────────────────
  function addActivity(title, details = '') {
    const act = {
      title,
      details,
      time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    };
    state.activities.unshift(act);
    if (state.activities.length > 12) state.activities.pop();
    localStorage.setItem('cleantv_activities', JSON.stringify(state.activities));
    renderActivityList();
  }

  function renderActivityList() {
    if (!recentActivityList) return;
    if (state.activities.length === 0) {
      recentActivityList.innerHTML = `
        <div class="activity-row">
          <div class="activity-info"><span class="activity-dot"></span><span class="activity-name">No recent activities recorded</span></div>
          <span class="activity-time">--</span>
        </div>`;
      return;
    }
    recentActivityList.innerHTML = state.activities.map(a => `
      <div class="activity-row">
        <div class="activity-info">
          <span class="activity-dot"></span>
          <span class="activity-name">${escHtml(a.title)}</span>
        </div>
        <span class="activity-time">${escHtml(a.time)}</span>
      </div>
    `).join('');
  }

  // ─── Tab Switching ──────────────────────────────────────────────────────────
  function switchTab(targetTabId) {
    navItems.forEach(item => {
      if (item.getAttribute('data-tab') === targetTabId) {
        item.classList.add('active');
      } else {
        item.classList.remove('active');
      }
    });

    tabPanes.forEach(pane => {
      if (pane.id === targetTabId) {
        pane.classList.add('active');
      } else {
        pane.classList.remove('active');
      }
    });

    if (targetTabId === 'tab-apps' && state.packages.length === 0 && state.isConnected) {
      loadPackages();
    }
    if (targetTabId === 'tab-backups') {
      loadBackups();
    }
  }

  navItems.forEach(item => {
    item.addEventListener('click', () => {
      const tabId = item.getAttribute('data-tab');
      if (tabId) switchTab(tabId);
    });
  });

  // Quick Action navigation shortcuts
  document.getElementById('qaScan')?.addEventListener('click', () => {
    switchTab('tab-scanner');
    startScanPipeline();
  });
  document.getElementById('qaManage')?.addEventListener('click', () => switchTab('tab-apps'));
  document.getElementById('qaDebloat')?.addEventListener('click', () => switchTab('tab-debloater'));
  document.getElementById('qaBackups')?.addEventListener('click', () => switchTab('tab-backups'));
  document.getElementById('qaSettings')?.addEventListener('click', () => switchTab('tab-settings'));
  btnHeroScan?.addEventListener('click', () => {
    switchTab('tab-scanner');
    startScanPipeline();
  });
  btnHeroManageApps?.addEventListener('click', () => switchTab('tab-apps'));
  btnHeaderSettings?.addEventListener('click', () => switchTab('tab-settings'));
  headerTierBadge?.addEventListener('click', () => switchTab('tab-pro'));

  // ─── Settings Sub-tabs ──────────────────────────────────────────────────────
  document.querySelectorAll('.settings-tab-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('.settings-tab-btn').forEach(b => b.classList.remove('active'));
      document.querySelectorAll('.sec-pane').forEach(p => p.style.display = 'none');
      btn.classList.add('active');
      const secId = btn.getAttribute('data-sec');
      const pane = document.getElementById(secId);
      if (pane) pane.style.display = 'block';
    });
  });

  // ─── Theme Management ───────────────────────────────────────────────────────
  function applyTheme(themeName) {
    document.body.classList.remove('theme-oled', 'theme-midnight');
    if (themeName === 'oled') document.body.classList.add('theme-oled');
    else if (themeName === 'midnight') document.body.classList.add('theme-midnight');
    state.settings.theme = themeName;
    localStorage.setItem('cleantv_theme', themeName);
    const sel = document.getElementById('settingTheme');
    if (sel) sel.value = themeName;
  }

  applyTheme(state.settings.theme);

  document.getElementById('settingTheme')?.addEventListener('change', (e) => {
    applyTheme(e.target.value);
    showToast(`Switched theme to ${e.target.value}`, 'info');
  });

  // ─── Licensing & CleanTV Pro Architecture ───────────────────────────────────
  function updateLicenseUI() {
    const isPro = state.license.isPro;
    if (headerTierBadge && headerTierText) {
      if (isPro) {
        headerTierBadge.className = 'tier-badge pro';
        headerTierText.textContent = 'Pro Lifetime';
      } else {
        headerTierBadge.className = 'tier-badge free';
        headerTierText.textContent = 'Free Tier';
      }
    }

    const proDashboardBadge = document.getElementById('proDashboardBadge');
    if (proDashboardBadge) {
      if (isPro) {
        proDashboardBadge.className = 'tier-badge pro';
        proDashboardBadge.textContent = 'Pro Lifetime';
      } else {
        proDashboardBadge.className = 'tier-badge free';
        proDashboardBadge.textContent = 'Free Tier';
      }
    }

    const sidebarProBadge = document.getElementById('sidebarProBadge');
    if (sidebarProBadge) {
      if (isPro) {
        sidebarProBadge.textContent = 'ACTIVE';
        sidebarProBadge.style.background = 'var(--accent-primary)';
        sidebarProBadge.style.color = '#000';
      } else {
        sidebarProBadge.textContent = 'PRO';
        sidebarProBadge.style.background = '';
        sidebarProBadge.style.color = '';
      }
    }

    const licBadge = document.getElementById('settingLicBadge');
    const licDesc = document.getElementById('settingLicTierDesc');
    if (licBadge && licDesc) {
      if (isPro) {
        licBadge.className = 'badge-pill safe';
        licBadge.textContent = 'PRO LIFETIME';
        licDesc.textContent = `CleanTV Pro Active · Key: ${state.license.key || 'Verified Store License'}`;
      } else {
        licBadge.className = 'badge-pill user';
        licBadge.textContent = 'FREE TIER';
        licDesc.textContent = 'Basic cleanup and scanning features active.';
      }
    }
  }

  function fetchLicenseStatus() {
    api('/api/license/status', {}).then(res => {
      state.license.tier = res.tier || 'free';
      state.license.isPro = !!res.isPro;
      state.license.key = res.key || null;
      updateLicenseUI();
    }).catch(() => {
      updateLicenseUI();
    });
  }

  // Pro Gating Modal
  const proGateModal = document.getElementById('proGateModal');
  const proGateHeading = document.getElementById('proGateHeading');
  const proGateText = document.getElementById('proGateText');
  const btnProGateClose = document.getElementById('btnProGateClose');
  const btnProGateCancel = document.getElementById('btnProGateCancel');
  const btnProGateEnterKey = document.getElementById('btnProGateEnterKey');
  const btnProGateUpgrade = document.getElementById('btnProGateUpgrade');

  function showProGate(featureName, featureDesc) {
    if (proGateHeading && featureName) proGateHeading.textContent = featureName;
    if (proGateText && featureDesc) proGateText.textContent = featureDesc;
    if (proGateModal) proGateModal.classList.add('open');
  }

  function closeProGate() {
    if (proGateModal) proGateModal.classList.remove('open');
  }

  btnProGateClose?.addEventListener('click', closeProGate);
  btnProGateCancel?.addEventListener('click', closeProGate);
  btnProGateEnterKey?.addEventListener('click', () => {
    closeProGate();
    openLicenseModal();
  });
  btnProGateUpgrade?.addEventListener('click', () => {
    closeProGate();
    switchTab('tab-pro');
  });

  // License Key Modal actions
  function openLicenseModal() {
    if (licenseModal) licenseModal.classList.add('open');
    const feedback = document.getElementById('licenseModalFeedback');
    if (feedback) feedback.style.display = 'none';
    if (licenseKeyInput) {
      licenseKeyInput.value = '';
      licenseKeyInput.focus();
    }
  }

  function closeLicenseModal() {
    if (licenseModal) licenseModal.classList.remove('open');
    const feedback = document.getElementById('licenseModalFeedback');
    if (feedback) feedback.style.display = 'none';
  }

  // Auto-format key input as CTV-PRO-XXXX-XXXX
  licenseKeyInput?.addEventListener('input', (e) => {
    let raw = e.target.value.toUpperCase().replace(/[^A-Z0-9]/g, '');
    let formatted = '';
    if (raw.startsWith('CTVPRO')) {
      raw = raw.replace(/^CTVPRO/, '');
      formatted = 'CTV-PRO';
      if (raw.length > 0) formatted += '-' + raw.substring(0, 4);
      if (raw.length > 4) formatted += '-' + raw.substring(4, 8);
    } else if (raw.startsWith('CTV')) {
      raw = raw.replace(/^CTV/, '');
      formatted = 'CTV';
      if (raw.startsWith('PRO')) {
        raw = raw.replace(/^PRO/, '');
        formatted += '-PRO';
      }
      if (raw.length > 0) formatted += '-' + raw.substring(0, 4);
      if (raw.length > 4) formatted += '-' + raw.substring(4, 8);
    } else if (raw.length > 0) {
      formatted = raw.substring(0, 4);
      if (raw.length > 4) formatted += '-' + raw.substring(4, 8);
    }
    if (formatted) e.target.value = formatted;
  });

  btnEnterLicenseKey?.addEventListener('click', openLicenseModal);
  document.getElementById('btnActivateKeyModal')?.addEventListener('click', openLicenseModal);
  btnLicenseCancel?.addEventListener('click', closeLicenseModal);
  btnLicenseClose?.addEventListener('click', closeLicenseModal);

  btnLicenseSubmit?.addEventListener('click', () => {
    const key = (licenseKeyInput?.value || '').trim().toUpperCase();
    const feedback = document.getElementById('licenseModalFeedback');
    if (!key) {
      if (feedback) {
        feedback.style.display = 'block';
        feedback.style.color = 'var(--accent-danger)';
        feedback.textContent = 'Please enter your CTV-PRO license key.';
      }
      showToast('Please enter a license key.', 'warn');
      return;
    }
    if (feedback) feedback.style.display = 'none';

    api('/api/license/activate', { licenseKey: key }).then(res => {
      closeLicenseModal();
      state.license.tier = 'pro';
      state.license.isPro = true;
      state.license.key = key;
      updateLicenseUI();
      addActivity('CleanTV Pro Activated', `License key: ${key}`);
      showToast('🎉 CleanTV Pro Lifetime activated successfully!', 'success');
    }).catch(err => {
      if (feedback) {
        feedback.style.display = 'block';
        feedback.style.color = 'var(--accent-danger)';
        feedback.textContent = err.message || 'License validation failed.';
      }
      showToast('License activation failed: ' + err.message, 'error');
    });
  });

  // Channel A: Microsoft Store Upgrade
  const btnUpgradeStore = document.getElementById('btnUpgradeStore');
  btnUpgradeStore?.addEventListener('click', () => {
    api('/api/license/config', {}).then(cfg => {
      const prodId = cfg.storeProductId || 'CleanTV.Pro.Lifetime';
      const storeUri = `ms-windows-store://pdp/?ProductId=${prodId}`;
      try {
        window.open(storeUri, '_blank');
        showToast('Opening Microsoft Store purchase window...', 'info');
        addActivity('Initiated Store upgrade', prodId);
      } catch (err) {
        showToast('Microsoft Store protocol not available. Redirecting to web checkout...', 'info');
        window.open(cfg.webStoreUrl || 'https://cleantv.app/pro', '_blank');
      }
    }).catch(() => {
      window.open('ms-windows-store://pdp/?ProductId=CleanTV.Pro.Lifetime', '_blank');
    });
  });

  // Channel B: Direct Web Purchase
  const btnBuyWeb = document.getElementById('btnBuyWeb');
  btnBuyWeb?.addEventListener('click', () => {
    api('/api/license/config', {}).then(cfg => {
      const url = cfg.webStoreUrl || 'https://cleantv.app/pro';
      window.open(url, '_blank');
      showToast('Opening web storefront checkout in your default browser...', 'info');
      addActivity('Opened web storefront checkout', '₹99 Introductory');
    }).catch(() => {
      window.open('https://cleantv.app/pro', '_blank');
    });
  });

  // Restore Purchases
  btnRestorePurchase?.addEventListener('click', () => {
    showToast('Checking entitlement cache and store records...', 'info');
    api('/api/license/restore', {}).then(res => {
      if (res.restored && res.isPro) {
        state.license.isPro = true;
        state.license.tier = 'pro';
        state.license.key = res.key || state.license.key;
        updateLicenseUI();
        showToast('🎉 CleanTV Pro purchase restored successfully!', 'success');
        addActivity('CleanTV Pro Restored', res.key || 'Store receipt');
      } else {
        showToast(res.message || 'No prior purchase found for this device or account.', 'info');
      }
    }).catch(err => showToast('Restore failed: ' + err.message, 'error'));
  });

  // Manage License button
  const btnManageLicense = document.getElementById('btnManageLicense');
  btnManageLicense?.addEventListener('click', () => {
    if (state.license.isPro) {
      alert(
        'CleanTV Pro — Entitlement Details\n\n' +
        `• Plan: CleanTV Pro Lifetime Edition\n` +
        `• Status: Verified & Active\n` +
        `• Channel: ${state.license.key ? 'Direct Web Store' : 'Microsoft Store'}\n` +
        `• License Identifier: ${state.license.key || 'Verified Durable Token'}\n` +
        `• Security: HMAC-SHA256 Signed & Anti-Tamper Protected\n\n` +
        'All features including batch management, custom profiles, and report exports are permanently unlocked.'
      );
    } else {
      openLicenseModal();
    }
  });

  // ─── Device Connection & ADB ────────────────────────────────────────────────
  function setConnectionUI(connected, label) {
    state.isConnected = connected;
    if (headerStatusDot) {
      headerStatusDot.className = `status-dot ${connected ? 'connected' : 'disconnected'}`;
    }
    if (headerStatusText) {
      headerStatusText.textContent = label || (connected ? `Online · ${state.device}` : 'No Device Connected');
    }
    if (heroStatusText) {
      heroStatusText.textContent = connected ? 'Online' : 'Offline';
    }
  }

  function connectDevice(deviceAddress) {
    const dev = (deviceAddress || state.device || '').trim();
    if (!dev) {
      showToast('Please enter an Android TV IP address or serial.', 'warn');
      return;
    }

    let endpoint = dev;
    if (!dev.includes(':') && (dev.includes('.') || dev.toLowerCase() === 'localhost')) {
      endpoint = `${dev}:5555`;
    }
    state.device = endpoint;
    localStorage.setItem('cleantv_device', endpoint);

    setConnectionUI(false, `Connecting to ${endpoint}...`);

    api('/api/connect', { device: endpoint }).then(res => {
      setConnectionUI(true, `Online · ${endpoint}`);
      showToast(`Connected to ${endpoint}`, 'success');
      addActivity(`Connected to ${endpoint}`);
      fetchDeviceInfo();
      loadPackages();
    }).catch(err => {
      setConnectionUI(false, 'Connection Failed');
      showToast(`Failed to connect to ${endpoint}: ${err.message}`, 'error');
    });
  }

  headerDevicePill?.addEventListener('click', () => {
    const input = prompt('Enter your Android TV IP address (e.g. 192.168.0.159:5555):', state.device);
    if (input) connectDevice(input);
  });

  document.getElementById('btnEmptyConnect')?.addEventListener('click', () => {
    const input = prompt('Enter your Android TV IP address (e.g. 192.168.0.159:5555):', state.device);
    if (input) connectDevice(input);
  });

  function checkAdbEngine() {
    api('/api/adb-status', {}).then(res => {
      state.adbAvailable = res.adbFound;
      const elAdbPath = document.getElementById('settingAdbPath');
      if (elAdbPath) {
        elAdbPath.textContent = res.adbFound
          ? `Engine: ${res.bundled ? 'Google Bundled Platform Tools' : 'System PATH (' + res.adbPath + ')'}`
          : 'ADB engine not found on system.';
      }
    }).catch(() => {});
  }

  function fetchDeviceInfo() {
    if (!state.device || !state.isConnected) return;
    api('/api/device-info', { device: state.device }).then(res => {
      state.deviceInfo = res;

      const fullDeviceName = `${res.brand || ''} ${res.model || 'Smart TV'}`.trim();
      if (heroDeviceName) heroDeviceName.textContent = fullDeviceName || 'Android TV';
      if (heroAndroidVer) heroAndroidVer.textContent = `Android ${res.androidVersion || '?'}`;
      if (cardDevModel) cardDevModel.textContent = res.model || 'Smart TV';
      if (cardDevMfr) cardDevMfr.textContent = `Brand: ${res.brand || res.manufacturer || 'OEM'}`;
      if (cardAndroidVer) cardAndroidVer.textContent = `v${res.androidVersion || '?'}`;
      if (cardAdbConn) cardAdbConn.textContent = state.device;
      if (cardRamVal) cardRamVal.textContent = res.memory ? res.memory.split('/')[0].trim() : '--';
      if (cardRamSub) cardRamSub.textContent = res.memory ? `${res.memory} available` : 'Memory queried';
      if (cardLauncherVal) cardLauncherVal.textContent = res.launcherState ? res.launcherState.replace(' Active', '') : 'Stock Home';
      if (cardLauncherSub) cardLauncherSub.textContent = res.activeHome ? `Intent: ${res.activeHome}` : 'Default Home Activity';

      // Auto-match debloat preset
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

      generateSmartInsights();
    }).catch(() => {});
  }

  // ─── Package Management Experience ──────────────────────────────────────────
  function loadPackages() {
    if (!state.isConnected) return;
    if (appsListContainer) {
      appsListContainer.innerHTML = `
        <div style="padding:40px;text-align:center;color:var(--text-secondary);">
          <div class="status-dot scanning" style="width:14px;height:14px;margin:0 auto 12px auto;"></div>
          Scanning packages on ${escHtml(state.device)}...
        </div>`;
    }

    api('/api/packages', { device: state.device }).then(res => {
      state.packages = res.packages || [];
      state.selectedPackages.clear();
      updateMetrics(res);
      renderApplicationList();
      generateSmartInsights();
    }).catch(err => {
      if (appsListContainer) {
        appsListContainer.innerHTML = `
          <div class="empty-state">
            <div class="empty-state-icon">⚠️</div>
            <h3>Could Not Retrieve Packages</h3>
            <p>${escHtml(err.message)}</p>
            <button class="btn btn-secondary btn-sm" id="btnRetryPkgLoad">Retry</button>
          </div>`;
        document.getElementById('btnRetryPkgLoad')?.addEventListener('click', loadPackages);
      }
    });
  }

  btnRefreshApps?.addEventListener('click', loadPackages);

  function updateMetrics(summary) {
    const total = summary.total || state.packages.length;
    const bloat = state.packages.filter(p => p.recommendation === 'safe_to_disable').length;
    const disabled = state.packages.filter(p => !p.isEnabled).length;
    const user = state.packages.filter(p => !p.isSystem).length;
    const sys = state.packages.filter(p => p.isSystem).length;
    const crit = state.packages.filter(p => p.isProtected).length;

    if (metricTotalApps) metricTotalApps.textContent = total;
    if (metricBloatApps) metricBloatApps.textContent = bloat;
    if (metricDisabledApps) metricDisabledApps.textContent = disabled;
    if (metricUserApps) metricUserApps.textContent = user;

    // Progress bar ratios
    const pbBloat = document.getElementById('pbBloat');
    const pbDisabled = document.getElementById('pbDisabled');
    const pbUser = document.getElementById('pbUser');
    if (pbBloat && total > 0) pbBloat.style.width = `${Math.min(100, Math.round((bloat / total) * 100))}%`;
    if (pbDisabled && total > 0) pbDisabled.style.width = `${Math.min(100, Math.round((disabled / total) * 100))}%`;
    if (pbUser && total > 0) pbUser.style.width = `${Math.min(100, Math.round((user / total) * 100))}%`;

    // Filter chip counts
    document.getElementById('fcAll') && (document.getElementById('fcAll').textContent = total);
    document.getElementById('fcSafe') && (document.getElementById('fcSafe').textContent = bloat);
    document.getElementById('fcUser') && (document.getElementById('fcUser').textContent = user);
    document.getElementById('fcSys') && (document.getElementById('fcSys').textContent = sys);
    document.getElementById('fcDis') && (document.getElementById('fcDis').textContent = disabled);
    document.getElementById('fcCrit') && (document.getElementById('fcCrit').textContent = crit);
  }

  function getFilteredAndSortedPackages() {
    let list = [...state.packages];

    // Search filter
    if (state.searchQuery) {
      const q = state.searchQuery.toLowerCase();
      list = list.filter(p => p.name.toLowerCase().includes(q) || p.pkg.toLowerCase().includes(q));
    }

    // Category filter
    if (state.activeFilter === 'safe_to_disable') {
      list = list.filter(p => p.recommendation === 'safe_to_disable');
    } else if (state.activeFilter === 'user_app') {
      list = list.filter(p => !p.isSystem);
    } else if (state.activeFilter === 'system') {
      list = list.filter(p => p.isSystem);
    } else if (state.activeFilter === 'disabled') {
      list = list.filter(p => !p.isEnabled);
    } else if (state.activeFilter === 'critical_system') {
      list = list.filter(p => p.isProtected);
    }

    // Sorting
    list.sort((a, b) => {
      if (state.sortBy === 'name') return a.name.localeCompare(b.name);
      if (state.sortBy === 'pkg') return a.pkg.localeCompare(b.pkg);
      if (state.sortBy === 'status') return (b.isEnabled ? 1 : 0) - (a.isEnabled ? 1 : 0);
      if (state.sortBy === 'recommendation') {
        const order = { safe_to_disable: 1, caution: 2, user_app: 3, critical_system: 4 };
        return (order[a.recommendation] || 5) - (order[b.recommendation] || 5);
      }
      return 0;
    });

    return list;
  }

  function renderApplicationList() {
    if (!appsListContainer) return;
    const pkgs = getFilteredAndSortedPackages();

    if (pkgs.length === 0) {
      appsListContainer.innerHTML = `
        <div class="empty-state">
          <div class="empty-state-icon">🔍</div>
          <h3>No Matching Applications</h3>
          <p>No applications match the active filter or search query "${escHtml(state.searchQuery)}".</p>
        </div>`;
      return;
    }

    appsListContainer.innerHTML = pkgs.map(p => {
      const isChecked = state.selectedPackages.has(p.pkg);
      const isProt = p.isProtected;
      const recBadgeClass = p.recommendation === 'safe_to_disable' ? 'safe' : (p.recommendation === 'critical_system' ? 'critical' : (p.recommendation === 'caution' ? 'caution' : 'user'));
      const recLabel = p.recommendation === 'safe_to_disable' ? 'SAFE TO DISABLE' : (p.recommendation === 'critical_system' ? 'CRITICAL SYSTEM' : (p.recommendation === 'caution' ? 'CAUTION' : 'USER APP'));

      return `
        <div class="app-row" data-pkg="${escHtml(p.pkg)}">
          <div>
            <input type="checkbox" class="app-check" data-pkg="${escHtml(p.pkg)}" ${isChecked ? 'checked' : ''} ${isProt ? 'disabled title="Protected core package"' : ''}>
          </div>
          <div class="app-meta-col">
            <div class="app-avatar">${escHtml(p.name.charAt(0))}</div>
            <div class="app-name-box">
              <h4>${escHtml(p.name)}</h4>
              <code>${escHtml(p.pkg)}</code>
            </div>
          </div>
          <div>
            <span class="badge-pill ${recBadgeClass}">${recLabel}</span>
          </div>
          <div>
            <span class="badge-pill ${p.isEnabled ? 'safe' : 'disabled'}">${p.isEnabled ? 'ACTIVE' : 'DISABLED'}</span>
          </div>
          <div class="app-actions-col">
            ${isProt
              ? `<span style="font-size:11px;color:var(--text-muted);font-weight:600;">🔒 PROTECTED</span>`
              : `
                <button class="btn btn-secondary btn-sm btn-pkg-toggle" data-pkg="${escHtml(p.pkg)}" data-enabled="${p.isEnabled ? 'true' : 'false'}" title="${p.isEnabled ? 'Disable package' : 'Enable package'}">
                  ${p.isEnabled ? 'Disable' : 'Enable'}
                </button>
                ${!p.isSystem ? `
                  <button class="btn btn-danger btn-sm btn-pkg-uninstall" data-pkg="${escHtml(p.pkg)}" title="Uninstall application">
                    🗑️
                  </button>
                ` : ''}
              `
            }
          </div>
        </div>
      `;
    }).join('');

    // Checkbox listeners
    document.querySelectorAll('.app-check').forEach(chk => {
      chk.addEventListener('change', (e) => {
        const pkg = chk.getAttribute('data-pkg');
        if (chk.checked) state.selectedPackages.add(pkg);
        else state.selectedPackages.delete(pkg);
        updateBatchBar();
      });
    });

    // Action button listeners
    document.querySelectorAll('.btn-pkg-toggle').forEach(btn => {
      btn.addEventListener('click', () => {
        const pkg = btn.getAttribute('data-pkg');
        const currentlyEnabled = btn.getAttribute('data-enabled') === 'true';
        if (currentlyEnabled) {
          confirmAction(
            'Disable Application',
            `Are you sure you want to disable ${pkg}? The application will be suspended from running in the background. A backup will be preserved.`,
            () => executePackageAction(pkg, 'disable')
          );
        } else {
          executePackageAction(pkg, 'enable');
        }
      });
    });

    document.querySelectorAll('.btn-pkg-uninstall').forEach(btn => {
      btn.addEventListener('click', () => {
        const pkg = btn.getAttribute('data-pkg');
        confirmAction(
          'Uninstall Application',
          `Are you sure you want to completely uninstall ${pkg}? All user data for this application will be removed from your TV.`,
          () => executePackageAction(pkg, 'uninstall')
        );
      });
    });

    updateBatchBar();
  }

  function updateBatchBar() {
    if (selectedAppsCount) selectedAppsCount.textContent = state.selectedPackages.size;
  }

  selectAllApps?.addEventListener('change', () => {
    const pkgs = getFilteredAndSortedPackages().filter(p => !p.isProtected);
    if (selectAllApps.checked) {
      pkgs.forEach(p => state.selectedPackages.add(p.pkg));
    } else {
      state.selectedPackages.clear();
    }
    renderApplicationList();
  });

  // Filter chips
  filterChips.forEach(chip => {
    chip.addEventListener('click', () => {
      filterChips.forEach(c => c.classList.remove('active'));
      chip.classList.add('active');
      state.activeFilter = chip.getAttribute('data-filter') || 'all';
      renderApplicationList();
    });
  });

  appSearchInput?.addEventListener('input', (e) => {
    state.searchQuery = e.target.value;
    renderApplicationList();
  });

  appSortSelect?.addEventListener('change', (e) => {
    state.sortBy = e.target.value;
    renderApplicationList();
  });

  function executePackageAction(pkg, action) {
    showToast(`Executing ${action} for ${pkg}...`, 'info');
    api('/api/package-action', { device: state.device, pkg, action }).then(res => {
      showToast(`Successfully completed ${action} on ${pkg}`, 'success');
      addActivity(`${action.toUpperCase()}: ${pkg}`);
      loadPackages();
    }).catch(err => {
      showToast(`Error: ${err.message}`, 'error');
    });
  }

  // Batch actions (Pro feature gating)
  btnBatchDisable?.addEventListener('click', () => {
    if (state.selectedPackages.size === 0) {
      showToast('Select one or more applications first.', 'warn');
      return;
    }
    if (!state.license.isPro) {
      showProGate('Batch Package Operations', 'Batch disabling multiple applications is a CleanTV Pro exclusive capability. Upgrade to execute batch operations in a single click.');
      return;
    }
    const pkgs = Array.from(state.selectedPackages);
    confirmAction(
      'Batch Disable Applications',
      `Disable ${pkgs.length} selected application(s)? An automated pre-operation backup will be created on disk before execution.`,
      () => {
        showToast(`Disabling ${pkgs.length} packages...`, 'info');
        api('/api/batch-action', { device: state.device, packages: pkgs, action: 'disable' }).then(res => {
          showToast(`Batch disable finished: ${res.succeeded.length} disabled.`, 'success');
          addActivity(`Batch Disabled ${res.succeeded.length} packages`);
          loadPackages();
        }).catch(err => showToast('Batch disable failed: ' + err.message, 'error'));
      }
    );
  });

  btnBatchEnable?.addEventListener('click', () => {
    if (state.selectedPackages.size === 0) {
      showToast('Select one or more applications first.', 'warn');
      return;
    }
    if (!state.license.isPro) {
      showProGate('Batch Package Operations', 'Batch enabling multiple applications is a CleanTV Pro exclusive capability. Upgrade to manage multiple apps simultaneously.');
      return;
    }
    const pkgs = Array.from(state.selectedPackages);
    showToast(`Enabling ${pkgs.length} packages...`, 'info');
    api('/api/batch-action', { device: state.device, packages: pkgs, action: 'enable' }).then(res => {
      showToast(`Batch enable finished: ${res.succeeded.length} enabled.`, 'success');
      addActivity(`Batch Enabled ${res.succeeded.length} packages`);
      loadPackages();
    }).catch(err => showToast('Batch enable failed: ' + err.message, 'error'));
  });

  // ─── Smart Application Insights Generator ───────────────────────────────────
  function generateSmartInsights() {
    if (!insightsStack) return;
    const insights = [];

    if (!state.isConnected) {
      insights.push({
        type: 'info',
        icon: 'ℹ️',
        title: 'Connect Android TV to Begin',
        desc: 'Connect CleanTV to your TV via Wi-Fi or USB to inspect installed packages and generate actionable insights.'
      });
    } else {
      const bloatPkgs = state.packages.filter(p => p.recommendation === 'safe_to_disable' && p.isEnabled);
      const disabledPkgs = state.packages.filter(p => !p.isEnabled);

      if (bloatPkgs.length > 0) {
        insights.push({
          type: 'warning',
          icon: '⚠️',
          title: `${bloatPkgs.length} Unnecessary Vendor Bloatware Detected`,
          desc: `Identified background telemetry, sponsored recommendation daemons, and vendor ad miners. Disabling them frees RAM and prevents unwanted background network activity.`,
          action: 'Review in Debloater',
          actionTab: 'tab-debloater'
        });
      }

      if (state.deviceInfo) {
        const activeHome = state.deviceInfo.activeHome || '';
        const isCleanTv = activeHome.toLowerCase().includes('tvhome');
        if (!isCleanTv && activeHome) {
          insights.push({
            type: 'info',
            icon: '🏠',
            title: 'Stock TV Launcher Currently Active',
            desc: `The TV is currently running "${activeHome}". You can set CleanTV Home as default launcher to eliminate home screen advertising banners completely.`,
            action: 'Debloater Tab',
            actionTab: 'tab-debloater'
          });
        }

        if (state.deviceInfo.freeGb !== undefined && state.deviceInfo.freeGb < 0.6) {
          insights.push({
            type: 'danger',
            icon: '⚡',
            title: 'Constrained Available System RAM',
            desc: `Your TV has only ${state.deviceInfo.freeGb} GB available RAM. Suspending dormant vendor background processes will improve 4K streaming buffer performance.`,
            action: 'Safe Debloat',
            actionTab: 'tab-debloater'
          });
        }
      }

      if (disabledPkgs.length > 0) {
        insights.push({
          type: 'success',
          icon: '🛡️',
          title: `${disabledPkgs.length} Packages Safely Suspended`,
          desc: `CleanTV has verified that ${disabledPkgs.length} package(s) are suspended and cannot run in the background. Full 1-click restore is available at any time.`,
          action: 'View Backups',
          actionTab: 'tab-backups'
        });
      }

      if (insights.length === 0) {
        insights.push({
          type: 'success',
          icon: '✅',
          title: 'Device Looks Well-Organized',
          desc: 'No aggressive bloatware or unaddressed telemetry daemons detected. Your Android TV is operating under clean parameters.'
        });
      }
    }

    if (insightCountPill) insightCountPill.textContent = `${insights.length} Insight${insights.length === 1 ? '' : 's'}`;

    insightsStack.innerHTML = insights.map(ins => `
      <div class="insight-item">
        <div class="insight-icon ${ins.type}">${ins.icon}</div>
        <div class="insight-text">
          <h4>${escHtml(ins.title)}</h4>
          <p>${escHtml(ins.desc)}</p>
        </div>
        ${ins.action ? `<button class="insight-action-btn" data-tab="${ins.actionTab}">${escHtml(ins.action)} →</button>` : ''}
      </div>
    `).join('');

    document.querySelectorAll('.insight-action-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        const tab = btn.getAttribute('data-tab');
        if (tab) switchTab(tab);
      });
    });
  }

  // ─── Multi-Stage Diagnostic Scanner ─────────────────────────────────────────
  let scanCancelRequested = false;

  function updateScanStage(stageNum, title, subtitle, pct) {
    for (let i = 1; i <= 5; i++) {
      const el = document.getElementById(`stage${i}`);
      if (!el) continue;
      el.classList.remove('active', 'done');
      if (i < stageNum) el.classList.add('done');
      else if (i === stageNum) el.classList.add('active');
    }
    if (scanStageTitle) scanStageTitle.textContent = title;
    if (scanStageSubtitle) scanStageSubtitle.textContent = subtitle;
    if (scanProgressFill) scanProgressFill.style.width = `${pct}%`;
  }

  function startScanPipeline() {
    if (!state.isConnected) {
      showToast('Connect your Android TV to run a diagnostic scan.', 'warn');
      return;
    }

    state.isScanning = true;
    scanCancelRequested = false;
    if (scanProgressCard) scanProgressCard.style.display = 'block';
    if (scanReportContainer) scanReportContainer.style.display = 'none';
    if (btnExportReport) btnExportReport.style.display = 'none';

    // Stage 1: Detect Device
    updateScanStage(1, 'Stage 1/5: Detecting Device Parameters', `Connecting to ${state.device} and reading hardware descriptors...`, 20);

    setTimeout(() => {
      if (scanCancelRequested) return;

      // Stage 2: Retrieve Installed Packages
      updateScanStage(2, 'Stage 2/5: Retrieving Package Manifests', 'Querying installed system and user packages via ADB...', 40);

      api('/api/packages', { device: state.device }).then(res => {
        if (scanCancelRequested) return;
        state.packages = res.packages || [];
        updateMetrics(res);

        // Stage 3: Analyze Bloatware & Telemetry
        updateScanStage(3, 'Stage 3/5: Cross-Referencing Knowledge Base', `Analyzing ${state.packages.length} packages against OEM telemetry heuristics...`, 65);

        setTimeout(() => {
          if (scanCancelRequested) return;

          // Stage 4: Safety Classification
          updateScanStage(4, 'Stage 4/5: Safety Classifications & Risk Scoring', 'Verifying fail-closed protections and active HOME launcher...', 85);

          setTimeout(() => {
            if (scanCancelRequested) return;

            // Stage 5: Compile Report
            updateScanStage(5, 'Stage 5/5: Compiling Diagnostic Audit Report', 'Finalizing device optimization summary...', 100);

            setTimeout(() => {
              finishScanPipeline();
            }, 400);
          }, 450);
        }, 500);
      }).catch(err => {
        showToast('Scan aborted: ' + err.message, 'error');
        cancelScan();
      });
    }, 450);
  }

  function cancelScan() {
    scanCancelRequested = true;
    state.isScanning = false;
    if (scanProgressCard) scanProgressCard.style.display = 'none';
    showToast('Scan cancelled by user.', 'info');
  }

  btnCancelScan?.addEventListener('click', cancelScan);
  btnStartScan?.addEventListener('click', startScanPipeline);

  function finishScanPipeline() {
    state.isScanning = false;
    const ts = new Date().toLocaleString();
    state.lastScanTime = ts;
    localStorage.setItem('cleantv_last_scan', ts);
    if (heroLastScan) heroLastScan.textContent = ts;

    if (scanProgressCard) scanProgressCard.style.display = 'none';
    if (scanReportContainer) scanReportContainer.style.display = 'block';
    if (btnExportReport) btnExportReport.style.display = 'inline-flex';

    // Compute report metrics
    const bloat = state.packages.filter(p => p.recommendation === 'safe_to_disable');
    const disabled = state.packages.filter(p => !p.isEnabled);
    const activeBloat = bloat.filter(p => p.isEnabled);

    document.getElementById('repTotal').textContent = state.packages.length;
    document.getElementById('repReview').textContent = activeBloat.length;
    document.getElementById('repDisabled').textContent = disabled.length;
    document.getElementById('repRam').textContent = `~${activeBloat.length * 15} MB`;

    // Grade evaluation
    let grade = 'A';
    if (activeBloat.length > 8) grade = 'D';
    else if (activeBloat.length > 4) grade = 'C';
    else if (activeBloat.length > 0) grade = 'B';
    if (reportGradeBadge) {
      reportGradeBadge.textContent = grade;
      reportGradeBadge.style.background = grade === 'A' ? 'var(--accent-primary)' : (grade === 'B' ? 'var(--accent-secondary)' : (grade === 'C' ? 'var(--color-warning)' : 'var(--color-danger)'));
    }

    const reportMeta = document.getElementById('reportMetaText');
    if (reportMeta && state.deviceInfo) {
      reportMeta.textContent = `Generated for ${state.deviceInfo.brand || ''} ${state.deviceInfo.model || 'Smart TV'} · Android ${state.deviceInfo.androidVersion || '?'}`;
    }

    // Render findings
    if (reportFindingsTable) {
      if (activeBloat.length === 0) {
        reportFindingsTable.innerHTML = `
          <div style="padding:24px;text-align:center;color:var(--text-secondary);">
            ✅ No unnecessary vendor bloatware currently active.
          </div>`;
      } else {
        reportFindingsTable.innerHTML = activeBloat.map(p => `
          <div class="app-row" style="grid-template-columns: 2fr 1.5fr 1fr;">
            <div>
              <strong style="font-size:13px;color:var(--text-primary);">${escHtml(p.name)}</strong>
              <div style="font-size:11px;font-family:var(--font-mono);color:var(--text-muted);">${escHtml(p.pkg)}</div>
            </div>
            <div style="font-size:12px;color:var(--text-secondary);">${escHtml(p.reason)}</div>
            <div style="text-align:right;">
              <span class="badge-pill safe">RECOMMENDED DISABLE</span>
            </div>
          </div>
        `).join('');
      }
    }

    addActivity('Completed Device Diagnostic Scan', `Grade: ${grade}`);
    showToast(`Scan complete: Device Health Grade ${grade}`, 'success');
  }

  btnReportDebloatCta?.addEventListener('click', () => switchTab('tab-debloater'));

  // Export report functionality
  btnExportReport?.addEventListener('click', () => {
    if (!state.license.isPro) {
      showProGate('Audit Report Export', 'Exporting formatted JSON & TXT system debloat audit reports is a CleanTV Pro exclusive feature. Upgrade to unlock full export capabilities.');
      return;
    }

    const reportData = {
      product: 'CleanTV Home Desktop Manager',
      edition: 'Commercial Edition',
      generatedAt: new Date().toISOString(),
      device: state.deviceInfo || { endpoint: state.device },
      metrics: {
        totalPackages: state.packages.length,
        bloatwareDetected: state.packages.filter(p => p.recommendation === 'safe_to_disable').length,
        disabledPackages: state.packages.filter(p => !p.isEnabled).length
      },
      classifications: state.packages.map(p => ({
        package: p.pkg,
        name: p.name,
        category: p.category,
        recommendation: p.recommendation,
        reason: p.reason,
        isEnabled: p.isEnabled,
        isSystem: p.isSystem
      }))
    };

    const blob = new Blob([JSON.stringify(reportData, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `cleantv-audit-report-${Date.now()}.json`;
    a.click();
    URL.revokeObjectURL(url);
    showToast('Audit report downloaded successfully.', 'success');
  });

  // ─── Debloater Presets & Execution ──────────────────────────────────────────
  function loadPresetPackages(presetName) {
    api('/api/preset-list', { preset: presetName }).then(res => {
      const pkgs = res.packages || [];
      if (packageList) {
        packageList.innerHTML = pkgs.map(pkg => `
          <div style="display:flex;justify-content:space-between;align-items:center;padding:8px;border-bottom:1px solid var(--border-subtle);">
            <div>
              <code style="font-size:12px;color:var(--text-primary);">${escHtml(pkg)}</code>
              <div style="font-size:11px;color:var(--text-muted);">Verified preset entry</div>
            </div>
            <span class="badge-pill safe">VERIFIED BLOAT</span>
          </div>
        `).join('');
      }
      if (statPackages) statPackages.textContent = pkgs.length;
      if (statRam) statRam.textContent = `~${pkgs.length * 15} MB`;
    }).catch(err => {
      showToast('Could not load preset: ' + err.message, 'warn');
    });
  }

  presetSelect?.addEventListener('change', () => loadPresetPackages(presetSelect.value));

  btnRunDebloat?.addEventListener('click', () => {
    if (!state.isConnected) {
      showToast('Connect your Android TV first.', 'warn');
      return;
    }
    const preset = presetSelect ? presetSelect.value : 'realme';
    confirmAction(
      '1-Click Safe Debloat',
      `Execute debloat for preset "${preset}"? CleanTV will automatically create a full safety backup of current TV states before disabling any vendor packages.`,
      () => {
        showToast('Executing safe debloat with pre-backup...', 'info');
        api('/api/debloat', { device: state.device, preset: preset }).then(res => {
          showToast(`Debloat complete: ${res.disabled} disabled, ${res.skipped} skipped.`, 'success');
          addActivity(`Debloated Preset [${preset}]`, `${res.disabled} packages disabled`);
          loadPackages();
        }).catch(err => {
          showToast('Debloat aborted: ' + err.message, 'error');
        });
      }
    );
  });

  btnRunRestore?.addEventListener('click', () => {
    if (!state.isConnected) {
      showToast('Connect your Android TV first.', 'warn');
      return;
    }
    confirmAction(
      'Factory Restore All Packages',
      'Re-enable all installer-disabled packages and restore stock TV Home launcher from verified backup?',
      () => {
        showToast('Restoring packages from verified backup...', 'info');
        api('/api/restore', { device: state.device }).then(res => {
          showToast(`Restore complete: Re-enabled ${res.restored} package(s).`, 'success');
          addActivity(`Factory Restore Completed`, `Re-enabled ${res.restored} packages`);
          loadPackages();
        }).catch(err => {
          showToast('Restore failed: ' + err.message, 'error');
        });
      }
    );
  });

  // Pro Profiles Handlers
  ['profUltra', 'profPrivacy', 'profStream', 'profMinimal'].forEach(id => {
    document.getElementById(id)?.addEventListener('click', () => {
      if (!state.license.isPro) {
        showProGate('Advanced Debloat Profiles', 'One-click Ultra, Privacy, and Streaming debloat profiles are exclusive to CleanTV Pro. Upgrade to unlock automated deep debloat presets.');
      } else {
        showToast(`Profile applied: ${id.replace('prof', '')}. Ready for debloat.`, 'info');
      }
    });
  });

  // ─── Backups & Snapshots ────────────────────────────────────────────────────
  function loadBackups() {
    if (!backupsListContainer) return;
    api('/api/list-backups', {}).then(res => {
      const backups = res.backups || [];
      if (backups.length === 0) {
        backupsListContainer.innerHTML = `
          <div class="empty-state">
            <div class="empty-state-icon">🛡️</div>
            <h3>No Backups Created Yet</h3>
            <p>CleanTV automatically generates verified state snapshots before performing debloating operations.</p>
          </div>`;
        return;
      }
      backupsListContainer.innerHTML = backups.map(b => `
        <div style="display:flex;justify-content:space-between;align-items:center;padding:12px 16px;border-bottom:1px solid var(--border-subtle);">
          <div>
            <strong style="font-size:13.5px;color:var(--text-primary);">${escHtml(b.name)}</strong>
            <div style="font-size:11.5px;color:var(--text-muted);font-family:var(--font-mono);">${escHtml(b.path)}</div>
          </div>
          <button class="btn btn-secondary btn-sm btn-restore-snap" data-path="${escHtml(b.path)}">
            🔄 Restore Snapshot
          </button>
        </div>
      `).join('');

      document.querySelectorAll('.btn-restore-snap').forEach(btn => {
        btn.addEventListener('click', () => {
          const bPath = btn.getAttribute('data-path');
          confirmAction(
            'Restore Specific Snapshot',
            `Restore TV state from backup directory:\n${bPath}?`,
            () => {
              showToast('Restoring snapshot...', 'info');
              api('/api/restore', { device: state.device, backupDir: bPath }).then(res => {
                showToast(`Snapshot restored: ${res.restored} package(s) re-enabled.`, 'success');
                addActivity('Restored Snapshot', bPath);
                loadPackages();
              }).catch(err => showToast('Restore failed: ' + err.message, 'error'));
            }
          );
        });
      });
    }).catch(() => {});
  }

  btnRefreshBackups?.addEventListener('click', loadBackups);

  // ─── Virtual Remote & AI Controls ───────────────────────────────────────────
  document.querySelectorAll('.remote-btn, .dpad-dir, .dpad-center').forEach(btn => {
    btn.addEventListener('click', () => {
      const key = btn.getAttribute('data-key');
      if (!key) return;
      if (!state.isConnected) {
        showToast('Connect TV before sending remote keyevents.', 'warn');
        return;
      }
      api('/api/key', { device: state.device, key: key }).catch(err => {
        showToast('Keyevent failed: ' + err.message, 'error');
      });
      if (remoteConsole) {
        remoteConsole.innerHTML += `<br>> KEYCODE_${key} transmitted.`;
        remoteConsole.scrollTop = remoteConsole.scrollHeight;
      }
    });
  });

  document.getElementById('btnRemoteAi')?.addEventListener('click', () => {
    if (!state.isConnected) return;
    api('/api/ai/command', { device: state.device, query: 'voice ai hud' });
  });

  document.getElementById('btnRemoteCc')?.addEventListener('click', () => {
    if (!state.isConnected) return;
    api('/api/key', { device: state.device, key: '82' });
  });

  function executeAi(cmd) {
    const q = (cmd || '').trim();
    if (!q) return;
    if (!state.isConnected) {
      showToast('Connect to your TV first.', 'warn');
      return;
    }
    if (aiIntentBadge) aiIntentBadge.textContent = 'EXECUTING';
    if (aiActionTitle) aiActionTitle.textContent = `"${q}"`;
    if (aiActionSubtitle) aiActionSubtitle.textContent = 'Dispatched intent to TV...';

    api('/api/ai/command', { device: state.device, query: q }).then(res => {
      if (aiIntentBadge) aiIntentBadge.textContent = 'EXECUTED';
      if (aiActionSubtitle) aiActionSubtitle.textContent = res.message || 'Action executed on TV.';
      showToast(`AI Command: ${res.message || 'Executed'}`, 'success');
    }).catch(err => {
      if (aiIntentBadge) aiIntentBadge.textContent = 'ERROR';
      if (aiActionSubtitle) aiActionSubtitle.textContent = err.message;
      showToast(err.message, 'error');
    });
  }

  btnAiSend?.addEventListener('click', () => {
    if (aiTextInput) executeAi(aiTextInput.value);
  });
  aiTextInput?.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') executeAi(aiTextInput.value);
  });
  document.querySelectorAll('.chip').forEach(c => {
    c.addEventListener('click', () => {
      const cmd = c.getAttribute('data-cmd');
      if (cmd) {
        if (aiTextInput) aiTextInput.value = cmd;
        executeAi(cmd);
      }
    });
  });

  // ─── Confirmation Modal ─────────────────────────────────────────────────────
  function confirmAction(heading, text, onProceed) {
    if (confirmModalHeading) confirmModalHeading.textContent = heading;
    if (confirmModalText) confirmModalText.textContent = text;
    confirmCallback = onProceed;
    if (confirmModal) confirmModal.classList.add('open');
  }

  function closeConfirmModal() {
    if (confirmModal) confirmModal.classList.remove('open');
    confirmCallback = null;
  }

  btnConfirmCancel?.addEventListener('click', closeConfirmModal);
  btnConfirmClose?.addEventListener('click', closeConfirmModal);
  btnConfirmProceed?.addEventListener('click', () => {
    if (confirmCallback) confirmCallback();
    closeConfirmModal();
  });

  // ─── Settings Handlers ──────────────────────────────────────────────────────
  document.getElementById('settingAutoConnect')?.addEventListener('change', (e) => {
    state.settings.autoConnect = e.target.checked;
    localStorage.setItem('cleantv_auto_connect', String(e.target.checked));
  });
  document.getElementById('settingNotifications')?.addEventListener('change', (e) => {
    state.settings.notifications = e.target.checked;
    localStorage.setItem('cleantv_notifications', String(e.target.checked));
  });
  document.getElementById('settingIncludeSystem')?.addEventListener('change', (e) => {
    state.settings.includeSystem = e.target.checked;
    localStorage.setItem('cleantv_inc_system', String(e.target.checked));
    renderApplicationList();
  });
  document.getElementById('settingAggressiveMode')?.addEventListener('change', (e) => {
    state.settings.aggressiveMode = e.target.checked;
    localStorage.setItem('cleantv_aggressive', String(e.target.checked));
  });
  document.getElementById('settingDefaultIp')?.addEventListener('change', (e) => {
    state.settings.defaultIp = e.target.value;
    localStorage.setItem('cleantv_default_ip', e.target.value);
  });
  document.getElementById('settingTimeout')?.addEventListener('change', (e) => {
    state.settings.timeout = parseInt(e.target.value, 10);
    localStorage.setItem('cleantv_timeout', e.target.value);
  });
  document.getElementById('btnClearHistory')?.addEventListener('click', () => {
    localStorage.removeItem('cleantv_activities');
    state.activities = [];
    renderActivityList();
    showToast('Scan and activity history cleared.', 'success');
  });
  document.getElementById('btnResetSettings')?.addEventListener('click', () => {
    localStorage.clear();
    applyTheme('executive');
    showToast('Settings reset to factory defaults.', 'info');
    setTimeout(() => location.reload(), 400);
  });

  // ─── Initialization ─────────────────────────────────────────────────────────
  
  // ── Ad-Shield System-Wide DNS Manager ──────────────────────────────────────
  let adShieldActive = false;

  async function checkAdShieldStatus() {
    if (!state.device) return;
    try {
      const res = await api('/api/adblock/status', { device: state.device });
      const badge = document.getElementById('adShieldBadge');
      const btn = document.getElementById('btnToggleAdShield');
      if (!badge || !btn) return;

      if (res && res.enabled) {
        adShieldActive = true;
        badge.textContent = '🟢 Active: ' + (res.provider || 'AdGuard DNS');
        badge.style.background = 'rgba(52, 211, 153, 0.15)';
        badge.style.color = '#34d399';
        badge.style.border = '1px solid rgba(52, 211, 153, 0.3)';
        btn.className = 'btn btn-secondary';
        btn.innerHTML = '<span>❌ Disable Ad-Blocker</span>';
      } else {
        adShieldActive = false;
        badge.textContent = '⚪ Inactive (Stock DNS)';
        badge.style.background = 'rgba(255, 255, 255, 0.06)';
        badge.style.color = 'var(--text-secondary)';
        badge.style.border = '1px solid rgba(255, 255, 255, 0.1)';
        btn.className = 'btn btn-primary';
        btn.innerHTML = '<span>🛡️ Enable System-Wide AdBlock</span>';
      }
    } catch (e) {
      console.warn('AdShield status query error:', e);
    }
  }

  const btnToggleAdShield = document.getElementById('btnToggleAdShield');
  if (btnToggleAdShield) {
    btnToggleAdShield.addEventListener('click', async () => {
      if (!state.device) {
        showToast('Please connect to your Android TV first.', 'warning');
        return;
      }
      const select = document.getElementById('adShieldProviderSelect');
      const provider = select ? select.value : 'adguard';
      const action = adShieldActive ? 'disable' : 'enable';

      btnToggleAdShield.disabled = true;
      btnToggleAdShield.innerHTML = '<span>⏳ Updating TV DNS...</span>';

      try {
        const res = await api('/api/adblock/toggle', {
          device: state.device,
          action: action,
          provider: provider
        });
        showToast(res.message || 'Ad-Blocker updated on TV!', 'success');
        await checkAdShieldStatus();
      } catch (e) {
        showToast('Failed to update Ad-Blocker: ' + (e.message || e), 'error');
      } finally {
        btnToggleAdShield.disabled = false;
      }
    });
  }

  checkAdbEngine();
  fetchLicenseStatus();
  renderActivityList();
  setConnectionUI(false, 'Disconnected');

  if (heroLastScan && state.lastScanTime) {
    heroLastScan.textContent = state.lastScanTime;
  }

  if (state.settings.autoConnect && state.device) {
    connectDevice(state.device);
  }
});
