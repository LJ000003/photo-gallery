<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { CheckOutlined } from '@ant-design/icons-vue'
import { Button, Drawer, Input, Modal } from 'ant-design-vue'
import { api } from '../../api'
import { useDataStore } from '../../stores/data'
import { useToastStore } from '../../stores/toast'
import { appendMediaParams } from '../../utils/token'
import type { Photo } from '../../types/photo'
import type { Album } from '../../types/album'
import type { ApiResponse, PageResponse } from '../../types/api'

/**
 * 相册创建/编辑抽屉：名称/描述 + 照片选择器（后端分页 + 搜索防抖）
 * 预选用 /albums/{id}/photo-ids 轻量投影（只取 id），浏览列表逐页拉取，
 * 选中集合与列表独立——搜索/翻页不丢失已选。
 */
const props = defineProps<{ album: Album | { id: null; name: string; description: string } }>()
const emit = defineEmits<{
  close: []
  saved: []
  deleted: []
}>()

const { t } = useI18n()
const { refreshAlbums } = useDataStore()
const toast = useToastStore()

const name = ref('')
const description = ref('')
const photos = ref<Photo[]>([])
const selectedPhotoIds = ref(new Set<number>())
const submitting = ref(false)
const searchQuery = ref('')
const page = ref(0)
const hasMore = ref(true)
const loadingPicker = ref(false)
let searchTimer: number | undefined

const PICKER_PAGE_SIZE = 50

async function loadPickerPage(reset = false): Promise<void> {
  if (loadingPicker.value || (!reset && !hasMore.value)) return
  if (reset) {
    page.value = 0
    hasMore.value = true
    photos.value = []
  }
  loadingPicker.value = true
  try {
    const params = new URLSearchParams({ size: String(PICKER_PAGE_SIZE), page: String(page.value) })
    const q = searchQuery.value.trim()
    if (q) params.set('q', q)
    const res = await api(`/api/photos?${params}`)
    if (!res.ok) throw new Error(String(res.status))
    const data: ApiResponse<PageResponse<Photo>> = await res.json()
    photos.value = reset
      ? data.data?.content || []
      : [...photos.value, ...(data.data?.content || [])]
    hasMore.value = !(data.data?.last ?? true)
    page.value += 1
  } catch (e) {
    console.error('加载照片选择器失败', e)
  } finally {
    loadingPicker.value = false
  }
}

// 搜索防抖（避免每击键请求）；重置列表但保留已选集合
watch(searchQuery, () => {
  window.clearTimeout(searchTimer)
  searchTimer = window.setTimeout(() => void loadPickerPage(true), 300)
})

onUnmounted(() => {
  window.clearTimeout(searchTimer)
})

onMounted(async () => {
  name.value = props.album.name || ''
  description.value = props.album.description || ''
  if (props.album.id) {
    try {
      const res = await api(`/api/albums/${props.album.id}/photo-ids`)
      if (res.ok) {
        const data: ApiResponse<number[]> = await res.json()
        selectedPhotoIds.value = new Set(data.data || [])
      }
    } catch {
      /* 预选失败不阻塞浏览（相册可能为空或已删除） */
    }
  }
  void loadPickerPage()
})

function togglePhoto(id: number): void {
  const s = new Set(selectedPhotoIds.value)
  if (s.has(id)) s.delete(id)
  else s.add(id)
  selectedPhotoIds.value = s
}

async function onSubmit(): Promise<void> {
  if (!name.value.trim()) {
    // 旧实现弹字段 label「相册名称」而非错误文案
    toast.error(t('albums.nameRequired'))
    return
  }
  if (submitting.value) return
  submitting.value = true
  try {
    const body = {
      name: name.value.trim(),
      description: description.value.trim(),
      photoIds: [...selectedPhotoIds.value],
    }
    if (props.album.id) {
      const res = await api(`/api/albums/${props.album.id}`, {
        method: 'PUT',
        body: JSON.stringify(body),
      })
      if (!res.ok) throw new Error()
    } else {
      const res = await api('/api/albums', { method: 'POST', body: JSON.stringify(body) })
      if (!res.ok) throw new Error()
    }
    refreshAlbums()
    toast.success(t('albums.saved'))
    emit('saved')
  } catch {
    toast.error(t('common.unknownError'))
  } finally {
    submitting.value = false
  }
}

function onDelete(): void {
  if (!props.album.id) return
  Modal.confirm({
    title: t('albums.delete'),
    content: t('albums.deleteConfirm', { name: props.album.name }),
    okText: t('actions.delete'),
    okButtonProps: { danger: true },
    cancelText: t('actions.cancel'),
    onOk: async () => {
      try {
        const res = await api(`/api/albums/${props.album.id}`, { method: 'DELETE' })
        if (!res.ok) throw new Error()
        refreshAlbums()
        // 旧实现删除成功后弹「已恢复」（trash.restored 文案方向反转）
        toast.success(t('albums.deleted'))
        emit('deleted')
      } catch {
        toast.error(t('common.unknownError'))
      }
    },
  })
}
</script>

<template>
  <Drawer
    :open="true"
    :title="props.album.id ? t('albums.edit') : t('albums.create')"
    placement="right"
    :width="'min(560px, 100vw)'"
    @close="emit('close')"
  >
    <div class="album-edit-body">
      <label class="field-label">{{ t('albums.name') }}</label>
      <Input v-model:value="name" :placeholder="t('albums.name')" @press-enter="onSubmit" />

      <label class="field-label">{{ t('albums.description') }}</label>
      <Input.TextArea
        v-model:value="description"
        :maxlength="500"
        :rows="2"
        :placeholder="t('albums.description')"
      />

      <div class="picker-header">
        <span class="field-label picker-label"
          >{{ t('albums.pickPhotos') }} ({{ selectedPhotoIds.size }})</span
        >
        <Input
          v-model:value="searchQuery"
          size="small"
          :placeholder="t('albums.searchInPicker')"
          class="picker-search"
        />
      </div>

      <div class="photo-picker">
        <button
          v-for="p in photos"
          :key="p.id"
          type="button"
          class="picker-item"
          :class="{ selected: selectedPhotoIds.has(p.id) }"
          @click="togglePhoto(p.id)"
        >
          <img
            :src="appendMediaParams(`/api/v1/photos/${p.id}/thumbnail`, p)"
            :alt="p.name"
            loading="lazy"
          />
          <span v-if="selectedPhotoIds.has(p.id)" class="picker-check">
            <CheckOutlined />
          </span>
        </button>
        <p v-if="!loadingPicker && photos.length === 0" class="picker-empty">
          {{ t('gallery.emptyTitle') }}
        </p>
      </div>

      <div v-if="photos.length > 0 && hasMore" class="picker-more">
        <Button size="small" :loading="loadingPicker" @click="loadPickerPage()">
          {{ t('gallery.loadMore') }}
        </Button>
      </div>

      <div class="footer-actions">
        <Button v-if="props.album.id" danger @click="onDelete">{{ t('albums.delete') }}</Button>
        <Button
          type="primary"
          size="large"
          class="save-btn"
          :loading="submitting"
          @click="onSubmit"
        >
          {{ t('actions.save') }}
        </Button>
      </div>
    </div>
  </Drawer>
</template>

<style scoped>
.album-edit-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.field-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--c-text-dim);
  margin-top: 14px;
}
.field-label:first-child {
  margin-top: 0;
}
.picker-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.picker-label {
  margin-top: 0;
  white-space: nowrap;
}
.picker-search {
  max-width: 200px;
}
.photo-picker {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(72px, 1fr));
  gap: 6px;
  max-height: 46vh;
  overflow-y: auto;
  padding: 2px;
}
.picker-item {
  position: relative;
  aspect-ratio: 1;
  border: 2px solid transparent;
  border-radius: 8px;
  overflow: hidden;
  padding: 0;
  cursor: pointer;
  background: var(--c-surface-2);
  transition: border-color 0.15s ease;
}
.picker-item:hover {
  border-color: var(--c-accent);
}
.picker-item.selected {
  border-color: var(--c-accent);
}
.picker-item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.picker-check {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--c-accent);
  color: #fff;
  font-size: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.picker-empty {
  grid-column: 1 / -1;
  text-align: center;
  padding: 40px 0;
  font-size: 13px;
  color: var(--c-text-dim);
}
.picker-more {
  display: flex;
  justify-content: center;
  padding: 12px 0 4px;
}
.footer-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 20px;
}
.save-btn {
  border-radius: 999px;
}
</style>
