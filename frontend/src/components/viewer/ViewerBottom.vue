<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import {
  FullscreenOutlined,
  InfoCircleOutlined,
  LeftOutlined,
  PauseCircleOutlined,
  PlayCircleOutlined,
  RightOutlined,
} from '@ant-design/icons-vue'
import { Button } from 'ant-design-vue'

/**
 * 灯箱底栏：幻灯片控制（prev/play/next/计数）+ EXIF/全屏
 * 纯展示 + 事件上抛，自 PhotoViewer 拆分
 */
defineProps<{
  canSlideshow: boolean
  playing: boolean
  currentIndex: number
  total: number
  exifOpen: boolean
  fullscreenActive: boolean
}>()
const emit = defineEmits<{
  navigatePrev: []
  navigateNext: []
  toggleSlideshow: []
  toggleExif: []
  toggleFullscreen: []
}>()

const { t } = useI18n()
</script>

<template>
  <footer class="viewer-bottom">
    <div v-if="canSlideshow" class="slideshow-controls">
      <button
        class="ss-btn"
        :disabled="currentIndex <= 0"
        :aria-label="t('viewer.previous')"
        @click="emit('navigatePrev')"
      >
        <LeftOutlined />
      </button>
      <button
        class="ss-btn ss-play"
        :aria-label="t(playing ? 'viewer.pause' : 'viewer.play')"
        @click="emit('toggleSlideshow')"
      >
        <PlayCircleOutlined v-if="!playing" />
        <PauseCircleOutlined v-else />
      </button>
      <button
        class="ss-btn"
        :disabled="currentIndex >= total - 1"
        :aria-label="t('viewer.next')"
        @click="emit('navigateNext')"
      >
        <RightOutlined />
      </button>
      <span class="ss-counter">{{ currentIndex + 1 }} / {{ total }}</span>
    </div>

    <div class="bottom-actions">
      <Button
        type="text"
        class="vd-btn"
        :class="{ active: exifOpen }"
        :title="t('viewer.exif')"
        :aria-label="t('viewer.exif')"
        @click="emit('toggleExif')"
      >
        <InfoCircleOutlined />
      </Button>
      <Button
        type="text"
        class="vd-btn"
        :title="t('viewer.fullscreen')"
        :aria-label="t('viewer.fullscreen')"
        @click="emit('toggleFullscreen')"
      >
        <FullscreenOutlined />
      </Button>
    </div>
  </footer>
</template>

<style scoped>
/* 底栏 */
.viewer-bottom {
  position: relative;
  z-index: 6;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 20px;
  padding: 12px 20px calc(12px + env(safe-area-inset-bottom));
}
.slideshow-controls {
  display: flex;
  align-items: center;
  gap: 8px;
}
.ss-btn {
  width: 38px;
  height: 38px;
  border: none;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.8);
  font-size: 15px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.15s ease;
}
.ss-btn:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.18);
  color: #fff;
}
.ss-btn:disabled {
  opacity: 0.25;
  cursor: default;
}
.ss-play {
  width: 44px;
  height: 44px;
  background: rgba(255, 255, 255, 0.14);
}
.ss-counter {
  margin-left: 8px;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.55);
  font-variant-numeric: tabular-nums;
}
.bottom-actions {
  display: flex;
  gap: 2px;
}
/* 圆形图标按钮（与 PhotoViewer 顶栏共用样式，scoped 下各自持有副本） */
.vd-btn {
  width: 36px;
  height: 36px;
  border-radius: 999px;
  color: rgba(255, 255, 255, 0.72);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
}
.vd-btn:hover {
  color: #fff;
  background: rgba(255, 255, 255, 0.12);
}
.vd-btn.active {
  color: var(--c-accent);
  background: rgba(37, 99, 235, 0.18);
}

@media (max-width: 768px) {
  .viewer-bottom {
    padding-left: 12px;
    padding-right: 12px;
  }
}
</style>
