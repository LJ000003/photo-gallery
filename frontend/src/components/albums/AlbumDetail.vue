<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowLeftOutlined } from '@ant-design/icons-vue'
import { Button } from 'ant-design-vue'
import PhotoGrid from '../gallery/PhotoGrid.vue'
import EmptyState from '../common/EmptyState.vue'
import { api } from '../../api'
import { usePhotoStore } from '../../stores/photo'
import { useInfinitePagination } from '../../composables/useInfinitePagination'
import type { Photo } from '../../types/photo'
import type { ApiResponse, PageResponse } from '../../types/api'

/**
 * 相册详情：复用 PhotoGrid 虚拟滚动，分页加载相册内照片（未分配专辑 = albumId 0）。
 * 排序直接读 photo store（与顶部菜单全局排序同源），排序变化自动重载。
 * 分页复用 useInfinitePagination（requestId 竞态守卫与 photo store 同源）：
 * 相册切换/排序变化时在途旧请求被 reset() 作废，不会混入已清空的新列表。
 */
const props = defineProps<{
  album: { id: number; name: string; photoCount: number }
}>()

const emit = defineEmits<{
  back: []
  view: [p: Photo, list: Photo[]]
}>()

const { t } = useI18n()
const photo = usePhotoStore()

const photos = ref<Photo[]>([])

const pagination = useInfinitePagination<Photo>(
  async (page) => {
    // 请求层排序与 photo store 一致：time 字段反转（store asc = 请求 desc = 最新优先）
    const fieldMap: Record<string, string> = { time: 'createdAt', name: 'name', size: 'fileSize' }
    const order =
      photo.sortBy === 'time' ? (photo.sortOrder === 'asc' ? 'desc' : 'asc') : photo.sortOrder
    const sortStr = `${fieldMap[photo.sortBy]},${order}`
    const url =
      props.album.id === 0
        ? `/api/photos?albumId=0&page=${page}&size=20&sort=${sortStr}`
        : `/api/albums/${props.album.id}/photos?page=${page}&size=20&sort=${sortStr}`
    const res = await api(url)
    const json: ApiResponse<PageResponse<Photo>> = await res.json()
    return json.data ?? null
  },
  (payload) => {
    if (payload.content && payload.content.length) photos.value.push(...payload.content)
  },
)
const { hasMore, loading, loadMore } = pagination

watch(
  [() => props.album.id, () => photo.sortBy, () => photo.sortOrder],
  () => {
    photos.value = []
    pagination.reset()
    void loadMore()
  },
  { immediate: true },
)
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
