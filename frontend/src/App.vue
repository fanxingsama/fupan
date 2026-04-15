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

    <!-- 采集失败弹窗 -->
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
        <p class="muted">把炸板、连板、首板和板块强弱沉淀成可以回看的交易笔记。</p>
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
        :selected-date="selectedDate"
        :capturing="capturing"
        @shift-month="shiftCalendarMonth"
        @day-click="handleCalendarClick"
      />
    </aside>

    <main class="content">
      <p v-if="error" class="error">{{ error }}</p>
      <p v-if="loading && !capturing" class="muted">正在加载复盘数据...</p>
      <p v-if="capturing" class="muted">正在采集 {{ selectedDate }} 的数据，请稍候...</p>
      <p v-if="!recap && !loading && !capturing && !error" class="muted center-hint">暂无复盘数据</p>

      <OverviewPage
        v-if="activeView === 'overview' && recap"
        :recap="recap"
        :trend-reports="trendReports"
      />

      <TabbedTablePage
        v-if="activeView === 'broken' && recap"
        :recap="recap"
        page-type="broken"
      />

      <TabbedTablePage
        v-if="activeView === 'consecutive' && recap"
        :recap="recap"
        page-type="consecutive"
      />

      <TabbedTablePage
        v-if="activeView === 'firstLimit' && recap"
        :recap="recap"
        page-type="firstLimit"
      />

      <HighRankPage
        v-if="activeView === 'highRank' && recap"
        :recap="recap"
      />
    </main>
  </div>
</template>

<script>
import { captureRecap, getRecap, listRecaps } from './api'
import { formatDateStr, formatMonthLabel, startOfMonth, shiftMonth, isTradingDay, getCurrentTradingDate, isTradingWindowNow, recapsAreDifferent } from './utils/trading'

import RecapCalendar from './components/RecapCalendar.vue'
import OverviewPage from './components/OverviewPage.vue'
import TabbedTablePage from './components/TabbedTablePage.vue'
import HighRankPage from './components/HighRankPage.vue'
import OverwriteDialog from './components/OverwriteDialog.vue'
import ToastBar from './components/ToastBar.vue'

export default {
  name: 'App',
  components: { RecapCalendar, OverviewPage, TabbedTablePage, HighRankPage, OverwriteDialog, ToastBar },
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
      calendarCursor: '',
      autoCaptureTimer: null,
      lastAutoCaptureStartedAt: 0,
      // 覆盖确认弹窗
      showOverwriteDialog: false,
      pendingOldRecap: null,
      pendingNewRecap: null,
      // Toast
      toastMessage: '',
      toastTimer: null,
      // 采集失败弹窗
      captureErrorMsg: ''
    }
  },
  computed: {
    navItems() {
      return [
        { id: 'overview', label: '总览' },
        { id: 'broken', label: '炸板票板块' },
        { id: 'consecutive', label: '连板票板块' },
        { id: 'firstLimit', label: '首板票板块' },
        { id: 'highRank', label: '高标板块' }
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
        const hasRecap = this.recaps.some(item => item.tradeDate === tradeDate)
        return {
          key: `${tradeDate}-${index}`,
          day: date.getDate(),
          tradeDate,
          inMonth: tradeDate.startsWith(this.calendarCursor),
          hasRecap,
          isSelected: tradeDate === this.selectedDate,
          isTradingDay: isTradingDay(tradeDate),
          isFuture: tradeDate > todayStr
        }
      })
    }
  },

  async mounted() {
    const tradingDate = getCurrentTradingDate()
    this.selectedDate = tradingDate
    this.setCalendarCursor(tradingDate)

    try {
      await this.loadRecapList()
      const hasData = this.recaps.some(r => r.tradeDate === tradingDate)
      if (hasData) {
        await this.loadRecap()
      } else {
        await this.autoCapture(tradingDate)
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

    // ── 日历点击 → 采集 + 数据比对 ──
    async handleCalendarClick(day) {
      if (!day.isTradingDay || this.capturing || day.isFuture) return

      this.selectedDate = day.tradeDate
      this.setCalendarCursor(day.tradeDate)

      const isCurrentTradingDay = day.tradeDate === getCurrentTradingDate()
      const hasExistingData = this.recaps.some(r => r.tradeDate === day.tradeDate)

      if (isCurrentTradingDay) {
        // 交易日当日：直接采集替换，不弹窗
        if (hasExistingData) {
          // 先展示旧数据
          try {
            this.recap = await getRecap(day.tradeDate)
            this.activeView = 'overview'
          } catch (_) {}
        }
        this.capturing = true
        this.error = ''
        try {
          this.recap = await captureRecap(day.tradeDate)
          this.activeView = 'overview'
          await this.loadRecapList()
          this.showToast('数据已更新')
        } catch (err) {
          this.captureErrorMsg = err.message
        } finally {
          this.capturing = false
        }
      } else if (hasExistingData) {
        // 历史日期且有旧数据：采集后比对，有变动弹窗展示详情
        let oldRecap = null
        try { oldRecap = await getRecap(day.tradeDate) } catch (_) {}

        if (oldRecap) {
          this.recap = oldRecap
          this.activeView = 'overview'
        }

        this.capturing = true
        this.error = ''
        try {
          const newRecap = await captureRecap(day.tradeDate)

          if (oldRecap && !recapsAreDifferent(oldRecap, newRecap)) {
            this.showToast('数据无变动，无需更新')
            await this.loadRecapList()
          } else if (oldRecap) {
            this.pendingOldRecap = oldRecap
            this.pendingNewRecap = newRecap
            this.showOverwriteDialog = true
          } else {
            this.recap = newRecap
            this.activeView = 'overview'
            await this.loadRecapList()
          }
        } catch (err) {
          this.captureErrorMsg = err.message
        } finally {
          this.capturing = false
        }
      } else {
        // 无旧数据：直接采集
        await this.autoCapture(day.tradeDate)
      }
    },

    async autoCapture(tradeDate) {
      this.capturing = true
      this.error = ''
      try {
        this.recap = await captureRecap(tradeDate)
        this.activeView = 'overview'
        await this.loadRecapList()
      } catch (err) {
        this.captureErrorMsg = err.message
      } finally {
        this.capturing = false
      }
    },

    // ── 覆盖确认弹窗 ──
    confirmOverwrite() {
      this.recap = this.pendingNewRecap
      this.showOverwriteDialog = false
      this.pendingOldRecap = null
      this.pendingNewRecap = null
      this.activeView = 'overview'
      this.loadRecapList()
    },
    cancelOverwrite() {
      this.recap = this.pendingOldRecap
      this.showOverwriteDialog = false
      this.pendingOldRecap = null
      this.pendingNewRecap = null
      this.activeView = 'overview'
    },

    // ── Toast ──
    showToast(msg) {
      this.toastMessage = msg
      if (this.toastTimer) clearTimeout(this.toastTimer)
      this.toastTimer = setTimeout(() => { this.toastMessage = '' }, 3000)
    },

    // ── 自动采集（盘中） ──
    shouldAutoCapture() {
      const tradingDate = getCurrentTradingDate()
      if (this.selectedDate !== tradingDate) return false
      if (this.capturing || this.loading) return false
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
      this.lastAutoCaptureStartedAt = now
      this.capturing = true
      this.error = ''
      try {
        this.recap = await captureRecap(this.selectedDate)
        await this.loadRecapList()
      } catch (error) {
        this.error = error.message
      } finally {
        this.capturing = false
      }
    },

    // ── 数据加载 ──
    async loadTrendReports() {
      const dates = this.recaps.map(item => item.tradeDate).sort().slice(-20)
      if (!dates.length) return (this.trendReports = [])
      const reports = await Promise.all(dates.map(date => getRecap(date)))
      this.trendReports = reports.sort((a, b) => a.tradeDate.localeCompare(b.tradeDate))
    },
    async loadRecapList() {
      this.recaps = await listRecaps()
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
      } catch (error) {
        this.recap = null
        this.error = error.message
      } finally {
        this.loading = false
      }
    }
  }
}
</script>
