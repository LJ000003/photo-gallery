<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, watch } from 'vue'
import { useUiStore } from '../stores/ui'
import { tokenParam } from '../utils/token'
import { api } from '../api'
import type { TimelineExifItem } from '../types/view'
import type { PageResponse } from '../types/api'

const props = defineProps<{
  sortOrder?: string
}>()
const emit = defineEmits<{ view: [p: object] }>()
const ui = useUiStore()

const items = ref<TimelineExifItem[]>([])
const page = ref(0)
const hasMore = ref(true)
const loading = ref(false)
const initialLoading = ref(true)
let requestId = 0

function groupByMonth(list: TimelineExifItem[]): [string, TimelineExifItem[]][] {
  const groups = new Map<string, TimelineExifItem[]>()
  for (const item of list) {
    if (!item.dateTaken) continue
    const key = item.dateTaken.substring(0, 7)
    if (!groups.has(key)) groups.set(key, [])
    groups.get(key)!.push(item)
  }
  const dir = props.sortOrder === 'asc' ? 1 : -1
  for (const photos of groups.values()) {
    photos.sort((a, b) => dir * a.dateTaken.localeCompare(b.dateTaken))
  }
  const monthDir = props.sortOrder === 'asc' ? 1 : -1
  return [...groups.entries()].sort((a, b) => monthDir * a[0].localeCompare(b[0]))
}

const grouped = computed(() => groupByMonth(items.value))

async function loadMore(): Promise<void> {
  if (loading.value || !hasMore.value) return
  loading.value = true
  const myId = ++requestId
  try {
    const res = await api(
      `/api/photos/timeline?sortOrder=${props.sortOrder || 'desc'}&page=${page.value}&size=50`,
    )
    const json = await res.json()
    if (json.code !== 200 || myId !== requestId) return
    const data: PageResponse<TimelineExifItem> = json.data
    if (data.content && data.content.length) items.value.push(...data.content)
    page.value++
    hasMore.value = page.value < data.totalPages
  } catch (e) {
    console.error('Failed to load timeline', e)
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
  loadMore()
}

// 无限滚动：监听 sentinel 元素
const sentinel = ref<HTMLDivElement | null>(null)
let observer: IntersectionObserver | null = null

onMounted(() => {
  loadMore()
  observer = new IntersectionObserver(
    (entries) => {
      if (entries[0].isIntersecting) loadMore()
    },
    { rootMargin: '200px' },
  )
  // 首次加载后绑定 sentinel
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

watch(
  () => props.sortOrder,
  () => reset(),
)
</script>

<template>
  <div class="timeline-wrap">
    <div v-if="initialLoading" class="timeline-loading">加载中...</div>
    <div v-else-if="items.length === 0" class="timeline-empty">
      没有可提取时间信息的照片，请上传带有 EXIF 拍摄日期的 JPEG/WebP 图片
    </div>
    <div v-else class="timeline">
      <div class="timeline-line"></div>
      <div v-for="[month, photos] in grouped" :key="month" class="timeline-group">
        <div class="timeline-month">{{ month }}</div>
        <div class="timeline-cards">
          <div
            v-for="exif in photos"
            :key="exif.id"
            class="timeline-card"
            @click="emit('view', { id: exif.photoId, name: exif.photoName })"
          >
            <img
              :src="`${exif.photoThumbnail}${tokenParam()}`"
              :alt="exif.photoName"
              loading="lazy"
            />
            <div class="timeline-info">
              <p class="timeline-name">{{ exif.photoName }}</p>
              <p class="timeline-date">{{ exif.dateTaken }}</p>
              <p v-if="exif.cameraModel" class="timeline-camera">{{ exif.cameraModel }}</p>
            </div>
          </div>
        </div>
      </div>
      <div ref="sentinel" class="timeline-sentinel">
        <span v-if="loading">加载中...</span>
        <span v-else-if="!hasMore">没有更多了</span>
      </div>
    </div>
  </div>
</template>
