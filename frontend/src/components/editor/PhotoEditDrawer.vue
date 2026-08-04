<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { PlusOutlined } from '@ant-design/icons-vue'
import { Button, Drawer, Input, Select } from 'ant-design-vue'
import { useDataStore } from '../../stores/data'
import { useToastStore } from '../../stores/toast'
import { api } from '../../api'
import { extractErrorMessage } from '../../utils/error'
import type { Photo } from '../../types/photo'
import type { Tag } from '../../types/tag'
import type { Category } from '../../types/category'
import type { Album } from '../../types/album'
import type { ApiResponse } from '../../types/api'

/**
 * 单张照片编辑抽屉：名称/描述/分类/标签/相册 + 行内新建
 * API 语义与旧版一致：PUT /photos/{id}；categoryId 0 = 清除分类（null 语义仅批量保留）
 */
const props = defineProps<{ photo: Photo }>()
const emit = defineEmits<{
  close: []
  saved: []
}>()

const { t } = useI18n()
const toast = useToastStore()
const data = useDataStore()

const editName = ref('')
const editDesc = ref('')
const selectedTagIds = ref<number[]>([])
const selectedCatId = ref<number | undefined>(undefined)
const selectedAlbumIds = ref<number[]>([])
const newTagName = ref('')
const newTagColor = ref('#2563eb')
const newCatName = ref('')
const newAlbumName = ref('')
const saving = ref(false)

onMounted(() => {
  editName.value = props.photo.name
  editDesc.value = props.photo.description || ''
  selectedTagIds.value = (props.photo.tags || []).map((t: Tag) => t.id)
  selectedCatId.value = props.photo.category?.id || undefined
  selectedAlbumIds.value = (props.photo.albums || []).map((a: Album) => a.id)
  void data.loadAll()
})

function toggleTag(id: number): void {
  const idx = selectedTagIds.value.indexOf(id)
  if (idx > -1) selectedTagIds.value.splice(idx, 1)
  else selectedTagIds.value.push(id)
}

function toggleAlbum(id: number): void {
  const idx = selectedAlbumIds.value.indexOf(id)
  if (idx > -1) selectedAlbumIds.value.splice(idx, 1)
  else selectedAlbumIds.value.push(id)
}

async function addTag(): Promise<void> {
  if (!newTagName.value.trim()) return
  const res = await api('/api/tags', {
    method: 'POST',
    body: JSON.stringify({ name: newTagName.value.trim(), color: newTagColor.value }),
  })
  if (res.ok) {
    const json: ApiResponse<Tag> = await res.json()
    selectedTagIds.value.push(json.data.id)
    newTagName.value = ''
    void data.refreshTags()
  }
}

async function addCat(): Promise<void> {
  if (!newCatName.value.trim()) return
  const res = await api('/api/categories', {
    method: 'POST',
    body: JSON.stringify({ name: newCatName.value.trim() }),
  })
  if (res.ok) {
    const json: ApiResponse<Category> = await res.json()
    selectedCatId.value = json.data.id
    newCatName.value = ''
    void data.refreshCategories()
    void data.refreshCategories()
  }
}

async function addAlbum(): Promise<void> {
  if (!newAlbumName.value.trim()) return
  const res = await api('/api/albums', {
    method: 'POST',
    body: JSON.stringify({ name: newAlbumName.value.trim() }),
  })
  if (res.ok) {
    const json: ApiResponse<Album> = await res.json()
    selectedAlbumIds.value.push(json.data.id)
    newAlbumName.value = ''
    void data.refreshAlbums()
  }
}

async function onSubmit(): Promise<void> {
  if (!editName.value.trim()) {
    // 旧实现弹字段 label「名称」而非错误文案
    toast.error(t('edit.nameRequired'))
    return
  }
  if (saving.value) return
  saving.value = true
  try {
    const body = {
      name: editName.value.trim(),
      description: editDesc.value.trim(),
      tagIds: selectedTagIds.value,
      // 后端语义：0 = 清除分类
      categoryId: selectedCatId.value || 0,
      albumIds: selectedAlbumIds.value,
    }
    const res = await api(`/api/photos/${props.photo.id}`, {
      method: 'PUT',
      body: JSON.stringify(body),
    })
    if (!res.ok) {
      const msg = await extractErrorMessage(res)
      throw new Error(msg)
    }
    toast.success(t('edit.saved'))
    emit('saved')
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
    :title="t('edit.title')"
    placement="right"
    :width="'min(480px, 100vw)'"
    @close="emit('close')"
  >
    <div class="edit-body">
      <label class="field-label">{{ t('edit.name') }}</label>
      <Input v-model:value="editName" :placeholder="t('edit.name')" @press-enter="onSubmit" />

      <label class="field-label">{{ t('edit.description') }}</label>
      <Input.TextArea
        v-model:value="editDesc"
        :maxlength="500"
        :rows="3"
        :placeholder="t('edit.description')"
      />

      <label class="field-label">{{ t('edit.category') }}</label>
      <div class="inline-row">
        <Select
          v-model:value="selectedCatId"
          style="flex: 1"
          :placeholder="t('edit.clearCategory')"
          :options="data.categories.map((c) => ({ value: c.id, label: c.name }))"
          :allow-clear="true"
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

      <label class="field-label">{{ t('edit.tags') }}</label>
      <div class="chips">
        <button
          v-for="tag in data.tags"
          :key="tag.id"
          type="button"
          class="chip"
          :class="{ on: selectedTagIds.includes(tag.id) }"
          @click="toggleTag(tag.id)"
        >
          <span class="chip-dot" :style="{ background: tag.color }"></span>
          {{ tag.name }}
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

      <label class="field-label">{{ t('edit.albums') }}</label>
      <div class="chips">
        <button
          v-for="a in data.albums"
          :key="a.id"
          type="button"
          class="chip album"
          :class="{ on: selectedAlbumIds.includes(a.id) }"
          @click="toggleAlbum(a.id)"
        >
          {{ a.name }}
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
.edit-body {
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
