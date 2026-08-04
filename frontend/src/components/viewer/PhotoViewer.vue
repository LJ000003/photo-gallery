<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { DeleteOutlined, DownloadOutlined, EditOutlined, LinkOutlined } from '@ant-design/icons-vue'
import { Button, Modal } from 'ant-design-vue'

import ExifPanel from './ExifPanel.vue'
import ViewerStage from './ViewerStage.vue'
import ViewerBottom from './ViewerBottom.vue'
import { webpUrl } from '../../utils/webp'
import { appendMediaParams } from '../../utils/token'
import { formatSize } from '../../utils/format'
import { useUiStore } from '../../stores/ui'
import { usePhotoActions } from '../../composables/usePhotoActions'
import { useViewerControls } from '../../composables/useViewerControls'
import type { Photo } from '../../types/photo'

/**
 * 全屏灯箱（沉浸式深色舞台，深浅主题下观感一致）——组装壳
 * 舞台/底栏/控制逻辑分别在 ViewerStage / ViewerBottom / useViewerControls
 */
const props = defineProps<{ photo: Photo }>()
const emit = defineEmits<{ close: [] }>()

const { t } = useI18n()
const ui = useUiStore()
const { deletePhoto, generateShare } = usePhotoActions()

const fullLoaded = ref(false)
const exifOpen = ref(false)
const rootRef = ref<HTMLElement | null>(null)

const viewer = useViewerControls(props, emit, rootRef, fullLoaded)
// 顶层解构：模板自动解包 ref（composable 返回对象中的 ref 不会自动解包）
const { currentIndex, canSlideshow } = viewer

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
    <div class="viewer-backdrop" @click="viewer.close"></div>

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
          @click="viewer.close"
        >
          <span class="close-x">×</span>
        </Button>
      </div>
    </header>

    <!-- 舞台：前后切换 + 图片 -->
    <ViewerStage
      :photo="photo"
      :full-loaded="fullLoaded"
      :can-slideshow="canSlideshow"
      :current-index="currentIndex"
      :total="ui.viewPhotos.length"
      @close="viewer.close"
      @prev="ui.navigateViewer(-1)"
      @next="ui.navigateViewer(1)"
      @loaded="fullLoaded = true"
    />

    <!-- 底栏：幻灯片控制 + EXIF/全屏 -->
    <ViewerBottom
      :can-slideshow="canSlideshow"
      :playing="ui.slideshowPlaying"
      :current-index="currentIndex"
      :total="ui.viewPhotos.length"
      :exif-open="exifOpen"
      :fullscreen-active="viewer.isFullscreenActive()"
      @navigate-prev="ui.navigateViewer(-1)"
      @navigate-next="ui.navigateViewer(1)"
      @toggle-slideshow="ui.toggleSlideshow()"
      @toggle-exif="exifOpen = !exifOpen"
      @toggle-fullscreen="viewer.toggleFullscreen"
    />

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

/* 全屏模式 */
.photo-viewer:fullscreen {
  background: #000;
}
.photo-viewer:fullscreen .viewer-header {
  padding-left: 32px;
  padding-right: 32px;
}
.photo-viewer:fullscreen :deep(.viewer-bottom) {
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
}
</style>
