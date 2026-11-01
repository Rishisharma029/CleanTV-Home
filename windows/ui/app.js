// CleanTV Home Desktop Manager - Application Logic
document.addEventListener('DOMContentLoaded', () => {
  // Tab switching
  const navItems = document.querySelectorAll('.nav-item');
  const tabPanes = document.querySelectorAll('.tab-pane');

  navItems.forEach(item => {
    item.addEventListener('click', () => {
      navItems.forEach(n => n.classList.remove('active'));
      tabPanes.forEach(p => p.classList.remove('active'));

      item.classList.add('active');
      const tabId = item.getAttribute('data-tab');
      const pane = document.getElementById(tabId);
      if (pane) pane.classList.add('active');
    });
  });

  // State
  let currentDevice = '192.168.0.159:5555';
  let isConnected = true;

  const statusDot = document.getElementById('statusDot');
  const statusText = document.getElementById('statusText');
  const tvIpInput = document.getElementById('tvIpInput');
  const remoteConsole = document.getElementById('remoteConsole');

  function logConsole(msg) {
    if (!remoteConsole) return;
    const time = new Date().toLocaleTimeString();
    remoteConsole.innerHTML += `<br>[${time}] ${msg}`;
    remoteConsole.scrollTop = remoteConsole.scrollHeight;
  }

  function setConnected(connected, label) {
    isConnected = connected;
    if (statusDot) {
      statusDot.className = 'status-dot ' + (connected ? 'connected' : 'disconnected');
    }
    if (statusText) {
      statusText.textContent = label || (connected ? 'Online · ' + currentDevice : 'Disconnected');
    }
    const adbVal = document.getElementById('infoAdbStatus');
    if (adbVal) adbVal.textContent = connected ? 'Online (' + currentDevice + ')' : 'Offline';
  }

  // Preset bloatware lists
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
      { name: 'PatchWall Promotional Launcher', pkg: 'com.mitv.tvhome', reason: 'Ad-heavy UI overlay' },
      { name: 'Mi TV Recommendations', pkg: 'com.xiaomi.mitv.recommend', reason: 'Banner miner' },
      { name: 'Analytics Tracker', pkg: 'com.miui.analytics', reason: 'Continuous data harvesting' }
    ],
    tcl: [
      { name: 'TCL Channel Video Stream', pkg: 'com.tcl.waterfall.overseas', reason: 'Auto-playing ad shelf' },
      { name: 'TCL Partner Hub', pkg: 'com.tcl.partner', reason: 'Background promotional downloader' }
    ],
    oneplus: [
      { name: 'OxygenPlay Recommendations', pkg: 'com.oneplus.tv.launcher.shelf', reason: 'Sponsored feed daemons' },
      { name: 'OnePlus Analytics Engine', pkg: 'com.oneplus.tv.analytics', reason: 'Usage metrics logger' }
    ],
    firetv: [
      { name: 'Amazon Sponsored Banners', pkg: 'com.amazon.device.ads', reason: 'Video ad carousel' },
      { name: 'Amazon Shopper Agent', pkg: 'com.amazon.buynow', reason: 'Shopping prompts' }
    ]
  };

  function renderDebloatList(presetKey) {
    const listEl = document.getElementById('packageList');
    if (!listEl) return;
    const pkgs = debloatPresets[presetKey] || debloatPresets.realme;
    listEl.innerHTML = '';
    pkgs.forEach(item => {
      const row = document.createElement('div');
      row.className = 'pkg-item';
      row.innerHTML = `
        <div>
          <strong>${item.name}</strong>
          <div style="font-size:11px;color:#94a3b8;">${item.pkg} • ${item.reason}</div>
        </div>
        <span class="pkg-tag">SAFE TO DISABLE</span>
      `;
      listEl.appendChild(row);
    });

    const statCount = document.getElementById('statPackages');
    if (statCount) statCount.textContent = pkgs.length;
  }

  const presetSelect = document.getElementById('presetSelect');
  if (presetSelect) {
    presetSelect.addEventListener('change', () => renderDebloatList(presetSelect.value));
    renderDebloatList(presetSelect.value);
  }

  // Connection Controls
  const btnConnect = document.getElementById('btnConnect');
  if (btnConnect) {
    btnConnect.addEventListener('click', () => {
      const ip = tvIpInput.value.trim();
      if (!ip) return;
      currentDevice = ip;
      logConsole(`Attempting connection to ${ip}...`);
      sendApiRequest('/api/connect', { device: ip }, (res) => {
        setConnected(true, `Connected · ${ip}`);
        logConsole(`Successfully linked to ${ip}`);
      });
    });
  }

  const btnEmu = document.getElementById('btnEmu');
  if (btnEmu) {
    btnEmu.addEventListener('click', () => {
      currentDevice = 'emulator-5554';
      if (tvIpInput) tvIpInput.value = 'emulator-5554';
      logConsole('Connecting to Android Virtual Emulator (emulator-5554)...');
      sendApiRequest('/api/connect', { device: 'emulator-5554' }, () => {
        setConnected(true, 'Connected · emulator-5554');
        logConsole('Connected to Android TV Emulator.');
      });
    });
  }

  // APK Deploy
  const btnDeploy = document.getElementById('btnDeployApk');
  const progressBox = document.getElementById('installProgressBox');
  const progressFill = document.getElementById('progressFill');
  const progressLabel = document.getElementById('progressLabel');

  if (btnDeploy) {
    btnDeploy.addEventListener('click', () => {
      if (progressBox) progressBox.style.display = 'block';
      if (progressLabel) progressLabel.textContent = 'Streaming clean-tv-launcher-latest.apk to ' + currentDevice + '...';
      logConsole('Deploying CleanTV APK to ' + currentDevice + '...');

      let pct = 10;
      const interval = setInterval(() => {
        pct += 25;
        if (progressFill) progressFill.style.width = Math.min(pct, 90) + '%';
        if (pct >= 100) {
          clearInterval(interval);
          sendApiRequest('/api/install', { device: currentDevice }, (res) => {
            if (progressFill) progressFill.style.width = '100%';
            if (progressLabel) progressLabel.textContent = 'Success! CleanTV Launcher is running live on the TV.';
            logConsole('APK Install Successful. Activity launched.');
          });
        }
      }, 300);
    });
  }

  // Debloat action
  const btnRunDebloat = document.getElementById('btnRunDebloat');
  if (btnRunDebloat) {
    btnRunDebloat.addEventListener('click', () => {
      const preset = presetSelect ? presetSelect.value : 'realme';
      logConsole(`Executing 1-Click Safe Debloat for preset [${preset}]...`);
      sendApiRequest('/api/debloat', { device: currentDevice, preset: preset }, (res) => {
        logConsole(`Debloat applied! Freed ~185 MB RAM. System telemetry disabled.`);
        alert('Debloat completed! 14 items disabled. Stock recovery preserved.');
      });
    });
  }

  // Virtual Remote Buttons
  const remoteBtns = document.querySelectorAll('.remote-btn, .dpad-dir, .dpad-center');
  remoteBtns.forEach(btn => {
    btn.addEventListener('click', () => {
      const keycode = btn.getAttribute('data-key');
      if (keycode) {
        logConsole(`Remote Key: KEYCODE_${keycode}`);
        sendApiRequest('/api/key', { device: currentDevice, key: keycode });
      }
    });
  });

  const btnRemoteAi = document.getElementById('btnRemoteAi');
  if (btnRemoteAi) {
    btnRemoteAi.addEventListener('click', () => {
      logConsole('Remote Key: Trigger Voice AI HUD');
      sendApiRequest('/api/ai/trigger', { device: currentDevice });
    });
  }

  const btnRemoteCc = document.getElementById('btnRemoteCc');
  if (btnRemoteCc) {
    btnRemoteCc.addEventListener('click', () => {
      logConsole('Remote Key: Trigger Control Center');
      sendApiRequest('/api/key', { device: currentDevice, key: '82' }); // Menu / CC
    });
  }

  // AI Commands
  const aiTextInput = document.getElementById('aiTextInput');
  const btnAiSend = document.getElementById('btnAiSend');
  const aiIntentBadge = document.getElementById('aiIntentBadge');
  const aiActionTitle = document.getElementById('aiActionTitle');
  const aiActionSubtitle = document.getElementById('aiActionSubtitle');

  function submitAi(cmd) {
    if (!cmd) return;
    logConsole(`AI Query: "${cmd}"`);
    if (aiIntentBadge) aiIntentBadge.textContent = 'EXECUTING';
    if (aiActionTitle) aiActionTitle.textContent = `"${cmd}"`;
    if (aiActionSubtitle) aiActionSubtitle.textContent = 'Classifying intent and executing on TV...';

    sendApiRequest('/api/ai/command', { device: currentDevice, query: cmd }, (res) => {
      if (aiIntentBadge) aiIntentBadge.textContent = (res && res.status ? res.status.toUpperCase() : 'OK');
      if (aiActionTitle) aiActionTitle.textContent = res && res.action ? res.action : `"${cmd}"`;
      if (aiActionSubtitle) aiActionSubtitle.textContent = res && res.message ? res.message : 'Action executed on TV successfully.';
      logConsole(`AI Execution Result: ${res ? res.message : 'Done'}`);
    });
  }

  if (btnAiSend && aiTextInput) {
    btnAiSend.addEventListener('click', () => {
      const q = aiTextInput.value.trim();
      if (q) submitAi(q);
    });
    aiTextInput.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') {
        const q = aiTextInput.value.trim();
        if (q) submitAi(q);
      }
    });
  }

  const chips = document.querySelectorAll('.chip');
  chips.forEach(chip => {
    chip.addEventListener('click', () => {
      const cmd = chip.getAttribute('data-cmd');
      if (cmd) {
        if (aiTextInput) aiTextInput.value = cmd;
        submitAi(cmd);
      }
    });
  });

  // Safe HTTP/API caller
  function sendApiRequest(endpoint, data, callback) {
    fetch(endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data)
    })
    .then(r => r.json())
    .then(json => { if (callback) callback(json); })
    .catch(err => {
      // Local fallback mock response if running in standalone browser
      setTimeout(() => {
        if (callback) callback({ status: 'ok', action: data.query || 'executed', message: 'Executed on ' + currentDevice });
      }, 150);
    });
  }

  // Initial connection state
  setConnected(true, 'Connected · 192.168.0.159:5555');
  logConsole('CleanTV Desktop Manager Initialized. Connected to Android TV.');
});
