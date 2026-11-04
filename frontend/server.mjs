import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const PORT = parseInt(process.env.PORT || process.env.FRONTEND_HTTP_PORT || '80', 10);
const HOST = process.env.HOST || '0.0.0.0';
const DIST_DIR = path.resolve(__dirname, process.env.DIST_DIR || 'dist/wwtrading/browser');

// Proxy targets (internal Docker service hostnames by default)
const API_GATEWAY_TARGET = process.env.API_GATEWAY_URL || 'http://api-gateway:8080';
const KEYCLOAK_TARGET = process.env.KEYCLOAK_INTERNAL_URL || 'http://keycloak:8080';
const ENABLE_PROXY = process.env.ENABLE_INTERNAL_PROXY !== 'false';

const MIME_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif': 'image/gif',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.webp': 'image/webp',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.ttf': 'font/ttf',
  '.eot': 'application/vnd.ms-fontobject',
  '.otf': 'font/otf',
  '.xml': 'application/xml; charset=utf-8',
  '.txt': 'text/plain; charset=utf-8',
  '.webmanifest': 'application/manifest+json; charset=utf-8'
};

function ensureRuntimeConfig() {
  const configDir = path.join(DIST_DIR, 'config');
  if (!fs.existsSync(configDir)) {
    fs.mkdirSync(configDir, { recursive: true });
  }
  const configPath = path.join(configDir, 'runtime-config.json');

  let existing = {};
  if (fs.existsSync(configPath)) {
    try {
      existing = JSON.parse(fs.readFileSync(configPath, 'utf8'));
    } catch {
      existing = {};
    }
  }

  // Same-origin default: when API_BASE_URL is empty, browser requests use relative /api/
  const apiBaseUrl = process.env.API_BASE_URL !== undefined
    ? process.env.API_BASE_URL
    : (process.env.FRONTEND_API_BASE_URL || '');

  const siteUrl = process.env.SITE_URL || process.env.FRONTEND_PUBLIC_URL || existing.siteUrl || 'http://74.248.151.43';
  const trackingBaseUrl = process.env.TRACKING_BASE_URL || existing.trackingBaseUrl || siteUrl;
  const keycloakUrl = process.env.KEYCLOAK_URL || process.env.KEYCLOAK_PUBLIC_URL || existing.keycloak?.url || siteUrl;

  const runtimeConfig = {
    apiBaseUrl: apiBaseUrl,
    siteUrl: siteUrl,
    trackingBaseUrl: trackingBaseUrl,
    googleSiteVerification: process.env.GOOGLE_SITE_VERIFICATION ?? existing.googleSiteVerification ?? '',
    googleAnalyticsMeasurementId: process.env.GOOGLE_ANALYTICS_MEASUREMENT_ID ?? existing.googleAnalyticsMeasurementId ?? '',
    keycloak: {
      url: keycloakUrl,
      realm: process.env.KEYCLOAK_REALM || existing.keycloak?.realm || 'wwtrading',
      clientId: process.env.KEYCLOAK_CLIENT_ID || existing.keycloak?.clientId || 'wwtrading-web'
    }
  };

  fs.writeFileSync(configPath, JSON.stringify(runtimeConfig, null, 2), 'utf8');
  console.log('[WWTRADING-FRONTEND] Runtime configuration initialized at:', configPath);
  console.log(JSON.stringify(runtimeConfig, null, 2));
}

function proxyRequest(req, res, targetUrlStr) {
  const target = new URL(targetUrlStr);
  const isHttps = target.protocol === 'https:';

  // Validate external protocol: trust Cloudflare edge headers or standard forwarded proto
  const isExternalHttps = Boolean(
    req.headers['cf-ray'] ||
    req.headers['cf-visitor']?.includes('https') ||
    req.headers['x-forwarded-proto'] === 'https' ||
    req.socket.encrypted
  );
  const extProto = isExternalHttps ? 'https' : (req.headers['x-forwarded-proto'] || 'http');

  // Extract clean host and port from incoming request
  const rawHost = req.headers['x-forwarded-host'] || req.headers.host || '';
  const [extHost, explicitPort] = rawHost.split(':');
  const extPort = explicitPort || (extProto === 'https' ? '443' : '80');

  const clientIp = req.headers['cf-connecting-ip'] ||
    (req.headers['x-forwarded-for']
      ? `${req.headers['x-forwarded-for']}, ${req.socket.remoteAddress}`
      : req.socket.remoteAddress);

  const headers = {
    ...req.headers,
    host: target.host,
    'x-real-ip': req.headers['cf-connecting-ip'] || req.socket.remoteAddress,
    'x-forwarded-for': clientIp,
    'x-forwarded-proto': extProto,
    'x-forwarded-host': extHost || target.hostname,
    'x-forwarded-port': String(extPort)
  };

  const options = {
    hostname: target.hostname,
    port: target.port || (isHttps ? 443 : 80),
    path: req.url,
    method: req.method,
    headers: headers,
    timeout: 60000
  };

  const proxyReq = http.request(options, (proxyRes) => {
    res.writeHead(proxyRes.statusCode, proxyRes.headers);
    proxyRes.pipe(res, { end: true });
  });

  proxyReq.on('error', (err) => {
    console.error(`[WWTRADING-PROXY-ERROR] ${req.method} ${req.url} -> ${targetUrlStr}:`, err.message);
    if (!res.headersSent) {
      res.writeHead(502, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: 'Bad Gateway', message: err.message, path: req.url }));
    }
  });

  proxyReq.on('timeout', () => {
    proxyReq.destroy();
    if (!res.headersSent) {
      res.writeHead(504, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: 'Gateway Timeout', path: req.url }));
    }
  });

  req.pipe(proxyReq, { end: true });
}

function serveStatic(req, res) {
  const parsedUrl = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  let pathname = decodeURIComponent(parsedUrl.pathname);

  let safePath = path.normalize(path.join(DIST_DIR, pathname));
  if (!safePath.startsWith(DIST_DIR)) {
    res.writeHead(403, { 'Content-Type': 'text/plain' });
    res.end('Forbidden');
    return;
  }

  let stat;
  try {
    stat = fs.statSync(safePath);
    if (stat.isDirectory()) {
      safePath = path.join(safePath, 'index.html');
      stat = fs.statSync(safePath);
    }
  } catch {
    // SPA fallback: any non-file request falls back to index.html
    safePath = path.join(DIST_DIR, 'index.html');
    try {
      stat = fs.statSync(safePath);
    } catch {
      res.writeHead(404, { 'Content-Type': 'text/plain' });
      res.end('Not Found');
      return;
    }
  }

  const ext = path.extname(safePath).toLowerCase();
  const contentType = MIME_TYPES[ext] || 'application/octet-stream';

  const headers = {
    'Content-Type': contentType,
    'Content-Length': stat.size,
    'X-Content-Type-Options': 'nosniff',
    'X-Frame-Options': 'SAMEORIGIN'
  };

  if (safePath.endsWith('index.html') || safePath.endsWith('runtime-config.json')) {
    headers['Cache-Control'] = 'no-store, no-cache, must-revalidate, max-age=0';
  } else if (/[-.][a-f0-9]{8,}\.(js|css|png|jpg|svg|woff2?)$/i.test(safePath)) {
    headers['Cache-Control'] = 'public, max-age=31536000, immutable';
  }

  res.writeHead(200, headers);
  fs.createReadStream(safePath).pipe(res);
}

try {
  ensureRuntimeConfig();
} catch (e) {
  console.warn('[WWTRADING-FRONTEND] Warning: could not initialize runtime config on startup:', e.message);
}

const server = http.createServer((req, res) => {
  // Healthcheck endpoint
  if (req.url === '/healthz' || req.url === '/health') {
    res.writeHead(200, { 'Content-Type': 'text/plain; charset=utf-8' });
    res.end('healthy\n');
    return;
  }

  // Dynamic runtime-config.json to adapt to current origin (Cloudflare tunnel, public IP, localhost)
  if (req.url === '/config/runtime-config.json' || req.url?.startsWith('/config/runtime-config.json?')) {
    const isExternalHttps = Boolean(
      req.headers['cf-ray'] ||
      req.headers['cf-visitor']?.includes('https') ||
      req.headers['x-forwarded-proto'] === 'https' ||
      req.socket.encrypted
    );
    const extProto = isExternalHttps ? 'https' : (req.headers['x-forwarded-proto'] || 'http');
    const rawHost = req.headers['x-forwarded-host'] || req.headers.host || '74.248.151.43';
    const currentOrigin = `${extProto}://${rawHost}`;

    const dynamicConfig = {
      apiBaseUrl: process.env.API_BASE_URL !== undefined
        ? process.env.API_BASE_URL
        : (process.env.FRONTEND_API_BASE_URL || ''),
      siteUrl: isExternalHttps ? currentOrigin : (process.env.SITE_URL || currentOrigin),
      trackingBaseUrl: isExternalHttps ? currentOrigin : (process.env.TRACKING_BASE_URL || process.env.SITE_URL || currentOrigin),
      googleSiteVerification: process.env.GOOGLE_SITE_VERIFICATION || '',
      googleAnalyticsMeasurementId: process.env.GOOGLE_ANALYTICS_MEASUREMENT_ID || '',
      keycloak: {
        url: isExternalHttps ? currentOrigin : (process.env.KEYCLOAK_PUBLIC_URL || currentOrigin),
        realm: process.env.KEYCLOAK_REALM || 'wwtrading',
        clientId: process.env.KEYCLOAK_CLIENT_ID || 'wwtrading-web'
      }
    };

    res.writeHead(200, {
      'Content-Type': 'application/json; charset=utf-8',
      'Cache-Control': 'no-store, no-cache, must-revalidate, max-age=0',
      'X-Content-Type-Options': 'nosniff',
      'X-Frame-Options': 'SAMEORIGIN'
    });
    res.end(JSON.stringify(dynamicConfig, null, 2));
    return;
  }

  // Same-origin reverse proxies
  if (ENABLE_PROXY) {
    if (req.url.startsWith('/api/')) {
      return proxyRequest(req, res, API_GATEWAY_TARGET);
    }
    if (
      req.url === '/admin' ||
      req.url.startsWith('/admin/') ||
      req.url.startsWith('/admin?') ||
      req.url.startsWith('/realms/') ||
      req.url.startsWith('/resources/')
    ) {
      return proxyRequest(req, res, KEYCLOAK_TARGET);
    }
  }

  // Static SPA delivery
  serveStatic(req, res);
});

// WebSocket support for API Gateway (e.g. support chat / live notifications)
server.on('upgrade', (req, clientSocket, head) => {
  if (ENABLE_PROXY && req.url.startsWith('/api/')) {
    const target = new URL(API_GATEWAY_TARGET);
    const options = {
      hostname: target.hostname,
      port: target.port || 80,
      path: req.url,
      method: req.method,
      headers: {
        ...req.headers,
        host: target.host
      }
    };

    const proxyReq = http.request(options);
    proxyReq.on('upgrade', (proxyRes, proxySocket, proxyHead) => {
      clientSocket.write(
        `HTTP/1.1 101 Switching Protocols\r\n` +
        Object.entries(proxyRes.headers)
          .map(([k, v]) => `${k}: ${v}`)
          .join('\r\n') +
        '\r\n\r\n'
      );
      if (proxyHead && proxyHead.length) clientSocket.write(proxyHead);
      proxySocket.pipe(clientSocket);
      clientSocket.pipe(proxySocket);
    });

    proxyReq.on('error', (err) => {
      console.error('[WWTRADING-WS-ERROR]', err.message);
      clientSocket.destroy();
    });

    proxyReq.end();
  } else {
    clientSocket.destroy();
  }
});

server.listen(PORT, HOST, () => {
  console.log(`[WWTRADING-FRONTEND] Node.js production server listening on http://${HOST}:${PORT}`);
  console.log(`[WWTRADING-FRONTEND] Dist Directory: ${DIST_DIR}`);
  console.log(`[WWTRADING-FRONTEND] Internal API Gateway: ${API_GATEWAY_TARGET}`);
  console.log(`[WWTRADING-FRONTEND] Internal Keycloak: ${KEYCLOAK_TARGET}`);
  console.log(`[WWTRADING-FRONTEND] Proxy Enabled: ${ENABLE_PROXY}`);
});

