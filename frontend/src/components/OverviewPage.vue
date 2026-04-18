<template>
  <div v-if="recap">
    <div class="page-header-block">
      <p class="eyebrow">交易驾驶舱</p>
      <h2>{{ recap.tradeDate }}</h2>
      <p class="muted">{{ tradePlan?.headline || recap.notes }}</p>
    </div>

    <section v-if="boardIndexes.length" class="board-index-grid">
      <article v-for="item in boardIndexes" :key="item.key || item.label" class="board-index-card">
        <span class="dash-label">{{ item.label }}</span>
        <strong class="board-index-latest">{{ item.latest }}</strong>
        <div class="board-index-change" :class="percentClass(item.changePercent)">
          <span>{{ item.changePercent || '-' }}</span>
          <small>{{ item.changeAmount ? `${item.changeAmount} 点` : '-' }}</small>
        </div>
      </article>
    </section>

    <div v-if="riskSignals.length" class="risk-signal-bar">
      <span
        v-for="(sig, idx) in riskSignals"
        :key="idx"
        class="risk-tag"
        :class="'risk-' + sig.level"
      >{{ sig.text }}</span>
    </div>

    <section class="battlefield-grid">
      <article class="panel battle-card battle-card-hero">
        <span class="eyebrow">次日作战结论</span>
        <h3>{{ tradePlan?.headline || '等待复盘结果' }}</h3>
        <p class="muted">{{ tradePlan?.executionSummary }}</p>
        <div class="battle-meta">
          <div>
            <span>市场倾向</span>
            <strong>{{ tradePlan?.marketBias || '-' }}</strong>
          </div>
          <div>
            <span>主做模式</span>
            <strong>{{ tradePlan?.tradeMode || '-' }}</strong>
          </div>
          <div>
            <span>仓位建议</span>
            <strong>{{ tradePlan?.positionAdvice || '-' }}</strong>
          </div>
        </div>
      </article>

      <article class="panel battle-card">
        <span class="eyebrow">重点关注</span>
        <ul class="text-list">
          <li v-for="(item, index) in tradePlan?.nextDayFocus || []" :key="'focus-' + index">{{ item }}</li>
        </ul>
      </article>

      <article class="panel battle-card">
        <span class="eyebrow">风险提醒</span>
        <ul class="text-list">
          <li v-for="(item, index) in tradePlan?.riskFocus || []" :key="'risk-' + index">{{ item }}</li>
        </ul>
      </article>
    </section>

    <div class="dashboard-grid">
      <article class="dash-card dash-card-phase">
        <span class="dash-label">情绪周期</span>
        <strong class="dash-value" :style="{ color: emotionPhase.color }">{{ emotionPhase.label }}</strong>
        <span class="dash-sub">{{ phaseDescription }}</span>
      </article>
      <article class="dash-card">
        <span class="dash-label">封板率</span>
        <strong class="dash-value" :class="sealRateClass">{{ sealRateDisplay }}</strong>
        <span class="dash-sub">成功封板 / (封板 + 炸板)</span>
      </article>
      <article class="dash-card">
        <span class="dash-label">涨跌停比</span>
        <strong class="dash-value">{{ limitRatioDisplay }}</strong>
        <span class="dash-sub">涨停 {{ limitUpTotal }} / 跌停 {{ limitDownTotal }}</span>
      </article>
      <article class="dash-card">
        <span class="dash-label">昨日涨停溢价</span>
        <strong class="dash-value" :class="premiumClass">{{ premiumDisplay }}</strong>
        <span class="dash-sub">昨日涨停股今日平均表现</span>
      </article>
      <article class="dash-card">
        <span class="dash-label">投机热度分</span>
        <strong class="dash-value">{{ scoreDisplay(indicators?.speculationScore) }}</strong>
        <span class="dash-sub">封板率、涨停家数、连板高度综合</span>
      </article>
      <article class="dash-card">
        <span class="dash-label">接力承接分</span>
        <strong class="dash-value">{{ scoreDisplay(indicators?.continuationScore) }}</strong>
        <span class="dash-sub">昨日涨停溢价与承接强度综合</span>
      </article>
      <article class="dash-card">
        <span class="dash-label">市场宽度</span>
        <strong class="dash-value">{{ scoreDisplay(indicators?.breadthScore) }}</strong>
        <span class="dash-sub">上涨家数 / 涨跌家数</span>
      </article>
      <article class="dash-card">
        <span class="dash-label">最高板</span>
        <strong class="dash-value">{{ currentMaxBoardHeight }} 板</strong>
        <span class="dash-sub">观察高标是否仍在抬高度</span>
      </article>
    </div>

    <section v-if="themes.length" class="panel">
      <div class="section-head">
        <div>
          <h2>主线强度</h2>
          <p class="muted section-subtitle">不只是看板块涨跌，而是帮助判断哪些题材值得明天优先盯。</p>
        </div>
      </div>
      <div class="theme-grid">
        <article v-for="theme in themes" :key="theme.name" class="theme-card">
          <div class="theme-head">
            <h3>{{ theme.name }}</h3>
            <span class="theme-phase">{{ theme.phase }}</span>
          </div>
          <strong class="theme-score">{{ theme.score }}</strong>
          <p>{{ theme.comment }}</p>
        </article>
      </div>
    </section>

    <AiBriefingPanel :trade-date="recap.tradeDate" />

    <AiInsightPanel :trade-date="recap.tradeDate" />

    <AiSummaryPanel :trade-date="recap.tradeDate" />

    <section v-if="watchStocks.length" class="panel">
      <div class="section-head">
        <div>
          <h2>重点票预案</h2>
          <p class="muted section-subtitle">把复盘结果直接转成“明天看谁、怎么做、什么情况下放弃”。</p>
        </div>
      </div>
      <div class="watchlist-grid">
        <article v-for="stock in watchStocks" :key="stock.code + stock.name" class="watch-card">
          <div class="watch-card-head">
            <div>
              <h3>{{ stock.name }}</h3>
              <small>{{ stock.code }} · {{ stock.theme }}</small>
            </div>
            <span class="watch-score">{{ stock.score }}</span>
          </div>
          <div class="tag-list">
            <span class="tag">{{ stock.role }}</span>
          </div>
          <p class="watch-summary">{{ stock.summary }}</p>
          <div class="watch-plan-row">
            <div>
              <span class="field-label">方案 A</span>
              <p>{{ stock.planA }}</p>
            </div>
            <div>
              <span class="field-label">方案 B</span>
              <p>{{ stock.planB }}</p>
            </div>
          </div>
          <p class="watch-risk">{{ stock.riskNote }}</p>
        </article>
      </div>
    </section>

    <section v-if="candidatePools.length" class="pool-grid">
      <article v-for="pool in candidatePools" :key="pool.key" class="panel">
        <div class="section-head">
          <div>
            <h2>{{ pool.title }}</h2>
            <p class="muted section-subtitle">{{ pool.description }}</p>
          </div>
        </div>
        <div class="mini-stock-list">
          <button
            v-for="stock in pool.stocks"
            :key="pool.key + stock.code + stock.name"
            type="button"
            class="mini-stock-item"
          >
            <div>
              <strong>{{ stock.name }}</strong>
              <small>{{ stock.theme }} · {{ stock.role }}</small>
            </div>
            <span>{{ stock.score }}</span>
          </button>
          <p v-if="!pool.stocks.length" class="muted">暂无符合条件的标的</p>
        </div>
      </article>
    </section>

    <section v-if="schedule.length" class="panel">
      <div class="section-head">
        <div>
          <h2>盘前到尾盘节奏</h2>
          <p class="muted section-subtitle">按时段拆开观察重点，避免临盘信息过载。</p>
        </div>
      </div>
      <div class="schedule-grid">
        <article v-for="item in schedule" :key="item.window" class="schedule-card">
          <span class="schedule-window">{{ item.window }}</span>
          <h3>{{ item.title }}</h3>
          <p>{{ item.focus }}</p>
        </article>
      </div>
    </section>

    <div class="summary-grid summary-grid-3">
      <article v-for="card in overviewCards" :key="card.label" class="summary-card">
        <span>{{ card.label }}</span>
        <strong>{{ card.value }}</strong>
      </article>
    </div>

    <section class="overview-stack">
      <div class="chart-row chart-row-2">
        <article class="panel chart-panel">
          <div class="chart-panel-head">
            <div>
              <h2>连板梯队</h2>
              <small>看高标是否断层，低位是否持续补充。</small>
            </div>
            <strong>{{ currentMaxBoardHeight }} 板</strong>
          </div>
          <div ref="ladderChart" class="chart-box"></div>
        </article>
        <article class="panel chart-panel">
          <div class="chart-panel-head">
            <div>
              <h2>封板率与涨停数趋势</h2>
              <small>情绪指标共振时，进攻容错率更高。</small>
            </div>
          </div>
          <div ref="sealRateTrendChart" class="chart-box"></div>
        </article>
      </div>

      <div class="chart-row">
        <article class="panel chart-panel">
          <div class="chart-panel-head">
            <div>
              <h2>上涨家数趋势</h2>
              <small>最近 {{ trendPoints.length }} 个交易日</small>
            </div>
            <strong>{{ recap.marketStats.upCount }}</strong>
          </div>
          <div ref="upTrendChart" class="chart-box"></div>
        </article>
        <article class="panel chart-panel">
          <div class="chart-panel-head">
            <div>
              <h2>主线首板聚焦</h2>
              <small>点击板块查看对应的首板股票。</small>
            </div>
          </div>
          <div ref="focusChart" class="chart-box"></div>
        </article>
        <article class="panel chart-panel">
          <div class="chart-panel-head">
            <div>
              <h2>上涨板块前列</h2>
              <small>辅助判断主线和轮动方向。</small>
            </div>
          </div>
          <div ref="upSectorChart" class="chart-box"></div>
        </article>
      </div>
    </section>

    <article v-if="selectedSector" class="panel focus-detail-panel">
      <div class="section-head">
        <h2>{{ selectedSector }} 首板涨停股</h2>
        <button class="close-btn" @click="selectedSector = null">关闭</button>
      </div>
      <div class="table-scroll">
        <table>
          <thead>
            <tr>
              <th v-for="col in focusColumns" :key="col.key">{{ col.label }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in focusSectorStocks" :key="item.code">
              <td v-for="col in focusColumns" :key="col.key" :class="getCellClass(col, item)">
                {{ getDisplayValue(col, item) }}
              </td>
            </tr>
            <tr v-if="!focusSectorStocks.length">
              <td :colspan="focusColumns.length" class="empty-hint">暂无数据</td>
            </tr>
          </tbody>
        </table>
      </div>
    </article>
  </div>
</template>

<script>
import { cellClass, displayValue, percentClass } from '../utils/format'
import { FOCUS_DETAIL_COLUMNS } from '../utils/columns'
import {
  buildTrendOption,
  buildDualTrendOption,
  buildBarOption,
  buildFocusOption,
  buildLadderOption,
  ensureChart
} from '../utils/chart'

const AiBriefingPanel = () => import('./AiBriefingPanel.vue')
const AiInsightPanel = () => import('./AiInsightPanel.vue')
const AiSummaryPanel = () => import('./AiSummaryPanel.vue')

export default {
  name: 'OverviewPage',
  components: { AiBriefingPanel, AiInsightPanel, AiSummaryPanel },
  props: {
    recap: { type: Object, default: null },
    indicators: { type: Object, default: null },
    tradePlan: { type: Object, default: null },
    trendPoints: { type: Array, default: () => [] }
  },
  data() {
    return {
      selectedSector: null,
      chartMap: {}
    }
  },
  computed: {
    boardIndexes() {
      return this.recap?.boardIndexes || []
    },
    riskSignals() {
      return this.indicators?.riskSignals || []
    },
    emotionPhase() {
      if (!this.indicators) return { label: '数据不足', color: '#94a3b8' }
      return { label: this.indicators.emotionLabel, color: this.indicators.emotionColor }
    },
    phaseDescription() {
      return this.indicators?.emotionDescription || ''
    },
    limitUpTotal() {
      return this.indicators?.limitUpTotal || 0
    },
    limitDownTotal() {
      return this.indicators?.limitDownTotal || 0
    },
    currentMaxBoardHeight() {
      return this.indicators?.maxBoardHeight || 0
    },
    sealRateDisplay() {
      return this.indicators?.sealRate != null ? `${this.indicators.sealRate}%` : '-'
    },
    sealRateClass() {
      const value = this.indicators?.sealRate
      if (value == null) return ''
      if (value >= 75) return 'indicator-good'
      if (value < 55) return 'indicator-bad'
      return 'indicator-warn'
    },
    limitRatioDisplay() {
      return this.indicators?.limitRatio != null ? `${this.indicators.limitRatio} : 1` : '-'
    },
    premiumDisplay() {
      const value = this.indicators?.yesterdayLimitPremium
      if (value == null) return '-'
      return `${value > 0 ? '+' : ''}${value}%`
    },
    premiumClass() {
      const value = this.indicators?.yesterdayLimitPremium
      if (value == null) return ''
      if (value >= 1) return 'indicator-good'
      if (value < -1) return 'indicator-bad'
      return 'indicator-warn'
    },
    overviewCards() {
      if (!this.recap) return []
      return [
        { label: '上涨家数', value: this.recap.marketStats.upCount },
        { label: '下跌家数', value: this.recap.marketStats.downCount },
        { label: '首板数量', value: this.recap.marketStats.firstLimitCount },
        { label: '炸板数量', value: this.indicators?.brokenCount || 0 }
      ]
    },
    themes() {
      return this.tradePlan?.primaryThemes || []
    },
    watchStocks() {
      return this.tradePlan?.watchStocks || []
    },
    candidatePools() {
      return this.tradePlan?.candidatePools || []
    },
    schedule() {
      return this.tradePlan?.schedule || []
    },
    boardLadder() {
      return this.indicators?.boardLadder || []
    },
    focusColumns() {
      return FOCUS_DETAIL_COLUMNS
    },
    focusSectorStocks() {
      if (!this.recap || !this.selectedSector) return []
      return (this.recap.firstLimitToday || []).filter(item =>
        item.industry === this.selectedSector || item.concept === this.selectedSector
      )
    }
  },
  watch: {
    recap() {
      this.selectedSector = null
      this.$nextTick(() => this.renderCharts())
    },
    trendPoints() {
      this.$nextTick(() => this.renderCharts())
    }
  },
  mounted() {
    window.addEventListener('resize', this.resizeCharts)
    this.$nextTick(() => this.renderCharts())
  },
  beforeDestroy() {
    window.removeEventListener('resize', this.resizeCharts)
    Object.values(this.chartMap).forEach(chart => {
      try { chart.dispose() } catch (_) {}
    })
    this.chartMap = {}
  },
  methods: {
    percentClass,
    scoreDisplay(value) {
      return value != null ? (value.toFixed ? value.toFixed(1) : value) : '-'
    },
    getCellClass(col, item) {
      return cellClass(col, item)
    },
    getDisplayValue(col, item) {
      return displayValue(col, item)
    },
    resizeCharts() {
      Object.values(this.chartMap).forEach(chart => {
        try { chart.resize() } catch (_) {}
      })
    },
    async renderCharts() {
      if (!this.recap) return

      const ladderChart = await ensureChart(this.chartMap, 'ladder', this.$refs.ladderChart)
      if (ladderChart) ladderChart.setOption(buildLadderOption(this.boardLadder), true)

      if (this.trendPoints.length) {
        const dates = this.trendPoints.map(point => point.tradeDate.slice(5))
        const upCounts = this.trendPoints.map(point => point.upCount)
        const sealRates = this.trendPoints.map(point => point.sealRate)
        const limitTotals = this.trendPoints.map(point => point.limitUpTotal)

        const upTrend = await ensureChart(this.chartMap, 'upTrend', this.$refs.upTrendChart)
        const sealRateTrend = await ensureChart(this.chartMap, 'sealRateTrend', this.$refs.sealRateTrendChart)

        if (upTrend) upTrend.setOption(buildTrendOption(upCounts, dates, '#f97316'), true)
        if (sealRateTrend) {
          sealRateTrend.setOption(
            buildDualTrendOption(sealRates, limitTotals, dates, '#8b5cf6', '#f97316', '封板率', '涨停数'),
            true
          )
        }
      }

      const focusChart = await ensureChart(this.chartMap, 'focus', this.$refs.focusChart)
      const upSectorChart = await ensureChart(this.chartMap, 'upSector', this.$refs.upSectorChart)

      if (focusChart) {
        focusChart.setOption(buildFocusOption(this.recap.firstLimitSectorFocus || {}), true)
        focusChart.off('click')
        focusChart.on('click', params => {
          if (params.name) this.selectedSector = params.name
        })
      }

      if (upSectorChart) upSectorChart.setOption(buildBarOption(this.recap.topUpSectors || [], '#fb7185'), true)
      this.resizeCharts()
    }
  }
}
</script>
