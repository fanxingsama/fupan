<template>
  <div v-if="recap">
    <div class="page-header-block">
      <p class="eyebrow">总览</p>
      <h2>{{ recap.tradeDate }}</h2>
      <p class="muted">{{ recap.notes }}</p>
    </div>

    <div class="summary-grid summary-grid-3">
      <article v-for="card in overviewCards" :key="card.label" class="summary-card">
        <span>{{ card.label }}</span>
        <strong>{{ card.value }}</strong>
      </article>
    </div>

    <section class="overview-stack">
      <div class="chart-row">
        <article class="panel chart-panel">
          <div class="chart-panel-head">
            <div>
              <h2>上涨家数趋势</h2>
              <small>最近 {{ trendReports.length }} 个交易日</small>
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
import { maxBoardHeight, cellClass, displayValue } from '../utils/format'
import { FOCUS_DETAIL_COLUMNS } from '../utils/columns'
import { buildTrendOption, buildBarOption, buildFocusOption, ensureChart } from '../utils/chart'

export default {
  name: 'OverviewPage',
  props: {
    recap: { type: Object, default: null },
    trendReports: { type: Array, default: () => [] }
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
      return this.recap ? maxBoardHeight(this.recap) : 0
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
    trendReports() {
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

      if (this.trendReports.length) {
        const dates = this.trendReports.map(r => r.tradeDate.slice(5))
        const upVals = this.trendReports.map(r => r.marketStats.upCount)
        const heightVals = this.trendReports.map(r => maxBoardHeight(r))
        const firstVals = this.trendReports.map(r => r.marketStats.firstLimitCount)

        const c1 = ensureChart(this.chartMap, 'upTrend', this.$refs.upTrendChart)
        const c2 = ensureChart(this.chartMap, 'heightTrend', this.$refs.heightTrendChart)
        const c3 = ensureChart(this.chartMap, 'firstLimitTrend', this.$refs.firstLimitTrendChart)

        if (c1) c1.setOption(buildTrendOption(upVals, dates, '#f97316'), true)
        if (c2) c2.setOption(buildTrendOption(heightVals, dates, '#14213d'), true)
        if (c3) c3.setOption(buildTrendOption(firstVals, dates, '#22c55e'), true)
      }

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
