const RECAP_BASE = '/api/recaps'
const TRADE_JOURNAL_BASE = '/api/trade-journal'
const USER_STOCK_ANALYSIS_BASE = '/api/user-stock-analysis'
const STOCK_AI_ANALYSIS_BASE = '/api/stock-ai-analysis'
const AI_CHAT_BASE = '/api/ai-chat'
const memoryCache = new Map()

function buildCacheKey(url, options = {}) {
  return JSON.stringify({
    url,
    method: options.method || 'GET'
  })
}

function invalidateCache(prefixes = []) {
  for (const key of memoryCache.keys()) {
    if (prefixes.some(prefix => key.includes(prefix))) {
      memoryCache.delete(key)
    }
  }
}

async function request(url, options = {}) {
  const headers = options.body instanceof FormData
    ? options.headers || {}
    : { 'Content-Type': 'application/json', ...(options.headers || {}) }

  const response = await fetch(url, { ...options, headers })
  if (!response.ok) {
    const text = await response.text()
    let message = text
    try {
      const payload = JSON.parse(text)
      message = payload.message || payload.error || text
    } catch (_) {
      message = text
    }
    throw new Error(message || `请求失败: ${response.status}`)
  }

  const contentType = response.headers.get('content-type') || ''
  if (contentType.includes('application/json')) {
    return response.json()
  }
  return response.text()
}

function cachedGet(url, { refresh = false } = {}) {
  const key = buildCacheKey(url)
  if (refresh) {
    memoryCache.delete(key)
  } else if (memoryCache.has(key)) {
    return memoryCache.get(key)
  }

  const pending = request(url)
    .then(result => {
      memoryCache.set(key, Promise.resolve(result))
      return result
    })
    .catch(error => {
      memoryCache.delete(key)
      throw error
    })

  memoryCache.set(key, pending)
  return pending
}

export function listRecaps() {
  return cachedGet(RECAP_BASE)
}

export function getRecap(tradeDate) {
  return cachedGet(`${RECAP_BASE}/${tradeDate}`)
}

export function captureRecap(tradeDate) {
  return request(`${RECAP_BASE}/capture`, {
    method: 'POST',
    body: JSON.stringify({ tradeDate })
  }).then(result => {
    invalidateCache([RECAP_BASE])
    return result
  })
}

export function getAiSummary(tradeDate, refresh = false) {
  return cachedGet(`${RECAP_BASE}/${tradeDate}/ai-summary?refresh=${refresh}`, { refresh })
}

export function getAiInsight(tradeDate, refresh = false) {
  return cachedGet(`${RECAP_BASE}/${tradeDate}/ai-insight?refresh=${refresh}`, { refresh })
}

export function getAiBriefing(tradeDate, refresh = false) {
  return cachedGet(`${RECAP_BASE}/${tradeDate}/ai-briefing?refresh=${refresh}`, { refresh })
}

export function getMarketIntelligence(tradeDate, refresh = false) {
  return cachedGet(`${RECAP_BASE}/${tradeDate}/market-intelligence?refresh=${refresh}`, { refresh })
}

export function listTradeJournal() {
  return cachedGet(TRADE_JOURNAL_BASE)
}

export function importTradeJournal(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request(`${TRADE_JOURNAL_BASE}/import`, {
    method: 'POST',
    body: formData
  }).then(result => {
    invalidateCache([TRADE_JOURNAL_BASE])
    return result
  })
}

export function analyzeUserStockImages(files) {
  const formData = new FormData()
  files.forEach(file => formData.append('files', file))
  return request(`${USER_STOCK_ANALYSIS_BASE}/analyze`, {
    method: 'POST',
    body: formData
  })
}

export function analyzeStockWithAi(file, timeframe, stockCode = '', stockName = '') {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('timeframe', timeframe)
  if (stockCode) formData.append('stockCode', stockCode)
  if (stockName) formData.append('stockName', stockName)
  return request(STOCK_AI_ANALYSIS_BASE, {
    method: 'POST',
    body: formData
  })
}

export function askAiChat(history, message) {
  return request(AI_CHAT_BASE, {
    method: 'POST',
    body: JSON.stringify({ history, message })
  })
}
