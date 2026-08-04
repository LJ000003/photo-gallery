<script setup lang="ts">
import { onErrorCaptured, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Button, Result } from 'ant-design-vue'

/**
 * 全局错误边界：渲染子树抛错时展示错误页（替代旧 ErrorFallback）
 * 提供 刷新 / 复制诊断信息 两个出口
 */
const { t } = useI18n()
const error = ref<Error | null>(null)

onErrorCaptured((err) => {
  error.value = err instanceof Error ? err : new Error(String(err))
  return false // 阻止继续向上冒泡，避免重复处理
})

function reload(): void {
  window.location.reload()
}

function copyDiagnostics(): void {
  const text = [
    `URL: ${window.location.href}`,
    `Time: ${new Date().toLocaleString()}`,
    `Error: ${error.value?.message || 'unknown'}`,
    `Stack: ${error.value?.stack || 'n/a'}`,
  ].join('\n')
  if (navigator.clipboard && window.isSecureContext) {
    navigator.clipboard.writeText(text)
  } else {
    const ta = document.createElement('textarea')
    ta.value = text
    document.body.appendChild(ta)
    ta.select()
    document.execCommand('copy')
    document.body.removeChild(ta)
  }
}
</script>

<template>
  <div v-if="error" class="error-boundary">
    <Result status="error" :title="t('common.errorTitle')" :sub-title="error.message">
      <template #extra>
        <Button type="primary" @click="reload">{{ t('common.errorReload') }}</Button>
        <Button @click="copyDiagnostics">{{ t('common.errorCopyDiagnostics') }}</Button>
      </template>
    </Result>
  </div>
  <slot v-else />
</template>

<style scoped>
.error-boundary {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100dvh;
  padding: 24px;
}
</style>
