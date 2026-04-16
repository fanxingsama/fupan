<template>
  <article class="panel ai-insight-panel">
    <div class="section-head">
      <div>
        <h2>AI 分析中心</h2>
        <p class="muted section-subtitle">把盘面、主线、龙头和次日动作压缩成一套更可执行的分析卡片。</p>
      </div>
      <button class="close-btn" :disabled="loading" @click="loadInsight(true)">
        {{ loading ? '生成中...' : '刷新 AI 分析' }}
      </button>
    </div>

    <p v-if="loading" class="muted">正在生成 AI 分析...</p>
    <p v-else-if="error" class="error">{{ error }}</p>
    <div v-else-if="insight" class="ai-insight-content">
      <p v-if="insight.status === 'disabled'" class="muted">{{ insight.disclaimer }}</p>
      <p v-else-if="insight.status === 'error'" class="error">{{ insight.disclaimer }}</p>
      <template v-else>
        <div class="ai-meta">
          <span>{{ insight.provider }} / {{ insight.model }}</span>
          <span>{{ insight.cached ? '缓存结果' : '实时生成' }}</span>
        </div>

        <section class="insight-hero">
          <div>
            <span class="eyebrow">市场结论</span>
            <h3>{{ insight.marketConclusion }}</h3>
          </div>
          <p class="muted">{{ insight.marketStyle }}</p>
        </section>

        <section v-if="insight.keySignals?.length" class="insight-section">
          <h3>关键信号</h3>
          <ul class="text-list">
            <li v-for="(item, index) in insight.keySignals" :key="'signal-' + index">{{ item }}</li>
          </ul>
        </section>

        <section v-if="insight.themes?.length" class="insight-section">
          <h3>主流题材</h3>
          <div class="insight-theme-grid">
            <article v-for="theme in insight.themes" :key="theme.name" class="insight-theme-card">
              <div class="theme-head">
                <h4>{{ theme.name }}</h4>
                <span class="theme-phase">{{ theme.strength }}</span>
              </div>
              <p><strong>驱动：</strong>{{ theme.driver }}</p>
              <p><strong>观察：</strong>{{ theme.observation }}</p>
            </article>
          </div>
        </section>

        <section v-if="insight.leaders?.length" class="insight-section">
          <h3>龙头与前排</h3>
          <div class="insight-leader-grid">
            <article v-for="leader in insight.leaders" :key="leader.code + leader.name" class="insight-leader-card">
              <div class="watch-card-head">
                <div>
                  <h4>{{ leader.name }}</h4>
                  <small>{{ leader.code || '待补充' }} / {{ leader.role }}</small>
                </div>
              </div>
              <p><strong>入选：</strong>{{ leader.reason }}</p>
              <p><strong>信号：</strong>{{ leader.signal }}</p>
              <p class="watch-risk"><strong>风险：</strong>{{ leader.risk }}</p>
            </article>
          </div>
        </section>

        <div class="insight-two-col">
          <section v-if="insight.actionPlan?.length" class="insight-section">
            <h3>次日动作</h3>
            <ul class="text-list">
              <li v-for="(item, index) in insight.actionPlan" :key="'plan-' + index">{{ item }}</li>
            </ul>
          </section>
          <section v-if="insight.riskAlerts?.length" class="insight-section">
            <h3>风险提醒</h3>
            <ul class="text-list">
              <li v-for="(item, index) in insight.riskAlerts" :key="'risk-' + index">{{ item }}</li>
            </ul>
          </section>
        </div>

        <p class="muted ai-disclaimer">{{ insight.disclaimer }}</p>
      </template>
    </div>
  </article>
</template>

<script>
import { getAiInsight } from '../api'

export default {
  name: 'AiInsightPanel',
  props: {
    tradeDate: { type: String, default: '' }
  },
  data() {
    return {
      loading: false,
      error: '',
      insight: null
    }
  },
  watch: {
    tradeDate: {
      immediate: true,
      handler(value) {
        if (value) this.loadInsight(false)
      }
    }
  },
  methods: {
    async loadInsight(refresh) {
      if (!this.tradeDate) return
      this.loading = true
      this.error = ''
      try {
        this.insight = await getAiInsight(this.tradeDate, refresh)
      } catch (error) {
        this.error = error.message
      } finally {
        this.loading = false
      }
    }
  }
}
</script>
