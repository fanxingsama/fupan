<template>
  <div v-if="recap">
    <div class="page-header-block">
      <p class="eyebrow">{{ pageTitle }}</p>
      <h2>{{ recap.tradeDate }}</h2>
    </div>
    <div class="tab-bar">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        :class="{ active: currentTab === tab.key }"
        @click="currentTab = tab.key"
      >
        {{ tab.label }}
        <small>{{ tab.rows.length }}</small>
      </button>
    </div>
    <DataTable
      :title="activeSection.title"
      :rows="activeSection.rows"
      :columns="activeSection.columns"
    />
  </div>
</template>

<script>
import DataTable from './DataTable.vue'
import { toBoardHeight } from '../utils/format'
import { BROKEN_ENHANCED_COLUMNS, STANDARD_COLUMNS, CONSECUTIVE_ENHANCED_COLUMNS, FIRST_LIMIT_ENHANCED_COLUMNS, LIMIT_REASON_COLUMNS } from '../utils/columns'

export default {
  name: 'TabbedTablePage',
  components: { DataTable },
  props: {
    recap: { type: Object, default: null },
    pageType: { type: String, required: true } // 'broken' | 'consecutive' | 'firstLimit'
  },
  data() {
    return { currentTab: 'today' }
  },
  computed: {
    pageTitle() {
      const map = { broken: '炸板票板块', consecutive: '连板票板块', firstLimit: '首板票板块' }
      return map[this.pageType] || ''
    },
    tabs() {
      if (!this.recap) return []
      if (this.pageType === 'broken') {
        return [
          { key: 'today', label: '当日炸板', rows: this.recap.brokenLimitToday || [] },
          { key: 'yesterday', label: '昨日炸板反馈', rows: this.recap.brokenLimitYesterdayFeedback || [] }
        ]
      }
      if (this.pageType === 'consecutive') {
        return [
          { key: 'today', label: '当日连板', rows: this.filteredConsecutiveRows },
          { key: 'yesterday', label: '昨日连板反馈', rows: this.filteredConsecutiveFeedbackRows }
        ]
      }
      if (this.pageType === 'firstLimit') {
        return [
          { key: 'today', label: '当日首板', rows: this.recap.firstLimitToday || [] },
          { key: 'yesterday', label: '昨日首板反馈', rows: this.recap.firstLimitYesterdayFeedback || [] }
        ]
      }
      return []
    },
    filteredConsecutiveRows() {
      return (this.recap.limitUpToday || []).filter(item => toBoardHeight(item.boardHeight) >= 2)
    },
    filteredConsecutiveFeedbackRows() {
      return (this.recap.limitUpYesterdayFeedback || []).filter(item => toBoardHeight(item.boardHeight) >= 2)
    },
    activeSection() {
      const tab = this.tabs.find(t => t.key === this.currentTab) || this.tabs[0]
      if (!tab) return { title: '', rows: [], columns: [] }

      if (this.pageType === 'broken') {
        return {
          title: tab.key === 'today' ? '当日炸板票' : '昨日炸板票反馈',
          rows: tab.rows,
          columns: BROKEN_ENHANCED_COLUMNS
        }
      }
      if (this.pageType === 'consecutive') {
        return {
          title: tab.key === 'today' ? '当日连板票' : '昨日连板票反馈',
          rows: tab.rows,
          columns: tab.key === 'today' ? CONSECUTIVE_ENHANCED_COLUMNS : STANDARD_COLUMNS
        }
      }
      if (this.pageType === 'firstLimit') {
        return {
          title: tab.key === 'today' ? '当日首板票' : '昨日首板票反馈',
          rows: tab.rows,
          columns: tab.key === 'today' ? FIRST_LIMIT_ENHANCED_COLUMNS : LIMIT_REASON_COLUMNS
        }
      }
      return { title: '', rows: [], columns: [] }
    }
  },
  watch: {
    pageType() { this.currentTab = 'today' }
  }
}
</script>
