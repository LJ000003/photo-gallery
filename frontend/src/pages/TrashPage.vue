<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import { thumbUrl } from '../webp'
import { tokenQS } from '../utils/token'
import { useToastStore } from '../stores/toast'
import { useUiStore } from '../stores/ui'
import { useDataStore } from '../stores/data'
import type { Album } from '../types/album'
import type { Photo } from '../types/photo'
import type { ApiResponse, PageResponse } from '../types/api'

const router = useRouter()
const toast = useToastStore()
const ui = useUiStore()
const { refreshAlbums } = useDataStore()

const photos = ref<Photo[]>([])
const albums = ref<Album[]>([])
const photoLoading = ref(false)
const albumLoading = ref(false)

function formatDate(d: string | undefined): string {
  if (!d) return ''
  return d.substring(0, 19).replace('T', ' ')
}

async function loadPhotos(): Promise<void> {
  photoLoading.value = true
  try {
    const res = await api('/api/trash/photos?size=200')
    if (!res.ok) throw new Error('加载失败')
    const json: ApiResponse<PageResponse<Photo>> = await res.json()
    photos.value = json.data.content
  } catch {
    toast.error('加载已删除照片失败')
  } finally {
    photoLoading.value = false
  }
}

async function loadAlbums(): Promise<void> {
  albumLoading.value = true
  try {
    const res = await api('/api/trash/albums')
    if (!res.ok) throw new Error('加载失败')
    const json: ApiResponse<Album[]> = await res.json()
    albums.value = json.data
  } catch {
    toast.error('加载已删除相册失败')
  } finally {
    albumLoading.value = false
  }
}

async function restorePhoto(id: number): Promise<void> {
  photos.value = photos.value.filter((p) => p.id !== id)
  try {
    const res = await api(`/api/trash/photos/${id}/restore`, { method: 'POST' })
    if (!res.ok) throw new Error('恢复失败')
    toast.success('已恢复')
  } catch (err) {
    toast.error(err instanceof Error ? err.message : '恢复失败')
    loadPhotos()
  }
}

async function permanentlyDeletePhoto(id: number): Promise<void> {
  photos.value = photos.value.filter((p) => p.id !== id)
  try {
    const res = await api(`/api/trash/photos/${id}`, { method: 'DELETE' })
    if (!res.ok) throw new Error('删除失败')
    toast.success('已彻底删除')
  } catch (err) {
    toast.error(err instanceof Error ? err.message : '删除失败')
    loadPhotos()
  }
}

async function restoreAlbum(id: number): Promise<void> {
  albums.value = albums.value.filter((a) => a.id !== id)
  try {
    const res = await api(`/api/trash/albums/${id}/restore`, { method: 'POST' })
    if (!res.ok) throw new Error('恢复失败')
    refreshAlbums()
    toast.success('已恢复')
  } catch (err) {
    toast.error(err instanceof Error ? err.message : '恢复失败')
    loadAlbums()
  }
}

async function permanentlyDeleteAlbum(id: number): Promise<void> {
  albums.value = albums.value.filter((a) => a.id !== id)
  try {
    const res = await api(`/api/trash/albums/${id}`, { method: 'DELETE' })
    if (!res.ok) throw new Error('删除失败')
    refreshAlbums()
    toast.success('已彻底删除')
  } catch (err) {
    toast.error(err instanceof Error ? err.message : '删除失败')
    loadAlbums()
  }
}

// --- 键盘快捷键事件监听 ---
function onKbTrashDelete(): void {
  const p = ui.viewPhoto
  if (p) permanentlyDeletePhoto(p.id)
}

function onKbTrashRestore(): void {
  const p = ui.viewPhoto
  if (p) restorePhoto(p.id)
}

onMounted(() => {
  loadPhotos()
  loadAlbums()
  document.addEventListener('kb:trashDelete', onKbTrashDelete)
  document.addEventListener('kb:trashRestore', onKbTrashRestore)
})

onUnmounted(() => {
  document.removeEventListener('kb:trashDelete', onKbTrashDelete)
  document.removeEventListener('kb:trashRestore', onKbTrashRestore)
})
</script>

<template>
  <section class="trash-page">
    <button class="btn-back" @click="router.back()">← 返回</button>
    <div class="trash-section">
      <h2>已删除照片 <span v-if="photos.length">({{ photos.length }})</span></h2>
      <p class="trash-hint">30 天后自动清理</p>
      <div v-if="photoLoading" class="trash-loading">加载中...</div>
      <div v-else-if="photos.length === 0" class="trash-empty">回收站暂无照片</div>
      <div v-else class="trash-grid">
        <div v-for="p in photos" :key="p.id" class="trash-card">
          <img
            :src="thumbUrl(p.id, 200) + tokenQS()"
            loading="lazy"
            class="trash-thumb"
            @click="ui.openViewer(p, photos)"
          />
          <div class="trash-info">
            <span class="trash-name">{{ p.name }}</span>
            <span class="trash-date">{{ formatDate(p.deletedAt) }}</span>
          </div>
          <div class="trash-actions">
            <button class="btn-restore" @click="restorePhoto(p.id)">恢复</button>
            <button class="btn-permanent" @click="permanentlyDeletePhoto(p.id)">彻底删除</button>
          </div>
        </div>
      </div>
    </div>

    <div class="trash-section">
      <h2>已删除相册 <span v-if="albums.length">({{ albums.length }})</span></h2>
      <div v-if="albumLoading" class="trash-loading">加载中...</div>
      <div v-else-if="albums.length === 0" class="trash-empty">回收站暂无相册</div>
      <div v-else class="trash-grid">
        <div v-for="a in albums" :key="a.id" class="trash-card trash-album-card">
          <div class="trash-album-icon">相册</div>
          <div class="trash-info">
            <span class="trash-name">{{ a.name }}</span>
            <span class="trash-date">{{ formatDate(a.deletedAt) }}</span>
          </div>
          <div class="trash-actions">
            <button class="btn-restore" @click="restoreAlbum(a.id!)">恢复</button>
            <button class="btn-permanent" @click="permanentlyDeleteAlbum(a.id!)">彻底删除</button>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.trash-page {
  display: flex;
  flex-direction: column;
  gap: 40px;
}
.btn-back {
  align-self: flex-start;
  padding: 8px 20px;
  border-radius: 10px;
  border: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.05);
  color: var(--text);
  font-size: 14px;
  cursor: pointer;
  transition: background 0.2s;
}
.btn-back:hover {
  background: rgba(255, 255, 255, 0.1);
}
.trash-section h2 {
  font-size: 18px;
  color: var(--text);
  margin: 0 0 4px;
}
.trash-hint {
  font-size: 12px;
  color: var(--text-dim);
  margin: 0 0 16px;
}
.trash-loading,
.trash-empty {
  color: var(--text-dim);
  font-size: 14px;
  padding: 24px 0;
}
.trash-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 12px;
}
.trash-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid var(--border);
}
.trash-thumb {
  width: 64px;
  height: 64px;
  object-fit: cover;
  border-radius: 8px;
  flex-shrink: 0;
  cursor: pointer;
}
.trash-album-icon {
  width: 64px;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: rgba(0, 180, 240, 0.15);
  color: var(--accent);
  font-size: 12px;
  flex-shrink: 0;
}
.trash-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.trash-name {
  font-size: 14px;
  color: var(--text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.trash-date {
  font-size: 11px;
  color: var(--text-dim);
}
.trash-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}
.btn-restore {
  padding: 6px 14px;
  border-radius: 8px;
  border: 1px solid rgba(0, 200, 100, 0.4);
  background: rgba(0, 200, 100, 0.12);
  color: #0c8;
  font-size: 13px;
  cursor: pointer;
  transition: background 0.2s;
}
.btn-restore:hover {
  background: rgba(0, 200, 100, 0.25);
}
.btn-permanent {
  padding: 6px 14px;
  border-radius: 8px;
  border: 1px solid rgba(220, 40, 80, 0.4);
  background: rgba(220, 40, 80, 0.1);
  color: #e55;
  font-size: 13px;
  cursor: pointer;
  transition: background 0.2s;
}
.btn-permanent:hover {
  background: rgba(220, 40, 80, 0.2);
}
</style>
