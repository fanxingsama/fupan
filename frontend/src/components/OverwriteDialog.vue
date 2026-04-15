<template>
  <div v-if="visible" class="modal-overlay" @click.self="$emit('cancel')">
    <div class="modal-card modal-card-wide">
      <h2>数据变动提醒</h2>
      <p>检测到 <strong>{{ tradeDate }}</strong> 的复盘数据发生变动，请先核对以下项目。</p>

      <div v-if="diffs.length" class="diff-summary-list">
        <button
          v-for="diff in diffs"
          :key="diff.label"
          type="button"
          class="diff-summary-item"
          :class="{ active: expandedLabel === diff.label }"
          @click="toggleExpanded(diff.label)"
        >
          <span>{{ diff.label }}</span>
          <small>{{ diff.summary }}</small>
        </button>
      </div>

      <div v-if="activeDiff" class="diff-detail-wrap">
        <div class="diff-detail-head">
          <h3>{{ activeDiff.label }}</h3>
          <span>{{ activeDiff.details.length }} 条明细</span>
        </div>
        <table class="diff-table">
          <thead>
            <tr>
              <th>字段</th>
              <th>旧数据</th>
              <th>新数据</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="detail in activeDiff.details" :key="`${activeDiff.label}-${detail.name}`">
              <td>{{ detail.name }}</td>
              <td class="diff-old">{{ detail.oldValue }}</td>
              <td class="diff-new">{{ detail.newValue }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <p v-else class="muted">没有检测到需要展示的变动明细。</p>
      <p class="muted">是否使用最新采集的数据替换当前数据？</p>

      <div class="modal-actions">
        <button class="modal-btn-secondary" @click="$emit('cancel')">保留旧数据</button>
        <button @click="$emit('confirm')">使用新数据</button>
      </div>
    </div>
  </div>
</template>

<script>
import { recapsDiffSummary } from '../utils/trading'

export default {
  name: 'OverwriteDialog',
  props: {
    visible: { type: Boolean, default: false },
    tradeDate: { type: String, default: '' },
    oldRecap: { type: Object, default: null },
    newRecap: { type: Object, default: null }
  },
  data() {
    return {
      expandedLabel: ''
    }
  },
  computed: {
    diffs() {
      return recapsDiffSummary(this.oldRecap, this.newRecap)
    },
    activeDiff() {
      if (!this.diffs.length) return null
      return this.diffs.find(item => item.label === this.expandedLabel) || this.diffs[0]
    }
  },
  watch: {
    visible(value) {
      if (value) {
        this.expandedLabel = this.diffs[0]?.label || ''
      }
    },
    diffs: {
      immediate: true,
      handler(next) {
        if (!next.length) {
          this.expandedLabel = ''
          return
        }
        if (!next.some(item => item.label === this.expandedLabel)) {
          this.expandedLabel = next[0].label
        }
      }
    }
  },
  methods: {
    toggleExpanded(label) {
      this.expandedLabel = this.expandedLabel === label ? '' : label
    }
  }
}
</script>
