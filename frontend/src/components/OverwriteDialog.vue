<template>
  <div v-if="visible" class="modal-overlay" @click.self="$emit('cancel')">
    <div class="modal-card modal-card-wide">
      <h2>数据变动提醒</h2>
      <p>检测到 <strong>{{ tradeDate }}</strong> 的复盘数据发生变动，以下是变动详情：</p>

      <div v-if="diffs.length" class="diff-table-wrap">
        <table class="diff-table">
          <thead>
            <tr>
              <th>项目</th>
              <th>旧数据</th>
              <th>新数据</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="d in diffs" :key="d.label">
              <td>{{ d.label }}</td>
              <td class="diff-old">{{ d.old }}</td>
              <td class="diff-new">{{ d.new }}</td>
            </tr>
          </tbody>
        </table>
      </div>

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
  computed: {
    diffs() {
      return recapsDiffSummary(this.oldRecap, this.newRecap)
    }
  }
}
</script>
