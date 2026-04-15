<template>
  <div class="panel calendar-panel">
    <div class="calendar-head">
      <div>
        <h2>历史记录</h2>
        <small>{{ monthLabel }}</small>
      </div>
      <div class="calendar-nav">
        <button class="nav-button" @click="$emit('shift-month', -1)">‹</button>
        <button class="nav-button" @click="$emit('shift-month', 1)">›</button>
      </div>
    </div>
    <div class="calendar-grid week-row">
      <span v-for="w in weekLabels" :key="w" class="week-label">{{ w }}</span>
    </div>
    <div class="calendar-grid">
      <button
        v-for="day in days"
        :key="day.key"
        class="calendar-cell"
        :class="{
          ghosted: !day.inMonth,
          active: day.isSelected,
          available: day.hasRecap,
          'non-trading': !day.isTradingDay && day.inMonth,
          'is-capturing': capturing && day.tradeDate === selectedDate && day.isSelected
        }"
        :disabled="!day.isTradingDay || capturing || day.isFuture"
        @click="$emit('day-click', day)"
      >
        <span class="day-number">{{ day.day }}</span>
        <i v-if="day.hasRecap" class="calendar-dot"></i>
      </button>
    </div>
    <p v-if="capturing" class="capturing-status">⏳ 正在采集 {{ selectedDate }} 的数据...</p>
  </div>
</template>

<script>
export default {
  name: 'RecapCalendar',
  props: {
    monthLabel: { type: String, default: '' },
    days: { type: Array, default: () => [] },
    selectedDate: { type: String, default: '' },
    capturing: { type: Boolean, default: false }
  },
  data() {
    return { weekLabels: ['一', '二', '三', '四', '五', '六', '日'] }
  }
}
</script>
