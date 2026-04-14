<template>
  <div class="page-shell">
    <aside class="sidebar">
      <div class="brand-block">
        <p class="eyebrow">A股短线复盘</p>
        <h1>每日复盘</h1>
        <p class="muted">把炸板、连板、首板、跌停和板块强弱沉淀成可以回看的交易笔记。</p>
      </div>

      <div class="panel controls-panel">
        <label class="field-label" for="tradeDate">交易日期</label>
        <input id="tradeDate" v-model="selectedDate" type="date" />
        <div class="actions">
          <button class="ghost" :disabled="loading" @click="loadRecap">查看报告</button>
          <button :disabled="capturing" @click="handleCapture">{{ capturing ? '采集中...' : '触发采集' }}</button>
        </div>
      </div>

      <div class="panel" v-if="quickViews.length">
        <div class="panel-title">
          <h2>快速切换</h2>
          <small>点击直达模块</small>
        </div>
        <div class="view-list">
          <button
            v-for="view in quickViews"
            :key="view.id"
            class="view-button"
            :class="{ active: activeView === view.id }"
            @click="switchView(view.id)"
          >
            <span>{{ view.label }}</span>
            <small>{{ view.meta }}</small>
          </button>
        </div>
      </div>

      <div class="panel calendar-panel">
        <div class="calendar-head">
          <div>
            <h2>历史记录</h2>
            <small>{{ calendarMonthLabel }}</small>
          </div>
          <div class="calendar-nav">
            <button class="nav-button" @click="shiftCalendarMonth(-1)">‹</button>
            <button class="nav-button" @click="shiftCalendarMonth(1)">›</button>
          </div>
        </div>
        <div class="calendar-grid week-row">
          <span v-for="week in weekLabels" :key="week" class="week-label">{{ week }}</span>
        </div>
        <div class="calendar-grid">
          <button
            v-for="day in calendarDays"
            :key="day.key"
            class="calendar-cell"
            :class="{ ghosted: !day.inMonth, active: day.isSelected, available: day.hasRecap }"
            :disabled="!day.hasRecap"
            @click="selectCalendarDay(day)"
          >
            <span class="day-number">{{ day.day }}</span>
            <i v-if="day.hasRecap" class="calendar-dot"></i>
          </button>
        </div>
      </div>
    </aside>

    <main class="content">
      <section class="hero-card">
        <div class="hero-copy">
          <p class="eyebrow">收盘看板</p>
          <h2>{{ recap ? recap.tradeDate : '请选择日期' }}</h2>
          <p class="muted">{{ recap ? recap.notes : '先触发一次采集，系统会自动生成这一天的复盘档案。' }}</p>
        </div>
        <div class="summary-grid">
          <article v-for="card in summaryCards" :key="card.label" class="summary-card">
            <span>{{ card.label }}</span>
            <strong>{{ card.value }}</strong>
          </article>
        </div>
      </section>

      <p v-if="error" class="error">{{ error }}</p>
      <p v-if="loading" class="muted">正在加载复盘数据...</p>

      <section v-if="recap && showOverview" id="overview" class="overview-stack">
        <div class="chart-row chart-row-hero">
          <article class="panel chart-panel">
            <div class="chart-panel-head">
              <div>
                <h2>上涨家数趋势</h2>
                <small>最近 {{ trendReports.length }} 个交易日</small>
              </div>
              <strong>{{ recap.marketStats.upCount }}</strong>
            </div>
            <div ref="upTrendChart" class="chart-box chart-box-large"></div>
          </article>
          <article class="panel chart-panel">
            <div class="chart-panel-head">
              <div>
                <h2>连板高度趋势</h2>
                <small>观察情绪高度变化</small>
              </div>
              <strong>{{ maxBoardHeight(recap) }}</strong>
            </div>
            <div ref="heightTrendChart" class="chart-box chart-box-large"></div>
          </article>
        </div>

        <div class="chart-row">
          <article class="panel chart-panel">
            <div class="chart-panel-head">
              <div>
                <h2>首板集中板块</h2>
                <small>首板票最集中的方向</small>
              </div>
            </div>
            <div ref="focusChart" class="chart-box"></div>
          </article>
          <article class="panel chart-panel">
            <div class="chart-panel-head">
              <div>
                <h2>上涨板块前列</h2>
                <small>按当前复盘数据聚合</small>
              </div>
            </div>
            <div ref="upSectorChart" class="chart-box"></div>
          </article>
          <article class="panel chart-panel">
            <div class="chart-panel-head">
              <div>
                <h2>下跌板块前列</h2>
                <small>弱势方向更好识别</small>
              </div>
            </div>
            <div ref="downSectorChart" class="chart-box"></div>
          </article>
        </div>
      </section>

      <section v-if="recap" class="tables">
        <article
          v-for="section in displayedSections"
          :id="section.id"
          :key="section.id"
          class="panel"
        >
          <div class="section-head">
            <h2>{{ section.title }}</h2>
            <span>{{ sortedRows(section).length }} 条</span>
          </div>
          <div class="table-scroll">
            <table>
              <thead>
                <tr>
                  <th v-for="column in section.columns" :key="`${section.id}-${column.key}`">
                    <button
                      v-if="column.sortable"
                      type="button"
                      class="sort-button"
                      :class="{ active: isSorted(section.id, column.key) }"
                      @click="toggleSort(section.id, column.key)"
                    >
                      <span>{{ column.label }}</span>
                      <span class="sort-arrows">
                        <span :class="{ on: sortDirection(section.id, column.key) === 'asc' }">▲</span>
                        <span :class="{ on: sortDirection(section.id, column.key) === 'desc' }">▼</span>
                      </span>
                    </button>
                    <span v-else>{{ column.label }}</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in sortedRows(section)" :key="`${section.id}-${item.code}-${item.name}`">
                  <td
                    v-for="column in section.columns"
                    :key="`${section.id}-${item.code}-${column.key}`"
                    :class="cellClass(column, item)"
                  >
                    {{ displayValue(column, item) }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </article>
      </section>
    </main>
  </div>
</template>

<script>
import * as echarts from 'echarts'
import { captureRecap, getRecap, listRecaps } from './api'

function toBoardHeight(value) {
  const numeric = Number(value)
  return Number.isFinite(numeric) ? numeric : 0
}

function parseNumericValue(value) {
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

function formatMonthLabel(monthKey) {
  const [year, month] = monthKey.split('-')
  return `${year}年${month}月`
}

function startOfMonth(monthKey) {
  return new Date(`${monthKey}-01T00:00:00`)
}

function shiftMonth(monthKey, offset) {
  const date = startOfMonth(monthKey)
  date.setMonth(date.getMonth() + offset)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
}

const BASE_COLUMNS = {
  code: { key: 'code', label: '代码', sortable: true, sortType: 'string' },
  name: { key: 'name', label: '名称', sortable: true, sortType: 'string' },
  boardHeight: { key: 'boardHeight', label: '连板', sortable: true, sortType: 'number' },
  changePercent: { key: 'changePercent', label: '涨幅', sortable: true, sortType: 'number' },
  price: { key: 'price', label: '股价', sortable: true, sortType: 'number' },
  concept: { key: 'concept', label: '概念', sortable: true, sortType: 'string' },
  amount: { key: 'amount', label: '成交额', sortable: true, sortType: 'number' },
  floatMarketValue: { key: 'floatMarketValue', label: '流通市值', sortable: true, sortType: 'number' },
  reason: { key: 'reason', label: '原因', sortable: true, sortType: 'string' }
}

const STANDARD_COLUMNS = [
  BASE_COLUMNS.code,
  BASE_COLUMNS.name,
  BASE_COLUMNS.boardHeight,
  BASE_COLUMNS.changePercent,
  BASE_COLUMNS.price,
  BASE_COLUMNS.concept,
  BASE_COLUMNS.amount,
  BASE_COLUMNS.floatMarketValue,
  BASE_COLUMNS.reason
]

const BROKEN_COLUMNS = [
  BASE_COLUMNS.code,
  BASE_COLUMNS.name,
  BASE_COLUMNS.changePercent,
  BASE_COLUMNS.price,
  BASE_COLUMNS.concept,
  BASE_COLUMNS.amount,
  BASE_COLUMNS.floatMarketValue,
  BASE_COLUMNS.reason
]

export default {
  name: 'App',
  data() {
    return {
      recaps: [],
      selectedDate: '',
      recap: null,
      loading: false,
      capturing: false,
      error: '',
      activeView: 'overview',
      trendReports: [],
      sortStates: {},
      chartMap: {},
      calendarCursor: '',
      autoCaptureTimer: null,
      lastAutoCaptureStartedAt: 0,
      weekLabels: ['一', '二', '三', '四', '五', '六', '日']
    }
  },
  computed: {
    calendarMonthLabel() {
      return this.calendarCursor ? formatMonthLabel(this.calendarCursor) : ''
    },
    calendarDays() {
      if (!this.calendarCursor) return []
      const monthStart = startOfMonth(this.calendarCursor)
      const dayOffset = (monthStart.getDay() + 6) % 7
      const firstGridDay = new Date(monthStart)
      firstGridDay.setDate(monthStart.getDate() - dayOffset)
      return Array.from({ length: 42 }, (_, index) => {
        const date = new Date(firstGridDay)
        date.setDate(firstGridDay.getDate() + index)
        const tradeDate = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
        const recap = this.recaps.find(item => item.tradeDate === tradeDate)
        return {
          key: `${tradeDate}-${index}`,
          day: date.getDate(),
          tradeDate,
          inMonth: tradeDate.startsWith(this.calendarCursor),
          hasRecap: Boolean(recap),
          isSelected: tradeDate === this.selectedDate
        }
      })
    },
    filteredConsecutiveRows() {
      return this.recap ? (this.recap.limitUpToday || []).filter(item => toBoardHeight(item.boardHeight) >= 2) : []
    },
    filteredConsecutiveFeedbackRows() {
      return this.recap ? (this.recap.limitUpYesterdayFeedback || []).filter(item => toBoardHeight(item.boardHeight) >= 2) : []
    },
    summaryCards() {
      if (!this.recap) return []
      return [
        { label: '上涨家数', value: this.recap.marketStats.upCount },
        { label: '下跌家数', value: this.recap.marketStats.downCount },
        { label: '平盘家数', value: this.recap.marketStats.flatCount },
        { label: '首板数量', value: this.recap.marketStats.firstLimitCount }
      ]
    },
    sections() {
      if (!this.recap) return []
      return [
        { id: 'broken-today', title: '当日炸板票', rows: this.recap.brokenLimitToday || [], columns: BROKEN_COLUMNS },
        { id: 'broken-yesterday', title: '昨日炸板票反馈', rows: this.recap.brokenLimitYesterdayFeedback || [], columns: BROKEN_COLUMNS },
        { id: 'limit-up', title: '当日连板票', rows: this.filteredConsecutiveRows, columns: STANDARD_COLUMNS },
        { id: 'limit-up-yesterday', title: '昨日连板票反馈', rows: this.filteredConsecutiveFeedbackRows, columns: STANDARD_COLUMNS },
        { id: 'first-limit', title: '当日首板票', rows: this.recap.firstLimitToday || [], columns: STANDARD_COLUMNS },
        { id: 'limit-down', title: '当日跌停票', rows: this.recap.limitDownToday || [], columns: STANDARD_COLUMNS },
        { id: 'gem-star', title: '创业板/科创板 10日涨幅前列', rows: this.recap.top10DayGainGemStar || [], columns: STANDARD_COLUMNS },
        { id: 'main-board', title: '主板 10日涨幅前列', rows: this.recap.top10DayGainMainBoard || [], columns: STANDARD_COLUMNS }
      ]
    },
    quickViews() {
      const base = [{ id: 'overview', label: '总览', meta: '图表总览' }]
      return base.concat(this.sections.map(section => ({ id: section.id, label: section.title, meta: `${section.rows.length} 条` })))
    },
    displayedSections() {
      return this.activeView === 'overview' ? this.sections : this.sections.filter(section => section.id === this.activeView)
    },
    showOverview() {
      return this.activeView === 'overview'
    }
  },
  async mounted() {
    window.addEventListener('resize', this.resizeCharts)
    try {
      await this.loadRecapList()
      await this.loadRecap()
    } catch (error) {
      this.error = error.message
    } finally {
      this.startAutoCaptureLoop()
    }
  },
  beforeDestroy() {
    window.removeEventListener('resize', this.resizeCharts)
    this.stopAutoCaptureLoop()
    Object.values(this.chartMap).forEach(chart => chart.dispose())
    this.chartMap = {}
  },
  methods: {
    maxBoardHeight(report) {
      const rows = [...(report.limitUpToday || []), ...(report.firstLimitToday || [])]
      return rows.reduce((max, item) => Math.max(max, toBoardHeight(item.boardHeight) || 1), 0)
    },
    setCalendarCursor(dateText) {
      if (dateText) this.calendarCursor = dateText.slice(0, 7)
    },
    shiftCalendarMonth(offset) {
      if (this.calendarCursor) this.calendarCursor = shiftMonth(this.calendarCursor, offset)
    },
    selectCalendarDay(day) {
      if (!day.hasRecap) return
      this.selectedDate = day.tradeDate
      this.loadRecap()
    },
    percentClass(value) {
      if (!value) return ''
      const numeric = Number(String(value).replace('%', ''))
      if (Number.isNaN(numeric)) return ''
      if (numeric > 0) return 'up'
      if (numeric < 0) return 'down'
      return ''
    },
    cellClass(column, item) {
      return column.key === 'changePercent' ? this.percentClass(item.changePercent) : ''
    },
    displayValue(column, item) {
      if (column.key === 'reason') return item.reason || item.extraTag || '-'
      if (column.key === 'concept') return item.concept || item.industry || '-'
      return item[column.key] || '-'
    },
    isSorted(sectionId, key) {
      return this.sortStates[sectionId]?.key === key
    },
    sortDirection(sectionId, key) {
      return this.isSorted(sectionId, key) ? this.sortStates[sectionId].direction : ''
    },
    toggleSort(sectionId, key) {
      const current = this.sortStates[sectionId]
      if (!current || current.key !== key) return this.$set(this.sortStates, sectionId, { key, direction: 'desc' })
      if (current.direction === 'desc') return this.$set(this.sortStates, sectionId, { key, direction: 'asc' })
      this.$delete(this.sortStates, sectionId)
    },
    sortedRows(section) {
      const rows = [...section.rows]
      const sortState = this.sortStates[section.id]
      if (!sortState) return rows
      const column = section.columns.find(item => item.key === sortState.key)
      if (!column) return rows
      const direction = sortState.direction === 'asc' ? 1 : -1
      rows.sort((left, right) => {
        const leftValue = sortState.key === 'reason' ? (left.reason || left.extraTag || '') : sortState.key === 'concept' ? (left.concept || left.industry || '') : left[sortState.key]
        const rightValue = sortState.key === 'reason' ? (right.reason || right.extraTag || '') : sortState.key === 'concept' ? (right.concept || right.industry || '') : right[sortState.key]
        if (column.sortType === 'number') {
          const leftNumber = parseNumericValue(leftValue)
          const rightNumber = parseNumericValue(rightValue)
          if (leftNumber === null && rightNumber === null) return 0
          if (leftNumber === null) return 1
          if (rightNumber === null) return -1
          return (leftNumber - rightNumber) * direction
        }
        return String(leftValue || '').localeCompare(String(rightValue || '')) * direction
      })
      return rows
    },
    switchView(viewId) {
      this.activeView = viewId
      this.$nextTick(() => {
        const targetId = viewId === 'overview' ? 'overview' : viewId
        const el = document.getElementById(targetId)
        if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' })
        else window.scrollTo({ top: 0, behavior: 'smooth' })
        if (viewId === 'overview') this.renderCharts()
      })
    },
    ensureChart(key, refName) {
      const el = this.$refs[refName]
      if (!el) return null
      if (!this.chartMap[key]) this.chartMap[key] = echarts.init(el)
      return this.chartMap[key]
    },
    todayText() {
      const now = new Date()
      const year = now.getFullYear()
      const month = String(now.getMonth() + 1).padStart(2, '0')
      const day = String(now.getDate()).padStart(2, '0')
      return `${year}-${month}-${day}`
    },
    isTradingWindowNow() {
      const now = new Date()
      const day = now.getDay()
      if (day === 0 || day === 6) return false
      const minutes = now.getHours() * 60 + now.getMinutes()
      const inMorning = minutes >= 9 * 60 + 30 && minutes <= 11 * 60 + 30
      const inAfternoon = minutes >= 13 * 60 && minutes <= 15 * 60
      return inMorning || inAfternoon
    },
    shouldAutoCapture() {
      if (!this.selectedDate || this.selectedDate !== this.todayText()) return false
      if (this.capturing || this.loading) return false
      if (!this.isTradingWindowNow()) return false
      if (typeof document !== 'undefined' && document.hidden) return false
      return true
    },
    startAutoCaptureLoop() {
      if (this.autoCaptureTimer) return
      this.autoCaptureTimer = window.setInterval(() => {
        this.runAutoCapture()
      }, 60 * 1000)
    },
    stopAutoCaptureLoop() {
      if (!this.autoCaptureTimer) return
      window.clearInterval(this.autoCaptureTimer)
      this.autoCaptureTimer = null
    },
    async runAutoCapture() {
      if (!this.shouldAutoCapture()) return
      const now = Date.now()
      if (now - this.lastAutoCaptureStartedAt < 55 * 1000) return
      this.lastAutoCaptureStartedAt = now
      await this.handleCapture()
    },
    resizeCharts() {
      Object.values(this.chartMap).forEach(chart => chart.resize())
    },
    buildTrendOption(title, subtitle, values, dates, color) {
      return {
        animationDuration: 500,
        grid: { left: 18, right: 18, top: 12, bottom: 24, containLabel: true },
        tooltip: { trigger: 'axis', backgroundColor: '#0f172a', borderWidth: 0, textStyle: { color: '#f8fafc' } },
        xAxis: {
          type: 'category',
          data: dates,
          boundaryGap: false,
          axisLine: { lineStyle: { color: 'rgba(20,33,61,0.12)' } },
          axisTick: { show: false },
          axisLabel: { color: '#64748b' }
        },
        yAxis: {
          type: 'value',
          axisLine: { show: false },
          axisTick: { show: false },
          splitLine: { lineStyle: { color: 'rgba(20,33,61,0.08)', type: 'dashed' } },
          axisLabel: { color: '#64748b' }
        },
        series: [{
          data: values,
          type: 'line',
          smooth: true,
          symbol: 'circle',
          symbolSize: 9,
          lineStyle: { width: 4, color },
          itemStyle: { color, borderColor: '#fff', borderWidth: 2 },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: `${color}66` },
              { offset: 1, color: `${color}08` }
            ])
          }
        }]
      }
    },
    buildBarOption(title, rows, color, reverse = false) {
      const data = rows
        .map(item => ({
          name: item.name,
          change: parseNumericValue(item.changePercent) ?? 0
        }))
        .sort((left, right) => (reverse ? left.change - right.change : right.change - left.change))
        .slice(0, 8)
        .reverse()
      const categories = data.map(item => item.name)
      const values = data.map(item => item.change)
      return {
        animationDuration: 500,
        grid: { left: 12, right: 16, top: 8, bottom: 8, containLabel: true },
        tooltip: {
          trigger: 'axis',
          axisPointer: { type: 'shadow' },
          backgroundColor: '#0f172a',
          borderWidth: 0,
          textStyle: { color: '#f8fafc' }
        },
        xAxis: {
          type: 'value',
          axisLabel: { color: '#64748b' },
          splitLine: { lineStyle: { color: 'rgba(20,33,61,0.08)', type: 'dashed' } }
        },
        yAxis: {
          type: 'category',
          data: categories,
          axisTick: { show: false },
          axisLine: { show: false },
          axisLabel: { color: '#14213d', fontWeight: 600 }
        },
        series: [{
          type: 'bar',
          data: values,
          barWidth: 14,
          showBackground: true,
          backgroundStyle: { color: 'rgba(20,33,61,0.05)' },
          itemStyle: {
            borderRadius: 999,
            color: new echarts.graphic.LinearGradient(1, 0, 0, 0, [
              { offset: 0, color: reverse ? '#0f766e' : '#f97316' },
              { offset: 1, color }
            ])
          },
          label: {
            show: true,
            position: 'right',
            color: '#14213d',
            formatter: ({ value }) => `${value.toFixed(2)}%`
          }
        }]
      }
    },
    buildFocusOption(dataMap) {
      const rows = Object.entries(dataMap)
        .map(([name, value]) => ({ name, value }))
        .sort((a, b) => b.value - a.value)
        .slice(0, 8)
        .reverse()
      return {
        animationDuration: 500,
        grid: { left: 12, right: 16, top: 8, bottom: 8, containLabel: true },
        tooltip: {
          trigger: 'axis',
          axisPointer: { type: 'shadow' },
          backgroundColor: '#0f172a',
          borderWidth: 0,
          textStyle: { color: '#f8fafc' }
        },
        xAxis: {
          type: 'value',
          axisLabel: { color: '#64748b' },
          splitLine: { lineStyle: { color: 'rgba(20,33,61,0.08)', type: 'dashed' } }
        },
        yAxis: {
          type: 'category',
          data: rows.map(item => item.name),
          axisTick: { show: false },
          axisLine: { show: false },
          axisLabel: { color: '#14213d', fontWeight: 600 }
        },
        series: [{
          type: 'bar',
          data: rows.map(item => item.value),
          barWidth: 14,
          itemStyle: {
            borderRadius: 999,
            color: new echarts.graphic.LinearGradient(1, 0, 0, 0, [
              { offset: 0, color: '#84cc16' },
              { offset: 1, color: '#22c55e' }
            ])
          },
          label: { show: true, position: 'right', color: '#14213d' }
        }]
      }
    },
    renderCharts() {
      if (!this.recap || !this.showOverview || !this.trendReports.length) return
      this.$nextTick(() => {
        const trendDates = this.trendReports.map(report => report.tradeDate.slice(5))
        const upValues = this.trendReports.map(report => report.marketStats.upCount)
        const heightValues = this.trendReports.map(report => this.maxBoardHeight(report))
        const upTrendChart = this.ensureChart('upTrend', 'upTrendChart')
        const heightTrendChart = this.ensureChart('heightTrend', 'heightTrendChart')
        const focusChart = this.ensureChart('focus', 'focusChart')
        const upSectorChart = this.ensureChart('upSector', 'upSectorChart')
        const downSectorChart = this.ensureChart('downSector', 'downSectorChart')
        if (upTrendChart) upTrendChart.setOption(this.buildTrendOption('上涨家数趋势', `当前 ${this.recap.marketStats.upCount}`, upValues, trendDates, '#f97316'), true)
        if (heightTrendChart) heightTrendChart.setOption(this.buildTrendOption('连板高度趋势', `当前 ${this.maxBoardHeight(this.recap)}`, heightValues, trendDates, '#14213d'), true)
        if (focusChart) focusChart.setOption(this.buildFocusOption(this.recap.firstLimitSectorFocus || {}), true)
        if (upSectorChart) upSectorChart.setOption(this.buildBarOption('上涨板块前列', this.recap.topUpSectors || [], '#fb7185'), true)
        if (downSectorChart) downSectorChart.setOption(this.buildBarOption('下跌板块前列', this.recap.topDownSectors || [], '#14b8a6', true), true)
        this.resizeCharts()
      })
    },
    async loadTrendReports() {
      const dates = this.recaps.map(item => item.tradeDate).sort().slice(-20)
      if (!dates.length) return (this.trendReports = [])
      const reports = await Promise.all(dates.map(date => getRecap(date)))
      this.trendReports = reports.sort((a, b) => a.tradeDate.localeCompare(b.tradeDate))
    },
    async loadRecapList() {
      this.recaps = await listRecaps()
      if (!this.selectedDate && this.recaps.length > 0) this.selectedDate = this.recaps[0].tradeDate
      this.setCalendarCursor(this.selectedDate || this.recaps[0]?.tradeDate || '')
      await this.loadTrendReports()
    },
    async loadRecap() {
      if (!this.selectedDate) return
      this.loading = true
      this.error = ''
      try {
        this.recap = await getRecap(this.selectedDate)
        this.activeView = 'overview'
        this.setCalendarCursor(this.selectedDate)
        this.renderCharts()
      } catch (error) {
        this.recap = null
        this.error = error.message
      } finally {
        this.loading = false
      }
    },
    async handleCapture() {
      if (!this.selectedDate) return
      this.capturing = true
      this.error = ''
      try {
        this.recap = await captureRecap(this.selectedDate)
        this.activeView = 'overview'
        await this.loadRecapList()
        this.renderCharts()
      } catch (error) {
        this.error = error.message
      } finally {
        this.capturing = false
      }
    },
    selectRecap(tradeDate) {
      this.selectedDate = tradeDate
      this.loadRecap()
    }
  }
}
</script>
