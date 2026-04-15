export function toBoardHeight(value) {
  const numeric = Number(value)
  return Number.isFinite(numeric) ? numeric : 0
}

export function parseNumericValue(value) {
  if (value === null || value === undefined || value === '') return null
  if (typeof value === 'number') return Number.isFinite(value) ? value : null

  const text = String(value).trim()
  if (!text || text === '-') return null

  if (text.endsWith('%')) {
    const numeric = Number(text.replace('%', ''))
    return Number.isFinite(numeric) ? numeric : null
  }
  if (text.endsWith('亿')) {
    const numeric = Number(text.replace('亿', ''))
    return Number.isFinite(numeric) ? numeric * 100000000 : null
  }
  if (text.endsWith('万')) {
    const numeric = Number(text.replace('万', ''))
    return Number.isFinite(numeric) ? numeric * 10000 : null
  }

  const numeric = Number(text)
  return Number.isFinite(numeric) ? numeric : null
}

export function percentClass(value) {
  if (!value) return ''
  const numeric = Number(String(value).replace('%', ''))
  if (Number.isNaN(numeric)) return ''
  if (numeric > 0) return 'up'
  if (numeric < 0) return 'down'
  return ''
}

export function cellClass(column, item) {
  if (column.key === 'changePercent') return percentClass(item.changePercent)
  if (column.key === 'auctionChangePercent') return percentClass(item.auctionChangePercent)
  return ''
}

export function formatSealTime(raw) {
  if (!raw || raw.length < 4) return raw || '-'
  if (String(raw).includes(':')) return raw
  const value = String(raw).padStart(6, '0')
  return `${value.slice(0, 2)}:${value.slice(2, 4)}:${value.slice(4, 6)}`
}

export function displayValue(column, item) {
  if (column.key === 'reason') return item.reason || item.extraTag || '-'
  if (column.key === 'concept') return item.concept || item.industry || '-'
  if (column.key === 'extraTag') return formatSealTime(item.extraTag)
  return item[column.key] || '-'
}

export function maxBoardHeight(report) {
  const rows = [...(report.limitUpToday || []), ...(report.firstLimitToday || [])]
  return rows.reduce((max, item) => Math.max(max, toBoardHeight(item.boardHeight) || 1), 0)
}
