/**
 * api.js —— 前端与后端 REST 接口的通信层。
 *
 * 所有接口都通过 Vite 开发代理转发到 localhost:8080 后端。
 * 生产部署时可替换为实际后端地址。
 *
 * 接口清单：
 *   GET  /api/recaps           → 获取所有已保存复盘的交易日列表（轻量摘要）
 *   GET  /api/recaps/{date}    → 获取指定交易日的完整复盘报告
 *   POST /api/recaps/capture   → 触发指定交易日的数据采集
 */
const BASE = '/api/recaps'

// 统一请求封装：自动添加 JSON Content-Type，统一错误处理。
async function request(url, options = {}) {
  const response = await fetch(url, {
    headers: {
      'Content-Type': 'application/json'
    },
    ...options
  })

  if (!response.ok) {
    throw new Error(`请求失败: ${response.status}`)
  }

  return response.json()
}

export function listRecaps() {
  return request(BASE)
}

// 读取某一天已经落盘好的复盘报告。
export function getRecap(tradeDate) {
  return request(`${BASE}/${tradeDate}`)
}

// 主动触发后端重新采集指定交易日的数据。
export function captureRecap(tradeDate) {
  return request(`${BASE}/capture`, {
    method: 'POST',
    body: JSON.stringify({ tradeDate })
  })
}
