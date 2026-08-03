<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  ArrowLeftOutlined,
  DeleteOutlined,
  FolderOutlined,
  UndoOutlined,
} from '@ant-design/icons-vue'
import { Button, Modal } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import { api } from '../../api'
import { thumbUrl } from '../../webp'
import { tokenQS } from '../../utils/token'
import { useToastStore } from '../../stores/toast'
import { useUiStore } from '../../stores/ui'
import { useDataStore } from '../../stores/data'
import type { Album } from '../../types/album'
import type { Photo } from '../../types/photo'
import type { ApiResponse, PageResponse } from '../../types/api'

/**
 * 回收站（仅从角落菜单进入）：已删除照片/相册，恢复或彻底删除
 * 键盘：查看器打开时 Delete = 彻底删除，R = 恢复
 */
const { t } = useI18n()
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
    if (!res.ok) throw new Error()
    const json: ApiResponse<PageResponse<Photo>> = await res.json()
    photos.value = json.data.content
  } catch {
    toast.error(t('common.unknownError'))
  } finally {
    photoLoading.value = false
  }
}

async function loadAlbums(): Promise<void> {
  albumLoading.value = true
  try {
    const res = await api('/api/trash/albums')
    if (!res.ok) throw new Error()
    const json: ApiResponse<Album[]> = await res.json()
    albums.value = json.data
  } catch {
    toast.error(t('common.unknownError'))
  } finally {
    albumLoading.value = false
  }
}

async function restorePhoto(id: number): Promise<void> {
  photos.value = photos.value.filter((p) => p.id !== id)
  try {
    const res = await api(`/api/trash/photos/${id}/restore`, { method: 'POST' })
    if (!res.ok) throw new Error()
    toast.success(t('trash.restored'))
  } catch (err) {
    toast.error(err instanceof Error ? err.message : t('common.unknownError'))
    void loadPhotos()
  }
}

async function permanentlyDeletePhoto(id: number): Promise<void> {
  photos.value = photos.value.filter((p) => p.id !== id)
  try {
    const res = await api(`/api/trash/photos/${id}`, { method: 'DELETE' })
    if (!res.ok) throw new Error()
    toast.success(t('actions.delete'))
  } catch (err) {
    toast.error(err instanceof Error ? err.message : t('common.unknownError'))
    void loadPhotos()
  }
}

async function restoreAlbum(id: number): Promise<void> {
  albums.value = albums.value.filter((a) => a.id !== id)
  try {
    const res = await api(`/api/trash/albums/${id}/restore`, { method: 'POST' })
    if (!res.ok) throw new Error()
    refreshAlbums()
    toast.success(t('trash.restored'))
  } catch (err) {
    toast.error(err instanceof Error ? err.message : t('common.unknownError'))
    void loadAlbums()
  }
}

async function permanentlyDeleteAlbum(id: number): Promise<void> {
  albums.value = albums.value.filter((a) => a.id !== id)
  try {
    const res = await api(`/api/trash/albums/${id}`, { method: 'DELETE' })
    if (!res.ok) throw new Error()
    refreshAlbums()
    toast.success(t('actions.delete'))
  } catch (err) {
    toast.error(err instanceof Error ? err.message : t('common.unknownError'))
    void loadAlbums()
  }
}

function confirmHardDelete(fn: () => Promise<void>): void {
  Modal.confirm({
    title: t('trash.title'),
    content: t('trash.deleteConfirm'),
    okText: t('actions.delete'),
    okButtonProps: { danger: true },
    cancelText: t('actions.cancel'),
    onOk: () => fn(),
  })
}

/* ---------- 键盘：查看器打开时 Delete / R ---------- */
function onKeydown(e: KeyboardEvent): void {
  const p = ui.viewPhoto
  if (!p || !ui.unlocked) return
  if (e.key === 'Delete' && !e.ctrlKey && !e.metaKey) {
    e.preventDefault()
    const id = p.id
    ui.closeViewer()
    confirmHardDelete(() => permanentlyDeletePhoto(id))
  } else if (e.key === 'r' || e.key === 'R') {
    e.preventDefault()
    const id = p.id
    ui.closeViewer()
    void restorePhoto(id)
  }
}

onMounted(() => {
  void loadPhotos()
  void loadAlbums()
  window.addEventListener('keydown', onKeydown)
})
onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <div class="trash-view">
    <div class="trash-header">
      <Button class="back-btn" type="text" @click="router.back()">
        <ArrowLeftOutlined />
        {{ t('actions.back') }}
      </Button>
      <h2 class="page-title">{{ t('nav.trash') }}</h2>
      <span class="trash-hint">{{ t('trash.empty') }}</span>
    </div>

    <!-- 已删除照片 -->
    <section class="trash-section">
      <h3 class="section-title">
        {{ t('trash.photos') }}
        <span v-if="photos.length" class="section-count">{{ photos.length }}</span>
      </h3>
      <div v-if="photoLoading" class="trash-skeleton" aria-hidden="true">
        <div v-for="i in 3" :key="i" class="sk-row shimmer"></div>
      </div>
      <p v-else-if="photos.length === 0" class="trash-empty">{{ t('trash.emptyPhotos') }}</p>
      <div v-else class="trash-list">
        <div v-for="p in photos" :key="p.id" class="trash-row">
          <img
            :src="thumbUrl(p.id, 200) + tokenQS()"
            :alt="p.name"
            loading="lazy"
            class="trash-thumb"
            @click="ui.openViewer(p, photos)"
          />
          <div class="trash-info">
            <span class="trash-name">{{ p.name }}</span>
            <span class="trash-date">{{ formatDate(p.deletedAt) }}</span>
          </div>
          <div class="trash-actions">
            <Button size="small" class="restore-btn" @click="restorePhoto(p.id)">
              <UndoOutlined />
              {{ t('actions.restore') }}
            </Button>
            <Button
              size="small"
              danger
              @click="confirmHardDelete(() => permanentlyDeletePhoto(p.id))"
            >
              <DeleteOutlined />
              {{ t('actions.delete') }}
            </Button>
          </div>
        </div>
      </div>
    </section>

    <!-- 已删除相册 -->
    <section class="trash-section">
      <h3 class="section-title">
        {{ t('trash.albums') }}
        <span v-if="albums.length" class="section-count">{{ albums.length }}</span>
      </h3>
      <div v-if="albumLoading" class="trash-skeleton" aria-hidden="true">
        <div v-for="i in 2" :key="i" class="sk-row shimmer"></div>
      </div>
      <p v-else-if="albums.length === 0" class="trash-empty">{{ t('trash.emptyAlbums') }}</p>
      <div v-else class="trash-list">
        <div v-for="a in albums" :key="a.id" class="trash-row">
          <div class="trash-album-icon">
            <FolderOutlined />
          </div>
          <div class="trash-info">
            <span class="trash-name">{{ a.name }}</span>
            <span class="trash-date">{{ formatDate(a.deletedAt) }}</span>
          </div>
          <div class="trash-actions">
            <Button size="small" class="restore-btn" @click="restoreAlbum(a.id!)">
              <UndoOutlined />
              {{ t('actions.restore') }}
            </Button>
            <Button
              size="small"
              danger
              @click="confirmHardDelete(() => permanentlyDeleteAlbum(a.id!))"
            >
              <DeleteOutlined />
              {{ t('actions.delete') }}
            </Button>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.trash-view {
  padding: 20px;
  max-width: 900px;
  margin: 0 auto;
}
.trash-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 28px;
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
.page-title {
  font-size: 20px;
  font-weight: 650;
  letter-spacing: -0.01em;
  color: var(--c-text);
}
.trash-hint {
  font-size: 12px;
  color: var(--c-text-dim);
}

.trash-section {
  margin-bottom: 36px;
}
.section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--c-text);
  margin-bottom: 12px;
}
.section-count {
  font-size: 12px;
  font-weight: 400;
  color: var(--c-text-dim);
  margin-left: 6px;
}
.trash-empty {
  font-size: 13px;
  color: var(--c-text-dim);
  padding: 16px 0;
}
.trash-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.trash-row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 10px 14px;
  border-radius: 12px;
  border: 1px solid var(--c-border);
  background: var(--c-surface);
  transition: border-color 0.2s ease;
}
.trash-row:hover {
  border-color: var(--c-border-strong);
}
.trash-thumb {
  width: 60px;
  height: 60px;
  object-fit: cover;
  border-radius: 8px;
  flex-shrink: 0;
  cursor: pointer;
}
.trash-album-icon {
  width: 60px;
  height: 60px;
  border-radius: 8px;
  background: var(--c-accent-soft);
  color: var(--c-accent);
  font-size: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.trash-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.trash-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--c-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.trash-date {
  font-size: 11px;
  color: var(--c-text-dim);
}
.trash-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}
.restore-btn {
  color: var(--c-success);
}
.restore-btn:hover {
  color: var(--c-success);
  border-color: var(--c-success);
  background: var(--c-success-soft);
}

/* 骨架 */
.trash-skeleton {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.sk-row {
  height: 80px;
  border-radius: 12px;
}
.shimmer {
  background: linear-gradient(
    100deg,
    var(--c-surface-2) 40%,
    color-mix(in srgb, var(--c-surface-2) 40%, var(--c-surface)) 50%,
    var(--c-surface-2) 60%
  );
  background-size: 200% 100%;
  animation: shimmer 1.6s ease-in-out infinite;
}
@keyframes shimmer {
  from {
    background-position: 200% 0;
  }
  to {
    background-position: -200% 0;
  }
}

@media (max-width: 768px) {
  .trash-view {
    padding: 14px 12px;
  }
  .trash-actions {
    flex-direction: column;
    gap: 6px;
  }
}
</style>
