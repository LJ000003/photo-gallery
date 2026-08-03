<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowLeftOutlined } from '@ant-design/icons-vue'
import { Button } from 'ant-design-vue'
import PhotoGrid from '../gallery/PhotoGrid.vue'
import EmptyState from '../common/EmptyState.vue'
import { api } from '../../api'
import type { Photo } from '../../types/photo'
import type { ApiResponse, PageResponse } from '../../types/api'
import type { SortField, SortOrder } from '../../types/view'

/**
 * 相册详情：复用 PhotoGrid 虚拟滚动，分页加载相册内照片（未分配专辑 = albumId 0）
 */
const props = defineProps<{
  album: { id: number; name: string; photoCount: number }
  sortBy: SortField
  sortOrder: SortOrder
}>()

const emit = defineEmits<{
  back: []
  view: [p: Photo, list: Photo[]]
  'update:sortBy': [key: SortField]
  'update:sortOrder': [order: SortOrder]
}>()

const { t } = useI18n()

const photos = ref<Photo[]>([])
const page = ref(0)
const hasMore = ref(true)
const loading = ref(false)

async function loadMore(): Promise<void> {
  if (loading.value || !hasMore.value) return
  loading.value = true
  const fieldMap: Record<SortField, string> = { time: 'createdAt', name: 'name', size: 'fileSize' }
  const sortStr = `${fieldMap[props.sortBy]},${props.sortOrder}`
  try {
    const url =
      props.album.id === 0
        ? `/api/photos?albumId=0&page=${page.value}&size=20&sort=${sortStr}`
        : `/api/albums/${props.album.id}/photos?page=${page.value}&size=20&sort=${sortStr}`
    const res = await api(url)
    const json: ApiResponse<PageResponse<Photo>> = await res.json()
    const { content, last } = json.data
    if (content && content.length) photos.value.push(...content)
    page.value++
    hasMore.value = !last
  } catch {
    hasMore.value = false
  } finally {
    loading.value = false
  }
}

watch(
  [() => props.album.id, () => props.sortBy, () => props.sortOrder],
  () => {
    photos.value = []
    page.value = 0
    hasMore.value = true
    void loadMore()
  },
  { immediate: true },
)

onMounted(() => void loadMore())
</script>

<template>
  <div class="album-detail">
    <div class="detail-header">
      <Button class="back-btn" type="text" @click="emit('back')">
        <ArrowLeftOutlined />
        {{ t('actions.back') }}
      </Button>
      <h2 class="detail-title">
        {{ props.album.name }}
        <span class="detail-count">{{
          t('albums.photoCount', { n: props.album.photoCount })
        }}</span>
      </h2>
    </div>

    <PhotoGrid
      v-if="photos.length > 0 || loading"
      :photos="photos"
      :selected-ids="new Set<number>()"
      :loading="loading"
      :has-more="hasMore"
      :selectable="false"
      @load-more="loadMore"
      @view="emit('view', $event, photos)"
    />

    <EmptyState
      v-else-if="!loading"
      :title="t('gallery.emptyTitle')"
      :hint="props.album.id === 0 ? t('albums.unassignedDesc') : t('albums.empty')"
    />
  </div>
</template>

<style scoped>
.album-detail {
  padding: 20px;
  max-width: 1400px;
  margin: 0 auto;
}
.detail-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 18px;
}
.back-btn {
  color: var(--c-text-dim);
  border-radius: 999px;
  padding: 0 12px;
  height: 36px;
}
.back-btn:hover {
  color: var(--c-text);
  background: var(--c-surface-2);
}
.detail-title {
  font-size: 20px;
  font-weight: 650;
  letter-spacing: -0.01em;
  color: var(--c-text);
}
.detail-count {
  font-size: 14px;
  font-weight: 400;
  color: var(--c-text-dim);
  margin-left: 8px;
}

@media (max-width: 768px) {
  .album-detail {
    padding: 14px 12px;
  }
  .detail-title {
    font-size: 17px;
  }
}
</style>
