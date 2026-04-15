<template>
  <article class="panel ai-summary-panel">
    <div class="section-head">
      <div>
        <h2>AI 复盘总结</h2>
        <p class="muted section-subtitle">用大模型把结构化复盘数据整理成更像交易员语言的总结。</p>
      </div>
      <button class="close-btn" :disabled="loading" @click="loadSummary(true)">
        {{ loading ? '生成中...' : '刷新 AI 总结' }}
      </button>
    </div>

    <p v-if="loading" class="muted">正在生成 AI 总结...</p>
    <p v-else-if="error" class="error">{{ error }}</p>
    <div v-else-if="summary" class="ai-summary-content">
      <p v-if="summary.status === 'disabled'" class="muted">{{ summary.disclaimer }}</p>
      <p v-else-if="summary.status === 'error'" class="error">{{ summary.disclaimer }}</p>
      <template v-else>
        <div class="ai-meta">
          <span>{{ summary.provider }} / {{ summary.model }}</span>
          <span>{{ summary.cached ? '缓存结果' : '实时生成' }}</span>
        </div>
        <div class="ai-summary-text">{{ summary.summary }}</div>
        <ul v-if="summary.bullets?.length" class="text-list">
          <li v-for="(item, index) in summary.bullets" :key="index">{{ item }}</li>
        </ul>
        <p class="muted ai-disclaimer">{{ summary.disclaimer }}</p>
      </template>
    </div>
  </article>
</template>

<script>
import { getAiSummary } from '../api'

export default {
  name: 'AiSummaryPanel',
  props: {
    tradeDate: { type: String, default: '' }
  },
  data() {
    return {
      loading: false,
      error: '',
      summary: null
    }
  },
  watch: {
    tradeDate: {
      immediate: true,
      handler(value) {
        if (value) this.loadSummary(false)
      }
    }
  },
  methods: {
    async loadSummary(refresh) {
      if (!this.tradeDate) return
      this.loading = true
      this.error = ''
      try {
        this.summary = await getAiSummary(this.tradeDate, refresh)
      } catch (error) {
        this.error = error.message
      } finally {
        this.loading = false
      }
    }
  }
}
</script>
