<template>
  <article class="panel ai-briefing-panel">
    <div class="section-head">
      <div>
        <h2>AI 情报中心</h2>
        <p class="muted section-subtitle">多源情报先采集，AI 再做整理，聚焦真正有用的题材与个股情报。</p>
      </div>
      <button class="close-btn" :disabled="loading" @click="loadAll(true)">
        {{ loading ? '生成中...' : '刷新情报' }}
      </button>
    </div>

    <p v-if="loading" class="muted">正在生成 AI 情报...</p>
    <p v-else-if="error" class="error">{{ error }}</p>
    <div v-else class="ai-briefing-content">
      <div v-if="briefing">
        <p v-if="briefing.status === 'disabled'" class="muted">{{ briefing.disclaimer }}</p>
        <p v-else-if="briefing.status === 'error'" class="error">{{ briefing.disclaimer }}</p>
        <template v-else>
          <div class="ai-meta">
            <span>{{ briefing.provider }} / {{ briefing.model }}</span>
            <span>{{ briefing.cached ? '缓存结果' : '实时生成' }}</span>
          </div>

          <section class="briefing-hero">
            <span class="eyebrow">情报标题</span>
            <h3>{{ briefing.headline }}</h3>
            <p class="muted">{{ briefing.briefing }}</p>
          </section>

          <div class="briefing-grid">
            <section v-if="briefing.themePulses?.length" class="briefing-card">
              <h3>题材脉冲</h3>
              <div class="briefing-list">
                <article v-for="item in briefing.themePulses" :key="item.name" class="briefing-item">
                  <div class="theme-head">
                    <h4>{{ item.name }}</h4>
                    <span class="theme-phase">{{ item.trend }}</span>
                  </div>
                  <p><strong>原因：</strong>{{ item.reason }}</p>
                  <p><strong>次日：</strong>{{ item.nextSignal }}</p>
                </article>
              </div>
            </section>

            <section v-if="briefing.stockFocuses?.length" class="briefing-card">
              <h3>重点观察票</h3>
              <div class="briefing-list">
                <article v-for="item in briefing.stockFocuses" :key="item.code + item.name" class="briefing-item">
                  <div class="theme-head">
                    <h4>{{ item.name }}</h4>
                    <span class="theme-phase">{{ item.tag }}</span>
                  </div>
                  <p><strong>观察理由：</strong>{{ item.reason }}</p>
                  <p><strong>看点：</strong>{{ item.catalyst }}</p>
                </article>
              </div>
            </section>
          </div>

          <div class="briefing-grid">
            <section v-if="briefing.timeline?.length" class="briefing-card">
              <h3>近几日轮动</h3>
              <div class="timeline-list">
                <article v-for="item in briefing.timeline" :key="item.tradeDate + item.summary" class="timeline-item">
                  <span class="timeline-date">{{ item.tradeDate }}</span>
                  <p>{{ item.summary }}</p>
                </article>
              </div>
            </section>

            <section v-if="briefing.tomorrowSignals?.length" class="briefing-card">
              <h3>次日情报信号</h3>
              <ul class="text-list">
                <li v-for="(item, index) in briefing.tomorrowSignals" :key="'signal-' + index">{{ item }}</li>
              </ul>
            </section>
          </div>

          <p class="muted ai-disclaimer">{{ briefing.disclaimer }}</p>
        </template>
      </div>

      <template v-if="intelligence">
        <section v-if="intelligence.sourceStats?.length" class="briefing-card">
          <h3>情报来源</h3>
          <div class="source-stat-grid">
            <article v-for="item in intelligence.sourceStats" :key="item.source + item.category" class="source-stat-card">
              <strong>{{ item.source }}</strong>
              <span>{{ item.category }}</span>
              <small>{{ item.itemCount }} 条</small>
            </article>
          </div>
        </section>

        <div class="briefing-grid">
          <section v-if="intelligence.themeClusters?.length" class="briefing-card">
            <h3>题材簇</h3>
            <div class="briefing-list">
              <article v-for="item in intelligence.themeClusters" :key="item.name" class="briefing-item">
                <div class="theme-head">
                  <h4>{{ item.name }}</h4>
                  <span class="theme-phase">热度 {{ item.heat }}</span>
                </div>
                <p><strong>来源：</strong>{{ (item.sources || []).join(' / ') || '暂无' }}</p>
                <p><strong>关联股：</strong>{{ (item.relatedStocks || []).join(' / ') || '暂无' }}</p>
                <p><strong>样本：</strong>{{ (item.sampleTitles || []).slice(0, 2).join('；') || '暂无' }}</p>
              </article>
            </div>
          </section>

          <section v-if="intelligence.hotStocks?.length" class="briefing-card">
            <h3>热股榜</h3>
            <div class="briefing-list">
              <article v-for="item in intelligence.hotStocks" :key="item.code + item.rank" class="briefing-item">
                <div class="theme-head">
                  <h4>{{ item.name }}</h4>
                  <span class="theme-phase">第 {{ item.rank }} 名</span>
                </div>
                <p><strong>表现：</strong>{{ item.price }} / {{ item.changePercent }}</p>
                <p><strong>热词：</strong>{{ (item.keywords || []).join(' / ') || '暂无' }}</p>
              </article>
            </div>
          </section>
        </div>
      </template>
    </div>
  </article>
</template>

<script>
import { getAiBriefing, getMarketIntelligence } from '../api'

export default {
  name: 'AiBriefingPanel',
  props: {
    tradeDate: { type: String, default: '' }
  },
  data() {
    return {
      loading: false,
      error: '',
      briefing: null,
      intelligence: null
    }
  },
  watch: {
    tradeDate: {
      immediate: true,
      handler(value) {
        if (value) this.loadAll(false)
      }
    }
  },
  methods: {
    async loadAll(refresh) {
      if (!this.tradeDate) return
      this.loading = true
      this.error = ''
      try {
        const [briefing, intelligence] = await Promise.all([
          getAiBriefing(this.tradeDate, refresh),
          getMarketIntelligence(this.tradeDate, refresh)
        ])
        this.briefing = briefing
        this.intelligence = intelligence
      } catch (error) {
        this.error = error.message
      } finally {
        this.loading = false
      }
    }
  }
}
</script>
