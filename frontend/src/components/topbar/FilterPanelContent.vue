<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  ReloadOutlined,
  SettingOutlined,
} from '@ant-design/icons-vue'
import { Button, Input, Popconfirm } from 'ant-design-vue'
import { usePhotoStore } from '../../stores/photo'
import { useDataStore } from '../../stores/data'
import { useToastStore } from '../../stores/toast'
import { api } from '../../api'
import type { Category } from '../../types/category'
import type { Tag } from '../../types/tag'

/**
 * 筛选面板内容：标签/分类 chips 多选 + 管理模式（增删改）
 * 桌面弹出面板与移动端抽屉共用
 */
const { t } = useI18n()
const photo = usePhotoStore()
const data = useDataStore()
const toast = useToastStore()

/* ---------- 筛选（直接写 photo store，语义与旧侧栏一致） ---------- */
function toggleTag(id: number): void {
  const arr = [...photo.selectedTagIds]
  const idx = arr.indexOf(id)
  if (idx > -1) arr.splice(idx, 1)
  else arr.push(id)
  photo.selectedTagIds = arr
  photo.resetAndReload()
}

function toggleCat(id: number): void {
  const arr = [...photo.selectedCategoryIds]
  const idx = arr.indexOf(id)
  if (idx > -1) arr.splice(idx, 1)
  else arr.push(id)
  photo.selectedCategoryIds = arr
  photo.resetAndReload()
}

function resetFilters(): void {
  const hadFilters = photo.selectedTagIds.length > 0 || photo.selectedCategoryIds.length > 0
  photo.selectedTagIds = []
  photo.selectedCategoryIds = []
  if (hadFilters) photo.resetAndReload()
}

const activeCount = computed(() => photo.selectedTagIds.length + photo.selectedCategoryIds.length)

/* ---------- 管理模式 ---------- */
const manageMode = ref(false)

const newCatName = ref('')
const newTagName = ref('')
const newTagColor = ref('#2563eb')
const editingCatId = ref<number | null>(null)
const editingTagId = ref<number | null>(null)
const editCatName = ref('')
const editTagName = ref('')
const editTagColor = ref('')

function startEditCat(c: Category): void {
  editingCatId.value = c.id
  editCatName.value = c.name
}
function startEditTag(tag: Tag): void {
  editingTagId.value = tag.id
  editTagName.value = tag.name
  editTagColor.value = tag.color || '#2563eb'
}

async function addCat(): Promise<void> {
  const name = newCatName.value.trim()
  if (!name) return
  try {
    const res = await api('/api/categories', { method: 'POST', body: JSON.stringify({ name }) })
    if (!res.ok) throw new Error()
    newCatName.value = ''
    await data.refreshCategories()
  } catch {
    toast.error(t('common.unknownError'))
  }
}

async function addTag(): Promise<void> {
  const name = newTagName.value.trim()
  if (!name) return
  try {
    const res = await api('/api/tags', {
      method: 'POST',
      body: JSON.stringify({ name, color: newTagColor.value }),
    })
    if (!res.ok) throw new Error()
    newTagName.value = ''
    await data.refreshTags()
  } catch {
    toast.error(t('common.unknownError'))
  }
}

async function saveEditCat(id: number): Promise<void> {
  const name = editCatName.value.trim()
  if (!name) return
  try {
    const res = await api(`/api/categories/${id}`, {
      method: 'PUT',
      body: JSON.stringify({ name }),
    })
    if (!res.ok) throw new Error()
    // 成功后才退出编辑态（失败保留输入，旧实现失败后输入被丢弃）
    editingCatId.value = null
    await data.refreshCategories()
  } catch {
    toast.error(t('common.unknownError'))
  }
}

async function saveEditTag(id: number): Promise<void> {
  const name = editTagName.value.trim()
  if (!name) return
  try {
    const res = await api(`/api/tags/${id}`, {
      method: 'PUT',
      body: JSON.stringify({ name, color: editTagColor.value }),
    })
    if (!res.ok) throw new Error()
    // 成功后才退出编辑态（失败保留输入）
    editingTagId.value = null
    await data.refreshTags()
  } catch {
    toast.error(t('common.unknownError'))
  }
}

async function deleteCat(c: Category): Promise<void> {
  try {
    const res = await api(`/api/categories/${c.id}`, { method: 'DELETE' })
    if (!res.ok) throw new Error()
    photo.selectedCategoryIds = photo.selectedCategoryIds.filter((id) => id !== c.id)
    await data.refreshCategories()
    photo.resetAndReload()
  } catch {
    toast.error(t('common.unknownError'))
  }
}

async function deleteTag(tag: Tag): Promise<void> {
  try {
    const res = await api(`/api/tags/${tag.id}`, { method: 'DELETE' })
    if (!res.ok) throw new Error()
    photo.selectedTagIds = photo.selectedTagIds.filter((id) => id !== tag.id)
    await data.refreshTags()
    photo.resetAndReload()
  } catch {
    toast.error(t('common.unknownError'))
  }
}

watch(
  () => manageMode.value,
  (on) => {
    if (on) void data.loadAll()
  },
)
</script>

<template>
  <div class="filter-body">
    <div class="filter-header">
      <span class="filter-title">
        {{ t('filter.title') }}
        <span v-if="activeCount" class="active-count">{{ activeCount }}</span>
      </span>
      <div class="filter-header-actions">
        <Button v-if="activeCount" type="text" size="small" class="reset-btn" @click="resetFilters">
          <ReloadOutlined />
          {{ t('filter.reset') }}
        </Button>
        <Button
          type="text"
          size="small"
          class="manage-toggle"
          :class="{ on: manageMode }"
          @click="manageMode = !manageMode"
        >
          <SettingOutlined />
          {{ t('filter.manage') }}
        </Button>
      </div>
    </div>

    <!-- 浏览模式：chips 多选 -->
    <template v-if="!manageMode">
      <div class="filter-section">
        <h3 class="section-title">{{ t('filter.categories') }}</h3>
        <div v-if="data.categories.length" class="chips">
          <button
            v-for="c in data.categories"
            :key="c.id"
            class="chip"
            :class="{ on: photo.selectedCategoryIds.includes(c.id) }"
            @click="toggleCat(c.id)"
          >
            {{ c.name }}
          </button>
        </div>
        <p v-else class="section-empty">{{ t('filter.emptyCategories') }}</p>
      </div>

      <div class="filter-section">
        <h3 class="section-title">{{ t('filter.tags') }}</h3>
        <div v-if="data.tags.length" class="chips">
          <button
            v-for="tag in data.tags"
            :key="tag.id"
            class="chip"
            :class="{ on: photo.selectedTagIds.includes(tag.id) }"
            @click="toggleTag(tag.id)"
          >
            <span class="tag-dot" :style="{ background: tag.color }"></span>
            {{ tag.name }}
          </button>
        </div>
        <p v-else class="section-empty">{{ t('filter.emptyTags') }}</p>
      </div>
    </template>

    <!-- 管理模式：增删改 -->
    <template v-else>
      <div class="filter-section">
        <h3 class="section-title">{{ t('filter.categories') }}</h3>
        <ul class="manage-list">
          <li v-for="c in data.categories" :key="c.id" class="manage-row">
            <template v-if="editingCatId === c.id">
              <Input
                v-model:value="editCatName"
                size="small"
                @press-enter="saveEditCat(c.id)"
                @blur="saveEditCat(c.id)"
              />
            </template>
            <template v-else>
              <span class="manage-name">{{ c.name }}</span>
              <span class="manage-actions">
                <button class="icon-btn" :aria-label="t('actions.rename')" @click="startEditCat(c)">
                  <EditOutlined />
                </button>
                <Popconfirm
                  :title="t('filter.deleteConfirm', { name: c.name })"
                  :ok-text="t('actions.delete')"
                  :cancel-text="t('actions.cancel')"
                  @confirm="deleteCat(c)"
                >
                  <button class="icon-btn danger" :aria-label="t('actions.delete')">
                    <DeleteOutlined />
                  </button>
                </Popconfirm>
              </span>
            </template>
          </li>
          <li v-if="!data.categories.length" class="manage-empty">
            {{ t('filter.emptyCategories') }}
          </li>
        </ul>
        <div class="add-row">
          <Input
            v-model:value="newCatName"
            size="small"
            :placeholder="t('filter.categoryName')"
            @press-enter="addCat"
          />
          <Button size="small" type="primary" :aria-label="t('filter.addCategory')" @click="addCat">
            <PlusOutlined />
          </Button>
        </div>
      </div>

      <div class="filter-section">
        <h3 class="section-title">{{ t('filter.tags') }}</h3>
        <ul class="manage-list">
          <li v-for="tag in data.tags" :key="tag.id" class="manage-row">
            <template v-if="editingTagId === tag.id">
              <input
                v-model="editTagColor"
                type="color"
                class="edit-color"
                :aria-label="t('filter.tagColor')"
              />
              <Input
                v-model:value="editTagName"
                size="small"
                @press-enter="saveEditTag(tag.id)"
                @blur="saveEditTag(tag.id)"
              />
            </template>
            <template v-else>
              <span class="tag-dot" :style="{ background: tag.color }"></span>
              <span class="manage-name">{{ tag.name }}</span>
              <span class="manage-actions">
                <button
                  class="icon-btn"
                  :aria-label="t('actions.rename')"
                  @click="startEditTag(tag)"
                >
                  <EditOutlined />
                </button>
                <Popconfirm
                  :title="t('filter.deleteConfirm', { name: tag.name })"
                  :ok-text="t('actions.delete')"
                  :cancel-text="t('actions.cancel')"
                  @confirm="deleteTag(tag)"
                >
                  <button class="icon-btn danger" :aria-label="t('actions.delete')">
                    <DeleteOutlined />
                  </button>
                </Popconfirm>
              </span>
            </template>
          </li>
          <li v-if="!data.tags.length" class="manage-empty">{{ t('filter.emptyTags') }}</li>
        </ul>
        <div class="add-row">
          <input
            v-model="newTagColor"
            type="color"
            class="edit-color"
            :aria-label="t('filter.tagColor')"
          />
          <Input
            v-model:value="newTagName"
            size="small"
            :placeholder="t('filter.tagName')"
            @press-enter="addTag"
          />
          <Button size="small" type="primary" :aria-label="t('filter.addTag')" @click="addTag">
            <PlusOutlined />
          </Button>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.filter-body {
  padding: 16px;
  max-height: min(70vh, 560px);
  overflow-y: auto;
}
.filter-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}
.filter-title {
  font-size: 15px;
  font-weight: 650;
  color: var(--c-text);
}
.active-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  margin-left: 6px;
  border-radius: 999px;
  background: var(--c-accent);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
}
.filter-header-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}
.reset-btn,
.manage-toggle {
  color: var(--c-text-dim);
  font-size: 12px;
}
.manage-toggle.on {
  color: var(--c-accent);
}

.filter-section {
  margin-bottom: 16px;
}
.filter-section:last-child {
  margin-bottom: 0;
}
.section-title {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.02em;
  color: var(--c-text-dim);
  margin-bottom: 8px;
}
.section-empty {
  font-size: 12px;
  color: var(--c-text-dim);
  opacity: 0.6;
  padding: 4px 0;
}

/* chips */
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--c-border);
  background: var(--c-surface);
  color: var(--c-text);
  font-size: 13px;
  padding: 5px 12px;
  border-radius: 999px;
  cursor: pointer;
  transition: all 0.15s ease;
  line-height: 1;
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
.chip:active {
  transform: scale(0.96);
}
.tag-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

/* 管理列表 */
.manage-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-bottom: 8px;
}
.manage-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 8px;
  border-radius: 8px;
  font-size: 13px;
  color: var(--c-text);
}
.manage-row:hover {
  background: var(--c-surface-2);
}
.manage-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.manage-actions {
  display: flex;
  gap: 2px;
  opacity: 0;
  transition: opacity 0.15s ease;
}
.manage-row:hover .manage-actions,
.manage-row:focus-within .manage-actions {
  opacity: 1;
}
.icon-btn {
  width: 26px;
  height: 26px;
  border: none;
  background: none;
  border-radius: 6px;
  color: var(--c-text-dim);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
}
.icon-btn:hover {
  color: var(--c-accent);
  background: var(--c-surface-2);
}
.icon-btn.danger:hover {
  color: var(--c-danger);
}
.manage-empty {
  font-size: 12px;
  color: var(--c-text-dim);
  opacity: 0.6;
  padding: 4px 8px;
}
.add-row {
  display: flex;
  align-items: center;
  gap: 6px;
}
.edit-color {
  width: 26px;
  height: 26px;
  padding: 0;
  border: 1px solid var(--c-border);
  border-radius: 8px;
  cursor: pointer;
  flex-shrink: 0;
  background: none;
}

@media (max-width: 768px) {
  .manage-actions {
    opacity: 1;
  }
}
</style>
