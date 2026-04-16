<template>
  <div class="user-analysis-page">
    <div class="page-header-block">
      <p class="eyebrow">AI分析用户</p>
      <h2>上传某位高手的历史持仓截图，反推其短线买卖逻辑</h2>
      <p class="muted">
        支持上传整个图片文件夹或多张截图。系统会按文件名里的日期分组，例如
        <code>2026-03-06_yesterday_stock_img_xxx</code> 会被识别为 2026-03-06 的截图。
        同一天如果有多张图片，默认使用文件名排序后的第一张。
      </p>
    </div>

    <article class="panel">
      <div class="section-head">
        <div>
          <h2>上传截图</h2>
          <p class="muted section-subtitle">建议直接上传你下载好的无水印截图。</p>
        </div>
      </div>

      <div class="analysis-upload-grid">
        <div class="upload-card">
          <h3>上传文件夹</h3>
          <p class="muted">适合一次分析某个用户完整的一段比赛记录。</p>
          <input ref="folderInput" type="file" accept="image/*" webkitdirectory directory multiple @change="handleFolderChange" />
        </div>
        <div class="upload-card">
          <h3>上传多张图片</h3>
          <p class="muted">适合你手动挑选几个关键交易日做深度分析。</p>
          <input ref="filesInput" type="file" accept="image/*" multiple @change="handleFilesChange" />
        </div>
      </div>

      <div class="import-row">
        <div class="analysis-selection-summary">
          <strong>{{ selectedFiles.length }} 张原始图片</strong>
          <span class="muted">按日期去重后会分析 {{ groupedPreview.length }} 个交易日。</span>
        </div>
        <button :disabled="!selectedFiles.length || analyzing" @click="submitAnalysis">
          {{ analyzing ? 'AI分析中...' : '开始分析' }}
        </button>
      </div>

      <div v-if="groupedPreview.length" class="analysis-preview-grid">
        <div v-for="item in groupedPreview" :key="item.tradeDate" class="preview-card">
          <strong>{{ item.tradeDate }}</strong>
          <span>{{ item.fileName }}</span>
          <small>{{ item.imageType }}</small>
        </div>
      </div>

      <p v-if="error" class="error">{{ error }}</p>
    </article>

    <article v-if="result" class="panel">
      <div class="section-head">
        <div>
          <h2>整体分析</h2>
          <p class="muted section-subtitle">上传 {{ result.uploadedImageCount }} 张，实际分析 {{ result.analyzedDayCount }} 个交易日。</p>
        </div>
      </div>

      <div class="insight-hero">
        <h3>{{ result.tradingStyleProfile || '暂无风格画像' }}</h3>
        <p class="watch-summary">{{ result.overallConclusion }}</p>
      </div>

      <div v-if="result.styleTags?.length" class="tag-list">
        <span v-for="tag in result.styleTags" :key="tag" class="tag">{{ tag }}</span>
      </div>

      <div class="insight-two-col user-analysis-top-grid">
        <section class="insight-section">
          <h3>重复出现的模式</h3>
          <ul class="text-list">
            <li v-for="item in result.recurringPatterns" :key="item">{{ item }}</li>
          </ul>
        </section>
        <section class="insight-section">
          <h3>可学习的方法论</h3>
          <ul class="text-list">
            <li v-for="item in result.methodology" :key="item">{{ item }}</li>
          </ul>
        </section>
      </div>

      <section v-if="result.riskWarnings?.length" class="insight-section">
        <h3>边界与风险提示</h3>
        <ul class="text-list">
          <li v-for="item in result.riskWarnings" :key="item">{{ item }}</li>
        </ul>
      </section>

      <p class="muted ai-disclaimer">{{ result.disclaimer }}</p>
    </article>

    <article v-for="day in result?.dayAnalyses || []" :key="`${day.tradeDate}-${day.imageName}`" class="panel">
      <div class="section-head">
        <div>
          <h2>{{ day.tradeDate }}</h2>
          <p class="muted section-subtitle">{{ day.imageName }}</p>
        </div>
        <span class="risk-tag risk-warning">{{ day.imageType }}</span>
      </div>

      <div class="briefing-hero">
        <h3>当日推断</h3>
        <p>{{ day.summary }}</p>
      </div>

      <div class="insight-two-col user-analysis-grid">
        <section class="insight-section">
          <h3>截图识别到的持仓重点</h3>
          <ul class="text-list">
            <li v-for="item in day.holdings" :key="item">{{ item }}</li>
          </ul>
        </section>
        <section class="insight-section">
          <h3>可能的买卖原因</h3>
          <ul class="text-list">
            <li v-for="item in day.inferredReasons" :key="item">{{ item }}</li>
          </ul>
        </section>
        <section class="insight-section">
          <h3>可能买点</h3>
          <ul class="text-list">
            <li v-for="item in day.probableBuyPoints" :key="item">{{ item }}</li>
          </ul>
        </section>
        <section class="insight-section">
          <h3>可能卖点</h3>
          <ul class="text-list">
            <li v-for="item in day.probableSellPoints" :key="item">{{ item }}</li>
          </ul>
        </section>
        <section class="insight-section">
          <h3>量价结构与盘面线索</h3>
          <ul class="text-list">
            <li v-for="item in day.volumePriceClues" :key="item">{{ item }}</li>
          </ul>
        </section>
        <section class="insight-section">
          <h3>消息面与板块催化</h3>
          <ul class="text-list">
            <li v-for="item in day.newsDrivers" :key="item">{{ item }}</li>
          </ul>
        </section>
      </div>

      <section v-if="day.nextDayFocus?.length" class="insight-section">
        <h3>隔日如果继续跟踪，重点看什么</h3>
        <ul class="text-list">
          <li v-for="item in day.nextDayFocus" :key="item">{{ item }}</li>
        </ul>
      </section>
    </article>
  </div>
</template>

<script>
import { analyzeUserStockImages } from '../api'

const DATE_PATTERN = /^(\d{4}-\d{2}-\d{2})_(.+)$/

export default {
  name: 'UserStockAnalysisPage',
  data() {
    return {
      selectedFiles: [],
      analyzing: false,
      error: '',
      result: null
    }
  },
  computed: {
    groupedPreview() {
      const firstByDate = new Map()
      const sorted = [...this.selectedFiles].sort((a, b) => a.name.localeCompare(b.name))
      sorted.forEach(file => {
        const parsed = this.parseFileName(file.name)
        if (!parsed) return
        if (!firstByDate.has(parsed.tradeDate)) {
          firstByDate.set(parsed.tradeDate, {
            tradeDate: parsed.tradeDate,
            fileName: file.name,
            imageType: parsed.imageType
          })
        }
      })
      return [...firstByDate.values()].sort((a, b) => a.tradeDate.localeCompare(b.tradeDate))
    }
  },
  methods: {
    parseFileName(fileName) {
      const match = DATE_PATTERN.exec(fileName || '')
      if (!match) return null
      const suffix = match[2] || ''
      return {
        tradeDate: match[1],
        imageType: suffix.includes('today_stock_img')
          ? 'today_stock_img'
          : suffix.includes('yesterday_stock_img')
            ? 'yesterday_stock_img'
            : 'unknown'
      }
    },
    replaceSelection(fileList) {
      this.selectedFiles = Array.from(fileList || []).filter(file => file.type.startsWith('image/'))
      this.error = ''
      this.result = null
    },
    handleFolderChange(event) {
      this.replaceSelection(event.target.files)
      if (this.$refs.filesInput) this.$refs.filesInput.value = ''
    },
    handleFilesChange(event) {
      this.replaceSelection(event.target.files)
      if (this.$refs.folderInput) this.$refs.folderInput.value = ''
    },
    async submitAnalysis() {
      if (!this.selectedFiles.length) return
      this.analyzing = true
      this.error = ''
      try {
        this.result = await analyzeUserStockImages(this.selectedFiles)
      } catch (error) {
        this.error = error.message
      } finally {
        this.analyzing = false
      }
    }
  }
}
</script>
