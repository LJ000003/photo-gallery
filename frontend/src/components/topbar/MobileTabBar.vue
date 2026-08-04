<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { PlusOutlined } from '@ant-design/icons-vue'
import { Button } from 'ant-design-vue'
import ModeTabs from './ModeTabs.vue'
import { useUiStore } from '../../stores/ui'

/**
 * 移动端底部导航：4 个浏览模式 + 中央凸起上传按钮（iOS 模式）
 * 仅 ≤768px 显示；顶栏的模式分段导航在移动端隐藏，避免双导航
 */
const { t } = useI18n()
const ui = useUiStore()
</script>

<template>
  <nav class="mobile-tabbar" :aria-label="t('nav.mobile')">
    <div class="tabbar-inner">
      <ModeTabs class="tabs" />
      <Button
        type="primary"
        shape="circle"
        class="fab"
        :aria-label="t('topbar.upload')"
        @click="ui.uploadOpen = true"
      >
        <PlusOutlined />
      </Button>
    </div>
  </nav>
</template>

<style scoped>
.mobile-tabbar {
  display: none;
}

@media (max-width: 768px) {
  .mobile-tabbar {
    display: block;
    position: fixed;
    left: 0;
    right: 0;
    bottom: 0;
    z-index: 60;
    background: color-mix(in srgb, var(--c-bg) 88%, transparent);
    backdrop-filter: blur(16px) saturate(180%);
    -webkit-backdrop-filter: blur(16px) saturate(180%);
    border-top: 1px solid var(--c-border);
    padding-bottom: env(safe-area-inset-bottom);
  }
  .tabbar-inner {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 16px;
    padding: 8px 12px;
    position: relative;
  }
  .fab {
    width: 52px;
    height: 52px;
    font-size: 18px;
    box-shadow: 0 4px 16px rgba(37, 99, 235, 0.35);
  }
}
</style>
