<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted, onUnmounted, defineAsyncComponent } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import gsap from 'gsap'
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

function onScroll(): void {
  if (!photo.hasMore || photo.loading) return
  if (window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 200) {
    photo.loadMore()
  }
}

onMounted(() => {
  window.addEventListener('scroll', onScroll, { passive: true })
})
onUnmounted(() => {
  window.removeEventListener('scroll', onScroll)
})

let prevCount = 0
watch(
  () => photo.photos.length,
  () => {
    if (photo.photos.length === prevCount) return
    const newCards = photo.photos.slice(prevCount)
    prevCount = photo.photos.length
    nextTick(() => {
      gsap.fromTo(
        newCards.map((_, i) => `.photo-card[data-insert="${prevCount - newCards.length + i}"]`),
        { y: 40, opacity: 0 },
        { y: 0, opacity: 1, stagger: 0.06, duration: 0.6, ease: 'expo.out' },
      )
    })
  },
)

function onUploaded(): void {
  photo.resetAndReload()
}

const sortOptions: { key: SortField; label: string }[] = [
  { key: 'time', label: '时间' },
  { key: 'name', label: '名称' },
  { key: 'size', label: '大小' },
]
</script>

<template>
  <section class="gallery-section">
    <h2>
      我的照片 <span v-if="photo.totalCount">({{ photo.totalCount }})</span>
    </h2>
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
      <button v-if="selectedIds.size > 0" class="btn-share" @click="onGenerateShare">
        生成分享链接
      </button>
      <button v-if="selectedIds.size > 0" class="btn-del" @click="batchDelete">
        批量删除 ({{ selectedIds.size }})
      </button>
      <div class="view-switch">
        <span class="sort-label">视图：</span>
        <div class="view-track">
          <router-link
            to="/"
            class="view-opt"
            :class="{ active: route.path === '/' }"
            @click.prevent="switchView('/')"
            >网格</router-link
          >
          <router-link to="/albums" class="view-opt" :class="{ active: route.path === '/albums' }"
            >相册</router-link
          >
          <router-link
            to="/timeline"
            class="view-opt"
            :class="{ active: route.path === '/timeline' }"
            >时间线</router-link
          >
          <router-link to="/map" class="view-opt" :class="{ active: route.path === '/map' }"
            >地图</router-link
          >
        </div>
      </div>
      <div class="sort-switch">
        <span class="sort-label">排序方式：</span>
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
            {{ opt.label }}
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

    <UploadCard @uploaded="onUploaded" />

    <div class="gallery">
      <div
        v-for="i in 6"
        v-if="photo.loading && photo.photos.length === 0"
        :key="'s' + i"
        class="skeleton-card"
      >
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
        @view="ui.viewPhoto = p"
        @edit="ui.editPhoto = p"
        @delete="deletePhoto"
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
