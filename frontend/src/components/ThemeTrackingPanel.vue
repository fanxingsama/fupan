<template>
  <section class="theme-tracking-shell panel">
    <div class="section-head">
      <div>
        <p class="eyebrow">主线任务</p>
        <h2>主线板块跟踪</h2>
        <p class="muted section-subtitle">只跟最近最强的几个方向，把催化、梯队和裸K证据放在同一处。</p>
      </div>
      <button :disabled="refreshing" class="theme-refresh-btn" @click="refresh">
        {{ refreshing ? '刷新中...' : '刷新主线' }}
      </button>
    </div>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-else-if="loading" class="muted">正在整理主线板块...</p>
    <p v-else-if="!summaries.length" class="muted">当前日期暂无可跟踪的主线板块。</p>

    <div v-if="summaries.length" class="theme-summary-grid">
      <button
        v-for="item in summaries"
        :key="item.themeName"
        class="theme-summary-card"
        :class="{ active: item.themeName === activeThemeName }"
        @click="selectTheme(item.themeName)"
      >
        <div class="theme-summary-head">
          <div>
            <h3>{{ item.themeName }}</h3>
            <small>{{ item.themeStatus }}</small>
          </div>
          <strong>{{ item.themeScore }}</strong>
        </div>
        <p>{{ item.summary }}</p>
        <div class="theme-summary-metrics">
          <span>涨停 {{ item.limitUpCount }}</span>
          <span>高标 {{ item.highBoardCount }}</span>
          <span>高度 {{ item.maxBoardHeight }} 板</span>
        </div>
        <div class="tag-list">
          <span v-for="catalyst in item.themeCatalysts" :key="item.themeName + catalyst" class="tag">
            {{ catalyst }}
          </span>
        </div>
      </button>
    </div>

    <div v-if="detail" class="theme-detail-stack">
      <article class="theme-detail-hero">
        <div>
          <p class="eyebrow">板块结论</p>
          <h3>{{ detail.themeName }} · {{ detail.themeStatus }}</h3>
          <p>{{ detail.verdict }}</p>
        </div>
        <div class="theme-detail-confidence">
          <span>置信度</span>
          <strong>{{ confidenceLabel }}</strong>
        </div>
      </article>

      <div class="theme-detail-grid">
        <article class="theme-detail-card">
          <h3>催化与叙事</h3>
          <ul class="text-list">
            <li v-for="item in detail.themeCatalysts" :key="item">{{ item }}</li>
          </ul>
        </article>
        <article class="theme-detail-card">
          <h3>支持证据</h3>
          <ul class="text-list">
            <li v-for="item in detail.evidenceList" :key="item">{{ item }}</li>
          </ul>
        </article>
        <article class="theme-detail-card">
          <h3>风险与反证</h3>
          <ul class="text-list">
            <li v-for="item in detail.counterEvidence" :key="item">{{ item }}</li>
            <li v-for="item in detail.riskSignals" :key="'risk-' + item">{{ item }}</li>
          </ul>
        </article>
        <article class="theme-detail-card">
          <h3>次日预案</h3>
          <ul class="text-list">
            <li v-for="item in detail.nextDayCheckpoints" :key="item">{{ item }}</li>
          </ul>
        </article>
      </div>

      <div class="theme-ladder-grid">
        <article class="theme-stock-panel">
          <div class="section-head">
            <h3>核心票行为</h3>
          </div>
          <div class="theme-stock-list">
            <article v-for="stock in detail.coreStocks" :key="stock.code + stock.name" class="theme-stock-card">
              <div class="theme-stock-head">
                <div>
                  <strong>{{ stock.name }}</strong>
                  <small>{{ stock.code }}</small>
                </div>
                <span>{{ stock.role }}</span>
              </div>
              <div class="tag-list">
                <span class="tag">{{ stock.behaviorTag }}</span>
                <span v-if="stock.boardHeight" class="tag">{{ stock.boardHeight }} 板</span>
                <span v-if="stock.changePercent" class="tag" :class="percentClass(stock.changePercent)">{{ stock.changePercent }}</span>
              </div>
              <p>{{ stock.observation }}</p>
              <small class="muted">{{ stock.reason || '暂无补充原因' }}</small>
            </article>
          </div>
        </article>

        <article class="theme-stock-panel">
          <div class="section-head">
            <h3>梯队结构</h3>
          </div>
          <div class="theme-structure-columns">
            <div>
              <span class="field-label">高标梯队</span>
              <ul class="mini-theme-list">
                <li v-for="stock in detail.highBoardStocks" :key="'high-' + stock.code">{{ stock.name }} · {{ stock.behaviorTag }}</li>
              </ul>
            </div>
            <div>
              <span class="field-label">中位承接</span>
              <ul class="mini-theme-list">
                <li v-for="stock in detail.midLevelFollowers" :key="'mid-' + stock.code">{{ stock.name }} · {{ stock.behaviorTag }}</li>
              </ul>
            </div>
            <div>
              <span class="field-label">低位补涨</span>
              <ul class="mini-theme-list">
                <li v-for="stock in detail.lowLevelAttempts" :key="'low-' + stock.code">{{ stock.name }} · {{ stock.behaviorTag }}</li>
              </ul>
            </div>
          </div>
        </article>
      </div>

      <article class="theme-history-panel">
        <div class="section-head">
          <h3>连续档案</h3>
          <small class="muted">最近 {{ detail.history.length }} 个交易日</small>
        </div>
        <div class="theme-history-list">
          <div v-for="item in detail.history" :key="item.tradeDate" class="theme-history-item">
            <div>
              <strong>{{ item.tradeDate }}</strong>
              <small>{{ item.themeStatus }} · {{ item.validationStatus }}</small>
            </div>
            <div>
              <span>分数 {{ item.themeScore }}</span>
              <span>高度 {{ item.maxBoardHeight }} 板</span>
            </div>
            <p>{{ item.summary }}</p>
            <small class="muted">核心票：{{ item.leadStock }}</small>
          </div>
        </div>
      </article>
    </div>
  </section>
</template>

<script>
import {
  getThemeTrackingDetail,
  listThemeTracking,
  refreshThemeTracking
} from '../api'
import { percentClass } from '../utils/format'

export default {
  name: 'ThemeTrackingPanel',
  props: {
    tradeDate: { type: String, default: '' }
  },
  data() {
    return {
      summaries: [],
      detail: null,
      activeThemeName: '',
      loading: false,
      refreshing: false,
      error: ''
    }
  },
  computed: {
    confidenceLabel() {
      if (!this.detail) return '-'
      return `${Math.round((this.detail.confidence || 0) * 100)}%`
    }
  },
  watch: {
    tradeDate: {
      immediate: true,
      handler() {
        this.load()
      }
    }
  },
  methods: {
    percentClass,
    async load(refresh = false) {
      if (!this.tradeDate) return
      this.loading = !refresh
      this.error = ''
      try {
        this.summaries = await listThemeTracking(this.tradeDate, refresh)
        const nextTheme = this.activeThemeName && this.summaries.some(item => item.themeName === this.activeThemeName)
          ? this.activeThemeName
          : this.summaries[0]?.themeName
        this.activeThemeName = nextTheme || ''
        this.detail = this.activeThemeName
          ? await getThemeTrackingDetail(this.tradeDate, this.activeThemeName, refresh)
          : null
      } catch (error) {
        this.error = error.message
      } finally {
        this.loading = false
      }
    },
    async selectTheme(themeName) {
      if (!themeName || themeName === this.activeThemeName) return
      this.activeThemeName = themeName
      try {
        this.detail = await getThemeTrackingDetail(this.tradeDate, themeName)
      } catch (error) {
        this.error = error.message
      }
    },
    async refresh() {
      if (!this.tradeDate || this.refreshing) return
      this.refreshing = true
      try {
        await refreshThemeTracking(this.tradeDate)
        await this.load(true)
      } catch (error) {
        this.error = error.message
      } finally {
        this.refreshing = false
      }
    }
  }
}
</script>
