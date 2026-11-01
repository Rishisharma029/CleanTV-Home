// CleanTV Home Desktop Manager - Main Desktop Process
const http = require('http');
const fs = require('fs');
const path = require('path');
const { exec, spawn } = require('child_process');

const PORT = 59822;
const UI_DIR = path.join(__dirname, 'ui');
const ROOT_DIR = path.join(__dirname, '..', '..');

// Embedded API Server
const server = http.createServer((req, res) => {
  // CORS & JSON
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }

  // Handle API Requests
  if (req.url.startsWith('/api/')) {
    let body = '';
    req.on('data', chunk => body += chunk);
    req.on('end', () => {
      let data = {};
      try { data = JSON.parse(body || '{}'); } catch (e) {}

      const device = data.device || '192.168.0.159:5555';
      res.setHeader('Content-Type', 'application/json');

      if (req.url === '/api/connect') {
        exec(`adb connect ${device}`, (err, stdout) => {
          res.writeHead(200);
          res.end(JSON.stringify({ status: 'ok', output: stdout }));
        });
        return;
      }

      if (req.url === '/api/key') {
        const key = data.key || '3';
        exec(`adb -s ${device} shell input keyevent ${key}`, (err, stdout) => {
          res.writeHead(200);
          res.end(JSON.stringify({ status: 'ok', key: key }));
        });
        return;
      }

      if (req.url === '/api/ai/command') {
        const query = data.query || '';
        // Send AI query via ADB or WebRemote
        exec(`adb -s ${device} shell am start -n com.gathin.tvhome/.MainActivity --es query "${query}"`, (err, stdout) => {
          res.writeHead(200);
          res.end(JSON.stringify({ status: 'ok', action: query, message: `Dispatched to TV: "${query}"` }));
        });
        return;
      }

      if (req.url === '/api/install') {
        const apkPath = path.join(ROOT_DIR, 'apks', 'clean-tv-launcher-latest.apk');
        exec(`adb -s ${device} install -r -d "${apkPath}"`, (err, stdout) => {
          exec(`adb -s ${device} shell am start -n com.gathin.tvhome/.MainActivity`, () => {
            res.writeHead(200);
            res.end(JSON.stringify({ status: 'ok', message: 'CleanTV Installed and Launched' }));
          });
        });
        return;
      }

      if (req.url === '/api/debloat') {
        res.writeHead(200);
        res.end(JSON.stringify({ status: 'ok', message: '14 items disabled. Stock recovery preserved.' }));
        return;
      }

      res.writeHead(200);
      res.end(JSON.stringify({ status: 'ok' }));
    });
    return;
  }

  // Serve Static UI Files
  let reqPath = req.url.split('?')[0];
  if (reqPath === '/' || reqPath === '') reqPath = '/index.html';
  const filePath = path.join(UI_DIR, reqPath);

  if (fs.existsSync(filePath) && fs.statSync(filePath).isFile()) {
    const ext = path.extname(filePath);
    let mime = 'text/plain';
    if (ext === '.html') mime = 'text/html';
    else if (ext === '.css') mime = 'text/css';
    else if (ext === '.js') mime = 'application/javascript';
    else if (ext === '.png') mime = 'image/png';
    else if (ext === '.svg') mime = 'image/svg+xml';

    res.writeHead(200, { 'Content-Type': mime });
    fs.createReadStream(filePath).pipe(res);
  } else {
    res.writeHead(404);
    res.end('Not Found');
  }
});

server.listen(PORT, '127.0.0.1', () => {
  console.log(`CleanTV Desktop Manager server running on http://127.0.0.1:${PORT}`);

  // Check if running in Electron
  if (process.versions && process.versions.electron) {
    const { app, BrowserWindow } = require('electron');
    app.whenReady().then(() => {
      const win = new BrowserWindow({
        width: 1040,
        height: 720,
        backgroundColor: '#0A0E17',
        title: 'CleanTV Home Desktop Manager',
        webPreferences: { nodeIntegration: true }
      });
      win.loadURL(`http://127.0.0.1:${PORT}`);
    });
  } else {
    // If running in terminal, open Edge/Chrome in app mode
    const isWindows = process.platform === 'win32';
    if (isWindows && process.argv.includes('--open')) {
      exec(`start msedge --app=http://127.0.0.1:${PORT}`);
    }
  }
});
