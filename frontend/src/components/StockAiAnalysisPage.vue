<template>
  <div class="stock-ai-page">
    <div class="page-header-block">
      <p class="eyebrow">AI分析个股</p>
      <h2>上传历史K线数据，让 AI 基于你本地导出的历史数据分析量价结构</h2>
      <p class="muted">
        支持上传 <code>csv / tsv / txt / json</code>。系统会优先识别常见的中英文列名，例如
        <code>time/open/high/low/close/volume/amount</code> 或
        <code>时间/开盘/最高/最低/收盘/成交量/成交额</code>。
        如果文件里没有股票代码或名称，你也可以手动补充。
      </p>
    </div>

    <article class="panel">
      <div class="section-head">
        <div>
          <h2>上传参数</h2>
          <p class="muted section-subtitle">建议优先上传 QMT 导出的单只个股历史K线文件</p>
        </div>
      </div>

      <div class="stock-ai-form">
        <label>
          <span class="field-label">历史数据文件</span>
          <input
            ref="fileInput"
            type="file"
            accept=".csv,.tsv,.txt,.json,application/json,text/csv,text/plain"
            @change="handleFileChange"
          />
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

        <label>
          <span class="field-label">股票代码（可选）</span>
          <input v-model.trim="stockCode" maxlength="6" placeholder="文件里没有代码时再填写" />
        </label>

        <label>
          <span class="field-label">股票名称（可选）</span>
          <input v-model.trim="stockName" maxlength="20" placeholder="文件里没有名称时再填写" />
        </label>
      </div>

      <div class="import-row stock-ai-actions">
        <div class="analysis-selection-summary">
          <strong>{{ currentSelectionLabel }}</strong>
          <span class="muted">上传后会直接基于文件中的历史K线做分析，不再联网抓取个股数据。</span>
        </div>
        <button :disabled="analyzing || !canSubmit" @click="submit">
          {{ analyzing ? '分析中...' : '开始分析' }}
        </button>
      </div>

      <div v-if="selectedFile" class="analysis-preview-grid">
        <div class="preview-card">
          <strong>{{ selectedFile.name }}</strong>
          <span>{{ formatFileSize(selectedFile.size) }}</span>
          <small>{{ timeframeLabel }}</small>
        </div>
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
            <p class="muted section-subtitle">展示最近 {{ result.candles.length }} 根K线，帮助你对照 AI 结论复盘</p>
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
      selectedFile: null,
      stockCode: '',
      stockName: '',
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
      return !!this.selectedFile
    },
    timeframeLabel() {
      return this.timeframeOptions.find(item => item.value === this.timeframe)?.label || ''
    },
    currentSelectionLabel() {
      return `${this.stockCode || '文件识别或未填写'} / ${this.stockName || '文件识别或未填写'} / ${this.timeframeLabel}`
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
    handleFileChange(event) {
      const [file] = Array.from(event.target.files || [])
      this.selectedFile = file || null
      this.result = null
      this.error = ''
    },
    async submit() {
      if (!this.selectedFile) {
        this.error = '请先上传历史数据文件'
        return
      }
      this.analyzing = true
      this.error = ''
      try {
        this.result = await analyzeStockWithAi(this.selectedFile, this.timeframe, this.stockCode, this.stockName)
        this.stockCode = this.result.stockCode || this.stockCode
        this.stockName = this.result.stockName || this.stockName
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
    formatFileSize(value) {
      const numeric = Number(value || 0)
      if (numeric >= 1024 * 1024) return `${(numeric / (1024 * 1024)).toFixed(2)} MB`
      if (numeric >= 1024) return `${(numeric / 1024).toFixed(1)} KB`
      return `${numeric} B`
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
