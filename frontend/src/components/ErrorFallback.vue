<script setup lang="ts">
import { ref } from 'vue'

const props = defineProps<{
  error: Error
  componentName?: string
}>()

const emit = defineEmits<{
  recover: []
}>()

const showDetail = ref(false)
const copied = ref(false)

function handleRefresh() {
  window.location.reload()
}

function handleCopy() {
  const info = `Error: ${props.error.message}
Component: ${props.componentName || 'unknown'}
Stack: ${props.error.stack || 'no stack'}
Time: ${new Date().toISOString()}
URL: ${window.location.href}`

  navigator.clipboard.writeText(info).then(() => {
    copied.value = true
    setTimeout(() => (copied.value = false), 2000)
  })
}
</script>

<template>
  <div class="error-fallback">
    <div class="error-card">
      <div class="error-icon">!</div>
      <h2>页面出错了</h2>
      <p class="error-hint">请尝试重试，如果问题持续可以刷新页面</p>

      <div class="error-actions">
        <button class="btn btn-primary" @click="emit('recover')">重试</button>
        <button class="btn btn-secondary" @click="handleRefresh">刷新页面</button>
        <button class="btn btn-secondary" @click="handleCopy">
          {{ copied ? '已复制' : '复制错误信息' }}
        </button>
      </div>

      <button class="detail-toggle" @click="showDetail = !showDetail">
        {{ showDetail ? '收起' : '查看' }}技术详情
      </button>

      <div v-if="showDetail" class="error-detail">
        <div class="detail-line"><strong>错误类型：</strong>{{ error.name }}</div>
        <div class="detail-line"><strong>错误信息：</strong>{{ error.message }}</div>
        <div class="detail-line"><strong>组件：</strong>{{ componentName || '未知' }}</div>
        <pre class="detail-stack">{{ error.stack || '无堆栈信息' }}</pre>
      </div>
    </div>
  </div>
</template>

<style scoped>
.error-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  min-height: 100dvb;
  padding: 24px;
  background: var(--bg);
}

.error-card {
  background: var(--glass);
  border: 1px solid var(--border);
  border-radius: 16px;
  padding: 48px 40px;
  max-width: 520px;
  width: 100%;
  text-align: center;
}

.error-icon {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--accent3), #ff6b6b);
  color: #fff;
  font-size: 28px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 20px;
}

h2 {
  margin: 0 0 8px;
  font-size: 20px;
  color: var(--text);
  font-weight: 600;
}

.error-hint {
  margin: 0 0 28px;
  color: var(--text-dim);
  font-size: 14px;
}

.error-actions {
  display: flex;
  gap: 10px;
  justify-content: center;
  flex-wrap: wrap;
}

.btn {
  padding: 10px 24px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  border: 1px solid transparent;
  transition: all 0.2s;
}

.btn-primary {
  background: var(--accent);
  color: #fff;
}

.btn-primary:hover {
  filter: brightness(1.15);
}

.btn-secondary {
  background: transparent;
  color: var(--text);
  border-color: var(--border);
}

.btn-secondary:hover {
  border-color: var(--accent);
  color: var(--accent);
}

.detail-toggle {
  margin-top: 24px;
  background: none;
  border: none;
  color: var(--text-dim);
  font-size: 12px;
  cursor: pointer;
  padding: 4px 8px;
}

.detail-toggle:hover {
  color: var(--accent);
}

.error-detail {
  margin-top: 16px;
  text-align: left;
  background: rgba(0, 0, 0, 0.3);
  border-radius: 8px;
  padding: 16px;
  font-size: 13px;
  color: var(--text-dim);
  line-height: 1.8;
}

.detail-line {
  word-break: break-all;
}

.detail-line strong {
  color: var(--text);
}

.detail-stack {
  margin: 12px 0 0;
  padding: 12px;
  background: rgba(0, 0, 0, 0.3);
  border-radius: 6px;
  font-size: 11px;
  color: var(--text-dim);
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-all;
  line-height: 1.5;
}
</style>
