<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

/**
 * 模式分段导航：照片 / 相册 / 时间线 / 地图（桌面顶栏与移动底栏共用）
 * 用 router-link 语义，点击当前模式无动作
 */
const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const modes = [
  { name: 'gallery', key: 'nav.photos', icon: '▦' },
  { name: 'albums', key: 'nav.albums', icon: '▤' },
  { name: 'timeline', key: 'nav.timeline', icon: '≡' },
  { name: 'map', key: 'nav.map', icon: '◉' },
] as const

function go(mode: string): void {
  if (route.name !== mode) router.push({ name: mode })
}
</script>

<template>
  <nav class="mode-tabs" role="tablist" :aria-label="t('nav.mode')">
    <button
      v-for="m in modes"
      :key="m.name"
      class="mode-tab"
      :class="{ active: route.name === m.name }"
      role="tab"
      :aria-selected="route.name === m.name"
      @click="go(m.name)"
    >
      {{ t(m.key) }}
    </button>
  </nav>
</template>

<style scoped>
.mode-tabs {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 3px;
  background: var(--c-surface-2);
  border: 1px solid var(--c-border);
  border-radius: 999px;
}
.mode-tab {
  border: none;
  background: transparent;
  color: var(--c-text-dim);
  font-size: 13px;
  font-weight: 500;
  line-height: 1;
  padding: 7px 16px;
  border-radius: 999px;
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
}
.mode-tab:hover {
  color: var(--c-text);
}
.mode-tab.active {
  background: var(--c-surface);
  color: var(--c-text);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.1);
}
.mode-tab:active {
  transform: scale(0.96);
}

@media (max-width: 768px) {
  .mode-tabs {
    flex: 1;
    justify-content: center;
  }
  .mode-tab {
    padding: 7px 12px;
    font-size: 12px;
  }
}
</style>
