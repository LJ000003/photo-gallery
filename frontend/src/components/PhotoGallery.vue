<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted, onUnmounted, defineAsyncComponent } from 'vue'
import gsap from 'gsap'
import PhotoCard from './PhotoCard.vue'
import TimelineView from './TimelineView.vue'
import MapView from './MapView.vue'
import AlbumView from './AlbumView.vue'
import { usePhotoStore } from '../stores/photo'
import type { ViewMode, SortField } from '../types/view'

const LottieLoader = defineAsyncComponent(() => import('./LottieLoader.vue'))

const photo = usePhotoStore()
const emit = defineEmits<{
  view: [p: object]
  edit: [p: object]
  delete: [id: number]
  loadMore: []
  batchDelete: [ids: number[]]
  generateShare: [ids: number[]]
}>()

const selectedIds = ref(new Set<number>())
const timelineSortOrder = ref('desc')

interface ViewModeOption { key: ViewMode; label: string }

const viewModes: ViewModeOption[] = [
  { key: 'grid', label: '网格' },
  { key: 'album', label: '相册' },
  { key: 'timeline', label: '时间线' },
  { key: 'map', label: '地图' }
]

function switchView(key: ViewMode): void {
  if (photo.viewMode === key) {
    if (key === 'timeline') toggleTimelineSort()
    return
  }
  photo.viewMode = key
  photo.syncUrlState()
}
function toggleTimelineSort(): void {
  timelineSortOrder.value = timelineSortOrder.value === 'desc' ? 'asc' : 'desc'
}

function isSelected(id: number): boolean { return selectedIds.value.has(id) }
function toggleSelect(id: number): void {
  const s = selectedIds.value
  s.has(id) ? s.delete(id) : s.add(id)
  selectedIds.value = new Set(s)
}
const allSelected = computed(() =>
  photo.totalCount > 0 && selectedIds.value.size === photo.totalCount
)

async function toggleAll(): Promise<void> {
  if (allSelected.value) {
    selectedIds.value = new Set()
    return
  }
  while (photo.hasMore && !photo.loading) {
    await photo.loadMore()
  }
  selectedIds.value = new Set(photo.photos.map(p => p.id))
}
function batchDelete(): void {
  if (selectedIds.value.size === 0) return
  emit('batchDelete', [...selectedIds.value])
  selectedIds.value = new Set()
}

function generateShare(): void {
  if (selectedIds.value.size === 0) return
  emit('generateShare', [...selectedIds.value])
}

interface SortOption { key: string; label: string }

const sortOptions: SortOption[] = [
  { key: 'time', label: '时间' },
  { key: 'name', label: '名称' },
  { key: 'size', label: '大小' }
]

watch(() => photo.photos, () => {
  const currentIds = new Set(photo.photos.map(p => p.id))
  let changed = false
  for (const id of selectedIds.value) {
    if (!currentIds.has(id)) { changed = true; break }
  }
  if (changed) {
    selectedIds.value = new Set([...selectedIds.value].filter(id => currentIds.has(id)))
  }
})

function onScroll(): void {
  if (!photo.hasMore || photo.loading) return
  if (window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 200) {
    emit('loadMore')
  }
}

onMounted(() => { window.addEventListener('scroll', onScroll, { passive: true }) })
onUnmounted(() => { window.removeEventListener('scroll', onScroll) })

let prevCount = 0
watch(() => photo.photos.length, () => {
  if (photo.photos.length === prevCount) return
  const newCards = photo.photos.slice(prevCount)
  prevCount = photo.photos.length
  nextTick(() => {
    gsap.fromTo(
      newCards.map((_, i) => `.photo-card[data-insert="${prevCount - newCards.length + i}"]`),
      { y: 40, opacity: 0 },
      { y: 0, opacity: 1, stagger: 0.06, duration: 0.6, ease: 'expo.out' }
    )
  })
})
</script>

<template>
  <section class="gallery-section">
    <h2>我的照片 <span v-if="photo.totalCount">({{ photo.totalCount }})</span></h2>
    <div class="gallery-toolbar">
      <input
        class="search-input"
        type="text"
        placeholder="搜索照片名称或描述..."
        :value="photo.searchQuery"
        @input="photo.setSearch(($event.target as HTMLInputElement).value)"
      />
      <label>
        <input type="checkbox" :checked="allSelected" @change="toggleAll" />
        全选
      </label>
      <button v-if="selectedIds.size > 0" class="btn-share" @click="generateShare">
        生成分享链接
      </button>
      <button v-if="selectedIds.size > 0" class="btn-del" @click="batchDelete">
        批量删除 ({{ selectedIds.size }})
      </button>
      <div class="view-switch">
        <span class="sort-label">视图：</span>
        <div class="view-track">
          <button v-for="vm in viewModes" :key="vm.key"
            class="view-opt" :class="{ active: photo.viewMode === vm.key }"
            @click="switchView(vm.key)">
            {{ vm.label }}
            <span v-if="vm.key === 'timeline' && photo.viewMode === 'timeline'" class="sort-arrows">
              <i class="iconfont icon-jiantou_qiehuanxiangshang_o sort-arrow-down" :class="{ active: timelineSortOrder === 'asc' }"></i>
              <i class="iconfont icon-jiantou_qiehuanxiangshang_o" :class="{ active: timelineSortOrder === 'desc' }"></i>
            </span>
          </button>
        </div>
      </div>
      <div class="sort-switch" v-if="photo.viewMode === 'grid'">
        <span class="sort-label">排序方式：</span>
        <div class="sort-track">
          <div class="sort-slider" :style="{ transform: `translateX(${sortOptions.findIndex(o => o.key === photo.sortBy) * 100}%)` }"></div>
          <button v-for="opt in sortOptions" :key="opt.key"
            class="sort-opt" :class="{ active: photo.sortBy === opt.key }"
            @click="photo.setSort(opt.key as SortField)">
            {{ opt.label }}
            <span v-if="photo.sortBy === opt.key" class="sort-arrows">
              <i class="iconfont icon-jiantou_qiehuanxiangshang_o sort-arrow-down" :class="{ active: photo.sortOrder === 'asc' }"></i>
              <i class="iconfont icon-jiantou_qiehuanxiangshang_o" :class="{ active: photo.sortOrder === 'desc' }"></i>
            </span>
          </button>
        </div>
      </div>
    </div>

    <template v-if="photo.viewMode === 'grid'">
      <div class="gallery">
        <div v-if="photo.loading && photo.photos.length === 0" v-for="i in 6" :key="'s'+i" class="skeleton-card">
          <div class="skeleton-img"></div>
          <div class="skeleton-body">
            <div class="skeleton-line"></div>
            <div class="skeleton-line"></div>
          </div>
        </div>
        <PhotoCard
          v-for="(p, i) in photo.photos"
          :key="p.id + '-' + p.fileSize"
          :photo="p"
          :search-query="photo.searchQuery"
          :selected="isSelected(p.id)"
          :data-insert="i"
          @view="emit('view', p)"
          @edit="emit('edit', p)"
          @delete="emit('delete', p.id)"
          @toggle-select="toggleSelect"
        />
      </div>
      <div v-if="photo.loading && photo.photos.length > 0" class="sentinel">
        <LottieLoader name="loading" :size="60" />
      </div>
      <div v-else-if="!photo.hasMore && photo.photos.length > 0" class="end-hint">没有更多了</div>
      <div v-if="!photo.hasMore && !photo.loading && photo.photos.length === 0" class="empty-state">
        <LottieLoader name="empty" :size="160" />
        <p class="empty-hint">还没有照片，上传第一张吧</p>
      </div>
    </template>

    <AlbumView v-else-if="photo.viewMode === 'album'" @view="p => emit('view', p)" />
    <TimelineView v-else-if="photo.viewMode === 'timeline'" :sort-order="timelineSortOrder" @view="p => emit('view', p)" />

    <MapView v-else-if="photo.viewMode === 'map'" @view="p => emit('view', p)" />
  </section>
</template>
