<template>
  <div class="page-shell">
    <OverwriteDialog
      :visible="showOverwriteDialog"
      :trade-date="selectedDate"
      :old-recap="pendingOldRecap"
      :new-recap="pendingNewRecap"
      @confirm="confirmOverwrite"
      @cancel="cancelOverwrite"
    />
    <ToastBar :message="toastMessage" />

    <div v-if="captureErrorMsg" class="modal-overlay" @click.self="captureErrorMsg = ''">
      <div class="modal-card">
        <h2>采集失败</h2>
        <p class="error-msg">{{ captureErrorMsg }}</p>
        <div class="modal-actions">
          <button @click="captureErrorMsg = ''">确定</button>
        </div>
      </div>
    </div>

    <aside class="sidebar">
      <div class="brand-block">
        <p class="eyebrow">A股短线复盘</p>
        <h1>每日复盘</h1>
        <p class="muted">把情绪、主线、AI 整理和个人交易日志串起来，形成更完整的交易辅助系统。</p>
      </div>

      <div class="panel nav-panel">
        <div class="view-list">
          <button
            v-for="nav in navItems"
            :key="nav.id"
            class="view-button"
            :class="{ active: activeView === nav.id }"
            @click="switchView(nav.id)"
          >
            <span>{{ nav.label }}</span>
          </button>
        </div>
      </div>

      <RecapCalendar
        :month-label="calendarMonthLabel"
        :days="calendarDays"
        :capturing-date="capturingDate"
        @shift-month="shiftCalendarMonth"
        @day-click="handleCalendarClick"
      />
    </aside>

    <main class="content">
      <p v-if="error" class="error">{{ error }}</p>
      <p v-if="loading" class="muted">正在加载复盘数据...</p>
      <p v-if="capturingDate" class="muted">正在采集 {{ capturingDate }} 的数据，请稍候...</p>
      <p v-if="!recap && !loading && !error && activeView !== 'journal'" class="muted center-hint">暂无复盘数据</p>

      <section v-if="selectedDate && activeView !== 'journal'" class="capture-meta-panel">
        <div class="capture-meta-item">
          <span>当前查看</span>
          <strong>{{ selectedDate }}</strong>
        </div>
        <div class="capture-meta-item">
          <span>最近采集时间</span>
          <strong>{{ lastCapturedAtLabel }}</strong>
        </div>
        <div class="capture-meta-item">
          <span>自动采集状态</span>
          <strong>{{ autoCaptureStatus }}</strong>
        </div>
      </section>

      <OverviewPage
        v-if="activeView === 'overview' && recap"
        :recap="recap"
        :indicators="indicators"
        :trade-plan="tradePlan"
        :trend-points="trendPoints"
      />

      <TabbedTablePage v-if="activeView === 'broken' && recap" :recap="recap" page-type="broken" />
      <TabbedTablePage v-if="activeView === 'consecutive' && recap" :recap="recap" page-type="consecutive" />
      <TabbedTablePage v-if="activeView === 'firstLimit' && recap" :recap="recap" page-type="firstLimit" />
      <HighRankPage v-if="activeView === 'highRank' && recap" :recap="recap" />
      <TradeJournalPage v-if="activeView === 'journal'" />
    </main>
  </div>
</template>

<script>
import { captureRecap, getRecap, listRecaps } from './api'
import {
  formatDateStr,
  formatMonthLabel,
  startOfMonth,
  shiftMonth,
  isTradingDay,
  getCurrentTradingDate,
  isTradingWindowNow,
  recapsAreDifferent,
  recapsDiffSummary
} from './utils/trading'

import RecapCalendar from './components/RecapCalendar.vue'
import OverviewPage from './components/OverviewPage.vue'
import TabbedTablePage from './components/TabbedTablePage.vue'
import HighRankPage from './components/HighRankPage.vue'
import TradeJournalPage from './components/TradeJournalPage.vue'
import OverwriteDialog from './components/OverwriteDialog.vue'
import ToastBar from './components/ToastBar.vue'

export default {
  name: 'App',
  components: { RecapCalendar, OverviewPage, TabbedTablePage, HighRankPage, TradeJournalPage, OverwriteDialog, ToastBar },
  data() {
    return {
      recaps: [],
      selectedDate: '',
      recap: null,
      indicators: null,
      tradePlan: null,
      trendPoints: [],
      loading: false,
      capturingDate: '',
      error: '',
      activeView: 'overview',
      calendarCursor: '',
      autoCaptureTimer: null,
      lastAutoCaptureStartedAt: 0,
      showOverwriteDialog: false,
      pendingOldRecap: null,
      pendingNewRecap: null,
      pendingNewResponse: null,
      toastMessage: '',
      toastTimer: null,
      captureErrorMsg: ''
    }
  },
  computed: {
    navItems() {
      return [
        { id: 'overview', label: '交易驾驶舱' },
        { id: 'broken', label: '炸板复盘' },
        { id: 'consecutive', label: '连板梯队' },
        { id: 'firstLimit', label: '首板池' },
        { id: 'highRank', label: '高标观察' },
        { id: 'journal', label: '交易日志' }
      ]
    },
    calendarMonthLabel() {
      return this.calendarCursor ? formatMonthLabel(this.calendarCursor) : ''
    },
    calendarDays() {
      if (!this.calendarCursor) return []
      const monthStart = startOfMonth(this.calendarCursor)
      const dayOffset = (monthStart.getDay() + 6) % 7
      const firstGridDay = new Date(monthStart)
      firstGridDay.setDate(monthStart.getDate() - dayOffset)
      const todayStr = formatDateStr(new Date())
      return Array.from({ length: 42 }, (_, index) => {
        const date = new Date(firstGridDay)
        date.setDate(firstGridDay.getDate() + index)
        const tradeDate = formatDateStr(date)
        return {
          key: `${tradeDate}-${index}`,
          day: date.getDate(),
          tradeDate,
          inMonth: tradeDate.startsWith(this.calendarCursor),
          hasRecap: this.recaps.some(item => item.tradeDate === tradeDate),
          isSelected: tradeDate === this.selectedDate,
          isTradingDay: isTradingDay(tradeDate),
          isFuture: tradeDate > todayStr
        }
      })
    },
    lastCapturedAtLabel() {
      if (!this.recap?.createdAt) return '暂无'
      return this.formatCaptureTime(this.recap.createdAt)
    },
    autoCaptureStatus() {
      if (this.capturingDate) return `正在采集 ${this.capturingDate}`
      return isTradingWindowNow() ? '盘中自动巡检中' : '当前不在自动采集时间段'
    }
  },
  async mounted() {
    const tradingDate = getCurrentTradingDate()
    this.selectedDate = tradingDate
    this.setCalendarCursor(tradingDate)

    try {
      await this.loadRecapList()
      const hasData = this.recaps.some(item => item.tradeDate === tradingDate)
      if (hasData) {
        await this.loadRecap(tradingDate)
      } else {
        await this.captureTradeDate(tradingDate, { forceApply: true })
      }
    } catch (error) {
      this.error = error.message
    }

    this.startAutoCaptureLoop()
  },
  beforeDestroy() {
    this.stopAutoCaptureLoop()
    if (this.toastTimer) clearTimeout(this.toastTimer)
  },
  methods: {
    setCalendarCursor(dateText) {
      if (dateText) this.calendarCursor = dateText.slice(0, 7)
    },
    shiftCalendarMonth(offset) {
      if (this.calendarCursor) this.calendarCursor = shiftMonth(this.calendarCursor, offset)
    },
    switchView(viewId) {
      this.activeView = viewId
      this.$nextTick(() => window.scrollTo({ top: 0, behavior: 'smooth' }))
    },
    formatCaptureTime(value) {
      const date = new Date(value)
      if (Number.isNaN(date.getTime())) return value
      return date.toLocaleString('zh-CN', { hour12: false })
    },
    setEmptyState() {
      this.recap = null
      this.indicators = null
      this.tradePlan = null
      this.trendPoints = []
    },
    applyResponse(resp, tradeDate = '') {
      if (!resp) return
      if (tradeDate && tradeDate !== this.selectedDate) return
      this.recap = resp.report
      this.indicators = resp.indicators
      this.tradePlan = resp.tradePlan
      this.trendPoints = resp.trendPoints
      this.error = ''
    },
    async loadRecapList() {
      this.recaps = await listRecaps()
    },
    async loadRecap(tradeDate = this.selectedDate) {
      if (!tradeDate) return
      this.loading = true
      this.error = ''
      try {
        const resp = await getRecap(tradeDate)
        this.applyResponse(resp, tradeDate)
        this.activeView = 'overview'
        this.setCalendarCursor(tradeDate)
      } catch (error) {
        if (tradeDate === this.selectedDate) this.setEmptyState()
        this.error = error.message
      } finally {
        this.loading = false
      }
    },
    async captureTradeDate(tradeDate, options = {}) {
      const { forceApply = false, showSuccessToast = false } = options
      this.capturingDate = tradeDate
      this.error = ''
      try {
        const resp = await captureRecap(tradeDate)
        await this.loadRecapList()
        if (forceApply || this.selectedDate === tradeDate) {
          this.applyResponse(resp, tradeDate)
          this.activeView = 'overview'
        }
        if (showSuccessToast) this.showToast('数据已更新')
        return resp
      } catch (err) {
        this.captureErrorMsg = err.message
        throw err
      } finally {
        this.capturingDate = ''
      }
    },
    async handleCalendarClick(day) {
      if (!day.isTradingDay || day.isFuture) return

      this.selectedDate = day.tradeDate
      this.setCalendarCursor(day.tradeDate)
      this.error = ''

      const hasExistingData = this.recaps.some(item => item.tradeDate === day.tradeDate)
      if (hasExistingData) {
        await this.loadRecap(day.tradeDate)
      } else {
        this.setEmptyState()
      }

      if (this.capturingDate) {
        if (!hasExistingData) {
          this.showToast(`正在采集 ${this.capturingDate}，完成后再采集 ${day.tradeDate}`)
        }
        return
      }

      const isCurrentTradingDay = day.tradeDate === getCurrentTradingDate()
      if (isCurrentTradingDay) {
        await this.captureTradeDate(day.tradeDate, { forceApply: true, showSuccessToast: hasExistingData })
        return
      }

      if (!hasExistingData) {
        await this.captureTradeDate(day.tradeDate, { forceApply: true })
        return
      }

      let oldResp = null
      try {
        oldResp = await getRecap(day.tradeDate)
      } catch (_) {
        oldResp = null
      }

      if (!oldResp) {
        await this.captureTradeDate(day.tradeDate, { forceApply: true })
        return
      }

      try {
        const newResp = await this.captureTradeDate(day.tradeDate)
        const diffs = recapsDiffSummary(oldResp.report, newResp.report)
        if (!diffs.length || !recapsAreDifferent(oldResp.report, newResp.report)) {
          this.showToast('数据无变化，无需更新')
          this.applyResponse(oldResp, day.tradeDate)
          return
        }

        this.pendingOldRecap = oldResp.report
        this.pendingNewRecap = newResp.report
        this.pendingNewResponse = newResp
        this.showOverwriteDialog = true
      } catch (_) {
        this.applyResponse(oldResp, day.tradeDate)
      }
    },
    confirmOverwrite() {
      if (this.pendingNewResponse) {
        this.applyResponse(this.pendingNewResponse, this.selectedDate)
      }
      this.showOverwriteDialog = false
      this.pendingOldRecap = null
      this.pendingNewRecap = null
      this.pendingNewResponse = null
      this.activeView = 'overview'
      this.loadRecapList()
    },
    cancelOverwrite() {
      this.showOverwriteDialog = false
      this.pendingOldRecap = null
      this.pendingNewRecap = null
      this.pendingNewResponse = null
      this.activeView = 'overview'
    },
    showToast(msg) {
      this.toastMessage = msg
      if (this.toastTimer) clearTimeout(this.toastTimer)
      this.toastTimer = setTimeout(() => {
        this.toastMessage = ''
      }, 3000)
    },
    shouldAutoCapture() {
      if (this.capturingDate || this.loading) return false
      if (!isTradingWindowNow()) return false
      if (typeof document !== 'undefined' && document.hidden) return false
      return true
    },
    startAutoCaptureLoop() {
      if (this.autoCaptureTimer) return
      this.autoCaptureTimer = window.setInterval(() => this.runAutoCapture(), 60 * 1000)
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

      const tradeDate = getCurrentTradingDate()
      this.lastAutoCaptureStartedAt = now

      try {
        await this.captureTradeDate(tradeDate, { forceApply: this.selectedDate === tradeDate })
      } catch (error) {
        if (this.selectedDate === tradeDate) {
          this.error = error.message
        }
      }
    }
  }
}
</script>
