<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  DeleteOutlined,
  DownloadOutlined,
  EditOutlined,
  FullscreenOutlined,
  InfoCircleOutlined,
  LeftOutlined,
  LinkOutlined,
  PauseCircleOutlined,
  PlayCircleOutlined,
  RightOutlined,
} from '@ant-design/icons-vue'
import { Button, Modal } from 'ant-design-vue'

import ExifPanel from './ExifPanel.vue'
import { webpUrl } from '../../webp'
import { appendMediaParams } from '../../utils/token'
import { formatSize } from '../../utils/format'
import { useUiStore } from '../../stores/ui'
import { usePhotoActions } from '../../composables/usePhotoActions'
import type { Photo } from '../../types/photo'

const SLIDESHOW_INTERVAL = 5000

/**
 * 全屏灯箱（沉浸式深色舞台，深浅主题下观感一致）：
 * 两段式加载（缩略图→全图淡入）、幻灯片（5s + 全屏联动）、
 * EXIF 滑出面板、编辑/分享/下载/删除、键盘 ← → Space E F Esc
 */
const props = defineProps<{ photo: Photo }>()
const emit = defineEmits<{ close: [] }>()

const { t } = useI18n()
const ui = useUiStore()
const { deletePhoto, generateShare } = usePhotoActions()

const fullLoaded = ref(false)
const exifOpen = ref(false)
const rootRef = ref<HTMLElement | null>(null)

/* ---------- 幻灯片 ---------- */
const currentIndex = computed(() => ui.viewPhotos.findIndex((p) => p.id === props.photo.id))
const canSlideshow = computed(() => ui.viewPhotos.length >= 2 && currentIndex.value !== -1)

let slideTimer: ReturnType<typeof setTimeout> | null = null
function clearSlideTimer(): void {
  if (slideTimer !== null) {
    clearTimeout(slideTimer)
    slideTimer = null
  }
}
/** 唯一调度入口：清旧 → 满足条件再挂新（回调内不自调度） */
function syncSlideTimer(): void {
  clearSlideTimer()
  if (!ui.slideshowPlaying || !canSlideshow.value) return
  slideTimer = setTimeout(() => {
    ui.navigateViewer(1, true)
  }, SLIDESHOW_INTERVAL)
}

/* ---------- 全屏 ---------- */
function isFullscreenActive(): boolean {
  return !!(
    document.fullscreenElement ||
    (document as Document & { webkitFullscreenElement?: Element }).webkitFullscreenElement
  )
}
function enterFullscreen(): void {
  const el = rootRef.value
  if (!el || isFullscreenActive()) return
  try {
    if (el.requestFullscreen) {
      el.requestFullscreen().catch(() => {})
    } else {
      const webkit = (el as HTMLElement & { webkitRequestFullscreen?: () => void })
        .webkitRequestFullscreen
      webkit?.call(el)
    }
  } catch {
    /* 静默 */
  }
}
function exitFullscreen(): void {
  if (!isFullscreenActive()) return
  try {
    if (document.exitFullscreen) {
      document.exitFullscreen().catch(() => {})
    } else {
      const webkit = (document as Document & { webkitExitFullscreen?: () => void })
        .webkitExitFullscreen
      webkit?.call(document)
    }
  } catch {
    /* 静默 */
  }
}
/** 用户按 Esc 退出全屏 → 同步暂停幻灯片 */
function onFullscreenChange(): void {
  if (!isFullscreenActive() && ui.slideshowPlaying) {
    ui.toggleSlideshow()
  }
}

/* ---------- 照片切换 / 播放状态 → 重置淡入与倒计时 ---------- */
watch(
  () => props.photo.id,
  () => {
    fullLoaded.value = false
    syncSlideTimer()
  },
)
watch(
  () => ui.slideshowPlaying,
  (playing) => {
    syncSlideTimer()
    if (playing) enterFullscreen()
    else exitFullscreen()
  },
)
watch(canSlideshow, () => syncSlideTimer())

/* ---------- 键盘 ---------- */
function onKeydown(e: KeyboardEvent): void {
  switch (e.key) {
    case 'Escape':
      // 分层处理：全屏中先退全屏（浏览器也拦截 Esc），不关闭灯箱
      if (isFullscreenActive()) {
        e.preventDefault()
        exitFullscreen()
      } else {
        emit('close')
      }
      break
    case 'ArrowLeft':
      ui.navigateViewer(-1)
      break
    case 'ArrowRight':
      ui.navigateViewer(1)
      break
    case ' ':
      e.preventDefault()
      ui.toggleSlideshow()
      break
    case 'e':
    case 'E':
      ui.editPhoto = props.photo
      break
    case 'f':
    case 'F':
      if (isFullscreenActive()) exitFullscreen()
      else enterFullscreen()
      break
  }
}

onMounted(() => {
  window.addEventListener('keydown', onKeydown)
  document.addEventListener('fullscreenchange', onFullscreenChange)
  document.addEventListener('webkitfullscreenchange', onFullscreenChange)
  syncSlideTimer()
})
onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown)
  document.removeEventListener('fullscreenchange', onFullscreenChange)
  document.removeEventListener('webkitfullscreenchange', onFullscreenChange)
  clearSlideTimer()
  exitFullscreen()
})

function onClose(): void {
  clearSlideTimer()
  emit('close')
}

/* ---------- 操作 ---------- */
function onDelete(): void {
  Modal.confirm({
    title: t('actions.delete'),
    content: t('edit.deleteConfirm', { name: props.photo.name || `#${props.photo.id}` }),
    okText: t('actions.delete'),
    okButtonProps: { danger: true },
    cancelText: t('actions.cancel'),
    onOk: () => {
      const id = props.photo.id
      // 统一删除路径：store 从快照移除并导航到下一张（或关闭），组件不再自行导航
      ui.removeViewerPhoto(id)
      void deletePhoto(id)
    },
  })
}

const downloadUrl = computed(() => appendMediaParams(webpUrl(props.photo.id), props.photo))
</script>

<template>
  <div ref="rootRef" class="photo-viewer">
    <!-- 背景（点击照片外区域关闭） -->
    <div class="viewer-backdrop" @click="onClose"></div>

    <!-- 顶栏：标题 + 操作 -->
    <header class="viewer-header">
      <div class="viewer-title">
        <h2 class="viewer-name">{{ photo.name || `#${photo.id}` }}</h2>
        <p class="viewer-meta">
          {{ formatSize(photo.fileSize)
          }}<template v-if="photo.category"> · {{ photo.category.name }}</template>
        </p>
      </div>
      <div class="viewer-actions">
        <Button
          type="text"
          class="vd-btn"
          :title="t('actions.edit')"
          :aria-label="t('actions.edit')"
          @click="ui.editPhoto = photo"
        >
          <EditOutlined />
        </Button>
        <Button
          type="text"
          class="vd-btn"
          :title="t('share.generate')"
          :aria-label="t('share.generate')"
          @click="generateShare([photo.id])"
        >
          <LinkOutlined />
        </Button>
        <a
          class="vd-btn vd-link"
          :href="downloadUrl"
          download
          :title="t('actions.download')"
          :aria-label="t('actions.download')"
        >
          <DownloadOutlined />
        </a>
        <Button
          type="text"
          class="vd-btn danger"
          :title="t('actions.delete')"
          :aria-label="t('actions.delete')"
          @click="onDelete"
        >
          <DeleteOutlined />
        </Button>
        <Button
          type="text"
          class="vd-btn"
          :title="t('viewer.close')"
          :aria-label="t('viewer.close')"
          @click="onClose"
        >
          <span class="close-x">×</span>
        </Button>
      </div>
    </header>

    <!-- 舞台：前后切换 + 图片 -->
    <div class="viewer-stage" @click="onClose">
      <button
        v-if="canSlideshow"
        class="nav-btn prev"
        :disabled="currentIndex <= 0"
        :aria-label="t('viewer.previous')"
        @click.stop="ui.navigateViewer(-1)"
      >
        <LeftOutlined />
      </button>

      <div class="img-wrap" @click.stop>
        <img
          class="img-thumb"
          :src="appendMediaParams(`/api/v1/photos/${photo.id}/thumbnail`, photo)"
          :alt="photo.name"
          decoding="async"
        />
        <img
          class="img-full"
          :class="{ show: fullLoaded }"
          :src="appendMediaParams(webpUrl(photo.id), photo)"
          :alt="photo.name"
          decoding="async"
          @load="fullLoaded = true"
        />
      </div>

      <button
        v-if="canSlideshow"
        class="nav-btn next"
        :disabled="currentIndex >= ui.viewPhotos.length - 1"
        :aria-label="t('viewer.next')"
        @click.stop="ui.navigateViewer(1)"
      >
        <RightOutlined />
      </button>
    </div>

    <!-- 底栏：幻灯片控制 + EXIF/全屏 -->
    <footer class="viewer-bottom">
      <div v-if="canSlideshow" class="slideshow-controls">
        <button
          class="ss-btn"
          :disabled="currentIndex <= 0"
          :aria-label="t('viewer.previous')"
          @click="ui.navigateViewer(-1)"
        >
          <LeftOutlined />
        </button>
        <button
          class="ss-btn ss-play"
          :aria-label="ui.slideshowPlaying ? 'pause' : 'play'"
          @click="ui.toggleSlideshow()"
        >
          <PlayCircleOutlined v-if="!ui.slideshowPlaying" />
          <PauseCircleOutlined v-else />
        </button>
        <button
          class="ss-btn"
          :disabled="currentIndex >= ui.viewPhotos.length - 1"
          :aria-label="t('viewer.next')"
          @click="ui.navigateViewer(1)"
        >
          <RightOutlined />
        </button>
        <span class="ss-counter">{{ currentIndex + 1 }} / {{ ui.viewPhotos.length }}</span>
      </div>

      <div class="bottom-actions">
        <Button
          type="text"
          class="vd-btn"
          :class="{ active: exifOpen }"
          :title="t('viewer.exif')"
          :aria-label="t('viewer.exif')"
          @click="exifOpen = !exifOpen"
        >
          <InfoCircleOutlined />
        </Button>
        <Button
          type="text"
          class="vd-btn"
          :title="t('viewer.fullscreen')"
          :aria-label="t('viewer.fullscreen')"
          @click="isFullscreenActive() ? exitFullscreen() : enterFullscreen()"
        >
          <FullscreenOutlined />
        </Button>
      </div>
    </footer>

    <ExifPanel :photo="photo" :open="exifOpen" />
  </div>
</template>

<style scoped>
.photo-viewer {
  position: fixed;
  inset: 0;
  z-index: 1000;
  background: rgba(10, 10, 12, 0.92);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  display: flex;
  flex-direction: column;
  color: #fff;
  animation: viewer-in 0.25s ease;
}
@keyframes viewer-in {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}
.viewer-backdrop {
  position: absolute;
  inset: 0;
}

/* 顶栏 */
.viewer-header {
  position: relative;
  z-index: 6;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 20px;
}
.viewer-name {
  font-size: 15px;
  font-weight: 600;
  color: #fff;
  max-width: 40vw;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.viewer-meta {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.5);
  margin-top: 2px;
}
.viewer-actions {
  display: flex;
  align-items: center;
  gap: 2px;
}
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
.vd-btn.danger:hover {
  color: #ff8a94;
  background: rgba(255, 80, 90, 0.16);
}
.vd-link {
  text-decoration: none;
  cursor: pointer;
}
.close-x {
  font-size: 20px;
  line-height: 1;
}

/* 舞台 */
.viewer-stage {
  position: relative;
  z-index: 4;
  flex: 1;
  min-height: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 12px 80px;
}
.img-wrap {
  position: relative;
  max-width: 100%;
  max-height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}
.img-wrap img {
  max-width: 100%;
  max-height: calc(100dvh - 160px);
  border-radius: 8px;
  object-fit: contain;
}
.img-thumb {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  filter: blur(2px);
  opacity: 0.5;
}
.img-full {
  opacity: 0;
  transition: opacity 0.35s ease;
  position: relative;
}
.img-full.show {
  opacity: 1;
}
.nav-btn {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  z-index: 5;
  width: 44px;
  height: 44px;
  border: none;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.8);
  font-size: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.15s ease;
}
.nav-btn:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.18);
  color: #fff;
}
.nav-btn:disabled {
  opacity: 0.25;
  cursor: default;
}
.nav-btn.prev {
  left: 20px;
}
.nav-btn.next {
  right: 20px;
}

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

/* 全屏模式 */
.photo-viewer:fullscreen {
  background: #000;
}
.photo-viewer:fullscreen .viewer-header,
.photo-viewer:fullscreen .viewer-bottom {
  padding-left: 32px;
  padding-right: 32px;
}

@media (max-width: 768px) {
  .viewer-header {
    padding: 10px 12px;
  }
  .viewer-name {
    font-size: 13px;
    max-width: 50vw;
  }
  .viewer-actions .vd-btn {
    width: 32px;
    height: 32px;
  }
  .viewer-stage {
    padding: 8px 0;
  }
  .img-wrap img {
    max-height: calc(100dvh - 190px);
  }
  .nav-btn {
    width: 36px;
    height: 36px;
  }
  .nav-btn.prev {
    left: 8px;
  }
  .nav-btn.next {
    right: 8px;
  }
}
</style>
