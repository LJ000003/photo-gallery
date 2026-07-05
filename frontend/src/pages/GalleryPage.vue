<script setup lang="ts">
import { ref, computed, watch, onUnmounted, defineAsyncComponent } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useWindowVirtualizer } from '@tanstack/vue-virtual'
import PhotoCard from '../components/PhotoCard.vue'
import UploadCard from '../components/UploadCard.vue'
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

const sortOptions: { key: SortField; i18n: string }[] = [
  { key: 'time', i18n: 'gallery.sortTime' },
  { key: 'name', i18n: 'gallery.sortName' },
  { key: 'size', i18n: 'gallery.sortSize' },
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
        <div class="view-switch">
          <span class="sort-label">{{ $t('nav.view') }}：</span>
          <div class="view-track">
            <router-link
              to="/"
              class="view-opt"
              :class="{ active: route.path === '/' }"
              @click.prevent="switchView('/')"
              >{{ $t('nav.grid') }}</router-link
            >
            <router-link to="/albums" class="view-opt" :class="{ active: route.path === '/albums' }"
              >{{ $t('nav.albums') }}</router-link
            >
            <router-link
              to="/timeline"
              class="view-opt"
              :class="{ active: route.path === '/timeline' }"
              >{{ $t('nav.timeline') }}</router-link
            >
            <router-link to="/map" class="view-opt" :class="{ active: route.path === '/map' }"
              >{{ $t('nav.map') }}</router-link
            >
          </div>
        </div>
        <div class="sort-switch">
          <span class="sort-label">{{ $t('gallery.sortBy') }}：</span>
          <div class="sort-track">
            <div
              class="sort-slider"
              :style="{
                transform: `translateX(${sortOptions.findIndex((o) => o.key === photo.sortBy) * 100}%)`,
              }"
            ></div>
            <button
              v-for="opt in sortOptions"
              :key="opt.key"
              class="sort-opt"
              :class="{ active: photo.sortBy === opt.key }"
              @click="photo.setSort(opt.key)"
            >
              {{ $t(opt.i18n) }}
              <span v-if="photo.sortBy === opt.key" class="sort-arrows">
                <i
                  class="iconfont icon-jiantou_qiehuanxiangshang_o sort-arrow-down"
                  :class="{ active: photo.sortOrder === 'asc' }"
                ></i>
                <i
                  class="iconfont icon-jiantou_qiehuanxiangshang_o"
                  :class="{ active: photo.sortOrder === 'desc' }"
                ></i>
              </span>
            </button>
          </div>
        </div>
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

    <!-- 分享弹窗 -->
    <div v-if="shareModal" class="modal" @click.self="shareModal = null">
      <div class="modal-content modal-small">
        <h3>分享 {{ shareModal.photoIds.length }} 张照片</h3>
        <p class="share-hint">链接 7 天内有效，拿到链接的人可直接查看</p>
        <div v-if="shareLoading" class="share-loading">生成中...</div>
        <div v-else-if="shareUrl" class="share-row">
          <input
            :value="shareUrl"
            readonly
            class="share-input"
            @focus="($event.target as HTMLInputElement).select()"
          />
          <button class="btn-primary" @click="copyShareLink">复制链接</button>
        </div>
        <button class="modal-close" @click="shareModal = null">✕</button>
      </div>
    </div>
  </section>
</template>
