<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { PlusOutlined } from '@ant-design/icons-vue'
import { Button, Drawer, Input, Select } from 'ant-design-vue'
import { useDataStore } from '../../stores/data'
import { useToastStore } from '../../stores/toast'
import { api } from '../../api'
import { extractErrorMessage } from '../../utils/error'
import type { Photo, BatchPhotoUpdateRequest } from '../../types/photo'
import type { Tag } from '../../types/tag'
import type { Category } from '../../types/category'
import type { Album } from '../../types/album'
import type { ApiResponse } from '../../types/api'

/**
 * 批量编辑抽屉（≤50 张）：
 * 标签/相册按「添加/移除」两组互斥操作（带 n/total 分布计数），分类三态 NONE/SET/CLEAR
 * API 语义与旧版一致：PUT /photos/batch
 */
const props = defineProps<{ photos: Photo[] }>()
const emit = defineEmits<{
  close: []
  saved: [updated: Photo[]]
}>()

const { t } = useI18n()
const toast = useToastStore()
const data = useDataStore()

const addTagIds = ref<number[]>([])
const removeTagIds = ref<number[]>([])
const addAlbumIds = ref<number[]>([])
const removeAlbumIds = ref<number[]>([])
const categoryChoice = ref<'none' | 'clear' | number>('none')
const newTagName = ref('')
const newTagColor = ref('#2563eb')
const newCatName = ref('')
const newAlbumName = ref('')
const saving = ref(false)

/** 选中照片中每个标签/相册的分布快照（如「旅行 3/5」） */
const tagCounts = computed(() => {
  const m = new Map<number, number>()
  for (const p of props.photos)
    for (const tag of p.tags || []) m.set(tag.id, (m.get(tag.id) || 0) + 1)
  return m
})
const albumCounts = computed(() => {
  const m = new Map<number, number>()
  for (const p of props.photos) for (const a of p.albums || []) m.set(a.id, (m.get(a.id) || 0) + 1)
  return m
})

onMounted(() => {
  void data.loadAll()
})

/** 把 id 加入 target 列表并从另一组移除（互斥） */
function toggleIn(list: number[], other: number[], id: number): void {
  const idx = list.indexOf(id)
  if (idx > -1) list.splice(idx, 1)
  else list.push(id)
  const oi = other.indexOf(id)
  if (oi > -1) other.splice(oi, 1)
}

// api() 在非 2xx 时直接 throw ApiError——失败必须 try/catch 才有反馈（P4-#47：此前失败静默为未处理 rejection）
async function addTag(): Promise<void> {
  if (!newTagName.value.trim()) return
  try {
    const res = await api('/api/tags', {
      method: 'POST',
      body: JSON.stringify({ name: newTagName.value.trim(), color: newTagColor.value }),
    })
    const json: ApiResponse<Tag> = await res.json()
    addTagIds.value.push(json.data.id)
    newTagName.value = ''
    void data.refreshTags()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : t('common.unknownError'))
  }
}

async function addCat(): Promise<void> {
  if (!newCatName.value.trim()) return
  try {
    const res = await api('/api/categories', {
      method: 'POST',
      body: JSON.stringify({ name: newCatName.value.trim() }),
    })
    const json: ApiResponse<Category> = await res.json()
    categoryChoice.value = json.data.id
    newCatName.value = ''
    void data.refreshCategories()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : t('common.unknownError'))
  }
}

async function addAlbum(): Promise<void> {
  if (!newAlbumName.value.trim()) return
  try {
    const res = await api('/api/albums', {
      method: 'POST',
      body: JSON.stringify({ name: newAlbumName.value.trim() }),
    })
    const json: ApiResponse<Album> = await res.json()
    addAlbumIds.value.push(json.data.id)
    newAlbumName.value = ''
    void data.refreshAlbums()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : t('common.unknownError'))
  }
}

async function onSubmit(): Promise<void> {
  if (props.photos.length > 50) {
    toast.error(t('batchEdit.tooMany'))
    return
  }
  if (saving.value) return
  saving.value = true
  const isSet = typeof categoryChoice.value === 'number'
  try {
    const body: BatchPhotoUpdateRequest = {
      photoIds: props.photos.map((p) => p.id),
      addTagIds: [...addTagIds.value],
      removeTagIds: [...removeTagIds.value],
      addAlbumIds: [...addAlbumIds.value],
      removeAlbumIds: [...removeAlbumIds.value],
      categoryOp: categoryChoice.value === 'clear' ? 'CLEAR' : isSet ? 'SET' : 'NONE',
      categoryId: isSet ? (categoryChoice.value as number) : null,
    }
    const res = await api('/api/photos/batch', { method: 'PUT', body: JSON.stringify(body) })
    if (!res.ok) {
      const msg = await extractErrorMessage(res)
      throw new Error(msg)
    }
    const json: ApiResponse<Photo[]> = await res.json()
    emit('saved', json.data || [])
  } catch (err) {
    toast.error(err instanceof Error ? err.message : t('common.unknownError'))
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <Drawer
    :open="true"
    :title="`${t('batchEdit.title')} (${photos.length})`"
    placement="right"
    :width="'min(520px, 100vw)'"
    @close="emit('close')"
  >
    <div class="batch-body">
      <!-- 分类三态 -->
      <label class="field-label">{{ t('batchEdit.category') }}</label>
      <div class="category-row">
        <Select
          v-model:value="categoryChoice"
          style="flex: 1"
          :options="[
            { value: 'none', label: t('batchEdit.categoryKeep') },
            { value: 'clear', label: t('batchEdit.categoryClear') },
            ...data.categories.map((c) => ({ value: c.id, label: c.name })),
          ]"
        />
        <Input
          v-model:value="newCatName"
          :placeholder="t('edit.addCategory')"
          @press-enter="addCat"
        />
        <Button :aria-label="t('edit.addCategory')" @click="addCat">
          <PlusOutlined />
        </Button>
      </div>

      <!-- 标签：添加 / 移除 -->
      <label class="field-label">{{ t('batchEdit.addTags') }}</label>
      <div class="chips">
        <button
          v-for="tag in data.tags"
          :key="tag.id"
          type="button"
          class="chip"
          :class="{ on: addTagIds.includes(tag.id) }"
          @click="toggleIn(addTagIds, removeTagIds, tag.id)"
        >
          <span class="chip-dot" :style="{ background: tag.color }"></span>
          + {{ tag.name }}
          <span class="chip-count">{{ tagCounts.get(tag.id) || 0 }}/{{ photos.length }}</span>
        </button>
      </div>

      <label class="field-label">{{ t('batchEdit.removeTags') }}</label>
      <div class="chips">
        <button
          v-for="tag in data.tags"
          :key="tag.id"
          type="button"
          class="chip"
          :class="{ on: removeTagIds.includes(tag.id) }"
          @click="toggleIn(removeTagIds, addTagIds, tag.id)"
        >
          <span class="chip-dot" :style="{ background: tag.color }"></span>
          − {{ tag.name }}
        </button>
      </div>
      <div class="inline-row">
        <input
          v-model="newTagColor"
          type="color"
          class="color-pick"
          :aria-label="t('filter.tagColor')"
        />
        <Input v-model:value="newTagName" :placeholder="t('edit.addTag')" @press-enter="addTag" />
        <Button :aria-label="t('edit.addTag')" @click="addTag">
          <PlusOutlined />
        </Button>
      </div>

      <!-- 相册：加入 / 移出 -->
      <label class="field-label">{{ t('batchEdit.addAlbums') }}</label>
      <div class="chips">
        <button
          v-for="a in data.albums"
          :key="a.id"
          type="button"
          class="chip album"
          :class="{ on: addAlbumIds.includes(a.id) }"
          @click="toggleIn(addAlbumIds, removeAlbumIds, a.id)"
        >
          + {{ a.name }}
          <span class="chip-count">{{ albumCounts.get(a.id) || 0 }}/{{ photos.length }}</span>
        </button>
      </div>

      <label class="field-label">{{ t('batchEdit.removeAlbums') }}</label>
      <div class="chips">
        <button
          v-for="a in data.albums"
          :key="a.id"
          type="button"
          class="chip album"
          :class="{ on: removeAlbumIds.includes(a.id) }"
          @click="toggleIn(removeAlbumIds, addAlbumIds, a.id)"
        >
          − {{ a.name }}
        </button>
      </div>
      <div class="inline-row">
        <Input
          v-model:value="newAlbumName"
          :placeholder="t('edit.addAlbum')"
          @press-enter="addAlbum"
        />
        <Button :aria-label="t('edit.addAlbum')" @click="addAlbum">
          <PlusOutlined />
        </Button>
      </div>

      <Button
        type="primary"
        block
        size="large"
        class="save-btn"
        :loading="saving"
        @click="onSubmit"
      >
        {{ t('actions.save') }}
      </Button>
    </div>
  </Drawer>
</template>

<style scoped>
.batch-body {
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
.category-row,
.inline-row {
  display: flex;
  gap: 8px;
  align-items: center;
}
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: 1px solid var(--c-border);
  background: var(--c-surface);
  color: var(--c-text);
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 999px;
  cursor: pointer;
  transition: all 0.15s ease;
}
.chip:hover {
  border-color: var(--c-accent);
}
.chip.on {
  border-color: var(--c-accent);
  background: var(--c-accent-soft);
  color: var(--c-accent);
  font-weight: 500;
}
.chip-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
}
.chip-count {
  font-size: 11px;
  opacity: 0.7;
}
.color-pick {
  width: 32px;
  height: 32px;
  padding: 0;
  border: 1px solid var(--c-border);
  border-radius: 8px;
  cursor: pointer;
  background: none;
  flex-shrink: 0;
}
.save-btn {
  margin-top: 24px;
  border-radius: 999px;
}
</style>
