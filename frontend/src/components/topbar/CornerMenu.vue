<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import {
  BarChartOutlined,
  DeleteOutlined,
  DownloadOutlined,
  LockOutlined,
  MoreOutlined,
  QuestionCircleOutlined,
} from '@ant-design/icons-vue'
import { Button, Dropdown, Menu, MenuItem } from 'ant-design-vue'
import { useUiStore } from '../../stores/ui'
import { useToastStore } from '../../stores/toast'
import { api } from '../../api'

/**
 * 角落菜单（⚙️）：回收站 / 备份导出 / 帮助 / 重新锁定
 * 工具类入口全部收敛于此，主导航保持 4 个浏览模式
 */
const { t } = useI18n()
const router = useRouter()
const ui = useUiStore()
const toast = useToastStore()

const exporting = ref(false)

/** 备份导出：POST 全量备份，blob 下载 */
async function exportBackup(): Promise<void> {
  if (exporting.value) return
  exporting.value = true
  try {
    const res = await api('/api/backup/export', { method: 'POST', body: JSON.stringify({}) })
    const blob = await res.blob()
    const cd = res.headers.get('Content-Disposition') || ''
    const match = /filename="?([^"]+)"?/.exec(cd)
    const filename =
      match?.[1] ?? `photo-gallery-backup-${new Date().toISOString().slice(0, 10)}.zip`
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(url)
    toast.success(t('backup.done'))
  } catch (err) {
    toast.error(err instanceof Error ? err.message : t('backup.failed'))
  } finally {
    exporting.value = false
  }
}

function onMenuClick({ key }: { key: string | number }): void {
  switch (String(key)) {
    case 'trash':
      router.push({ name: 'trash' })
      break
    case 'backup':
      void exportBackup()
      break
    case 'stats':
      router.push({ name: 'stats' })
      break
    case 'help':
      ui.helpOpen = true
      break
    case 'relock':
      ui.reLock()
      break
  }
}
</script>

<template>
  <Dropdown placement="bottomRight" trigger="click">
    <Button class="corner-btn" type="text" :aria-label="t('topbar.more')">
      <MoreOutlined />
    </Button>
    <template #overlay>
      <Menu @click="onMenuClick">
        <MenuItem key="trash">
          <DeleteOutlined />
          {{ t('nav.trash') }}
        </MenuItem>
        <MenuItem key="backup" :disabled="exporting">
          <DownloadOutlined />
          {{ exporting ? t('topbar.backupExporting') : t('topbar.backupExport') }}
        </MenuItem>
        <MenuItem key="stats">
          <BarChartOutlined />
          {{ t('stats.title') }}
        </MenuItem>
        <MenuItem key="help">
          <QuestionCircleOutlined />
          {{ t('topbar.help') }}
        </MenuItem>
        <MenuItem key="relock" danger>
          <LockOutlined />
          {{ t('topbar.relock') }}
        </MenuItem>
      </Menu>
    </template>
  </Dropdown>
</template>

<style scoped>
.corner-btn {
  width: 36px;
  height: 36px;
  border-radius: 999px;
  color: var(--c-text-dim);
}
.corner-btn:hover {
  color: var(--c-text);
  background: var(--c-surface-2);
}
</style>
