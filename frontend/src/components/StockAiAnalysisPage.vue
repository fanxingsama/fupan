<template>
  <div class="stock-ai-page">
    <div class="page-header-block">
      <p class="eyebrow">AI分析个股</p>
      <h2>输入股票代码，用分钟级或日K量价关系学习买卖点</h2>
      <p class="muted">
        当前版本默认使用 AKShare 抓取 K 线，支持 1/5/15/30/60 分钟和日K。
        分析只看裸K与成交量，不引入基本面与消息面，更适合拿来训练节奏感和复盘思路。
      </p>
    </div>

    <article class="panel">
      <div class="section-head">
        <div>
          <h2>分析参数</h2>
          <p class="muted section-subtitle">示例：000001、600519、300750</p>
        </div>
      </div>

      <div class="stock-ai-form">
        <label>
          <span class="field-label">股票代码</span>
          <input v-model.trim="stockCode" maxlength="6" placeholder="输入 6 位股票代码" @keyup.enter="submit" />
        </label>

        <label>
          <span class="field-label">K线周期</span>
          <div class="timeframe-grid">
            <button
              v-for="item in timeframeOptions"
              :key="item.value"
              type="button"
              class="timeframe-chip"
              :class="{ active: timeframe === item.value }"
              @click="timeframe = item.value"
            >
              {{ item.label }}
            </button>
          </div>
        </label>
      </div>

      <div class="import-row stock-ai-actions">
        <div class="analysis-selection-summary">
          <strong>{{ currentSelectionLabel }}</strong>
          <span class="muted">默认分析最近一段同周期K线，AI会聚焦量价结构与学习型买卖点。</span>
        </div>
        <button :disabled="analyzing || !canSubmit" @click="submit">
          {{ analyzing ? '分析中...' : '开始分析' }}
        </button>
      </div>

      <p v-if="error" class="error">{{ error }}</p>
    </article>

    <template v-if="result">
      <article class="panel">
        <div class="insight-hero">
          <h3>{{ result.headline }}</h3>
          <p class="watch-summary">{{ result.summary }}</p>
        </div>

        <div class="stock-snapshot-grid">
          <div class="summary-card">
            <span>股票</span>
            <strong>{{ result.stockName || result.stockCode }}</strong>
            <small class="summary-hint">{{ result.stockCode }} / {{ result.timeframeLabel }}</small>
          </div>
          <div class="summary-card">
            <span>最新价</span>
            <strong>{{ formatPrice(result.latestPrice) }}</strong>
            <small class="summary-hint">区间涨跌 {{ formatPercent(result.periodChangePercent) }}</small>
          </div>
          <div class="summary-card">
            <span>量能比</span>
            <strong>{{ formatRatio(result.recentVolumeRatio) }}</strong>
            <small class="summary-hint">均量 {{ formatVolume(result.averageVolume) }}</small>
          </div>
          <div class="summary-card">
            <span>结构判断</span>
            <strong>{{ result.trendBias || '震荡' }}</strong>
            <small class="summary-hint">{{ result.actionBias || '等待确认' }} / 置信 {{ result.confidence || '中' }}</small>
          </div>
        </div>

        <div class="risk-signal-bar">
          <span class="risk-tag risk-safe">窗口 {{ result.windowStart }} 至 {{ result.windowEnd }}</span>
          <span class="risk-tag risk-warning">高点 {{ formatPrice(result.rangeHigh) }}</span>
          <span class="risk-tag risk-warning">低点 {{ formatPrice(result.rangeLow) }}</span>
          <span class="risk-tag" :class="result.status === 'ready' ? 'risk-safe' : result.status === 'disabled' ? 'risk-warning' : 'risk-danger'">
            {{ result.status === 'ready' ? 'AI已输出' : result.status === 'disabled' ? 'AI未启用，使用规则分析' : 'AI失败，已回退规则分析' }}
          </span>
        </div>
      </article>

      <article class="panel">
        <div class="section-head">
          <div>
            <h2>K线与成交量</h2>
            <p class="muted section-subtitle">展示最近 {{ result.candles.length }} 根K线，帮助你对照 AI 结论复盘。</p>
          </div>
        </div>
        <div ref="klineChart" class="chart-box chart-box-large"></div>
      </article>

      <article class="panel">
        <div class="insight-two-col stock-ai-learning-grid">
          <section class="insight-section">
            <h3>量价信号</h3>
            <ul class="text-list">
              <li v-for="item in result.volumePriceSignals" :key="item">{{ item }}</li>
            </ul>
          </section>
          <section class="insight-section">
            <h3>学习重点</h3>
            <ul class="text-list">
              <li v-for="item in result.learningPoints" :key="item">{{ item }}</li>
            </ul>
          </section>
          <section class="insight-section">
            <h3>偏学习视角的买点</h3>
            <ul class="text-list">
              <li v-for="item in result.buyPoints" :key="item">{{ item }}</li>
            </ul>
          </section>
          <section class="insight-section">
            <h3>偏学习视角的卖点</h3>
            <ul class="text-list">
              <li v-for="item in result.sellPoints" :key="item">{{ item }}</li>
            </ul>
          </section>
        </div>

        <section class="insight-section">
          <h3>风险提醒</h3>
          <ul class="text-list">
            <li v-for="item in result.riskWarnings" :key="item">{{ item }}</li>
          </ul>
        </section>

        <p class="muted ai-disclaimer">{{ result.disclaimer }}</p>
      </article>
    </template>
  </div>
</template>

<script>
import * as echarts from 'echarts'
import { analyzeStockWithAi } from '../api'

export default {
  name: 'StockAiAnalysisPage',
  data() {
    return {
      stockCode: '',
      timeframe: '15',
      analyzing: false,
      error: '',
      result: null,
      chart: null,
      timeframeOptions: [
        { value: '1', label: '1分钟' },
        { value: '5', label: '5分钟' },
        { value: '15', label: '15分钟' },
        { value: '30', label: '30分钟' },
        { value: '60', label: '60分钟' },
        { value: 'day', label: '日K' }
      ]
    }
  },
  computed: {
    canSubmit() {
      return /^\d{6}$/.test(this.stockCode)
    },
    currentSelectionLabel() {
      const option = this.timeframeOptions.find(item => item.value === this.timeframe)
      return `${this.stockCode || '未填写股票'} / ${option?.label || ''}`
    }
  },
  mounted() {
    window.addEventListener('resize', this.handleResize)
  },
  beforeDestroy() {
    window.removeEventListener('resize', this.handleResize)
    if (this.chart) {
      this.chart.dispose()
      this.chart = null
    }
  },
  methods: {
    async submit() {
      if (!this.canSubmit) {
        this.error = '请输入 6 位股票代码'
        return
      }
      this.analyzing = true
      this.error = ''
      try {
        this.result = await analyzeStockWithAi(this.stockCode, this.timeframe)
        this.$nextTick(() => this.renderChart())
      } catch (error) {
        this.error = error.message
      } finally {
        this.analyzing = false
      }
    },
    formatPrice(value) {
      return value == null ? '--' : Number(value).toFixed(2)
    },
    formatPercent(value) {
      return value == null ? '--' : `${Number(value).toFixed(2)}%`
    },
    formatRatio(value) {
      return value == null ? '--' : `${Number(value).toFixed(2)}x`
    },
    formatVolume(value) {
      if (value == null) return '--'
      const numeric = Number(value)
      if (numeric >= 100000000) return `${(numeric / 100000000).toFixed(2)}亿`
      if (numeric >= 10000) return `${(numeric / 10000).toFixed(2)}万`
      return `${numeric.toFixed(0)}`
    },
    handleResize() {
      if (this.chart) this.chart.resize()
    },
    renderChart() {
      if (!this.result?.candles?.length || !this.$refs.klineChart) return
      if (!this.chart) this.chart = echarts.init(this.$refs.klineChart)
      const labels = this.result.candles.map(item => item.time.length > 10 ? item.time.slice(5, 16) : item.time.slice(5))
      const candleData = this.result.candles.map(item => [item.open, item.close, item.low, item.high])
      const volumeData = this.result.candles.map(item => ({
        value: item.volume,
        itemStyle: { color: item.close >= item.open ? '#d62828' : '#2a9d8f' }
      }))

      this.chart.setOption({
        animationDuration: 500,
        legend: { data: ['K线', '成交量'], textStyle: { color: '#64748b' } },
        tooltip: {
          trigger: 'axis',
          axisPointer: { type: 'cross' },
          backgroundColor: '#0f172a',
          borderWidth: 0,
          textStyle: { color: '#f8fafc' }
        },
        grid: [
          { left: 18, right: 18, top: 24, height: '58%', containLabel: true },
          { left: 18, right: 18, top: '74%', height: '16%', containLabel: true }
        ],
        xAxis: [
          {
            type: 'category',
            data: labels,
            boundaryGap: true,
            axisLine: { lineStyle: { color: 'rgba(20,33,61,0.12)' } },
            axisLabel: { color: '#64748b' }
          },
          {
            type: 'category',
            gridIndex: 1,
            data: labels,
            boundaryGap: true,
            axisLine: { lineStyle: { color: 'rgba(20,33,61,0.12)' } },
            axisLabel: { show: false }
          }
        ],
        yAxis: [
          {
            scale: true,
            splitLine: { lineStyle: { color: 'rgba(20,33,61,0.08)', type: 'dashed' } },
            axisLabel: { color: '#64748b' }
          },
          {
            gridIndex: 1,
            splitNumber: 2,
            splitLine: { show: false },
            axisLabel: { color: '#64748b' }
          }
        ],
        dataZoom: [
          { type: 'inside', xAxisIndex: [0, 1], start: 0, end: 100 },
          { show: false, xAxisIndex: [0, 1], type: 'slider', start: 0, end: 100 }
        ],
        series: [
          {
            name: 'K线',
            type: 'candlestick',
            data: candleData,
            itemStyle: {
              color: '#d62828',
              color0: '#2a9d8f',
              borderColor: '#d62828',
              borderColor0: '#2a9d8f'
            }
          },
          {
            name: '成交量',
            type: 'bar',
            xAxisIndex: 1,
            yAxisIndex: 1,
            data: volumeData
          }
        ]
      })
    }
  }
}
</script>
