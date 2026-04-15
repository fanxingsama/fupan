const RECAP_BASE = '/api/recaps'
const TRADE_JOURNAL_BASE = '/api/trade-journal'

async function request(url, options = {}) {
  const headers = options.body instanceof FormData
    ? options.headers || {}
    : { 'Content-Type': 'application/json', ...(options.headers || {}) }

  const response = await fetch(url, { ...options, headers })
  if (!response.ok) {
    const text = await response.text()
    throw new Error(text || `请求失败: ${response.status}`)
  }

  const contentType = response.headers.get('content-type') || ''
  if (contentType.includes('application/json')) {
    return response.json()
  }
  return response.text()
}

export function listRecaps() {
  return request(RECAP_BASE)
}

export function getRecap(tradeDate) {
  return request(`${RECAP_BASE}/${tradeDate}`)
}

export function captureRecap(tradeDate) {
  return request(`${RECAP_BASE}/capture`, {
    method: 'POST',
    body: JSON.stringify({ tradeDate })
  })
}

export function getAiSummary(tradeDate, refresh = false) {
  return request(`${RECAP_BASE}/${tradeDate}/ai-summary?refresh=${refresh}`)
}

export function listTradeJournal() {
  return request(TRADE_JOURNAL_BASE)
}

export function importTradeJournal(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request(`${TRADE_JOURNAL_BASE}/import`, {
    method: 'POST',
    body: formData
  })
}
