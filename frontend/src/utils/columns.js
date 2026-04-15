// 表格列定义

export const BASE_COLUMNS = {
  code: { key: 'code', label: '代码', sortable: true, sortType: 'string' },
  name: { key: 'name', label: '名称', sortable: true, sortType: 'string' },
  boardHeight: { key: 'boardHeight', label: '连板', sortable: true, sortType: 'number' },
  changePercent: { key: 'changePercent', label: '涨幅', sortable: true, sortType: 'number' },
  price: { key: 'price', label: '股价', sortable: true, sortType: 'number' },
  concept: { key: 'concept', label: '概念', sortable: true, sortType: 'string' },
  amount: { key: 'amount', label: '成交额', sortable: true, sortType: 'number' },
  floatMarketValue: { key: 'floatMarketValue', label: '流通市值', sortable: true, sortType: 'number' },
  reason: { key: 'reason', label: '原因', sortable: true, sortType: 'string' }
}

export const STANDARD_COLUMNS = [
  BASE_COLUMNS.code, BASE_COLUMNS.name, BASE_COLUMNS.boardHeight,
  BASE_COLUMNS.changePercent, BASE_COLUMNS.price, BASE_COLUMNS.concept,
  BASE_COLUMNS.amount, BASE_COLUMNS.floatMarketValue
]

export const LIMIT_REASON_COLUMNS = [...STANDARD_COLUMNS, BASE_COLUMNS.reason]

export const BROKEN_COLUMNS = [
  BASE_COLUMNS.code, BASE_COLUMNS.name, BASE_COLUMNS.changePercent,
  BASE_COLUMNS.price, BASE_COLUMNS.concept, BASE_COLUMNS.amount,
  BASE_COLUMNS.floatMarketValue
]

export const FOCUS_DETAIL_COLUMNS = [
  BASE_COLUMNS.code, BASE_COLUMNS.name, BASE_COLUMNS.price,
  BASE_COLUMNS.amount, BASE_COLUMNS.floatMarketValue, BASE_COLUMNS.reason
]
