<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useUiStore } from '../stores/ui'
import { useToastStore } from '../stores/toast'
import { api } from '../api'

const { t } = useI18n()
const ui = useUiStore()
const toast = useToastStore()

const clicks = ref(0)
const easterEgg = ref(false)
const exporting = ref(false)

function onClick(): void {
  clicks.value++
  if (clicks.value >= 30) easterEgg.value = true
}

/**
 * 导出备份：POST 全量备份（照片原文件 + 数据库元数据 JSON，tar.gz 流式下载）。
 * 大文件走 blob 下载（个人图库量级可接受）；导出中按钮禁用防连点。
 */
async function exportBackup(): Promise<void> {
  if (exporting.value) return
  exporting.value = true
  try {
    const res = await api('/api/backup/export', { method: 'POST', body: JSON.stringify({}) })
    const blob = await res.blob()
    const cd = res.headers.get('Content-Disposition') || ''
    const match = /filename="?([^"]+)"?/.exec(cd)
    const filename =
      match?.[1] ?? `photo-gallery-backup-${new Date().toISOString().slice(0, 10)}.tar.gz`
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(url)
    toast.success(t('backup.success'))
  } catch (err) {
    toast.error(err instanceof Error ? err.message : t('backup.failed'))
  } finally {
    exporting.value = false
  }
}
</script>

<template>
  <header class="header">
    <h1 :class="{ rgb: easterEgg }" @click="onClick">照片管理器</h1>
    <div class="header-actions">
      <button
        class="export-btn"
        :title="exporting ? t('backup.exporting') : t('backup.export')"
        :disabled="exporting"
        @click="exportBackup"
      >
        <span>{{ exporting ? '⏳' : '⤓' }}</span>
      </button>
      <button class="help-btn" :title="t('help.button')" @click="ui.helpOpen = true">
        <span>?</span>
      </button>
    </div>
  </header>
</template>

<style scoped>
.header-actions {
  position: absolute;
  top: 50%;
  right: 24px;
  transform: translateY(-50%);
  display: flex;
  gap: 10px;
}

.help-btn,
.export-btn {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--glass);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid var(--border);
  color: var(--text-dim);
  font-size: 18px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.3s;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}
.help-btn:hover,
.export-btn:hover:not(:disabled) {
  border-color: var(--accent);
  color: var(--accent);
  box-shadow: var(--glow-cyan);
}
.export-btn:disabled {
  opacity: 0.6;
  cursor: default;
}

.header h1.rgb {
  background: linear-gradient(90deg, #f00, #f80, #ff0, #0f0, #08f, #80f, #f00);
  background-size: 300% 100%;
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  animation: rgb-sweep 2s linear infinite;
}
@keyframes rgb-sweep {
  0% {
    background-position: 0% 50%;
  }
  100% {
    background-position: 200% 50%;
  }
}

@media (max-width: 768px) {
  .header-actions {
    right: 12px;
    gap: 8px;
  }
  .help-btn,
  .export-btn {
    width: 32px;
    height: 32px;
    font-size: 16px;
  }
}
</style>
