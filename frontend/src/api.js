const BASE = '/api/recaps'

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

export function getRecap(tradeDate) {
  return request(`${BASE}/${tradeDate}`)
}

export function captureRecap(tradeDate) {
  return request(`${BASE}/capture`, {
    method: 'POST',
    body: JSON.stringify({ tradeDate })
  })
}
