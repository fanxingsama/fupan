// ECharts option 构建器
import { parseNumericValue } from './format'

let echartsLoader = null

function linearGradient(stops) {
  return {
    type: 'linear',
    x: 0,
    y: 0,
    x2: 0,
    y2: 1,
    colorStops: stops
  }
}

async function loadEcharts() {
  if (!echartsLoader) {
    echartsLoader = import('echarts')
  }
  return echartsLoader
}

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
        color: linearGradient([
          { offset: 0, color: `${color}66` }, { offset: 1, color: `${color}08` }
        ])
      }
    }]
  }
}

// 双线趋势图（如封板率+涨停数叠加）
export function buildDualTrendOption(values1, values2, dates, color1, color2, name1, name2) {
  return {
    animationDuration: 500,
    grid: { left: 18, right: 18, top: 28, bottom: 24, containLabel: true },
    tooltip: { trigger: 'axis', backgroundColor: '#0f172a', borderWidth: 0, textStyle: { color: '#f8fafc' } },
    legend: { data: [name1, name2], top: 0, textStyle: { color: '#64748b', fontSize: 11 } },
    xAxis: {
      type: 'category', data: dates, boundaryGap: false,
      axisLine: { lineStyle: { color: 'rgba(20,33,61,0.12)' } },
      axisTick: { show: false }, axisLabel: { color: '#64748b' }
    },
    yAxis: [
      { type: 'value', axisLine: { show: false }, axisTick: { show: false }, splitLine: { lineStyle: { color: 'rgba(20,33,61,0.08)', type: 'dashed' } }, axisLabel: { color: '#64748b' } },
      { type: 'value', axisLine: { show: false }, axisTick: { show: false }, splitLine: { show: false }, axisLabel: { color: '#64748b' } }
    ],
    series: [
      { name: name1, data: values1, type: 'line', smooth: true, symbol: 'circle', symbolSize: 7, yAxisIndex: 0, lineStyle: { width: 3, color: color1 }, itemStyle: { color: color1, borderColor: '#fff', borderWidth: 2 }, areaStyle: { color: linearGradient([{ offset: 0, color: `${color1}44` }, { offset: 1, color: `${color1}08` }]) } },
      { name: name2, data: values2, type: 'line', smooth: true, symbol: 'circle', symbolSize: 7, yAxisIndex: 1, lineStyle: { width: 3, color: color2 }, itemStyle: { color: color2, borderColor: '#fff', borderWidth: 2 } }
    ]
  }
}

// 连板梯队柱状图（纵向，从低到高）
export function buildLadderOption(ladderData) {
  if (!ladderData || !ladderData.length) return {}
  const categories = ladderData.map(d => d.height === 1 ? '首板' : d.height + '板')
  const values = ladderData.map(d => d.count)
  const maxVal = Math.max(...values)

  return {
    animationDuration: 500,
    grid: { left: 12, right: 40, top: 8, bottom: 8, containLabel: true },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, backgroundColor: '#0f172a', borderWidth: 0, textStyle: { color: '#f8fafc' } },
    xAxis: { type: 'value', axisLabel: { color: '#64748b' }, splitLine: { lineStyle: { color: 'rgba(20,33,61,0.08)', type: 'dashed' } } },
    yAxis: {
      type: 'category', data: categories, axisTick: { show: false }, axisLine: { show: false },
      axisLabel: { color: '#14213d', fontWeight: 600 }
    },
    series: [{
      type: 'bar',
      data: values.map((v, idx) => ({
        value: v,
        itemStyle: {
          borderRadius: 999,
          color: {
            type: 'linear',
            x: 1,
            y: 0,
            x2: 0,
            y2: 0,
            colorStops: [
              { offset: 0, color: ladderData[idx].height >= 4 ? '#ef4444' : ladderData[idx].height >= 2 ? '#f97316' : '#22c55e' },
              { offset: 1, color: ladderData[idx].height >= 4 ? '#fca5a5' : ladderData[idx].height >= 2 ? '#fed7aa' : '#bbf7d0' }
            ]
          }
        }
      })),
      barWidth: 16,
      showBackground: true,
      backgroundStyle: { color: 'rgba(20,33,61,0.04)' },
      label: { show: true, position: 'right', distance: 8, color: '#14213d', fontWeight: 600 }
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
        color: {
          type: 'linear',
          x: 1,
          y: 0,
          x2: 0,
          y2: 0,
          colorStops: [
            { offset: 0, color: reverse ? '#0f766e' : '#f97316' }, { offset: 1, color }
          ]
        }
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
        color: {
          type: 'linear',
          x: 1,
          y: 0,
          x2: 0,
          y2: 0,
          colorStops: [
            { offset: 0, color: '#84cc16' }, { offset: 1, color: '#22c55e' }
          ]
        }
      },
      label: { show: true, position: 'right', color: '#14213d' }
    }]
  }
}

// 初始化或复用 ECharts 实例，处理 DOM 元素可能已更换的情况
export async function ensureChart(chartMap, key, el) {
  if (!el) return null
  const echarts = await loadEcharts()
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
