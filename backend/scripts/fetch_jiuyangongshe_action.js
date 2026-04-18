#!/usr/bin/env node

const fs = require('fs');
const os = require('os');
const path = require('path');

let chromium;
try {
  ({ chromium } = require('playwright'));
} catch (error) {
  console.error(JSON.stringify({
    ok: false,
    error: `playwright unavailable: ${error.message}`,
  }));
  process.exit(1);
}

const SCRIPT_DIR = __dirname;
const BACKEND_DIR = path.resolve(SCRIPT_DIR, '..');
const PROFILE_DIR = process.env.JIUYANGONGSHE_PROFILE_DIR || path.join(BACKEND_DIR, '.cache', 'jiuyangongshe-profile');
const DEFAULT_LOGIN_URL = 'https://www.jiuyangongshe.com/action';
const DEFAULT_DATE = new Date().toISOString().slice(0, 10);
const CHROME_CANDIDATES = [
  process.env.JIUYANGONGSHE_CHROME,
  process.env.CHROME_PATH,
  'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
  'C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe',
];

function parseArgs(argv) {
  const result = {
    mode: 'fetch',
    date: DEFAULT_DATE,
    headless: true,
    autoLogin: false,
    timeoutMs: 120000,
  };

  for (let i = 0; i < argv.length; i += 1) {
    const token = argv[i];
    if (token === '--mode' && argv[i + 1]) {
      result.mode = argv[i + 1];
      i += 1;
    } else if (token === '--date' && argv[i + 1]) {
      result.date = argv[i + 1];
      i += 1;
    } else if (token === '--headed') {
      result.headless = false;
    } else if (token === '--headless') {
      result.headless = true;
    } else if (token === '--timeout-ms' && argv[i + 1]) {
      result.timeoutMs = Number(argv[i + 1]) || result.timeoutMs;
      i += 1;
    } else if (token === '--auto-login') {
      result.autoLogin = true;
    }
  }

  return result;
}

function resolveChromeExecutable() {
  for (const candidate of CHROME_CANDIDATES) {
    if (candidate && fs.existsSync(candidate)) {
      return candidate;
    }
  }
  throw new Error('Chrome executable not found. Set JIUYANGONGSHE_CHROME or install Chrome.');
}

async function waitFor(predicate, timeoutMs, intervalMs = 250) {
  const started = Date.now();
  while (Date.now() - started < timeoutMs) {
    // eslint-disable-next-line no-await-in-loop
    const value = await predicate();
    if (value) {
      return value;
    }
    // eslint-disable-next-line no-await-in-loop
    await new Promise((resolve) => setTimeout(resolve, intervalMs));
  }
  throw new Error(`Timed out after ${timeoutMs}ms`);
}

function shouldSkipProfileEntry(sourcePath) {
  const normalized = sourcePath.replace(/\\/g, '/');
  const base = path.basename(sourcePath);
  if (['SingletonLock', 'SingletonCookie', 'SingletonSocket', 'LOCK', 'lockfile'].includes(base)) {
    return true;
  }
  return [
    '/Crashpad',
    '/BrowserMetrics',
    '/component_crx_cache',
    '/extensions_crx_cache',
    '/GraphiteDawnCache',
    '/GrShaderCache',
    '/ShaderCache',
    '/segmentation_platform',
    '/Default/Cache',
    '/Default/Code Cache',
    '/Default/GPUCache',
    '/Default/DawnGraphiteCache',
    '/Default/DawnWebGPUCache',
  ].some((fragment) => normalized.includes(fragment));
}

function createProfileSnapshot() {
  fs.mkdirSync(PROFILE_DIR, { recursive: true });
  const snapshotDir = fs.mkdtempSync(path.join(os.tmpdir(), 'jygs-profile-'));
  fs.cpSync(PROFILE_DIR, snapshotDir, {
    recursive: true,
    force: true,
    filter: (source) => !shouldSkipProfileEntry(source),
  });
  return snapshotDir;
}

async function launchPersistent(headless, profileDir = PROFILE_DIR) {
  fs.mkdirSync(profileDir, { recursive: true });
  return chromium.launchPersistentContext(profileDir, {
    headless,
    executablePath: resolveChromeExecutable(),
    args: ['--disable-blink-features=AutomationControlled'],
  });
}

async function hasValidSession(context) {
  const cookies = await context.cookies([
    'https://www.jiuyangongshe.com',
    'https://app.jiuyangongshe.com',
  ]);
  const now = Date.now() / 1000;
  return cookies.some((cookie) => {
    if (cookie.name !== 'SESSION') {
      return false;
    }
    if (!cookie.value) {
      return false;
    }
    return cookie.expires === -1 || cookie.expires === 0 || cookie.expires > now;
  });
}

async function setupLogin(timeoutMs) {
  const context = await launchPersistent(false);
  const page = context.pages()[0] || await context.newPage();
  await page.goto(DEFAULT_LOGIN_URL, { waitUntil: 'networkidle', timeout: timeoutMs });

  await waitFor(() => hasValidSession(context), timeoutMs, 1000);
  await context.close();

  return {
    ok: true,
    mode: 'login',
    profileDir: PROFILE_DIR,
    message: 'jiuyangongshe session saved',
  };
}

function isPotentialStockName(value) {
  if (typeof value !== 'string') {
    return false;
  }
  const text = value.trim();
  if (!text || text.length < 2 || text.length > 16) {
    return false;
  }
  if (!/[\u4e00-\u9fff]/.test(text)) {
    return false;
  }
  if (/^(题材|展开板块|收起板块|异动|全部|原发|转发|短文|登录|返回今日|保存图片|分享到微信)$/.test(text)) {
    return false;
  }
  return true;
}

function sanitizeReason(text, stockName = '') {
  if (typeof text !== 'string') {
    return '';
  }
  let value = text.replace(/\s+/g, ' ').trim();
  if (!value) {
    return '';
  }
  if (stockName) {
    const escaped = stockName.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    value = value.replace(new RegExp(`^${escaped}[：: ]*`), '');
    value = value.replace(new RegExp(`[，, ]*${escaped}[，, ]*触及涨停$`), '');
  }
  value = value.replace(/^题材[：:]\s*/, '');
  value = value.replace(/\s*触及涨停\s*$/, '');
  value = value.replace(/^\d+\s*[:：]\s*/, '');
  if (/^\d+\s*\/\s*\d+$/.test(value)) {
    return '';
  }
  if (/^(上一页|下一页|展开|收起|查看全文|全文|详情)$/.test(value)) {
    return '';
  }
  if (value.length <= 4 && !/[\u4e00-\u9fffA-Za-z]/.test(value)) {
    return '';
  }
  if (value.length > 120) {
    value = value.slice(0, 120);
  }
  return value.trim();
}

function looksLikeStockLabel(text, stockName = '') {
  if (!text) {
    return true;
  }
  const normalized = text.replace(/\s+/g, '');
  const normalizedName = String(stockName || '').replace(/\s+/g, '');
  if (normalizedName && (normalized === normalizedName || normalized === `${normalizedName}触及涨停`)) {
    return true;
  }
  return /^[A-Za-z]{2}\d{6}$/.test(normalized) || /^\d{6}$/.test(normalized);
}

function firstTextCandidate(item, stockName = '') {
  if (!item || typeof item !== 'object') {
    return '';
  }

  const preferredKeys = [
    'reason',
    'summary',
    'analysis',
    'parse',
    'interpretation',
    'content',
    'desc',
    'introduction',
    'article_title',
    'title',
  ];
  for (const key of preferredKeys) {
    if (typeof item[key] === 'string' && item[key].trim()) {
      const candidate = sanitizeReason(item[key].trim(), stockName);
      if (candidate && !looksLikeStockLabel(candidate, stockName)) {
        return candidate;
      }
    }
  }
  return '';
}

function collectStockNames(value, bucket = new Set(), parentKey = '') {
  if (value == null) {
    return bucket;
  }
  if (Array.isArray(value)) {
    value.forEach((item) => collectStockNames(item, bucket, parentKey));
    return bucket;
  }
  if (typeof value === 'object') {
    for (const [key, child] of Object.entries(value)) {
      const lower = key.toLowerCase();
      if (typeof child === 'string') {
        if ((lower.includes('stock') && lower.includes('name')) || lower === 'name') {
          if (isPotentialStockName(child)) {
            bucket.add(child.trim());
          }
        }
      } else {
        collectStockNames(child, bucket, lower || parentKey);
      }
    }
    return bucket;
  }
  return bucket;
}

function collectStockCodes(value, bucket = new Set()) {
  if (value == null) {
    return bucket;
  }
  if (Array.isArray(value)) {
    value.forEach((item) => collectStockCodes(item, bucket));
    return bucket;
  }
  if (typeof value === 'object') {
    for (const [key, child] of Object.entries(value)) {
      const lower = key.toLowerCase();
      if (typeof child === 'string' || typeof child === 'number') {
        const text = String(child).trim();
        if ((lower.includes('stock') || lower.includes('code') || lower.endsWith('id')) && /^\d{6}$/.test(text)) {
          bucket.add(text);
        }
      } else {
        collectStockCodes(child, bucket);
      }
    }
    return bucket;
  }
  return bucket;
}

function buildReasonEntries(fields) {
  const entries = [];
  for (const field of fields || []) {
    const fieldReason = sanitizeReason(field && field.reason ? String(field.reason) : '');
    const fieldName = field && typeof field.name === 'string' ? field.name.trim() : '';
    const list = Array.isArray(field && field.list) ? field.list : [];
    for (const item of list) {
      const stockNames = Array.from(collectStockNames(item));
      const stockCodes = Array.from(collectStockCodes(item));
      for (const stockName of stockNames) {
        const itemReason = firstTextCandidate(item, stockName);
        const finalReason = itemReason || fieldReason || fieldName;
        if (!finalReason) {
          continue;
        }
        entries.push({
          stockName,
          stockCode: stockCodes[0] || '',
          reason: finalReason,
          fieldName,
          fieldReason,
        });
      }
    }
  }
  return entries;
}

async function fetchActionFields(date, timeoutMs) {
  const snapshotDir = createProfileSnapshot();
  const context = await launchPersistent(true, snapshotDir);
  try {
    const page = context.pages()[0] || await context.newPage();
    await page.goto(`https://www.jiuyangongshe.com/action/${date}`, {
      waitUntil: 'networkidle',
      timeout: timeoutMs,
    });
    const currentUrl = page.url();
    if (/login|signin|passport/i.test(currentUrl)) {
      throw new Error('jiuyangongshe session missing or expired; run login mode first');
    }

    const payload = await page.evaluate(async ({ tradeDate }) => {
      const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

      function findActionVm(vm) {
        if (!vm) {
          return null;
        }
        if (vm._data && Object.prototype.hasOwnProperty.call(vm._data, 'activeName')
          && Object.prototype.hasOwnProperty.call(vm._data, 'actionFieldList')
          && vm.$options && vm.$options.methods && typeof vm.$options.methods.initData === 'function') {
          return vm;
        }
        for (const child of (vm.$children || [])) {
          const found = findActionVm(child);
          if (found) {
            return found;
          }
        }
        return null;
      }

      async function waitFor(check, timeoutMsInner = 15000) {
        const started = Date.now();
        while (Date.now() - started < timeoutMsInner) {
          if (check()) {
            return true;
          }
          // eslint-disable-next-line no-await-in-loop
          await sleep(250);
        }
        return false;
      }

      const vm = findActionVm(window.$nuxt);
      if (!vm) {
        throw new Error('action vue instance not found');
      }

      vm.activeName = 'all';
      vm.selectDate = tradeDate;
      vm.apiDate = tradeDate;
      vm.initData();

      const hasFields = await waitFor(() => Array.isArray(vm.actionFieldList) && vm.actionFieldList.length > 0, 20000);
      if (!hasFields) {
        throw new Error('actionFieldList not loaded');
      }

      for (let index = 0; index < vm.actionFieldList.length; index += 1) {
        const field = vm.actionFieldList[index];
        if (!field || !field.action_field_id) {
          // eslint-disable-next-line no-continue
          continue;
        }
        vm.listAction(field.action_field_id, index);
        // eslint-disable-next-line no-await-in-loop
        await waitFor(() => Array.isArray(vm.actionFieldList[index].list), 15000);
      }

      return JSON.parse(JSON.stringify(vm.actionFieldList));
    }, { tradeDate: date });

    const entries = buildReasonEntries(payload);
    return {
      ok: true,
      mode: 'fetch',
      date,
      profileDir: snapshotDir,
      entryCount: entries.length,
      entries,
    };
  } finally {
    await context.close();
    fs.rmSync(snapshotDir, { recursive: true, force: true });
  }
}

async function fetchWithAutoLogin(date, timeoutMs, autoLogin) {
  try {
    return await fetchActionFields(date, timeoutMs);
  } catch (error) {
    if (!autoLogin || !String(error.message || '').includes('session missing or expired')) {
      throw error;
    }
    await setupLogin(Math.max(timeoutMs, 600000));
    return fetchActionFields(date, timeoutMs);
  }
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const result = args.mode === 'login'
    ? await setupLogin(args.timeoutMs)
    : await fetchWithAutoLogin(args.date, args.timeoutMs, args.autoLogin);
  process.stdout.write(`${JSON.stringify(result)}\n`);
}

main().catch((error) => {
  process.stderr.write(`${JSON.stringify({
    ok: false,
    error: error.message,
  })}\n`);
  process.exit(1);
});
