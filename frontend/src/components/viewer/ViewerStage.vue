<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { LeftOutlined, RightOutlined } from '@ant-design/icons-vue'
import { appendMediaParams, mediaUrlWithVersion } from '../../utils/token'
import { webpUrl } from '../../utils/webp'
import type { Photo } from '../../types/photo'

/**
 * 灯箱舞台：前后切换 + 两段式图片（缩略图→全图淡入）
 * 纯展示 + 事件上抛（close/prev/next/loaded），自 PhotoViewer 拆分
 */
defineProps<{
  photo: Photo
  fullLoaded: boolean
  canSlideshow: boolean
  currentIndex: number
  total: number
}>()
const emit = defineEmits<{
  close: []
  prev: []
  next: []
  loaded: []
}>()

const { t } = useI18n()
</script>

<template>
  <div class="viewer-stage" @click="emit('close')">
    <button
      v-if="canSlideshow"
      class="nav-btn prev"
      :disabled="currentIndex <= 0"
      :aria-label="t('viewer.previous')"
      @click.stop="emit('prev')"
    >
      <LeftOutlined />
    </button>

    <div class="img-wrap" @click.stop>
      <img
        class="img-thumb"
        :src="
          mediaUrlWithVersion(
            appendMediaParams(`/api/v1/photos/${photo.id}/thumbnail`, photo),
            photo,
          )
        "
        :alt="photo.name"
        decoding="async"
      />
      <img
        class="img-full"
        :class="{ show: fullLoaded }"
        :src="mediaUrlWithVersion(appendMediaParams(webpUrl(photo.id), photo), photo)"
        :alt="photo.name"
        decoding="async"
        @load="emit('loaded')"
      />
    </div>

    <button
      v-if="canSlideshow"
      class="nav-btn next"
      :disabled="currentIndex >= total - 1"
      :aria-label="t('viewer.next')"
      @click.stop="emit('next')"
    >
      <RightOutlined />
    </button>
  </div>
</template>

<style scoped>
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

@media (max-width: 768px) {
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
