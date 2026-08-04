<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { PlusOutlined } from '@ant-design/icons-vue'
import { Button } from 'ant-design-vue'
import AlbumDetail from './AlbumDetail.vue'
import AlbumEditDrawer from './AlbumEditDrawer.vue'
import { api } from '../../api'
import { useDataStore } from '../../stores/data'
import { usePhotoStore } from '../../stores/photo'
import { useToastStore } from '../../stores/toast'
import { appendMediaParams } from '../../utils/token'
import { useUiStore } from '../../stores/ui'
import type { Album } from '../../types/album'
import type { ApiResponse, PageResponse } from '../../types/api'

/**
 * 相册页：相册墙（封面 + 数量）→ 详情流（复用 PhotoGrid）
 * 删除带撤销；创建/编辑走 AlbumEditDrawer
 */
const { t } = useI18n()
const { refreshAlbums } = useDataStore()
const toast = useToastStore()
const ui = useUiStore()
const photo = usePhotoStore()

const albums = ref<Album[]>([])
const loading = ref(true)
const brokenCovers = ref(new Set<number>())
const selectedAlbum = ref<{ id: number; name: string; photoCount: number } | null>(null)
const editingAlbum = ref<Album | { id: null; name: string; description: string } | null>(null)
const unassignedCount = ref(0)

async function loadAlbums(): Promise<void> {
  loading.value = true
  try {
    const res = await api('/api/albums')
    const data: ApiResponse<Album[]> = await res.json()
    albums.value = data.data || []
  } catch {
    albums.value = []
  } finally {
    loading.value = false
  }
}

/** 未分配照片数：albumId=0 与相册详情同语义，复用列表接口的 totalElements */
async function loadUnassignedCount(): Promise<void> {
  try {
    const res = await api('/api/photos?albumId=0&page=0&size=1')
    const json: ApiResponse<PageResponse<unknown>> = await res.json()
    unassignedCount.value = json.data?.totalElements ?? 0
  } catch {
    unassignedCount.value = 0
  }
}

/**
 * 相册墙排序跟随顶部菜单（photo store 全局排序），不再有本地按钮。
 * 方向语义与 TopBar 完全一致：store 层 time 反转（store asc = 用户可见 desc = 最新优先）。
 * size 字段对相册映射为 photoCount（相册没有文件大小，照片数量即「相册大小」）。
 */
const sortedAlbums = computed(() => {
  const list = [...albums.value]
  const visibleOrder: 'asc' | 'desc' =
    photo.sortBy === 'time'
      ? photo.sortOrder === 'asc'
        ? 'desc'
        : 'asc'
      : photo.sortOrder
  const dir = visibleOrder === 'asc' ? 1 : -1
  if (photo.sortBy === 'name') {
    list.sort((a, b) => dir * a.name.localeCompare(b.name))
  } else if (photo.sortBy === 'size') {
    list.sort((a, b) => dir * (a.photoCount - b.photoCount))
  } else {
    list.sort((a, b) => dir * (new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()))
  }
  return list
})

/** 相册搜索（顶部搜索框驱动）：按名称/描述包含过滤，大小写不敏感 */
const visibleAlbums = computed(() => {
  const q = ui.albumSearch.trim().toLowerCase()
  if (!q) return sortedAlbums.value
  return sortedAlbums.value.filter(
    (a) =>
      a.name.toLowerCase().includes(q) || (a.description || '').toLowerCase().includes(q),
  )
})

function formatDate(d: string | undefined): string {
  if (!d) return ''
  return d.substring(0, 10)
}

function selectAlbum(album: Album | null): void {
  selectedAlbum.value = album
    ? { id: album.id, name: album.name, photoCount: album.photoCount }
    : { id: 0, name: t('albums.unassigned'), photoCount: unassignedCount.value }
}

function backToList(): void {
  selectedAlbum.value = null
}

function openEdit(album: Album): void {
  editingAlbum.value = album
}

function onCreate(): void {
  editingAlbum.value = { id: null, name: '', description: '' }
}

async function revertDeleteAlbum(id: number): Promise<void> {
  try {
    const res = await api(`/api/albums/${id}/restore`, { method: 'POST' })
    if (!res.ok) throw new Error()
    await loadAlbums()
    refreshAlbums()
    toast.success(t('trash.restored'))
  } catch {
    toast.error(t('common.unknownError'))
  }
}

async function deleteAlbum(album: Album): Promise<void> {
  const prev = [...albums.value]
  albums.value = albums.value.filter((a) => a.id !== album.id)
  try {
    const res = await api(`/api/albums/${album.id}`, { method: 'DELETE' })
    if (!res.ok) throw new Error()
    refreshAlbums()
    toast.add(t('actions.delete'), 'success', 5000, {
      label: t('actions.restore'),
      onClick: () => void revertDeleteAlbum(album.id),
    })
  } catch {
    albums.value = prev
    toast.error(t('common.unknownError'))
  }
}

function onAlbumSaved(): void {
  editingAlbum.value = null
  refreshAlbums()
  void loadAlbums()
}

onMounted(() => {
  void loadAlbums()
  void loadUnassignedCount()
})
</script>

<template>
  <div class="albums-view">
    <!-- 相册墙 -->
    <template v-if="!selectedAlbum">
      <div class="albums-header">
        <h2 class="page-title">{{ t('nav.albums') }}</h2>
        <div class="header-actions">
          <!-- 排序已收敛到顶部菜单（全局生效），此处不再有本地按钮 -->
          <Button type="primary" @click="onCreate">
            <PlusOutlined />
            {{ t('albums.create') }}
          </Button>
        </div>
      </div>

      <div class="album-grid">
        <!-- 未分配 -->
        <div class="album-card unassigned">
          <button class="album-main" @click="selectAlbum(null)">
            <div class="album-cover">
              <span class="cover-placeholder">▦</span>
            </div>
            <div class="album-body">
              <h3 class="album-name">{{ t('albums.unassigned') }}</h3>
              <p class="album-meta">
                {{ t('albums.photoCount', { n: unassignedCount }) }}
                · {{ t('albums.unassignedDesc') }}
              </p>
            </div>
          </button>
        </div>

        <div v-for="a in visibleAlbums" :key="a.id" class="album-card">
          <button class="album-main" @click="selectAlbum(a)">
            <div class="album-cover">
              <img
                v-if="a.coverPhotoId && !brokenCovers.has(a.id)"
                :src="appendMediaParams(`/api/v1/photos/${a.coverPhotoId}/thumbnail`, a)"
                :alt="a.name"
                loading="lazy"
                @error="brokenCovers.add(a.id)"
              />
              <span v-else class="cover-placeholder">▤</span>
            </div>
            <div class="album-body">
              <h3 class="album-name">{{ a.name }}</h3>
              <p class="album-meta">
                {{ t('albums.photoCount', { n: a.photoCount })
                }}<template v-if="a.createdAt"> · {{ formatDate(a.createdAt) }}</template>
              </p>
            </div>
          </button>
          <div class="album-actions">
            <Button size="small" type="text" @click="openEdit(a)">{{ t('actions.edit') }}</Button>
            <Button size="small" type="text" danger @click="deleteAlbum(a)">
              {{ t('actions.delete') }}
            </Button>
          </div>
        </div>
      </div>

      <p v-if="!loading && albums.length === 0" class="albums-empty">{{ t('albums.empty') }}</p>
      <p v-else-if="!loading && visibleAlbums.length === 0" class="albums-empty">
        {{ t('albums.emptyFiltered') }}
      </p>
    </template>

    <!-- 详情流（排序跟随顶部菜单，由 AlbumDetail 直接读 photo store） -->
    <AlbumDetail
      v-else
      :album="selectedAlbum"
      @back="backToList"
      @view="(p, list) => ui.openViewer(p, list)"
    />

    <AlbumEditDrawer
      v-if="editingAlbum"
      :album="editingAlbum"
      @close="editingAlbum = null"
      @saved="onAlbumSaved"
      @deleted="onAlbumSaved"
    />
  </div>
</template>

<style scoped>
.albums-view {
  padding: 20px;
  max-width: 1400px;
  margin: 0 auto;
}
.albums-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}
.page-title {
  font-size: 20px;
  font-weight: 650;
  letter-spacing: -0.01em;
  color: var(--c-text);
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.sort-btn {
  color: var(--c-text-dim);
  border-radius: 999px;
  height: 36px;
  padding: 0 12px;
}
.sort-btn:hover {
  color: var(--c-text);
  background: var(--c-surface-2);
}
.caret {
  font-size: 10px;
  margin-left: 2px;
}

.album-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
}
.album-card {
  position: relative;
  border: none;
  background: none;
  padding: 0;
  text-align: left;
  border-radius: 12px;
}
.album-main {
  display: block;
  width: 100%;
  border: none;
  background: none;
  padding: 0;
  text-align: left;
  cursor: pointer;
}
.album-cover {
  aspect-ratio: 4 / 3;
  border-radius: 12px;
  overflow: hidden;
  background: var(--c-surface-2);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: box-shadow 0.25s ease;
}
.album-card:hover .album-cover {
  box-shadow: var(--shadow-photo);
}
.album-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.35s ease;
}
.album-card:hover .album-cover img {
  transform: scale(1.03);
}
.cover-placeholder {
  font-size: 32px;
  color: var(--c-text-dim);
  opacity: 0.5;
}
.album-body {
  padding: 10px 4px 4px;
}
.album-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--c-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.album-meta {
  font-size: 12px;
  color: var(--c-text-dim);
  margin-top: 3px;
}
.album-actions {
  position: absolute;
  top: 8px;
  right: 8px;
  display: flex;
  gap: 2px;
  opacity: 0;
  transition: opacity 0.2s ease;
  background: rgba(0, 0, 0, 0.45);
  backdrop-filter: blur(8px);
  border-radius: 999px;
  padding: 2px;
}
.album-card:hover .album-actions,
.album-card:focus-within .album-actions {
  opacity: 1;
}
.album-actions :deep(.ant-btn) {
  color: #fff;
}
.album-actions :deep(.ant-btn:hover) {
  background: rgba(255, 255, 255, 0.16);
}
.albums-empty {
  text-align: center;
  padding: 80px 24px;
  font-size: 13px;
  color: var(--c-text-dim);
}

@media (max-width: 768px) {
  .albums-view {
    padding: 14px 12px;
  }
  .album-grid {
    grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
    gap: 12px;
  }
  .album-actions {
    opacity: 1;
  }
}
</style>
