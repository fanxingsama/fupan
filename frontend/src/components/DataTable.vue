<template>
  <article class="panel" :class="panelClass">
    <div class="section-head">
      <h2>{{ title }}</h2>
      <span>{{ sortedData.length }} 条</span>
    </div>
    <div class="table-scroll">
      <table>
        <thead>
          <tr>
            <th v-for="col in columns" :key="col.key">
              <button
                v-if="col.sortable"
                type="button"
                class="sort-button"
                :class="{ active: sortKey === col.key }"
                @click="toggleSort(col.key)"
              >
                <span>{{ col.label }}</span>
                <span class="sort-arrows">
                  <span :class="{ on: sortKey === col.key && sortDir === 'asc' }">▲</span>
                  <span :class="{ on: sortKey === col.key && sortDir === 'desc' }">▼</span>
                </span>
              </button>
              <span v-else>{{ col.label }}</span>
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in sortedData" :key="item.code + '-' + item.name">
            <td
              v-for="col in columns"
              :key="col.key"
              :class="getCellClass(col, item)"
            >
              {{ getDisplayValue(col, item) }}
            </td>
          </tr>
          <tr v-if="!sortedData.length">
            <td :colspan="columns.length" class="empty-hint">暂无数据</td>
          </tr>
        </tbody>
      </table>
    </div>
  </article>
</template>

<script>
import { parseNumericValue, cellClass, displayValue } from '../utils/format'

export default {
  name: 'DataTable',
  props: {
    title: { type: String, default: '' },
    rows: { type: Array, default: () => [] },
    columns: { type: Array, default: () => [] },
    panelClass: { type: String, default: '' }
  },
  data() {
    return { sortKey: '', sortDir: '' }
  },
  computed: {
    sortedData() {
      const rows = [...this.rows]
      if (!this.sortKey) return rows
      const col = this.columns.find(c => c.key === this.sortKey)
      if (!col) return rows
      const dir = this.sortDir === 'asc' ? 1 : -1
      rows.sort((a, b) => {
        const av = this.sortKey === 'reason' ? (a.reason || a.extraTag || '')
          : this.sortKey === 'concept' ? (a.concept || a.industry || '') : a[this.sortKey]
        const bv = this.sortKey === 'reason' ? (b.reason || b.extraTag || '')
          : this.sortKey === 'concept' ? (b.concept || b.industry || '') : b[this.sortKey]
        if (col.sortType === 'number') {
          const an = parseNumericValue(av)
          const bn = parseNumericValue(bv)
          if (an === null && bn === null) return 0
          if (an === null) return 1
          if (bn === null) return -1
          return (an - bn) * dir
        }
        return String(av || '').localeCompare(String(bv || '')) * dir
      })
      return rows
    }
  },
  methods: {
    toggleSort(key) {
      if (this.sortKey !== key) {
        this.sortKey = key
        this.sortDir = 'desc'
      } else if (this.sortDir === 'desc') {
        this.sortDir = 'asc'
      } else {
        this.sortKey = ''
        this.sortDir = ''
      }
    },
    getCellClass(col, item) {
      return cellClass(col, item)
    },
    getDisplayValue(col, item) {
      return displayValue(col, item)
    }
  }
}
</script>
