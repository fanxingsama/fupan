// ECharts option 构建器
import * as echarts from 'echarts'
import { parseNumericValue } from './format'

export function buildTrendOption(values, dates, color) {
  return {
    animationDuration: 500,
    grid: { left: 18, right: 18, top: 12, bottom: 24, containLabel: true },
    tooltip: { trigger: 'axis', backgroundColor: '#0f172a', borderWidth: 0, textStyle: { color: '#f8fafc' } },
    xAxis: {
      type: 'category', data: dates, boundaryGap: false,
      axisLine: { lineStyle: { color: 'rgba(20,33,61,0.12)' } },
      axisTick: { show: false }, axisLabel: { color: '#64748b' }
    },
    yAxis: {
      type: 'value', axisLine: { show: false }, axisTick: { show: false },
      splitLine: { lineStyle: { color: 'rgba(20,33,61,0.08)', type: 'dashed' } },
      axisLabel: { color: '#64748b' }
    },
    series: [{
      data: values, type: 'line', smooth: true, symbol: 'circle', symbolSize: 9,
      lineStyle: { width: 4, color },
      itemStyle: { color, borderColor: '#fff', borderWidth: 2 },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: `${color}66` }, { offset: 1, color: `${color}08` }
        ])
      }
    }]
  }
}

export function buildBarOption(rows, color, reverse = false) {
  const data = rows
    .map(item => ({ name: item.name, change: parseNumericValue(item.changePercent) ?? 0 }))
    .sort((l, r) => (reverse ? l.change - r.change : r.change - l.change))
    .slice(0, 8)
  const categories = data.map(item => item.name)
  const rawValues = data.map(item => item.change)
  const values = reverse ? rawValues.map(v => Math.abs(v)) : rawValues
  return {
    animationDuration: 500,
    grid: { left: 18, right: 56, top: 8, bottom: 8, containLabel: true },
    tooltip: {
      trigger: 'axis', axisPointer: { type: 'shadow' },
      backgroundColor: '#0f172a', borderWidth: 0, textStyle: { color: '#f8fafc' },
      formatter: reverse ? (params) => { const i = params[0]; return `${i.name}<br/>${(-i.value).toFixed(2)}%` } : undefined
    },
    xAxis: { type: 'value', axisLabel: { color: '#64748b' }, splitLine: { lineStyle: { color: 'rgba(20,33,61,0.08)', type: 'dashed' } } },
    yAxis: { type: 'category', data: categories, inverse: true, axisTick: { show: false }, axisLine: { show: false }, axisLabel: { color: '#14213d', fontWeight: 600 } },
    series: [{
      type: 'bar', data: values, barWidth: 14,
      showBackground: true, backgroundStyle: { color: 'rgba(20,33,61,0.05)' },
      itemStyle: {
        borderRadius: 999,
        color: new echarts.graphic.LinearGradient(1, 0, 0, 0, [
          { offset: 0, color: reverse ? '#0f766e' : '#f97316' }, { offset: 1, color }
        ])
      },
      label: {
        show: true, position: 'right', distance: 8, color: '#14213d',
        formatter: reverse ? ({ value }) => `${(-value).toFixed(2)}%` : ({ value }) => `${value.toFixed(2)}%`
      }
    }]
  }
}

export function buildFocusOption(dataMap) {
  const rows = Object.entries(dataMap)
    .map(([name, value]) => ({ name, value }))
    .sort((a, b) => b.value - a.value)
    .slice(0, 8)
    .reverse()
  return {
    animationDuration: 500,
    grid: { left: 12, right: 16, top: 8, bottom: 8, containLabel: true },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, backgroundColor: '#0f172a', borderWidth: 0, textStyle: { color: '#f8fafc' } },
    xAxis: { type: 'value', axisLabel: { color: '#64748b' }, splitLine: { lineStyle: { color: 'rgba(20,33,61,0.08)', type: 'dashed' } } },
    yAxis: { type: 'category', data: rows.map(i => i.name), axisTick: { show: false }, axisLine: { show: false }, axisLabel: { color: '#14213d', fontWeight: 600 } },
    series: [{
      type: 'bar', data: rows.map(i => i.value), barWidth: 14,
      itemStyle: {
        borderRadius: 999,
        color: new echarts.graphic.LinearGradient(1, 0, 0, 0, [
          { offset: 0, color: '#84cc16' }, { offset: 1, color: '#22c55e' }
        ])
      },
      label: { show: true, position: 'right', color: '#14213d' }
    }]
  }
}

// 初始化或复用 ECharts 实例，处理 DOM 元素可能已更换的情况
export function ensureChart(chartMap, key, el) {
  if (!el) return null
  const existing = chartMap[key]
  if (existing) {
    try {
      if (existing.getDom() !== el) {
        existing.dispose()
        chartMap[key] = null
      }
    } catch (_) {
      chartMap[key] = null
    }
  }
  if (!chartMap[key]) {
    chartMap[key] = echarts.init(el)
  }
  return chartMap[key]
}
