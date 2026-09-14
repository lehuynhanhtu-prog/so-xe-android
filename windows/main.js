const { app, BrowserWindow, dialog, shell } = require('electron');
const fs = require('fs');
const http = require('http');
const path = require('path');

const PORT = 18765;
const HOST = '127.0.0.1';
const APP_ORIGIN = `http://${HOST}:${PORT}`;
const WEB_ROOT = path.join(__dirname, 'www');

const MIME = {
  '.css': 'text/css; charset=utf-8',
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.webmanifest': 'application/manifest+json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.mobileconfig': 'application/x-apple-aspen-config'
};

let mainWindow;
let localServer;

app.setPath('userData', path.join(app.getPath('appData'), 'SoXeData'));

function safeFilePath(requestUrl) {
  const pathname = decodeURIComponent(new URL(requestUrl, APP_ORIGIN).pathname);
  const relative = pathname === '/' ? 'index.html' : pathname.replace(/^\/+/, '');
  const resolved = path.resolve(WEB_ROOT, relative);
  return resolved === WEB_ROOT || resolved.startsWith(WEB_ROOT + path.sep) ? resolved : null;
}

function startLocalServer() {
  return new Promise((resolve, reject) => {
    localServer = http.createServer((request, response) => {
      const filePath = safeFilePath(request.url || '/');
      if (!filePath) {
        response.writeHead(403).end('Forbidden');
        return;
      }
      fs.stat(filePath, (statError, stats) => {
        const target = !statError && stats.isDirectory() ? path.join(filePath, 'index.html') : filePath;
        fs.readFile(target, (error, content) => {
          if (error) {
            response.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
            response.end('Không tìm thấy tài nguyên');
            return;
          }
          response.writeHead(200, {
            'Content-Type': MIME[path.extname(target).toLowerCase()] || 'application/octet-stream',
            'Cache-Control': 'no-cache',
            'X-Content-Type-Options': 'nosniff',
            'Service-Worker-Allowed': '/'
          });
          response.end(content);
        });
      });
    });
    localServer.once('error', reject);
    localServer.listen(PORT, HOST, resolve);
  });
}

function isGoogleOAuth(url) {
  try {
    const hostname = new URL(url).hostname;
    return hostname === 'accounts.google.com' || hostname.endsWith('.googleusercontent.com');
  } catch {
    return false;
  }
}

async function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1280,
    height: 820,
    minWidth: 900,
    minHeight: 620,
    show: false,
    backgroundColor: '#f2f6f3',
    icon: path.join(WEB_ROOT, 'icons', 'icon-512.png'),
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
      sandbox: true
    }
  });

  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    if (url.startsWith(APP_ORIGIN) || isGoogleOAuth(url)) {
      return {
        action: 'allow',
        overrideBrowserWindowOptions: {
          width: 520,
          height: 720,
          autoHideMenuBar: true,
          webPreferences: { nodeIntegration: false, contextIsolation: true, sandbox: true }
        }
      };
    }
    shell.openExternal(url);
    return { action: 'deny' };
  });

  mainWindow.webContents.on('will-navigate', (event, url) => {
    if (!url.startsWith(APP_ORIGIN)) {
      event.preventDefault();
      shell.openExternal(url);
    }
  });

  mainWindow.once('ready-to-show', () => mainWindow.show());
  await mainWindow.loadURL(APP_ORIGIN);
  mainWindow.on('closed', () => { mainWindow = null; });
}

const hasLock = app.requestSingleInstanceLock();
if (!hasLock) {
  app.quit();
} else {
  app.on('second-instance', () => {
    if (mainWindow) {
      if (mainWindow.isMinimized()) mainWindow.restore();
      mainWindow.focus();
    }
  });

  app.whenReady().then(async () => {
    try {
      await startLocalServer();
      await createWindow();
    } catch (error) {
      dialog.showErrorBox('Không thể mở Sổ Xe', `Cổng nội bộ ${PORT} đang được sử dụng hoặc ứng dụng bị lỗi.\n\n${error.message}`);
      app.quit();
    }
  });
}

app.on('window-all-closed', () => app.quit());
app.on('before-quit', () => localServer?.close());
