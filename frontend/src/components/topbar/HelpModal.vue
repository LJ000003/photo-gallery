<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { Modal } from 'ant-design-vue'
import { useUiStore } from '../../stores/ui'

/**
 * 帮助弹窗：快捷键表 + 使用提示（内容来自 i18n 的 help.shortcuts / help.usage 数组）
 */
const { t, tm } = useI18n()
const ui = useUiStore()

const shortcuts = tm('help.shortcuts') as { keys: string; desc: string }[]
const usage = tm('help.usage') as string[]
</script>

<template>
  <Modal
    :open="ui.helpOpen"
    :title="t('help.title')"
    :footer="null"
    width="460px"
    @cancel="ui.helpOpen = false"
  >
    <div class="help-body">
      <h3 class="help-section">{{ t('help.shortcutsTitle') }}</h3>
      <ul class="shortcut-list">
        <li v-for="s in shortcuts" :key="s.keys" class="shortcut-row">
          <kbd class="kbd">{{ s.keys }}</kbd>
          <span class="desc">{{ s.desc }}</span>
        </li>
      </ul>

      <h3 class="help-section">{{ t('help.usageTitle') }}</h3>
      <ul class="usage-list">
        <li v-for="(u, i) in usage" :key="i">{{ u }}</li>
      </ul>
    </div>
  </Modal>
</template>

<style scoped>
.help-body {
  padding: 4px 0;
}
.help-section {
  font-size: 13px;
  font-weight: 600;
  color: var(--c-text-dim);
  margin: 18px 0 10px;
}
.help-section:first-child {
  margin-top: 0;
}
.shortcut-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.shortcut-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.kbd {
  min-width: 108px;
  padding: 4px 10px;
  border-radius: 6px;
  background: var(--c-surface-2);
  border: 1px solid var(--c-border);
  border-bottom-width: 2px;
  font-family: var(--font-sans);
  font-size: 12px;
  font-weight: 600;
  color: var(--c-text);
  text-align: center;
}
.desc {
  font-size: 13px;
  color: var(--c-text);
}
.usage-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.usage-list li {
  font-size: 13px;
  color: var(--c-text);
  line-height: 1.6;
  padding-left: 14px;
  position: relative;
}
.usage-list li::before {
  content: '';
  position: absolute;
  left: 2px;
  top: 9px;
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--c-text-dim);
}
</style>
