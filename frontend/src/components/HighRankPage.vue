<template>
  <div v-if="recap">
    <div class="page-header-block">
      <p class="eyebrow">高标板块</p>
      <h2>{{ recap.tradeDate }}</h2>
    </div>
    <DataTable
      v-for="section in sections"
      :key="section.id"
      :title="section.title"
      :rows="section.rows"
      :columns="section.columns"
      panel-class="table-panel"
    />
  </div>
</template>

<script>
import DataTable from './DataTable.vue'
import { STANDARD_COLUMNS } from '../utils/columns'

export default {
  name: 'HighRankPage',
  components: { DataTable },
  props: {
    recap: { type: Object, default: null }
  },
  computed: {
    sections() {
      if (!this.recap) return []
      return [
        { id: 'main-board', title: '主板 10日涨幅前40', rows: this.recap.top10DayGainMainBoard || [], columns: STANDARD_COLUMNS },
        { id: 'gem-star', title: '创业板/科创板 10日涨幅前40', rows: this.recap.top10DayGainGemStar || [], columns: STANDARD_COLUMNS }
      ]
    }
  }
}
</script>
