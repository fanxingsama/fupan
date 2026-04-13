<template>
  <div class="page-shell">
    <aside class="sidebar">
      <div>
        <p class="eyebrow">A股短线复盘</p>
        <h1>每日复盘</h1>
        <p class="muted">收盘后统一抓取炸板、连板、首板、跌停与板块强弱，沉淀成可回看的历史档案。</p>
      </div>

      <div class="panel">
        <label class="field-label" for="tradeDate">交易日期</label>
        <input id="tradeDate" v-model="selectedDate" type="date" />
        <div class="actions">
          <button class="ghost" :disabled="loading" @click="loadRecap">查看报告</button>
          <button :disabled="capturing" @click="handleCapture">{{ capturing ? '采集中...' : '触发采集' }}</button>
        </div>
      </div>

      <div class="panel">
        <h2>历史记录</h2>
        <ul class="history-list">
          <li v-for="item in recaps" :key="item.tradeDate">
            <button class="history-item" @click="selectRecap(item.tradeDate)">
              <span>{{ item.tradeDate }}</span>
              <small>上涨 {{ item.upCount }} / 下跌 {{ item.downCount }}</small>
            </button>
          </li>
        </ul>
      </div>
    </aside>

    <main class="content">
      <section class="hero-card">
        <div>
          <p class="eyebrow">收盘看板</p>
          <h2>{{ recap ? recap.tradeDate : '请选择日期' }}</h2>
          <p class="muted">{{ recap ? recap.notes : '先触发一次采集，系统会自动生成这一天的复盘档案。' }}</p>
        </div>
        <div class="summary-grid">
          <article v-for="card in summaryCards" :key="card.label" class="summary-card">
            <span>{{ card.label }}</span>
            <strong>{{ card.value }}</strong>
          </article>
        </div>
      </section>

      <p v-if="error" class="error">{{ error }}</p>
      <p v-if="loading" class="muted">正在加载复盘数据...</p>

      <section v-if="recap" class="sector-layout">
        <article class="panel">
          <h2>首板集中板块</h2>
          <div class="tag-list">
            <span v-for="(count, name) in recap.firstLimitSectorFocus" :key="name" class="tag">
              {{ name }} {{ count }}
            </span>
          </div>
        </article>
        <article class="panel">
          <h2>上涨板块前列</h2>
          <ul class="sector-list">
            <li v-for="item in recap.topUpSectors" :key="item.name">
              <span>{{ item.name }}</span>
              <strong>{{ item.changePercent }}</strong>
              <small>{{ item.reason }}</small>
            </li>
          </ul>
        </article>
        <article class="panel">
          <h2>下跌板块前列</h2>
          <ul class="sector-list">
            <li v-for="item in recap.topDownSectors" :key="item.name">
              <span>{{ item.name }}</span>
              <strong>{{ item.changePercent }}</strong>
              <small>{{ item.reason }}</small>
            </li>
          </ul>
        </article>
      </section>

      <section v-if="recap" class="tables">
        <article v-for="section in sections" :key="section.title" class="panel">
          <div class="section-head">
            <h2>{{ section.title }}</h2>
            <span>{{ section.rows.length }} 条</span>
          </div>
          <div class="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>代码</th>
                  <th>名称</th>
                  <th>连板</th>
                  <th>涨幅</th>
                  <th>股价</th>
                  <th>概念</th>
                  <th>成交额</th>
                  <th>流通市值</th>
                  <th>原因</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in section.rows" :key="section.title + '-' + item.code + '-' + item.name">
                  <td>{{ item.code }}</td>
                  <td>{{ item.name }}</td>
                  <td>{{ item.boardHeight || '-' }}</td>
                  <td>{{ item.changePercent || '-' }}</td>
                  <td>{{ item.price || '-' }}</td>
                  <td>{{ item.concept || '-' }}</td>
                  <td>{{ item.amount || '-' }}</td>
                  <td>{{ item.floatMarketValue || '-' }}</td>
                  <td>{{ item.reason || item.extraTag || '-' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </article>
      </section>
    </main>
  </div>
</template>

<script>
import { captureRecap, getRecap, listRecaps } from './api'

export default {
  name: 'App',
  data() {
    return {
      recaps: [],
      selectedDate: '',
      recap: null,
      loading: false,
      capturing: false,
      error: ''
    }
  },
  computed: {
    summaryCards() {
      if (!this.recap) {
        return []
      }
      return [
        { label: '上涨家数', value: this.recap.marketStats.upCount },
        { label: '下跌家数', value: this.recap.marketStats.downCount },
        { label: '平盘家数', value: this.recap.marketStats.flatCount },
        { label: '首板数量', value: this.recap.marketStats.firstLimitCount }
      ]
    },
    sections() {
      if (!this.recap) {
        return []
      }
      return [
        { title: '当日炸板票', rows: this.recap.brokenLimitToday },
        { title: '昨日炸板票反馈', rows: this.recap.brokenLimitYesterdayFeedback },
        { title: '当日连板票', rows: this.recap.limitUpToday },
        { title: '昨日连板票反馈', rows: this.recap.limitUpYesterdayFeedback },
        { title: '当日首板票', rows: this.recap.firstLimitToday },
        { title: '当日跌停票', rows: this.recap.limitDownToday },
        { title: '创业板/科创板 10日涨幅前列', rows: this.recap.top10DayGainGemStar },
        { title: '主板 10日涨幅前列', rows: this.recap.top10DayGainMainBoard }
      ]
    }
  },
  async mounted() {
    try {
      await this.loadRecapList()
      await this.loadRecap()
    } catch (error) {
      this.error = error.message
    }
  },
  methods: {
    async loadRecapList() {
      this.recaps = await listRecaps()
      if (!this.selectedDate && this.recaps.length > 0) {
        this.selectedDate = this.recaps[0].tradeDate
      }
    },
    async loadRecap() {
      if (!this.selectedDate) {
        return
      }
      this.loading = true
      this.error = ''
      try {
        this.recap = await getRecap(this.selectedDate)
      } catch (error) {
        this.recap = null
        this.error = error.message
      } finally {
        this.loading = false
      }
    },
    async handleCapture() {
      if (!this.selectedDate) {
        return
      }
      this.capturing = true
      this.error = ''
      try {
        this.recap = await captureRecap(this.selectedDate)
        await this.loadRecapList()
      } catch (error) {
        this.error = error.message
      } finally {
        this.capturing = false
      }
    },
    selectRecap(tradeDate) {
      this.selectedDate = tradeDate
      this.loadRecap()
    }
  }
}
</script>
