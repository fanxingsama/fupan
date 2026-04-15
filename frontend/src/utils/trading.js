// 日期格式化、交易日判断、节假日

export function formatDateStr(d) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

export function formatMonthLabel(monthKey) {
  const [year, month] = monthKey.split('-')
  return `${year}年${month}月`
}

export function startOfMonth(monthKey) {
  return new Date(`${monthKey}-01T00:00:00`)
}

export function shiftMonth(monthKey, offset) {
  const date = startOfMonth(monthKey)
  date.setMonth(date.getMonth() + offset)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
}

// 每年的具体休市日需根据国务院/证监会公告更新
export const HOLIDAYS = new Set([
  // 2025
  '2025-01-01',
  '2025-01-28','2025-01-29','2025-01-30','2025-01-31',
  '2025-02-03','2025-02-04',
  '2025-04-04',
  '2025-05-01','2025-05-02','2025-05-05',
  '2025-06-02',
  '2025-10-01','2025-10-02','2025-10-03','2025-10-06','2025-10-07','2025-10-08',
  // 2026
  '2026-01-01','2026-01-02',
  '2026-02-16','2026-02-17','2026-02-18','2026-02-19','2026-02-20',
  '2026-04-06',
  '2026-05-01','2026-05-04','2026-05-05',
  '2026-06-19',
  '2026-09-25',
  '2026-10-01','2026-10-02','2026-10-05','2026-10-06','2026-10-07',
  // 2027
  '2027-01-01',
  '2027-02-08','2027-02-09','2027-02-10','2027-02-11','2027-02-12',
  '2027-04-05',
  '2027-05-03','2027-05-04','2027-05-05',
  '2027-06-09',
  '2027-09-16',
  '2027-10-01','2027-10-04','2027-10-05','2027-10-06','2027-10-07',
])

export function isTradingDay(dateStr) {
  if (HOLIDAYS.has(dateStr)) return false
  const d = new Date(dateStr + 'T12:00:00')
  const dow = d.getDay()
  return dow !== 0 && dow !== 6
}

// 获取当前应展示的交易日：
// - 交易日 9:30 之后 → 当天
// - 交易日 9:30 之前 / 非交易日 → 最近一个交易日
export function getCurrentTradingDate() {
  const now = new Date()
  const todayStr = formatDateStr(now)
  const minuteOfDay = now.getHours() * 60 + now.getMinutes()

  if (isTradingDay(todayStr) && minuteOfDay >= 9 * 60 + 30) {
    return todayStr
  }
  const d = new Date(todayStr + 'T12:00:00')
  for (let i = 0; i < 30; i++) {
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

// 比较两个 recap 的核心数据是否一致
export function recapsAreDifferent(a, b) {
  if (!a || !b) return true
  const fields = [
    'marketStats', 'brokenLimitToday', 'brokenLimitYesterdayFeedback',
    'limitUpToday', 'limitUpYesterdayFeedback', 'firstLimitToday',
    'limitDownToday', 'topUpSectors', 'topDownSectors',
    'top10DayGainGemStar', 'top10DayGainMainBoard', 'firstLimitSectorFocus'
  ]
  for (const f of fields) {
    if (JSON.stringify(a[f]) !== JSON.stringify(b[f])) return true
  }
  return false
}

// 生成两个 recap 之间的变动摘要列表
export function recapsDiffSummary(oldR, newR) {
  if (!oldR || !newR) return []
  const diffs = []
  const ms = oldR.marketStats || {}
  const msn = newR.marketStats || {}
  if (ms.upCount !== msn.upCount) diffs.push({ label: '上涨家数', old: ms.upCount, new: msn.upCount })
  if (ms.downCount !== msn.downCount) diffs.push({ label: '下跌家数', old: ms.downCount, new: msn.downCount })
  if (ms.flatCount !== msn.flatCount) diffs.push({ label: '平盘家数', old: ms.flatCount, new: msn.flatCount })
  if (ms.firstLimitCount !== msn.firstLimitCount) diffs.push({ label: '首板数量', old: ms.firstLimitCount, new: msn.firstLimitCount })

  const sections = [
    { key: 'brokenLimitToday', label: '当日炸板票' },
    { key: 'brokenLimitYesterdayFeedback', label: '昨日炸板票反馈' },
    { key: 'limitUpToday', label: '当日连板票' },
    { key: 'limitUpYesterdayFeedback', label: '昨日连板票反馈' },
    { key: 'firstLimitToday', label: '当日首板票' },
    { key: 'limitDownToday', label: '当日跌停票' },
    { key: 'top10DayGainGemStar', label: '创业板/科创板10日涨幅' },
    { key: 'top10DayGainMainBoard', label: '主板10日涨幅' },
  ]
  for (const s of sections) {
    const ol = (oldR[s.key] || []).length
    const nl = (newR[s.key] || []).length
    if (JSON.stringify(oldR[s.key]) !== JSON.stringify(newR[s.key])) {
      diffs.push({ label: s.label, old: ol + ' 条', new: nl + ' 条' })
    }
  }
  return diffs
}
