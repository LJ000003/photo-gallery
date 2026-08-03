<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { LeftOutlined, RightOutlined } from '@ant-design/icons-vue'
import { webpUrl } from '../../webp'
import { formatSize } from '../../utils/format'
import { api } from '../../api'
import PhotoGrid from '../gallery/PhotoGrid.vue'
import GridSkeleton from '../gallery/GridSkeleton.vue'
import EmptyState from '../common/EmptyState.vue'
import type { Photo } from '../../types/photo'
import type { PageResponse } from '../../types/api'

/**
 * 公开分享查看器（/share/:token，免登录）：
 * 极简照片流 + 轻量灯箱；无导航无上传，带品牌落款
 */
const { t } = useI18n()
const token = window.location.pathname.replace('/share/', '')

const photos = ref<Photo[]>([])
const loading = ref(true)
const hasMore = ref(true)
const page = ref(0)
const viewIndex = ref(-1)

// 网格内缩略图由 PhotoGrid 内部生成（走 /thumbnail?token= 形式，与公开访问一致）

async function load(): Promise<void> {
  if (loading.value || !hasMore.value) return
  loading.value = true
  try {
    const res = await api(`/api/share/view?page=${page.value}&size=20`, {
      token,
      skipAuth: true,
    })
    if (!res.ok) {
      hasMore.value = false
      return
    }
    const json = await res.json()
    const data: PageResponse<Photo> = json.data
    photos.value = [...photos.value, ...data.content]
    hasMore.value = !data.last
    page.value++
  } catch {
    hasMore.value = false
  } finally {
    loading.value = false
  }
}

function onScroll(): void {
  if (!hasMore.value || loading.value) return
  if (window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 300) {
    void load()
  }
}

/* ---------- 轻量灯箱 ---------- */
function openViewer(index: number): void {
  viewIndex.value = index
}
function closeViewer(): void {
  viewIndex.value = -1
}
function navigate(dir: -1 | 1): void {
  const next = viewIndex.value + dir
  if (next < 0 || next >= photos.value.length) return
  viewIndex.value = next
}
function onKeydown(e: KeyboardEvent): void {
  if (viewIndex.value === -1) return
  if (e.key === 'Escape') closeViewer()
  if (e.key === 'ArrowLeft') navigate(-1)
  if (e.key === 'ArrowRight') navigate(1)
}

onMounted(() => {
  window.addEventListener('scroll', onScroll, { passive: true })
  window.addEventListener('keydown', onKeydown)
  void load()
})
onUnmounted(() => {
  window.removeEventListener('scroll', onScroll)
  window.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <div class="share-page">
    <header class="share-header">
      <span class="share-brand">{{ t('app.name') }}</span>
      <span class="share-sub">{{ t('share.viewer') }}</span>
    </header>

    <GridSkeleton v-if="loading && photos.length === 0" :count="8" />

    <EmptyState
      v-else-if="!loading && photos.length === 0"
      :title="t('gallery.emptyTitle')"
      :hint="t('share.expire')"
    />

    <PhotoGrid
      v-else
      :photos="photos"
      :selected-ids="new Set<number>()"
      :loading="loading"
      :has-more="hasMore"
      :selectable="false"
      @load-more="load"
      @view="(p) => openViewer(photos.findIndex((x) => x.id === p.id))"
    />

    <footer class="share-footer">{{ t('app.name') }} · {{ t('share.expire') }}</footer>

    <!-- 轻量灯箱 -->
    <Teleport to="body">
      <div v-if="viewIndex >= 0" class="share-lightbox" @click.self="closeViewer">
        <button
          v-if="viewIndex > 0"
          class="lb-nav prev"
          :aria-label="t('viewer.previous')"
          @click="navigate(-1)"
        >
          <LeftOutlined />
        </button>
        <div class="lb-content" @click.self="closeViewer">
          <img
            v-if="photos[viewIndex]"
            :src="`${webpUrl(photos[viewIndex].id)}?token=${token}`"
            :alt="photos[viewIndex].name"
            loading="lazy"
          />
          <div v-if="photos[viewIndex]" class="lb-info">
            <h3>{{ photos[viewIndex].name }}</h3>
            <p v-if="photos[viewIndex].description" class="lb-desc">
              {{ photos[viewIndex].description }}
            </p>
            <p class="lb-meta">{{ formatSize(photos[viewIndex].fileSize) }}</p>
          </div>
        </div>
        <button
          v-if="viewIndex < photos.length - 1"
          class="lb-nav next"
          :aria-label="t('viewer.next')"
          @click="navigate(1)"
        >
          <RightOutlined />
        </button>
        <button class="lb-close" :aria-label="t('viewer.close')" @click="closeViewer">×</button>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.share-page {
  min-height: 100dvh;
  padding: 20px;
  max-width: 1400px;
  margin: 0 auto;
}
.share-header {
  display: flex;
  align-items: baseline;
  gap: 12px;
  padding: 8px 2px 20px;
}
.share-brand {
  font-size: 17px;
  font-weight: 650;
  letter-spacing: -0.02em;
  color: var(--c-text);
}
.share-sub {
  font-size: 12px;
  color: var(--c-text-dim);
}
.share-footer {
  text-align: center;
  padding: 40px 0 24px;
  font-size: 11px;
  color: var(--c-text-dim);
  opacity: 0.7;
}

/* 灯箱 */
.share-lightbox {
  position: fixed;
  inset: 0;
  z-index: 1000;
  background: rgba(10, 10, 12, 0.92);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}
.lb-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  max-width: 92vw;
}
.lb-content img {
  max-width: 92vw;
  max-height: 78dvh;
  border-radius: 10px;
  object-fit: contain;
}
.lb-info {
  text-align: center;
}
.lb-info h3 {
  font-size: 15px;
  font-weight: 600;
}
.lb-desc {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.75);
  margin-top: 4px;
}
.lb-meta {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.5);
  margin-top: 4px;
}
.lb-nav {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 44px;
  height: 44px;
  border: none;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.8);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s ease;
}
.lb-nav:hover {
  background: rgba(255, 255, 255, 0.18);
}
.lb-nav.prev {
  left: 20px;
}
.lb-nav.next {
  right: 20px;
}
.lb-close {
  position: absolute;
  top: 16px;
  right: 20px;
  width: 40px;
  height: 40px;
  border: none;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.8);
  font-size: 20px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}
.lb-close:hover {
  background: rgba(255, 255, 255, 0.18);
}

@media (max-width: 768px) {
  .share-page {
    padding: 14px 12px;
  }
  .lb-nav {
    width: 36px;
    height: 36px;
  }
  .lb-nav.prev {
    left: 8px;
  }
  .lb-nav.next {
    right: 8px;
  }
}
</style>
