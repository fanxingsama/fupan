<template>
  <div v-if="recap">
    <div class="page-header-block">
      <p class="eyebrow">总览</p>
      <h2>{{ recap.tradeDate }}</h2>
      <p class="muted">{{ recap.notes }}</p>
    </div>

    <!-- ▶ 风险预警信号 -->
    <div v-if="riskSignals.length" class="risk-signal-bar">
      <span
        v-for="(sig, idx) in riskSignals"
        :key="idx"
        class="risk-tag"
        :class="'risk-' + sig.level"
      >{{ sig.text }}</span>
    </div>

    <!-- ▶ 情绪驾驶舱 -->
    <div class="dashboard-grid">
      <!-- 情绪周期 -->
      <article class="dash-card dash-card-phase">
        <span class="dash-label">情绪周期</span>
        <strong class="dash-value" :style="{ color: emotionPhase.color }">{{ emotionPhase.label }}</strong>
        <span class="dash-sub">{{ phaseDescription }}</span>
      </article>
      <!-- 封板率 -->
      <article class="dash-card">
        <span class="dash-label">封板率</span>
        <strong class="dash-value" :class="sealRateClass">{{ sealRateDisplay }}</strong>
        <span class="dash-sub">成功封板 / (封板+炸板)</span>
      </article>
      <!-- 涨跌停比 -->
      <article class="dash-card">
        <span class="dash-label">涨跌停比</span>
        <strong class="dash-value">{{ limitRatioDisplay }}</strong>
        <span class="dash-sub">涨停 {{ limitUpTotal }} / 跌停 {{ limitDownTotal }}</span>
      </article>
      <!-- 赚钱效应 -->
      <article class="dash-card">
        <span class="dash-label">昨涨停溢价率</span>
        <strong class="dash-value" :class="premiumClass">{{ premiumDisplay }}</strong>
        <span class="dash-sub">昨涨停今日平均表现</span>
      </article>
      <!-- 昨涨停上涨比例 -->
      <article class="dash-card">
        <span class="dash-label">昨涨停上涨占比</span>
        <strong class="dash-value">{{ winRateDisplay }}</strong>
        <span class="dash-sub">上涨家数 / 昨涨停总数</span>
      </article>
      <!-- 昨炸板表现 -->
      <article class="dash-card">
        <span class="dash-label">昨炸板今日表现</span>
        <strong class="dash-value" :class="brokenAvgClass">{{ brokenAvgDisplay }}</strong>
        <span class="dash-sub">昨日炸板票今日均涨幅</span>
      </article>
    </div>

    <!-- ▶ 快速统计 -->
    <div class="summary-grid summary-grid-3">
      <article v-for="card in overviewCards" :key="card.label" class="summary-card">
        <span>{{ card.label }}</span>
        <strong>{{ card.value }}</strong>
      </article>
    </div>

    <section class="overview-stack">
      <!-- Row 1: 连板梯队 + 趋势 -->
      <div class="chart-row chart-row-2">
        <article class="panel chart-panel">
          <div class="chart-panel-head">
            <div>
              <h2>连板梯队</h2>
              <small>当日涨停板梯队结构</small>
            </div>
            <strong>最高 {{ currentMaxBoardHeight }}板</strong>
          </div>
          <div ref="ladderChart" class="chart-box"></div>
        </article>
        <article class="panel chart-panel">
          <div class="chart-panel-head">
            <div>
              <h2>封板率 &amp; 涨停数趋势</h2>
              <small>核心情绪指标联动</small>
            </div>
          </div>
          <div ref="sealRateTrendChart" class="chart-box"></div>
        </article>
      </div>

      <!-- Row 2: 原有三条趋势 -->
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
              <h2>连板高度趋势</h2>
              <small>观察情绪高度变化</small>
            </div>
            <strong>{{ currentMaxBoardHeight }}</strong>
          </div>
          <div ref="heightTrendChart" class="chart-box"></div>
        </article>
        <article class="panel chart-panel">
          <div class="chart-panel-head">
            <div>
              <h2>首板数量趋势</h2>
              <small>首板数量变化</small>
            </div>
            <strong>{{ recap.marketStats.firstLimitCount }}</strong>
          </div>
          <div ref="firstLimitTrendChart" class="chart-box"></div>
        </article>
      </div>

      <!-- Row 3: 新增 炸板数趋势 + 昨涨停溢价趋势 -->
      <div class="chart-row chart-row-2" v-if="trendPoints.length >= 2">
        <article class="panel chart-panel">
          <div class="chart-panel-head">
            <div>
              <h2>炸板数趋势</h2>
              <small>炸板突增 = 分歧加剧</small>
            </div>
            <strong>{{ brokenCount }}</strong>
          </div>
          <div ref="brokenTrendChart" class="chart-box"></div>
        </article>
        <article class="panel chart-panel">
          <div class="chart-panel-head">
            <div>
              <h2>昨涨停溢价趋势</h2>
              <small>判断追高风险演变</small>
            </div>
          </div>
          <div ref="premiumTrendChart" class="chart-box"></div>
        </article>
      </div>

      <!-- Row 4: 板块分析 -->
      <div class="chart-row">
        <article class="panel chart-panel">
          <div class="chart-panel-head">
            <div>
              <h2>首板集中板块</h2>
              <small>点击板块查看涨停个股</small>
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

    <article v-if="selectedSector" class="panel focus-detail-panel">
      <div class="section-head">
        <h2>「{{ selectedSector }}」首板涨停个股</h2>
        <button class="close-btn" @click="selectedSector = null">✕ 关闭</button>
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
import { cellClass, displayValue } from '../utils/format'
import { FOCUS_DETAIL_COLUMNS } from '../utils/columns'
import { buildTrendOption, buildDualTrendOption, buildBarOption, buildFocusOption, buildLadderOption, ensureChart } from '../utils/chart'

export default {
  name: 'OverviewPage',
  props: {
    recap: { type: Object, default: null },
    indicators: { type: Object, default: null },
    trendPoints: { type: Array, default: () => [] }
  },
  data() {
    return {
      selectedSector: null,
      chartMap: {}
    }
  },
  computed: {
    overviewCards() {
      if (!this.recap) return []
      return [
        { label: '上涨家数', value: this.recap.marketStats.upCount },
        { label: '下跌家数', value: this.recap.marketStats.downCount },
        { label: '首板数量', value: this.recap.marketStats.firstLimitCount }
      ]
    },
    currentMaxBoardHeight() {
      return this.indicators ? this.indicators.maxBoardHeight : 0
    },
    brokenCount() {
      return this.indicators ? this.indicators.brokenCount : 0
    },
    limitUpTotal() {
      return this.indicators ? this.indicators.limitUpTotal : 0
    },
    limitDownTotal() {
      return this.indicators ? this.indicators.limitDownTotal : 0
    },
    sealRateDisplay() {
      const v = this.indicators?.sealRate
      return v != null ? v + '%' : '-'
    },
    sealRateClass() {
      const v = this.indicators?.sealRate
      if (v == null) return ''
      if (v >= 75) return 'indicator-good'
      if (v < 55) return 'indicator-bad'
      return 'indicator-warn'
    },
    limitRatioDisplay() {
      const v = this.indicators?.limitRatio
      return v != null ? v + ' : 1' : '-'
    },
    premiumDisplay() {
      const v = this.indicators?.yesterdayLimitPremium
      if (v == null) return '-'
      return (v > 0 ? '+' : '') + v + '%'
    },
    premiumClass() {
      const v = this.indicators?.yesterdayLimitPremium
      if (v == null) return ''
      if (v >= 1) return 'indicator-good'
      if (v < -1) return 'indicator-bad'
      return 'indicator-warn'
    },
    winRateDisplay() {
      const v = this.indicators?.yesterdayLimitWinRate
      return v != null ? v + '%' : '-'
    },
    brokenAvgDisplay() {
      const v = this.indicators?.yesterdayBrokenAvg
      if (v == null) return '-'
      return (v > 0 ? '+' : '') + v + '%'
    },
    brokenAvgClass() {
      const v = this.indicators?.yesterdayBrokenAvg
      if (v == null) return ''
      if (v >= 0) return 'indicator-good'
      return 'indicator-bad'
    },
    emotionPhase() {
      if (!this.indicators) return { phase: 'unknown', label: '数据不足', color: '#94a3b8' }
      return {
        phase: this.indicators.emotionPhase,
        label: this.indicators.emotionLabel,
        color: this.indicators.emotionColor
      }
    },
    phaseDescription() {
      return this.indicators?.emotionDescription || ''
    },
    riskSignals() {
      return this.indicators?.riskSignals || []
    },
    boardLadder() {
      return this.indicators?.boardLadder || []
    },
    focusColumns() {
      return FOCUS_DETAIL_COLUMNS
    },
    focusSectorStocks() {
      if (!this.recap || !this.selectedSector) return []
      const sector = this.selectedSector
      return (this.recap.firstLimitToday || []).filter(item =>
        item.industry === sector || item.concept === sector
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
    Object.values(this.chartMap).forEach(c => { try { c.dispose() } catch (_) {} })
    this.chartMap = {}
  },
  methods: {
    getCellClass(col, item) { return cellClass(col, item) },
    getDisplayValue(col, item) { return displayValue(col, item) },
    resizeCharts() {
      Object.values(this.chartMap).forEach(c => { try { c.resize() } catch (_) {} })
    },
    renderCharts() {
      if (!this.recap) return

      // 连板梯队图
      const lc = ensureChart(this.chartMap, 'ladder', this.$refs.ladderChart)
      if (lc) lc.setOption(buildLadderOption(this.boardLadder), true)

      // 趋势图（数据直接来自后端 trendPoints）
      if (this.trendPoints.length) {
        const dates = this.trendPoints.map(p => p.tradeDate.slice(5))
        const upCounts = this.trendPoints.map(p => p.upCount)
        const heights = this.trendPoints.map(p => p.maxBoardHeight)
        const firstLimits = this.trendPoints.map(p => p.firstLimitCount)
        const sealRates = this.trendPoints.map(p => p.sealRate)
        const limitTotals = this.trendPoints.map(p => p.limitUpTotal)
        const brokenCounts = this.trendPoints.map(p => p.brokenCount)
        const premiums = this.trendPoints.map(p => p.yesterdayLimitPremium)

        const c1 = ensureChart(this.chartMap, 'upTrend', this.$refs.upTrendChart)
        const c2 = ensureChart(this.chartMap, 'heightTrend', this.$refs.heightTrendChart)
        const c3 = ensureChart(this.chartMap, 'firstLimitTrend', this.$refs.firstLimitTrendChart)

        if (c1) c1.setOption(buildTrendOption(upCounts, dates, '#f97316'), true)
        if (c2) c2.setOption(buildTrendOption(heights, dates, '#14213d'), true)
        if (c3) c3.setOption(buildTrendOption(firstLimits, dates, '#22c55e'), true)

        const sr = ensureChart(this.chartMap, 'sealRateTrend', this.$refs.sealRateTrendChart)
        if (sr) sr.setOption(buildDualTrendOption(sealRates, limitTotals, dates, '#8b5cf6', '#f97316', '封板率%', '涨停数'), true)

        if (this.$refs.brokenTrendChart) {
          const bc = ensureChart(this.chartMap, 'brokenTrend', this.$refs.brokenTrendChart)
          if (bc) bc.setOption(buildTrendOption(brokenCounts, dates, '#ef4444'), true)
        }

        if (this.$refs.premiumTrendChart) {
          const pc = ensureChart(this.chartMap, 'premiumTrend', this.$refs.premiumTrendChart)
          if (pc) pc.setOption(buildTrendOption(premiums, dates, '#0ea5e9'), true)
        }
      }

      // 板块图
      const fc = ensureChart(this.chartMap, 'focus', this.$refs.focusChart)
      const uc = ensureChart(this.chartMap, 'upSector', this.$refs.upSectorChart)
      const dc = ensureChart(this.chartMap, 'downSector', this.$refs.downSectorChart)

      if (fc) {
        fc.setOption(buildFocusOption(this.recap.firstLimitSectorFocus || {}), true)
        fc.off('click')
        fc.on('click', (params) => {
          if (params.name) this.selectedSector = params.name
        })
      }
      if (uc) uc.setOption(buildBarOption(this.recap.topUpSectors || [], '#fb7185'), true)
      if (dc) dc.setOption(buildBarOption(this.recap.topDownSectors || [], '#14b8a6', true), true)

      this.resizeCharts()
    }
  }
}
</script>
