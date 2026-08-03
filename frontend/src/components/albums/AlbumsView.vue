<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { CaretDownOutlined, CaretUpOutlined, PlusOutlined } from '@ant-design/icons-vue'
import { Button, Dropdown } from 'ant-design-vue'
import AlbumDetail from './AlbumDetail.vue'
import AlbumEditDrawer from './AlbumEditDrawer.vue'
import { api } from '../../api'
import { useDataStore } from '../../stores/data'
import { useToastStore } from '../../stores/toast'
import { tokenParam } from '../../utils/token'
import { useUiStore } from '../../stores/ui'
import type { Album } from '../../types/album'
import type { ApiResponse } from '../../types/api'
import type { SortField, SortOrder } from '../../types/view'

/**
 * 相册页：相册墙（封面 + 数量）→ 详情流（复用 PhotoGrid）
 * 删除带撤销；创建/编辑走 AlbumEditDrawer
 */
const { t } = useI18n()
const { refreshAlbums } = useDataStore()
const toast = useToastStore()
const ui = useUiStore()

const albums = ref<Album[]>([])
const loading = ref(true)
const brokenCovers = ref(new Set<number>())
const selectedAlbum = ref<{ id: number; name: string; photoCount: number } | null>(null)
const editingAlbum = ref<Album | { id: null; name: string; description: string } | null>(null)

const sortBy = ref<SortField>('time')
const sortOrder = ref<SortOrder>('desc')

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

const sortedAlbums = computed(() => {
  const list = [...albums.value]
  const dir = sortOrder.value === 'asc' ? 1 : -1
  if (sortBy.value === 'name') {
    list.sort((a, b) => dir * a.name.localeCompare(b.name))
  } else {
    list.sort((a, b) => dir * (new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()))
  }
  return list
})

function formatDate(d: string | undefined): string {
  if (!d) return ''
  return d.substring(0, 10)
}

function selectAlbum(album: Album | null): void {
  selectedAlbum.value = album
    ? { id: album.id, name: album.name, photoCount: album.photoCount }
    : { id: 0, name: t('albums.unassigned'), photoCount: 0 }
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

/* ---------- 排序菜单（antd-vue 4 不支持函数式 label/icon，用字符串 + VNode） ---------- */
const sortItems = [
  {
    key: 'time-desc',
    label: t('sort.time'),
    icon: h(CaretDownOutlined),
    onClick: () => applySort('time', 'desc'),
  },
  {
    key: 'time-asc',
    label: t('sort.time'),
    icon: h(CaretUpOutlined),
    onClick: () => applySort('time', 'asc'),
  },
  {
    key: 'name-asc',
    label: t('sort.name'),
    icon: h(CaretUpOutlined),
    onClick: () => applySort('name', 'asc'),
  },
  {
    key: 'name-desc',
    label: t('sort.name'),
    icon: h(CaretDownOutlined),
    onClick: () => applySort('name', 'desc'),
  },
]

function applySort(field: SortField, order: SortOrder): void {
  if (sortBy.value === field && sortOrder.value === order) return
  sortBy.value = field
  sortOrder.value = order
}

onMounted(() => void loadAlbums())
</script>

<template>
  <div class="albums-view">
    <!-- 相册墙 -->
    <template v-if="!selectedAlbum">
      <div class="albums-header">
        <h2 class="page-title">{{ t('nav.albums') }}</h2>
        <div class="header-actions">
          <Dropdown
            :menu="{ items: sortItems, selectable: true, selectedKeys: [`${sortBy}-${sortOrder}`] }"
            placement="bottomRight"
          >
            <Button type="text" class="sort-btn" :aria-label="t('topbar.sort')">
              {{ t('sort.' + sortBy) }}
              <CaretDownOutlined class="caret" />
            </Button>
          </Dropdown>
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
              <p class="album-meta">{{ t('albums.unassignedDesc') }}</p>
            </div>
          </button>
        </div>

        <div v-for="a in sortedAlbums" :key="a.id" class="album-card">
          <button class="album-main" @click="selectAlbum(a)">
            <div class="album-cover">
              <img
                v-if="a.coverPhotoId && !brokenCovers.has(a.id)"
                :src="`/api/v1/photos/${a.coverPhotoId}/thumbnail${tokenParam()}`"
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
    </template>

    <!-- 详情流 -->
    <AlbumDetail
      v-else
      :album="selectedAlbum"
      :sort-by="sortBy"
      :sort-order="sortOrder"
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
