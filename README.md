# 每日复盘

`每日复盘` 是一个面向 A 股短线复盘的前后端分离项目。

- 后端：Spring Boot 3
- 前端：Vue 2 + Vite

## 项目结构

```text
每日复盘/
  backend/
  frontend/
```

## 当前能力

- 后端提供复盘报告查询、历史列表查询、手动触发采集接口
- 默认使用问财真实采集器，采集后保存为按日期归档的 JSON 历史报告
- 复盘内容覆盖炸板、连板、首板、跌停、板块强弱、10 日涨幅榜和涨跌家数
- 保留 `mock` 采集器作为离线演示兜底
- 前端使用 Vue 2 Options API 展示复盘总览、板块强弱和历史记录

## 启动

### 后端

```powershell
cd backend
$env:WENCAI_COOKIE="your_cookie"
mvn spring-boot:run
```

### 前端

```powershell
cd frontend
npm install
npm run dev
```

前端默认访问 `http://localhost:8080/api`。

## 真实数据采集要求

- Python 3.10+
- `pip install pywencai`
- 本机登录问财后导出可用 `cookie`
- 启动后端前设置环境变量 `WENCAI_COOKIE`
- `pywencai` 依赖 Node.js 环境，请保证本机已安装 Node.js

## 保存历史

- Spring Boot 后端会将每个交易日的复盘结果保存到 `backend/data/<tradeDate>.json`
- 前端的历史记录列表就是读取这些历史文件生成的

## 一键使用

```powershell
cd scripts
.\setup_local_env.ps1
.\register_daily_task.ps1
.\start_all.ps1
```

- `setup_local_env.ps1` 会准备 Maven、Python 虚拟环境、`pywencai` 和前端依赖
- `register_daily_task.ps1` 会注册工作日 15:10 的自动采集任务
- `start_all.ps1` 会分别打开后端和前端窗口
- 也可以直接双击项目根目录下的 `启动每日复盘.bat`

## 后续建议

- 根据你实际跑出来的字段再继续微调问财列名别名
- 增加交易日历和自动调度
- 再补一层 SQLite 或 MySQL 索引，便于多条件检索和复盘筛选
