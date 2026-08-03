<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useWindowVirtualizer } from '@tanstack/vue-virtual'
import PhotoTile from './PhotoTile.vue'
import type { Photo } from '../../types/photo'

/**
 * 虚拟化照片网格（@tanstack/vue-virtual，窗口级滚动）
 * 无框悬浮：间距 8px（移动 4px），列宽 min 240px（移动 150px）
 * 尾部哨兵行：加载中（CSS 微旋转）/ 到底提示
 */
const props = withDefaults(
  defineProps<{
    photos: Photo[]
    searchQuery?: string
    selectedIds: Set<number>
    loading: boolean
    hasMore: boolean
    /** 关闭选择圈（相册详情/分享端等纯浏览场景） */
    selectable?: boolean
  }>(),
  { selectable: true, searchQuery: '' },
)

const emit = defineEmits<{
  'load-more': []
  view: [p: Photo]
  edit: [p: Photo]
  'edit-image': [p: Photo]
  delete: [id: number]
  toggleSelect: [id: number]
}>()

const { t } = useI18n()

const gridRef = ref<HTMLDivElement | null>(null)
const containerWidth = ref(0)
let resizeObs: ResizeObserver | null = null

const GAP_DESKTOP = 8
const MIN_COL_DESKTOP = 240
const GAP_MOBILE = 4
const MIN_COL_MOBILE = 150

const isMobile = computed(() => containerWidth.value < 600)
const gap = computed(() => (isMobile.value ? GAP_MOBILE : GAP_DESKTOP))

const columns = computed(() => {
  const w = containerWidth.value
  const min = isMobile.value ? MIN_COL_MOBILE : MIN_COL_DESKTOP
  if (w < min) return 1
  return Math.max(1, Math.floor((w + gap.value) / (min + gap.value)))
})

const rows = computed(() => {
  const cols = columns.value
  const result: Photo[][] = []
  for (let i = 0; i < props.photos.length; i += cols) {
    result.push(props.photos.slice(i, i + cols))
  }
  return result
})

/** 虚拟行数：数据行 + 末尾哨兵（加载中 / 到底） */
const virtualCount = computed(() => {
  let n = rows.value.length
  if (props.loading && props.hasMore) n++
  else if (!props.hasMore && props.photos.length > 0) n++
  return n
})

const ROW_ESTIMATE = 340

const virtualizer = useWindowVirtualizer(
  computed(() => ({
    count: virtualCount.value,
    estimateSize: () => ROW_ESTIMATE,
    overscan: 3,
    scrollMargin: 0,
    measureElement: (el: Element) => el.getBoundingClientRect().height,
  })),
)

const virtualItems = computed(() => virtualizer.value.getVirtualItems())

// 接近底部时触发加载
watch(virtualItems, (items) => {
  if (items.length === 0 || !props.hasMore || props.loading) return
  const lastIdx = items[items.length - 1].index
  if (lastIdx >= rows.value.length - 3) {
    emit('load-more')
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

function isSelected(id: number): boolean {
  return props.selectedIds.has(id)
}
</script>

<template>
  <div
    ref="gridRef"
    class="photo-grid"
    :style="{ height: `${virtualizer.getTotalSize()}px`, position: 'relative' }"
  >
    <div
      v-for="vr in virtualItems"
      :key="vr.index"
      :ref="(el) => virtualizer.measureElement(el as Element)"
      :data-index="vr.index"
      class="photo-grid-row"
      :style="{ transform: `translateY(${vr.start}px)` }"
    >
      <!-- 照片行 -->
      <div
        v-if="vr.index < rows.length"
        class="photo-grid-row-inner"
        :style="{
          gridTemplateColumns: `repeat(${columns}, 1fr)`,
          gap: `${gap}px`,
          paddingBottom: `${gap}px`,
        }"
      >
        <PhotoTile
          v-for="p in rows[vr.index]"
          :key="p.id + '-' + (p.fileSize || '')"
          :photo="p"
          :search-query="searchQuery"
          :selected="isSelected(p.id)"
          :selectable="selectable"
          @view="emit('view', $event)"
          @edit="emit('edit', $event)"
          @edit-image="emit('edit-image', $event)"
          @delete="emit('delete', $event)"
          @toggle-select="emit('toggleSelect', $event)"
        />
      </div>

      <!-- 加载中 -->
      <div v-else-if="loading" class="sentinel" role="status" aria-label="loading">
        <span class="sentinel-spinner"></span>
      </div>

      <!-- 到底了 -->
      <div v-else class="end-hint">{{ t('gallery.end') }}</div>
    </div>
  </div>
</template>

<style scoped>
.photo-grid {
  min-height: 200px;
}
.photo-grid-row {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  will-change: transform;
}
.photo-grid-row-inner {
  display: grid;
  width: 100%;
}

.sentinel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 0;
}
.sentinel-spinner {
  width: 26px;
  height: 26px;
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
  text-align: center;
  padding: 36px 0;
  font-size: 12px;
  color: var(--c-text-dim);
  opacity: 0.7;
}
</style>
