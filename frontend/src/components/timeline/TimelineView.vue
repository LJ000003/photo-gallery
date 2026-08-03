<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { CalendarOutlined } from '@ant-design/icons-vue'
import { useUiStore } from '../../stores/ui'
import { usePhotoStore } from '../../stores/photo'
import { appendMediaParams } from '../../utils/token'
import { api } from '../../api'
import EmptyState from '../common/EmptyState.vue'
import type { TimelineExifItem } from '../../types/view'
import type { PageResponse } from '../../types/api'
import type { Photo } from '../../types/photo'

/**
 * 时间线：按 EXIF 拍摄时间按月分组（desc 默认，头部按钮切换排序）
 * 分页加载 + sentinel 无限滚动；组内为静态网格（每页 50 条，无需虚拟化）
 */
const { t } = useI18n()
const ui = useUiStore()
const photo = usePhotoStore()

const sortOrder = ref<'asc' | 'desc'>('desc')

const items = ref<TimelineExifItem[]>([])
const page = ref(0)
const hasMore = ref(true)
const loading = ref(false)
const initialLoading = ref(true)
let requestId = 0

/** 加载失败冷却：避免 IntersectionObserver 对持续不可用的服务器连环重试 */
let lastFailAt = 0
const FAIL_COOLDOWN_MS = 5000

function toggleOrder(): void {
  sortOrder.value = sortOrder.value === 'desc' ? 'asc' : 'desc'
  reset()
}

function groupByMonth(list: TimelineExifItem[]): [string, TimelineExifItem[]][] {
  const groups = new Map<string, TimelineExifItem[]>()
  for (const item of list) {
    if (!item.dateTaken) continue
    const key = item.dateTaken.substring(0, 7)
    if (!groups.has(key)) groups.set(key, [])
    groups.get(key)!.push(item)
  }
  const dir = sortOrder.value === 'asc' ? 1 : -1
  for (const photos of groups.values()) {
    photos.sort((a, b) => dir * a.dateTaken.localeCompare(b.dateTaken))
  }
  const monthDir = sortOrder.value === 'asc' ? 1 : -1
  return [...groups.entries()].sort((a, b) => monthDir * a[0].localeCompare(b[0]))
}

const grouped = computed(() => groupByMonth(items.value))

function monthLabel(month: string): string {
  const [y, m] = month.split('-')
  return `${y} 年 ${Number(m)} 月`
}

/** @returns 是否成功加载了一页（失败时调用方不触发连环重试） */
async function loadMore(): Promise<boolean> {
  if (loading.value || !hasMore.value) return false
  if (Date.now() - lastFailAt < FAIL_COOLDOWN_MS) return false
  loading.value = true
  const myId = ++requestId
  try {
    const res = await api(
      `/api/photos/timeline?sortOrder=${sortOrder.value}&page=${page.value}&size=50`,
    )
    const json = await res.json()
    if (json.code !== 200 || myId !== requestId) return false
    const data: PageResponse<TimelineExifItem> = json.data
    if (data.content && data.content.length) items.value.push(...data.content)
    page.value++
    hasMore.value = page.value < data.totalPages
    return true
  } catch (e) {
    console.error('Failed to load timeline', e)
    lastFailAt = Date.now()
    return false
  } finally {
    if (myId === requestId) loading.value = false
    initialLoading.value = false
  }
}

function reset(): void {
  requestId++
  items.value = []
  page.value = 0
  hasMore.value = true
  loading.value = false
  initialLoading.value = true
  void loadMore()
}

/* ---------- 无限滚动（sentinel） ---------- */
const sentinel = ref<HTMLDivElement | null>(null)
let observer: IntersectionObserver | null = null

onMounted(() => {
  void loadMore()
  observer = new IntersectionObserver(
    (entries) => {
      if (entries[0].isIntersecting) void loadMore()
    },
    { rootMargin: '200px' },
  )
  const bind = setInterval(() => {
    if (sentinel.value) {
      observer!.observe(sentinel.value)
      clearInterval(bind)
    }
  }, 100)
})

onUnmounted(() => {
  observer?.disconnect()
})

function openViewer(exif: TimelineExifItem): void {
  // 时间线数据为 EXIF 摘要，用最小 Photo 结构打开查看器（名称/图片 URL 均由 id 驱动）
  // mediaToken 必须带上：灯箱大图鉴权用 per-photo 签名，残缺对象不带会 401
  const partial = { id: exif.photoId, name: exif.photoName, mediaToken: exif.mediaToken } as Photo
  ui.openViewer(partial, [partial])
}

// 删除同步：照片被删除后从本地时间线移除（photo store 统一记录 deletedIds）
watch(
  () => [...photo.deletedIds],
  (ids) => {
    if (ids.length === 0) return
    const set = new Set(ids)
    items.value = items.value.filter((i) => !set.has(i.photoId))
  },
)
</script>

<template>
  <div class="timeline-view">
    <div class="timeline-header">
      <h2 class="page-title">{{ t('nav.timeline') }}</h2>
      <button class="month-toggle" :title="t('sort.asc')" @click="toggleOrder">
        <CalendarOutlined />
        <span>{{ t('sort.time') }}</span>
        <span class="order-arrow">{{ sortOrder === 'desc' ? '↓' : '↑' }}</span>
      </button>
    </div>

    <div v-if="initialLoading" class="timeline-skeleton" aria-hidden="true">
      <div v-for="i in 4" :key="i" class="sk-group">
        <div class="sk-month shimmer"></div>
        <div class="sk-cards">
          <div v-for="j in 4" :key="j" class="sk-card shimmer"></div>
        </div>
      </div>
    </div>

    <EmptyState
      v-else-if="items.length === 0"
      :title="t('timeline.empty')"
      :hint="t('gallery.emptyHint')"
    />

    <div v-else class="timeline">
      <div class="timeline-line" aria-hidden="true"></div>
      <section v-for="[month, photos] in grouped" :key="month" class="timeline-group">
        <h3 class="timeline-month">{{ monthLabel(month) }}</h3>
        <div class="timeline-cards">
          <button
            v-for="exif in photos"
            :key="exif.id"
            class="timeline-card"
            @click="openViewer(exif)"
          >
            <img
              :src="appendMediaParams(exif.photoThumbnail, exif)"
              :alt="exif.photoName"
              loading="lazy"
            />
            <span class="timeline-info">
              <span class="timeline-name">{{ exif.photoName }}</span>
              <span class="timeline-date">{{ exif.dateTaken }}</span>
              <span v-if="exif.cameraModel" class="timeline-camera">{{ exif.cameraModel }}</span>
            </span>
          </button>
        </div>
      </section>
      <div ref="sentinel" class="timeline-sentinel">
        <span v-if="loading" class="sentinel-spinner"></span>
        <span v-else-if="!hasMore" class="end-hint">{{ t('gallery.end') }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.timeline-view {
  padding: 20px;
  max-width: 1100px;
  margin: 0 auto;
}
.timeline-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}
.page-title {
  font-size: 20px;
  font-weight: 650;
  letter-spacing: -0.01em;
  color: var(--c-text);
}
.month-toggle {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--c-border);
  background: var(--c-surface);
  color: var(--c-text-dim);
  font-size: 12px;
  padding: 6px 12px;
  border-radius: 999px;
  cursor: pointer;
  transition: all 0.15s ease;
}
.month-toggle:hover {
  border-color: var(--c-accent);
  color: var(--c-accent);
}
.order-arrow {
  font-size: 10px;
}

.timeline {
  position: relative;
  padding-left: 36px;
}
.timeline-line {
  position: absolute;
  left: 13px;
  top: 4px;
  bottom: 0;
  width: 2px;
  background: var(--c-border);
  border-radius: 2px;
}
.timeline-group {
  margin-bottom: 36px;
}
.timeline-month {
  position: relative;
  font-size: 16px;
  font-weight: 650;
  color: var(--c-text);
  margin-bottom: 14px;
}
.timeline-month::before {
  content: '';
  position: absolute;
  left: -31px;
  top: 7px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--c-accent);
  border: 2px solid var(--c-bg);
  box-shadow: 0 0 0 2px var(--c-accent-soft);
}
.timeline-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 10px;
}
.timeline-card {
  display: flex;
  align-items: center;
  gap: 12px;
  border: 1px solid var(--c-border);
  background: var(--c-surface);
  border-radius: 12px;
  padding: 8px;
  cursor: pointer;
  text-align: left;
  transition:
    border-color 0.2s ease,
    transform 0.2s ease,
    box-shadow 0.2s ease;
  font-family: inherit;
}
.timeline-card:hover {
  border-color: var(--c-accent);
  box-shadow: var(--shadow-photo);
  transform: translateY(-1px);
}
.timeline-card:active {
  transform: scale(0.98);
}
.timeline-card img {
  width: 96px;
  height: 68px;
  object-fit: cover;
  border-radius: 8px;
  flex-shrink: 0;
}
.timeline-info {
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-width: 0;
  gap: 3px;
}
.timeline-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--c-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.timeline-date {
  font-size: 11px;
  color: var(--c-text-dim);
}
.timeline-camera {
  font-size: 11px;
  color: var(--c-text-dim);
  opacity: 0.7;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.timeline-sentinel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px 0;
  min-height: 48px;
}
.sentinel-spinner {
  width: 24px;
  height: 24px;
  border: 3px solid var(--c-surface-2);
  border-top-color: var(--c-accent);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
.end-hint {
  font-size: 12px;
  color: var(--c-text-dim);
  opacity: 0.7;
}

/* 骨架屏 */
.timeline-skeleton {
  display: flex;
  flex-direction: column;
  gap: 28px;
}
.sk-group {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.sk-month {
  width: 120px;
  height: 18px;
  border-radius: 6px;
}
.sk-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 10px;
}
.sk-card {
  height: 84px;
  border-radius: 12px;
}
.shimmer {
  background: linear-gradient(
    100deg,
    var(--c-surface-2) 40%,
    color-mix(in srgb, var(--c-surface-2) 40%, var(--c-surface)) 50%,
    var(--c-surface-2) 60%
  );
  background-size: 200% 100%;
  animation: shimmer 1.6s ease-in-out infinite;
}
@keyframes shimmer {
  from {
    background-position: 200% 0;
  }
  to {
    background-position: -200% 0;
  }
}

@media (max-width: 768px) {
  .timeline-view {
    padding: 14px 12px;
  }
  .timeline {
    padding-left: 28px;
  }
  .timeline-line {
    left: 9px;
  }
  .timeline-month::before {
    left: -23px;
  }
  .timeline-cards {
    grid-template-columns: 1fr;
  }
}
</style>
