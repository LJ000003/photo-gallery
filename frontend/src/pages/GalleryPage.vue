<script setup lang="ts">
import { ref, computed, watch, onUnmounted, defineAsyncComponent } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useWindowVirtualizer } from '@tanstack/vue-virtual'
import PhotoCard from '../components/PhotoCard.vue'
import UploadCard from '../components/UploadCard.vue'
import ViewSwitcher from '../components/ViewSwitcher.vue'
import SortSwitch from '../components/SortSwitch.vue'
import type { SortOption } from '../components/SortSwitch.vue'
import ShareModal from '../components/ShareModal.vue'
import { usePhotoStore } from '../stores/photo'
import { useUiStore } from '../stores/ui'
import { usePhotoActions } from '../composables/usePhotoActions'
import type { SortField } from '../types/view'

const LottieLoader = defineAsyncComponent(() => import('../components/LottieLoader.vue'))

const router = useRouter()
const route = useRoute()
const photo = usePhotoStore()
const ui = useUiStore()
const {
  deletePhoto,
  deletePhotos,
  generateShare,
  shareModal,
  shareUrl,
  shareLoading,
  copyShareLink,
} = usePhotoActions()

const selectedIds = ref(new Set<number>())

function switchView(key: string): void {
  if (key === '/') return
  router.push(key)
}

function isSelected(id: number): boolean {
  return selectedIds.value.has(id)
}
function toggleSelect(id: number): void {
  const s = selectedIds.value
  s.has(id) ? s.delete(id) : s.add(id)
  selectedIds.value = new Set(s)
}
const allSelected = computed(
  () => photo.totalCount > 0 && selectedIds.value.size === photo.totalCount,
)

async function toggleAll(): Promise<void> {
  if (allSelected.value) {
    selectedIds.value = new Set()
    return
  }
  while (photo.hasMore && !photo.loading) {
    await photo.loadMore()
  }
  selectedIds.value = new Set(photo.photos.map((p) => p.id))
}
function batchDelete(): void {
  if (selectedIds.value.size === 0) return
  deletePhotos([...selectedIds.value])
  selectedIds.value = new Set()
}

function onGenerateShare(): void {
  if (selectedIds.value.size === 0) return
  generateShare([...selectedIds.value])
}

watch(
  () => photo.photos,
  () => {
    const currentIds = new Set(photo.photos.map((p) => p.id))
    let changed = false
    for (const id of selectedIds.value) {
      if (!currentIds.has(id)) {
        changed = true
        break
      }
    }
    if (changed) {
      selectedIds.value = new Set([...selectedIds.value].filter((id) => currentIds.has(id)))
    }
  },
)

// --- 虚拟滚动 ---
const gridRef = ref<HTMLDivElement | null>(null)
const containerWidth = ref(0)
let resizeObs: ResizeObserver | null = null

const GAP = 20

const columns = computed(() => {
  const w = containerWidth.value
  const min = w < 600 ? 150 : 260
  if (w < min) return 1
  return Math.max(1, Math.floor((w + GAP) / (min + GAP)))
})

const rows = computed(() => {
  const cols = columns.value
  const result: (typeof photo.photos)[] = []
  for (let i = 0; i < photo.photos.length; i += cols) {
    result.push(photo.photos.slice(i, i + cols))
  }
  return result
})

const virtualCount = computed(() => {
  let n = rows.value.length
  if (photo.loading && photo.hasMore) n++
  else if (!photo.hasMore && photo.photos.length > 0) n++
  return n
})

const virtualizer = useWindowVirtualizer(
  computed(() => ({
    count: virtualCount.value,
    estimateSize: () => 360,
    overscan: 3,
    scrollMargin: 0,
    measureElement: (el: Element) => el.getBoundingClientRect().height,
  })),
)

const virtualItems = computed(() => virtualizer.value.getVirtualItems())

// 接近底部时触发加载
watch(virtualItems, (items) => {
  if (items.length === 0 || !photo.hasMore || photo.loading) return
  const lastIdx = items[items.length - 1].index
  if (lastIdx >= rows.value.length - 3) {
    photo.loadMore()
  }
})

watch(
  gridRef,
  (el) => {
    resizeObs?.disconnect()
    if (el) {
      containerWidth.value = el.getBoundingClientRect().width
      resizeObs = new ResizeObserver(([entry]) => {
        containerWidth.value = entry.contentRect.width
      })
      resizeObs.observe(el)
    }
  },
  { immediate: true },
)

onUnmounted(() => {
  resizeObs?.disconnect()
})

function onUploaded(): void {
  photo.resetAndReload()
}

const sortOptions: SortOption[] = [
  { key: 'time', label: 'gallery.sortTime' },
  { key: 'name', label: 'gallery.sortName' },
  { key: 'size', label: 'gallery.sortSize' },
]
</script>

<template>
  <section class="gallery-section">
    <h2>
      {{ $t('gallery.myPhotos') }} <span v-if="photo.totalCount">({{ photo.totalCount }})</span>
    </h2>
    <div class="gallery-toolbar">
      <label>
        <input type="checkbox" :checked="allSelected" @change="toggleAll" />
        {{ $t('gallery.selectAll') }}
      </label>
      <button v-if="selectedIds.size > 0" class="btn-share" @click="onGenerateShare">
        {{ $t('gallery.generateShare') }}
      </button>
      <button v-if="selectedIds.size > 0" class="btn-del" @click="batchDelete">
        {{ $t('gallery.batchDelete') }} ({{ selectedIds.size }})
      </button>
      <div class="toolbar-center">
        <input
          class="search-input"
          type="text"
          :placeholder="$t('gallery.searchPlaceholder')"
          :value="photo.searchQuery"
          @input="photo.setSearch(($event.target as HTMLInputElement).value)"
        />
        <ViewSwitcher :current-path="route.path" />
        <SortSwitch
          :options="sortOptions"
          :model-value="photo.sortBy"
          :order="photo.sortOrder"
          @toggle="photo.setSort($event as SortField)"
        />
      </div>
    </div>

    <UploadCard @uploaded="onUploaded" />

    <!-- 骨架屏（首次加载） -->
    <div v-if="photo.loading && photo.photos.length === 0" class="gallery">
      <div v-for="i in 6" :key="'s' + i" class="skeleton-card">
        <div class="skeleton-img"></div>
        <div class="skeleton-body">
          <div class="skeleton-line"></div>
          <div class="skeleton-line"></div>
        </div>
      </div>
    </div>

    <!-- 空状态 -->
    <div
      v-else-if="!photo.hasMore && !photo.loading && photo.photos.length === 0"
      class="empty-state"
    >
      <LottieLoader name="empty" :size="160" />
      <p class="empty-hint">{{ $t('gallery.empty') }}</p>
    </div>

    <!-- 虚拟滚动网格 -->
    <div
      v-else
      ref="gridRef"
      class="gallery-virt"
      :style="{ height: virtualizer.getTotalSize() + 'px', width: '100%', position: 'relative' }"
    >
      <div
        v-for="vr in virtualItems"
        :key="vr.index"
        :ref="(el) => virtualizer.measureElement(el as Element)"
        :data-index="vr.index"
        class="gallery-virt-row"
        :style="{
          position: 'absolute',
          top: 0,
          left: 0,
          width: '100%',
          transform: `translateY(${vr.start}px)`,
        }"
      >
        <!-- 照片行 -->
        <div
          v-if="vr.index < rows.length"
          class="gallery"
          :style="{ gridTemplateColumns: `repeat(${columns}, 1fr)`, paddingBottom: GAP + 'px' }"
        >
          <PhotoCard
            v-for="p in rows[vr.index]"
            :key="p.id + '-' + p.fileSize"
            :photo="p"
            :search-query="photo.searchQuery"
            :selected="isSelected(p.id)"
            @view="ui.viewPhoto = p"
            @edit="ui.editPhoto = p"
            @delete="deletePhoto"
            @toggle-select="toggleSelect"
          />
        </div>

        <!-- 加载中 -->
        <div v-else-if="photo.loading" class="sentinel">
          <LottieLoader name="loading" :size="60" />
        </div>

        <!-- 到底了 -->
        <div v-else class="end-hint">{{ $t('gallery.noMore') }}</div>
      </div>
    </div>

    <ShareModal
      v-if="shareModal"
      :photo-count="shareModal.photoIds.length"
      :loading="shareLoading"
      :url="shareUrl"
      @close="shareModal = null"
      @copy="copyShareLink"
    />
  </section>
</template>
