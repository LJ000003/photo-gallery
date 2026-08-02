<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import gsap from 'gsap'
import { useI18n } from 'vue-i18n'
import { useDataStore } from '../stores/data'
import { useToastStore } from '../stores/toast'
import { api } from '../api'
import type { Photo, BatchPhotoUpdateRequest } from '../types/photo'
import type { Tag } from '../types/tag'
import type { Category } from '../types/category'
import type { Album } from '../types/album'
import type { ApiResponse } from '../types/api'

const { t } = useI18n()
const toast = useToastStore()
const props = defineProps<{ photos: Photo[] }>()
const emit = defineEmits<{
  close: []
  saved: [updated: Photo[]]
}>()

const dataStore = useDataStore()
const { tags: allTags, categories: allCats, albums: allAlbums } = storeToRefs(dataStore)
const { loadAll, refreshTags, refreshCategories, refreshAlbums } = dataStore

// 标签/相册分「添加」与「移除」两组；同一个 id 不能同时出现在两组（toggleIn 互斥）
const addTagIds = ref<number[]>([])
const removeTagIds = ref<number[]>([])
const addAlbumIds = ref<number[]>([])
const removeAlbumIds = ref<number[]>([])
// 分类三态：'none' 不修改 / 'clear' 清除 / number = 设为该分类
const categoryChoice = ref<'none' | 'clear' | number>('none')
const newTagName = ref('')
const newTagColor = ref('#00d4ff')
const newCatName = ref('')
const newAlbumName = ref('')
const saving = ref(false)

/** 选中照片中每个标签/相册的分布快照（如「旅行 3/5」），提示添加 vs 移除的语义 */
const tagCounts = computed(() => {
  const m = new Map<number, number>()
  for (const p of props.photos) for (const t of p.tags || []) m.set(t.id, (m.get(t.id) || 0) + 1)
  return m
})
const albumCounts = computed(() => {
  const m = new Map<number, number>()
  for (const p of props.photos) for (const a of p.albums || []) m.set(a.id, (m.get(a.id) || 0) + 1)
  return m
})

onMounted(() => {
  loadAll()
  const content = document.querySelector('#batchEditModal .modal-content')
  const backdrop = document.querySelector('#batchEditModal .modal-backdrop')
  gsap.fromTo(
    content,
    { scale: 0.85, opacity: 0 },
    { scale: 1, opacity: 1, duration: 0.35, ease: 'expo.out' },
  )
  gsap.fromTo(backdrop, { opacity: 0 }, { opacity: 1, duration: 0.35, ease: 'none' })
})

/** 把 id 加入 target 列表并从另一组中移除（保证互斥） */
function toggleIn(list: number[], other: number[], id: number): void {
  const idx = list.indexOf(id)
  if (idx > -1) list.splice(idx, 1)
  else list.push(id)
  const oi = other.indexOf(id)
  if (oi > -1) other.splice(oi, 1)
}

async function addTag(): Promise<void> {
  if (!newTagName.value.trim()) return
  const res = await api('/api/tags', {
    method: 'POST',
    body: JSON.stringify({ name: newTagName.value.trim(), color: newTagColor.value }),
  })
  if (res.ok) {
    const json: ApiResponse<Tag> = await res.json()
    addTagIds.value.push(json.data.id)
    newTagName.value = ''
    refreshTags()
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
    categoryChoice.value = json.data.id
    newCatName.value = ''
    refreshCategories()
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
    addAlbumIds.value.push(json.data.id)
    newAlbumName.value = ''
    refreshAlbums()
  }
}

function onClose(): void {
  const content = document.querySelector('#batchEditModal .modal-content')
  const backdrop = document.querySelector('#batchEditModal .modal-backdrop')
  gsap.to(content, {
    scale: 0.9,
    opacity: 0,
    duration: 0.2,
    ease: 'power1.in',
    onComplete: () => emit('close'),
  })
  gsap.to(backdrop, { opacity: 0, duration: 0.2, ease: 'none' })
}

async function extractErrorMessage(res: Response): Promise<string> {
  try {
    const data = await res.json()
    return data.message || `请求失败（${res.status}）`
  } catch {
    return `服务器返回异常（${res.status}），请稍后重试`
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
    toast.error(err instanceof Error ? err.message : '批量编辑失败')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div id="batchEditModal" class="modal">
    <div class="modal-backdrop" @click="onClose"></div>
    <div class="modal-content modal-small">
      <button class="modal-close" @click="onClose">&times;</button>
      <h2>{{ $t('batchEdit.title') }} ({{ photos.length }})</h2>
      <form @submit.prevent="onSubmit">
        <label>{{ $t('batchEdit.category') }}</label>
        <select v-model="categoryChoice" class="mini-select" style="width: 100%">
          <option value="none">{{ $t('batchEdit.noChange') }}</option>
          <option value="clear">{{ $t('batchEdit.clearCategory') }}</option>
          <option v-for="c in allCats" :key="c.id" :value="c.id">{{ c.name }}</option>
        </select>
        <div class="filter-input" style="margin-top: 6px">
          <input
            v-model="newCatName"
            :placeholder="$t('batchEdit.newCategory')"
            @keyup.enter="addCat"
          />
          <button type="button" class="btn-mini" @click="addCat">+</button>
        </div>

        <label>{{ $t('batchEdit.addTags') }}</label>
        <div class="tag-chips">
          <button
            v-for="t in allTags"
            :key="t.id"
            type="button"
            class="tag-chip batch-chip"
            :class="{ on: addTagIds.includes(t.id) }"
            :style="addTagIds.includes(t.id) ? { background: t.color, borderColor: t.color } : {}"
            @click="toggleIn(addTagIds, removeTagIds, t.id)"
          >
            + {{ t.name }}
            <span class="chip-count">{{ tagCounts.get(t.id) || 0 }}/{{ photos.length }}</span>
          </button>
        </div>

        <label>{{ $t('batchEdit.removeTags') }}</label>
        <div class="tag-chips">
          <button
            v-for="t in allTags"
            :key="t.id"
            type="button"
            class="tag-chip batch-chip"
            :class="{ on: removeTagIds.includes(t.id) }"
            :style="
              removeTagIds.includes(t.id) ? { background: t.color, borderColor: t.color } : {}
            "
            @click="toggleIn(removeTagIds, addTagIds, t.id)"
          >
            − {{ t.name }}
          </button>
        </div>
        <div class="filter-input" style="margin-top: 6px">
          <input v-model="newTagColor" type="color" class="color-pick" />
          <input v-model="newTagName" :placeholder="$t('batchEdit.newTag')" @keyup.enter="addTag" />
          <button type="button" class="btn-mini" @click="addTag">+</button>
        </div>

        <label>{{ $t('batchEdit.addAlbums') }}</label>
        <div class="tag-chips">
          <button
            v-for="a in allAlbums"
            :key="a.id"
            type="button"
            class="tag-chip album-chip batch-chip"
            :class="{ on: addAlbumIds.includes(a.id) }"
            @click="toggleIn(addAlbumIds, removeAlbumIds, a.id)"
          >
            + {{ a.name }}
            <span class="chip-count">{{ albumCounts.get(a.id) || 0 }}/{{ photos.length }}</span>
          </button>
        </div>

        <label>{{ $t('batchEdit.removeAlbums') }}</label>
        <div class="tag-chips">
          <button
            v-for="a in allAlbums"
            :key="a.id"
            type="button"
            class="tag-chip album-chip batch-chip"
            :class="{ on: removeAlbumIds.includes(a.id) }"
            @click="toggleIn(removeAlbumIds, addAlbumIds, a.id)"
          >
            − {{ a.name }}
          </button>
        </div>
        <div class="filter-input" style="margin-top: 6px">
          <input
            v-model="newAlbumName"
            :placeholder="$t('batchEdit.newAlbum')"
            @keyup.enter="addAlbum"
          />
          <button type="button" class="btn-mini" @click="addAlbum">+</button>
        </div>

        <button type="submit" :disabled="saving">{{ $t('batchEdit.save') }}</button>
      </form>
    </div>
  </div>
</template>

<style scoped>
.batch-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.chip-count {
  font-size: 11px;
  opacity: 0.75;
}
</style>
