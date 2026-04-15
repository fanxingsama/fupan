<template>
  <div class="trade-journal-page">
    <div class="page-header-block">
      <p class="eyebrow">个人交易日志</p>
      <h2>导入成交记录并结合市场环境复盘</h2>
      <p class="muted">当前 MVP 支持导入券商导出的 CSV / TSV 文本文件，并自动关联当天市场情绪与主线信息。</p>
    </div>

    <article class="panel">
      <div class="section-head">
        <div>
          <h2>导入交易记录</h2>
          <p class="muted section-subtitle">建议优先导出包含成交日期、代码、名称、买卖、成交均价、成交数量、成交金额的文件。</p>
        </div>
      </div>
      <div class="import-row">
        <input ref="fileInput" type="file" accept=".csv,.txt,.tsv" @change="handleFileChange" />
        <button :disabled="!selectedFile || importing" @click="submitImport">
          {{ importing ? '导入中...' : '导入成交记录' }}
        </button>
      </div>
      <p v-if="selectedFile" class="muted">已选择：{{ selectedFile.name }}</p>
      <div v-if="importResult" class="import-result">
        <strong>导入 {{ importResult.importedCount }} 条，跳过 {{ importResult.skippedCount }} 条</strong>
        <ul v-if="importResult.warnings?.length" class="text-list">
          <li v-for="(item, index) in importResult.warnings" :key="index">{{ item }}</li>
        </ul>
      </div>
      <p v-if="error" class="error">{{ error }}</p>
    </article>

    <article v-for="day in journalDays" :key="day.tradeDate" class="panel trade-day-panel">
      <div class="section-head">
        <div>
          <h2>{{ day.tradeDate }}</h2>
          <p class="muted section-subtitle">{{ day.marketContext.headline }}</p>
        </div>
      </div>

      <div class="summary-grid">
        <div class="summary-card">
          <span>当日成交笔数</span>
          <strong>{{ day.tradeCount }}</strong>
        </div>
        <div class="summary-card">
          <span>买入 / 卖出</span>
          <strong>{{ day.buyCount }} / {{ day.sellCount }}</strong>
        </div>
        <div class="summary-card">
          <span>成交总额</span>
          <strong>{{ formatAmount(day.totalAmount) }}</strong>
        </div>
        <div class="summary-card">
          <span>情绪 / 模式</span>
          <strong>{{ day.marketContext.emotionLabel }} / {{ day.marketContext.tradeMode }}</strong>
        </div>
      </div>

      <div class="journal-meta-grid">
        <div class="capture-meta-item">
          <span>市场倾向</span>
          <strong>{{ day.marketContext.marketBias }}</strong>
        </div>
        <div class="capture-meta-item">
          <span>领涨主题</span>
          <strong>{{ day.marketContext.leadingTheme }}</strong>
        </div>
      </div>

      <div class="table-scroll">
        <table>
          <thead>
            <tr>
              <th>代码</th>
              <th>名称</th>
              <th>买卖</th>
              <th>均价</th>
              <th>数量</th>
              <th>金额</th>
              <th>费用</th>
              <th>来源文件</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="trade in day.trades" :key="trade.id">
              <td>{{ trade.code }}</td>
              <td>{{ trade.name || '-' }}</td>
              <td>{{ trade.side }}</td>
              <td>{{ trade.price || '-' }}</td>
              <td>{{ trade.quantity }}</td>
              <td>{{ formatAmount(trade.amount) }}</td>
              <td>{{ formatAmount(trade.fee) }}</td>
              <td>{{ trade.sourceFile }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </article>

    <p v-if="!journalDays.length && !loading" class="muted center-hint">还没有导入任何交易记录</p>
  </div>
</template>

<script>
import { importTradeJournal, listTradeJournal } from '../api'

export default {
  name: 'TradeJournalPage',
  data() {
    return {
      selectedFile: null,
      importing: false,
      loading: false,
      error: '',
      importResult: null,
      journalDays: []
    }
  },
  async mounted() {
    await this.loadJournal()
  },
  methods: {
    handleFileChange(event) {
      this.selectedFile = event.target.files?.[0] || null
    },
    formatAmount(value) {
      if (!value) return '0'
      if (value >= 100000000) return `${(value / 100000000).toFixed(2)}亿`
      if (value >= 10000) return `${(value / 10000).toFixed(2)}万`
      return Number(value).toFixed(2)
    },
    async loadJournal() {
      this.loading = true
      this.error = ''
      try {
        this.journalDays = await listTradeJournal()
      } catch (error) {
        this.error = error.message
      } finally {
        this.loading = false
      }
    },
    async submitImport() {
      if (!this.selectedFile) return
      this.importing = true
      this.error = ''
      try {
        this.importResult = await importTradeJournal(this.selectedFile)
        await this.loadJournal()
      } catch (error) {
        this.error = error.message
      } finally {
        this.importing = false
      }
    }
  }
}
</script>
