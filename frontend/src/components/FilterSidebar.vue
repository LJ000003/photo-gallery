<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from 'vue'
import { useStore } from '../store'
import { useConfirm } from '../useConfirm'
import { api } from '../api'
import type { Tag } from '../types/tag'
import type { Category } from '../types/category'

const confirmFn = useConfirm()
const props = defineProps<{
  selectedTagIds: number[]
  selectedCategoryIds: number[]
}>()
const emit = defineEmits<{
  'update:selectedTagIds': [ids: number[]]
  'update:selectedCategoryIds': [ids: number[]]
}>()

const { tags, categories, loadAll, refreshTags, refreshCategories } = useStore()
const newTagName = ref('')
const newTagColor = ref('#00d4ff')
const newCatName = ref('')

const selCats = ref(new Set<number>())
const selTags = ref(new Set<number>())

const allCatsSel = computed(
  () => categories.value.length > 0 && categories.value.every((c) => selCats.value.has(c.id)),
)
const allTagsSel = computed(
  () => tags.value.length > 0 && tags.value.every((t) => selTags.value.has(t.id)),
)

const editingCatId = ref<number | null>(null)
const editingTagId = ref<number | null>(null)
const editCatName = ref('')
const editTagName = ref('')
const editTagColor = ref('')

function toggleSelCat(id: number): void {
  const s = selCats.value
  s.has(id) ? s.delete(id) : s.add(id)
  selCats.value = new Set(s)
}
function toggleSelTag(id: number): void {
  const s = selTags.value
  s.has(id) ? s.delete(id) : s.add(id)
  selTags.value = new Set(s)
}
function toggleAllCats(): void {
  selCats.value = allCatsSel.value ? new Set() : new Set(categories.value.map((c) => c.id))
}
function toggleAllTags(): void {
  selTags.value = allTagsSel.value ? new Set() : new Set(tags.value.map((t) => t.id))
}

async function batchDeleteCats(): Promise<void> {
  const ids = [...selCats.value]
  if (ids.length === 0) return
  if (
    !(await confirmFn(
      `确定要删除选中的 ${ids.length} 个分类？所属照片的分类将被清除。`,
      '批量删除分类',
    ))
  )
    return
  for (const id of ids) await api(`/api/categories/${id}`, { method: 'DELETE' })
  selCats.value = new Set()
  refreshCategories()
}
async function batchDeleteTags(): Promise<void> {
  const ids = [...selTags.value]
  if (ids.length === 0) return
  if (!(await confirmFn(`确定要删除选中的 ${ids.length} 个标签？`, '批量删除标签'))) return
  for (const id of ids) await api(`/api/tags/${id}`, { method: 'DELETE' })
  selTags.value = new Set()
  refreshTags()
}

async function addTag(): Promise<void> {
  if (!newTagName.value.trim()) return
  const res = await api('/api/tags', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name: newTagName.value.trim(), color: newTagColor.value }),
  })
  if (res.ok) {
    newTagName.value = ''
    refreshTags()
  }
}
async function addCat(): Promise<void> {
  if (!newCatName.value.trim()) return
  const res = await api('/api/categories', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name: newCatName.value.trim() }),
  })
  if (res.ok) {
    newCatName.value = ''
    refreshCategories()
  }
}

async function deleteTag(id: number, name: string): Promise<void> {
  if (!(await confirmFn(`确定删除标签「${name}」？`, '删除标签'))) return
  await api(`/api/tags/${id}`, { method: 'DELETE' })
  refreshTags()
}
async function deleteCat(id: number, name: string): Promise<void> {
  if (!(await confirmFn(`确定删除分类「${name}」？`, '删除分类'))) return
  await api(`/api/categories/${id}`, { method: 'DELETE' })
  refreshCategories()
}

function startEditCat(c: Category): void {
  editingCatId.value = c.id
  editCatName.value = c.name
  nextTick(() => document.getElementById('cat-edit-' + c.id)?.focus())
}
function startEditTag(t: Tag): void {
  editingTagId.value = t.id
  editTagName.value = t.name
  editTagColor.value = t.color || '#00d4ff'
  nextTick(() => document.getElementById('tag-edit-' + t.id)?.focus())
}
function cancelEditCat(): void {
  editingCatId.value = null
}
function cancelEditTag(): void {
  editingTagId.value = null
}

async function saveEditCat(id: number): Promise<void> {
  const name = editCatName.value.trim()
  if (!name) {
    cancelEditCat()
    return
  }
  await api(`/api/categories/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name }),
  })
  editingCatId.value = null
  refreshCategories()
}
async function saveEditTag(id: number): Promise<void> {
  const name = editTagName.value.trim()
  if (!name) {
    cancelEditTag()
    return
  }
  await api(`/api/tags/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name, color: editTagColor.value }),
  })
  editingTagId.value = null
  refreshTags()
}

function toggleTag(id: number): void {
  const arr = [...props.selectedTagIds]
  const idx = arr.indexOf(id)
  if (idx > -1) arr.splice(idx, 1)
  else arr.push(id)
  emit('update:selectedTagIds', arr)
}
function toggleCat(id: number): void {
  const arr = [...props.selectedCategoryIds]
  const idx = arr.indexOf(id)
  if (idx > -1) arr.splice(idx, 1)
  else arr.push(id)
  emit('update:selectedCategoryIds', arr)
}

onMounted(() => {
  loadAll()
})
</script>

<template>
  <aside class="sidebar">
    <div class="filter-group">
      <h3>分类</h3>
      <div class="sidebar-toolbar">
        <label><input type="checkbox" :checked="allCatsSel" @change="toggleAllCats" /> 全选</label>
        <button v-if="selCats.size > 0" class="btn-del" @click="batchDeleteCats">
          删除 ({{ selCats.size }})
        </button>
      </div>
      <ul>
        <li
          v-for="c in categories"
          :key="c.id"
          :class="{ active: selectedCategoryIds.includes(c.id) }"
        >
          <input
            type="checkbox"
            class="item-check"
            :checked="selCats.has(c.id)"
            @click.stop
            @change="toggleSelCat(c.id)"
          />
          <span v-if="editingCatId !== c.id" class="item-name" @click="toggleCat(c.id)">{{
            c.name
          }}</span>
          <input
            v-else
            :id="'cat-edit-' + c.id"
            v-model="editCatName"
            class="item-edit-input"
            @keyup.enter="saveEditCat(c.id)"
            @keyup.escape="cancelEditCat"
            @blur="saveEditCat(c.id)"
          />
          <button
            v-if="editingCatId !== c.id"
            class="btn-item-edit"
            title="重命名"
            @click.stop="startEditCat(c)"
          >
            ✎
          </button>
          <button
            v-if="editingCatId !== c.id"
            class="btn-item-del"
            title="删除"
            @click.stop="deleteCat(c.id, c.name)"
          >
            <svg width="12" height="13" viewBox="0 0 12 13" fill="none">
              <path
                d="M1.5 3.5h9M4.5 3V2a.5.5 0 01.5-.5h2a.5.5 0 01.5.5v1m-4 3v4m3-4v4M2 3.5l.7 8a.5.5 0 00.5.5h5.6a.5.5 0 00.5-.5l.7-8H2z"
                stroke="currentColor"
                stroke-width="1.1"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
            </svg>
          </button>
        </li>
        <li v-if="categories.length === 0" class="empty">暂无分类</li>
      </ul>
      <div class="filter-input">
        <input v-model="newCatName" placeholder="新建分类" @keyup.enter="addCat" />
        <button type="button" class="btn-mini" @click="addCat">+</button>
      </div>
    </div>

    <div class="filter-group">
      <h3>标签</h3>
      <div class="sidebar-toolbar">
        <label><input type="checkbox" :checked="allTagsSel" @change="toggleAllTags" /> 全选</label>
        <button v-if="selTags.size > 0" class="btn-del" @click="batchDeleteTags">
          删除 ({{ selTags.size }})
        </button>
      </div>
      <ul>
        <li v-for="t in tags" :key="t.id" :class="{ active: selectedTagIds.includes(t.id) }">
          <input
            type="checkbox"
            class="item-check"
            :checked="selTags.has(t.id)"
            @click.stop
            @change="toggleSelTag(t.id)"
          />
          <span
            v-if="editingTagId !== t.id"
            class="tag-dot"
            :style="{ background: t.color }"
          ></span>
          <span v-if="editingTagId !== t.id" class="item-name" @click="toggleTag(t.id)">{{
            t.name
          }}</span>
          <template v-else>
            <input v-model="editTagColor" type="color" class="edit-color-pick" />
            <input
              :id="'tag-edit-' + t.id"
              v-model="editTagName"
              class="item-edit-input"
              @keyup.enter="saveEditTag(t.id)"
              @keyup.escape="cancelEditTag"
              @blur="saveEditTag(t.id)"
            />
          </template>
          <button
            v-if="editingTagId !== t.id"
            class="btn-item-edit"
            title="重命名"
            @click.stop="startEditTag(t)"
          >
            ✎
          </button>
          <button
            v-if="editingTagId !== t.id"
            class="btn-item-del"
            title="删除"
            @click.stop="deleteTag(t.id, t.name)"
          >
            <svg width="12" height="13" viewBox="0 0 12 13" fill="none">
              <path
                d="M1.5 3.5h9M4.5 3V2a.5.5 0 01.5-.5h2a.5.5 0 01.5.5v1m-4 3v4m3-4v4M2 3.5l.7 8a.5.5 0 00.5.5h5.6a.5.5 0 00.5-.5l.7-8H2z"
                stroke="currentColor"
                stroke-width="1.1"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
            </svg>
          </button>
        </li>
        <li v-if="tags.length === 0" class="empty">暂无标签</li>
      </ul>
      <div class="filter-input">
        <input v-model="newTagColor" type="color" class="color-pick" />
        <input v-model="newTagName" placeholder="新建标签" @keyup.enter="addTag" />
        <button type="button" class="btn-mini" @click="addTag">+</button>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  width: 220px;
  flex-shrink: 0;
  position: sticky;
  top: 24px;
  z-index: 2;
  pointer-events: auto;
}
.filter-group {
  background: var(--glass);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 16px;
  margin-bottom: 16px;
}
.filter-group h3 {
  font-size: 13px;
  text-transform: uppercase;
  letter-spacing: 1px;
  color: var(--text-dim);
  margin-bottom: 6px;
}
.sidebar-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 11px;
}
.sidebar-toolbar label {
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  color: var(--text-dim);
  user-select: none;
}
.sidebar-toolbar label input[type='checkbox'],
.item-check {
  appearance: none;
  -webkit-appearance: none;
  width: 14px;
  height: 14px;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid var(--border);
  border-radius: 3px;
  cursor: pointer;
  position: relative;
  transition: all 0.2s;
  flex-shrink: 0;
}
.sidebar-toolbar label input[type='checkbox']:checked,
.item-check:checked {
  background: var(--accent);
  border-color: var(--accent);
}
.sidebar-toolbar label input[type='checkbox']:checked::after,
.item-check:checked::after {
  content: '';
  position: absolute;
  left: 3px;
  top: 0px;
  width: 5px;
  height: 8px;
  border: solid #fff;
  border-width: 0 2px 2px 0;
  transform: rotate(45deg);
}
.item-check {
  opacity: 0;
}
.filter-group li:hover .item-check,
.filter-group li .item-check:checked {
  opacity: 1;
}
.item-edit-input {
  flex: 1;
  min-width: 0;
  padding: 3px 6px;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid var(--accent);
  border-radius: 4px;
  font-size: 12px;
  color: var(--text);
  outline: none;
}
.edit-color-pick {
  width: 20px;
  height: 20px;
  padding: 0;
  border: none;
  border-radius: 50%;
  cursor: pointer;
  flex-shrink: 0;
}
.filter-group ul {
  list-style: none;
  margin-bottom: 8px;
}
.filter-group li {
  padding: 6px 10px;
  border-radius: 8px;
  font-size: 13px;
  color: var(--text-dim);
  cursor: pointer;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  gap: 6px;
}
.filter-group li:hover {
  background: var(--glass-hover);
  color: var(--text);
}
.filter-group li.active {
  background: rgba(0, 212, 255, 0.12);
  color: var(--accent);
}
.filter-group li.empty {
  cursor: default;
  color: var(--text-dim);
  opacity: 0.5;
}
.tag-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.item-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
}
.btn-item-edit,
.btn-item-del {
  flex-shrink: 0;
  width: 22px;
  height: 22px;
  padding: 0;
  border-radius: 50%;
  font-size: 14px;
  line-height: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  color: var(--text-dim);
  border: none;
  cursor: pointer;
  opacity: 0;
  transition:
    opacity 0.2s,
    color 0.2s;
}
.filter-group li:hover .btn-item-edit,
.filter-group li:hover .btn-item-del {
  opacity: 1;
}
.btn-item-edit:hover {
  color: var(--accent);
}
.btn-item-del:hover {
  color: var(--accent3);
}

@media (max-width: 768px) {
  .item-check {
    opacity: 0.6;
  }
  .btn-item-edit,
  .btn-item-del {
    opacity: 0.6;
  }
  .sidebar {
    position: fixed;
    top: 0;
    left: 0;
    width: 260px;
    height: 100vh;
    z-index: 200;
    padding: 16px;
    padding-top: 60px;
    background: rgba(10, 10, 20, 0.97);
    backdrop-filter: blur(20px);
    -webkit-backdrop-filter: blur(20px);
    border-right: 1px solid var(--border);
    transform: translateX(-100%);
    transition: transform 0.3s ease;
    overflow-y: auto;
  }
  .sidebar.open {
    transform: translateX(0);
  }
}
</style>
