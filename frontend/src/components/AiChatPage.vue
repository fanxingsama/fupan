<template>
  <div class="ai-chat-page">
    <div class="page-header-block">
      <p class="eyebrow">AI问答</p>
      <h2>普通对话模式，直接通过 API 和大模型聊天</h2>
      <p class="muted">
        这里不预设股票、复盘或任何行业场景，就是一个正常的通用问答入口。
        你可以自由提问，连续追问时也会保留上下文。
      </p>
    </div>

    <article class="panel ai-chat-shell">
      <div class="ai-chat-toolbar">
        <div class="ai-chat-status">
          <strong>AI问答会话</strong>
          <small class="muted">
            {{ loading ? '正在等待模型回复...' : `共 ${messages.length} 条消息` }}
          </small>
        </div>
        <button
          type="button"
          class="view-button ai-chat-reset"
          :disabled="loading || !messages.length"
          @click="resetChat"
        >
          <span>清空会话</span>
        </button>
      </div>

      <div ref="messageList" class="ai-chat-messages">
        <div v-if="!messages.length" class="ai-chat-empty">
          <strong>可以这样开始：</strong>
          <p>你是谁？</p>
          <p>帮我写一段 Python 读取 CSV 的示例。</p>
          <p>把这句话翻译成英文，并更自然一点。</p>
        </div>

        <div
          v-for="(item, index) in messages"
          :key="`${item.role}-${index}-${item.createdAt}`"
          class="ai-chat-bubble"
          :class="item.role === 'user' ? 'user' : 'assistant'"
        >
          <div class="ai-chat-bubble-head">
            <strong>{{ item.role === 'user' ? '我' : 'AI' }}</strong>
            <small>{{ formatTime(item.createdAt) }}</small>
          </div>
          <div class="ai-chat-bubble-body">{{ item.content }}</div>
        </div>
      </div>

      <div class="ai-chat-composer">
        <label class="ai-chat-input-wrap">
          <span class="field-label">发送消息</span>
          <textarea
            v-model.trim="draft"
            class="ai-chat-input"
            rows="4"
            placeholder="输入你想问的问题，按 Ctrl+Enter 发送"
            @keydown.ctrl.enter.prevent="sendMessage"
          ></textarea>
        </label>

        <div class="ai-chat-actions">
          <p v-if="error" class="error">{{ error }}</p>
          <button type="button" :disabled="loading || !draft" @click="sendMessage">
            {{ loading ? '发送中...' : '发送' }}
          </button>
        </div>
      </div>
    </article>
  </div>
</template>

<script>
import { askAiChat } from '../api'

export default {
  name: 'AiChatPage',
  data() {
    return {
      draft: '',
      loading: false,
      error: '',
      messages: []
    }
  },
  methods: {
    formatTime(value) {
      if (!value) return ''
      const date = new Date(value)
      if (Number.isNaN(date.getTime())) return ''
      return date.toLocaleTimeString('zh-CN', { hour12: false })
    },
    resetChat() {
      this.messages = []
      this.draft = ''
      this.error = ''
    },
    async sendMessage() {
      const message = this.draft.trim()
      if (!message || this.loading) return

      const userMessage = {
        role: 'user',
        content: message,
        createdAt: new Date().toISOString()
      }

      const history = this.messages.map(item => ({
        role: item.role,
        content: item.content
      }))

      this.messages.push(userMessage)
      this.draft = ''
      this.error = ''
      this.loading = true
      this.scrollToBottom()

      try {
        const response = await askAiChat(history, message)
        if (response.status !== 'ready') {
          throw new Error(response.error || 'AI 暂时无法回答')
        }

        this.messages.push({
          role: 'assistant',
          content: response.answer,
          createdAt: response.repliedAt || new Date().toISOString()
        })
        this.$nextTick(() => this.scrollToBottom())
      } catch (error) {
        this.error = error.message
        this.messages.pop()
        this.draft = message
      } finally {
        this.loading = false
      }
    },
    scrollToBottom() {
      this.$nextTick(() => {
        const container = this.$refs.messageList
        if (!container) return
        container.scrollTop = container.scrollHeight
      })
    }
  }
}
</script>
