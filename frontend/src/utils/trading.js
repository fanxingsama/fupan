export function formatDateStr(d) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

export function formatMonthLabel(monthKey) {
  const [year, month] = monthKey.split('-')
  return `${year}\u5e74${month}\u6708`
}

export function startOfMonth(monthKey) {
  return new Date(`${monthKey}-01T00:00:00`)
}

export function shiftMonth(monthKey, offset) {
  const date = startOfMonth(monthKey)
  date.setMonth(date.getMonth() + offset)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
}

export const HOLIDAYS = new Set([
  '2025-01-01',
  '2025-01-28', '2025-01-29', '2025-01-30', '2025-01-31',
  '2025-02-03', '2025-02-04',
  '2025-04-04',
  '2025-05-01', '2025-05-02', '2025-05-05',
  '2025-06-02',
  '2025-10-01', '2025-10-02', '2025-10-03', '2025-10-06', '2025-10-07', '2025-10-08',
  '2026-01-01', '2026-01-02',
  '2026-02-16', '2026-02-17', '2026-02-18', '2026-02-19', '2026-02-20',
  '2026-04-06',
  '2026-05-01', '2026-05-04', '2026-05-05',
  '2026-06-19',
  '2026-09-25',
  '2026-10-01', '2026-10-02', '2026-10-05', '2026-10-06', '2026-10-07',
  '2027-01-01',
  '2027-02-08', '2027-02-09', '2027-02-10', '2027-02-11', '2027-02-12',
  '2027-04-05',
  '2027-05-03', '2027-05-04', '2027-05-05',
  '2027-06-09',
  '2027-09-16',
  '2027-10-01', '2027-10-04', '2027-10-05', '2027-10-06', '2027-10-07'
])

export function isTradingDay(dateStr) {
  if (HOLIDAYS.has(dateStr)) return false
  const d = new Date(`${dateStr}T12:00:00`)
  const dow = d.getDay()
  return dow !== 0 && dow !== 6
}

export function getCurrentTradingDate() {
  const now = new Date()
  const todayStr = formatDateStr(now)
  const minuteOfDay = now.getHours() * 60 + now.getMinutes()

  if (isTradingDay(todayStr) && minuteOfDay >= 9 * 60 + 30) {
    return todayStr
  }

  const d = new Date(`${todayStr}T12:00:00`)
  for (let i = 0; i < 30; i += 1) {
    d.setDate(d.getDate() - 1)
    const ds = formatDateStr(d)
    if (isTradingDay(ds)) return ds
  }
  return todayStr
}

export function isTradingWindowNow() {
  const now = new Date()
  const day = now.getDay()
  if (day === 0 || day === 6) return false
  const todayStr = formatDateStr(now)
  if (HOLIDAYS.has(todayStr)) return false
  const minutes = now.getHours() * 60 + now.getMinutes()
  const inMorning = minutes >= 9 * 60 + 30 && minutes <= 11 * 60 + 30
  const inAfternoon = minutes >= 13 * 60 && minutes <= 15 * 60
  return inMorning || inAfternoon
}

export function recapsAreDifferent(a, b) {
  if (!a || !b) return true
  return recapsDiffSummary(a, b).length > 0
}

function formatDiffValue(value) {
  if (value === null || value === undefined || value === '') return '-'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

function buildScalarDiff(label, oldValue, newValue) {
  return {
    label,
    summary: `${formatDiffValue(oldValue)} -> ${formatDiffValue(newValue)}`,
    details: [
      {
        name: label,
        oldValue: formatDiffValue(oldValue),
        newValue: formatDiffValue(newValue)
      }
    ]
  }
}

function buildSectionDiff(label, oldRows = [], newRows = []) {
  const oldMap = new Map((oldRows || []).map(item => [`${item.code || item.key || item.label || ''}-${item.name || ''}`, item]))
  const newMap = new Map((newRows || []).map(item => [`${item.code || item.key || item.label || ''}-${item.name || ''}`, item]))
  const keys = Array.from(new Set([...oldMap.keys(), ...newMap.keys()]))
  const fields = [
    ['label', '\u9879\u76ee'],
    ['latest', '\u6700\u65b0\u503c'],
    ['changeAmount', '\u6da8\u8dcc\u70b9\u6570'],
    ['boardHeight', '\u8fde\u677f'],
    ['changePercent', '\u6da8\u5e45'],
    ['price', '\u80a1\u4ef7'],
    ['concept', '\u6982\u5ff5'],
    ['amount', '\u6210\u4ea4\u989d'],
    ['floatMarketValue', '\u6d41\u901a\u5e02\u503c'],
    ['sealAmount', '\u5c01\u677f\u91d1\u989d'],
    ['turnoverRate', '\u6362\u624b\u7387'],
    ['amplitude', '\u632f\u5e45'],
    ['extraTag', '\u9644\u52a0\u4fe1\u606f'],
    ['reason', '\u539f\u56e0']
  ]

  const details = []
  keys.forEach(key => {
    const oldItem = oldMap.get(key)
    const newItem = newMap.get(key)
    const title = [
      newItem?.label || oldItem?.label,
      newItem?.code || oldItem?.code,
      newItem?.name || oldItem?.name
    ].filter(Boolean).join(' ')

    if (!oldItem && newItem) {
      details.push({
        name: `${title} \u65b0\u589e`,
        oldValue: '-',
        newValue: JSON.stringify(newItem)
      })
      return
    }
    if (oldItem && !newItem) {
      details.push({
        name: `${title} \u5220\u9664`,
        oldValue: JSON.stringify(oldItem),
        newValue: '-'
      })
      return
    }

    fields.forEach(([field, fieldLabel]) => {
      const oldValue = oldItem[field] || '-'
      const newValue = newItem[field] || '-'
      if (oldValue !== newValue) {
        details.push({
          name: `${title} ${fieldLabel}`.trim(),
          oldValue,
          newValue
        })
      }
    })
  })

  return details.length
    ? {
        label,
        summary: `${oldRows.length} \u6761 -> ${newRows.length} \u6761`,
        details
      }
    : null
}

export function recapsDiffSummary(oldR, newR) {
  if (!oldR || !newR) return []

  const diffs = []
  const oldStats = oldR.marketStats || {}
  const newStats = newR.marketStats || {}

  if (oldStats.upCount !== newStats.upCount) diffs.push(buildScalarDiff('\u4e0a\u6da8\u5bb6\u6570', oldStats.upCount, newStats.upCount))
  if (oldStats.downCount !== newStats.downCount) diffs.push(buildScalarDiff('\u4e0b\u8dcc\u5bb6\u6570', oldStats.downCount, newStats.downCount))
  if (oldStats.flatCount !== newStats.flatCount) diffs.push(buildScalarDiff('\u5e73\u76d8\u5bb6\u6570', oldStats.flatCount, newStats.flatCount))
  if (oldStats.firstLimitCount !== newStats.firstLimitCount) diffs.push(buildScalarDiff('\u9996\u677f\u6570\u91cf', oldStats.firstLimitCount, newStats.firstLimitCount))

  const sections = [
    ['boardIndexes', '\u6307\u6570\u6da8\u8dcc'],
    ['brokenLimitToday', '\u5f53\u65e5\u70b8\u677f\u7968'],
    ['brokenLimitYesterdayFeedback', '\u6628\u65e5\u70b8\u677f\u53cd\u9988'],
    ['limitUpToday', '\u5f53\u65e5\u8fde\u677f\u7968'],
    ['limitUpYesterdayFeedback', '\u6628\u65e5\u8fde\u677f\u53cd\u9988'],
    ['firstLimitToday', '\u5f53\u65e5\u9996\u677f\u7968'],
    ['firstLimitYesterdayFeedback', '\u6628\u65e5\u9996\u677f\u53cd\u9988'],
    ['limitDownToday', '\u5f53\u65e5\u8dcc\u505c\u7968'],
    ['top10DayGainGemStar', '\u521b\u4e1a\u677f/\u79d1\u521b\u677f10\u65e5\u6da8\u5e45'],
    ['top10DayGainMainBoard', '\u4e3b\u677f10\u65e5\u6da8\u5e45']
  ]

  sections.forEach(([key, label]) => {
    if (JSON.stringify(oldR[key]) !== JSON.stringify(newR[key])) {
      const diff = buildSectionDiff(label, oldR[key] || [], newR[key] || [])
      if (diff) diffs.push(diff)
    }
  })

  return diffs
}
