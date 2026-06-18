<script setup>
import { ref, computed, onMounted, nextTick } from 'vue';
import { useStore } from '../store.js';
import { useConfirm } from '../useConfirm.js';
import { api } from '../api.js';

const confirmFn = useConfirm();
const props = defineProps({
  selectedTagIds: Array,
  selectedCategoryIds: Array
});
const emit = defineEmits(['update:selectedTagIds', 'update:selectedCategoryIds']);

const { tags, categories, loadAll, refreshTags, refreshCategories } = useStore();
const newTagName = ref('');
const newTagColor = ref('#00d4ff');
const newCatName = ref('');

const selCats = ref(new Set());
const selTags = ref(new Set());

const allCatsSel = computed(() => categories.value.length > 0 && categories.value.every(c => selCats.value.has(c.id)));
const allTagsSel = computed(() => tags.value.length > 0 && tags.value.every(t => selTags.value.has(t.id)));

// 行内编辑状态
const editingCatId = ref(null);
const editingTagId = ref(null);
const editCatName = ref('');
const editTagName = ref('');
const editTagColor = ref('');

// === 选择逻辑 ===
function toggleSelCat(id) { const s = selCats.value; s.has(id) ? s.delete(id) : s.add(id); selCats.value = new Set(s); }
function toggleSelTag(id) { const s = selTags.value; s.has(id) ? s.delete(id) : s.add(id); selTags.value = new Set(s); }
function toggleAllCats() { selCats.value = allCatsSel.value ? new Set() : new Set(categories.value.map(c => c.id)); }
function toggleAllTags() { selTags.value = allTagsSel.value ? new Set() : new Set(tags.value.map(t => t.id)); }

// === 批量删除 ===
async function batchDeleteCats() {
  const ids = [...selCats.value];
  if (ids.length === 0) return;
  if (!await confirmFn(`确定要删除选中的 ${ids.length} 个分类？所属照片的分类将被清除。`, '批量删除分类')) return;
  for (const id of ids) await api(`/api/categories/${id}`, { method: 'DELETE' });
  selCats.value = new Set();
  refreshCategories();
}
async function batchDeleteTags() {
  const ids = [...selTags.value];
  if (ids.length === 0) return;
  if (!await confirmFn(`确定要删除选中的 ${ids.length} 个标签？`, '批量删除标签')) return;
  for (const id of ids) await api(`/api/tags/${id}`, { method: 'DELETE' });
  selTags.value = new Set();
  refreshTags();
}

// === 新建 ===
async function addTag() {
  if (!newTagName.value.trim()) return;
  const res = await api('/api/tags', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name: newTagName.value.trim(), color: newTagColor.value })
  });
  if (res.ok) { newTagName.value = ''; refreshTags(); }
}
async function addCat() {
  if (!newCatName.value.trim()) return;
  const res = await api('/api/categories', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name: newCatName.value.trim() })
  });
  if (res.ok) { newCatName.value = ''; refreshCategories(); }
}

// === 删除 ===
async function deleteTag(id, name) {
  if (!await confirmFn(`确定删除标签「${name}」？`, '删除标签')) return;
  await api(`/api/tags/${id}`, { method: 'DELETE' });
  refreshTags();
}
async function deleteCat(id, name) {
  if (!await confirmFn(`确定删除分类「${name}」？`, '删除分类')) return;
  await api(`/api/categories/${id}`, { method: 'DELETE' });
  refreshCategories();
}

// === 行内编辑 ===
function startEditCat(c) {
  editingCatId.value = c.id;
  editCatName.value = c.name;
  nextTick(() => document.getElementById('cat-edit-' + c.id)?.focus());
}
function startEditTag(t) {
  editingTagId.value = t.id;
  editTagName.value = t.name;
  editTagColor.value = t.color || '#00d4ff';
  nextTick(() => document.getElementById('tag-edit-' + t.id)?.focus());
}
function cancelEditCat() { editingCatId.value = null; }
function cancelEditTag() { editingTagId.value = null; }

async function saveEditCat(id) {
  const name = editCatName.value.trim();
  if (!name) { cancelEditCat(); return; }
  await api(`/api/categories/${id}`, {
    method: 'PUT', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name })
  });
  editingCatId.value = null;
  refreshCategories();
}
async function saveEditTag(id) {
  const name = editTagName.value.trim();
  if (!name) { cancelEditTag(); return; }
  await api(`/api/tags/${id}`, {
    method: 'PUT', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name, color: editTagColor.value })
  });
  editingTagId.value = null;
  refreshTags();
}

// === 筛选 ===
function toggleTag(id) {
  const arr = [...props.selectedTagIds];
  const idx = arr.indexOf(id);
  if (idx > -1) arr.splice(idx, 1);
  else arr.push(id);
  emit('update:selectedTagIds', arr);
}
function toggleCat(id) {
  const arr = [...props.selectedCategoryIds];
  const idx = arr.indexOf(id);
  if (idx > -1) arr.splice(idx, 1);
  else arr.push(id);
  emit('update:selectedCategoryIds', arr);
}

onMounted(() => { loadAll(); });
</script>

<template>
  <aside class="sidebar">
    <!-- 分类 -->
    <div class="filter-group">
      <h3>分类</h3>
      <div class="sidebar-toolbar">
        <label><input type="checkbox" :checked="allCatsSel" @change="toggleAllCats" /> 全选</label>
        <button v-if="selCats.size > 0" class="btn-del" @click="batchDeleteCats">删除 ({{ selCats.size }})</button>
      </div>
      <ul>
        <li v-for="c in categories" :key="c.id"
            :class="{ active: selectedCategoryIds.includes(c.id) }">
          <input type="checkbox" class="item-check" :checked="selCats.has(c.id)" @click.stop @change="toggleSelCat(c.id)" />
          <span v-if="editingCatId !== c.id" class="item-name" @click="toggleCat(c.id)">{{ c.name }}</span>
          <input v-else :id="'cat-edit-'+c.id" v-model="editCatName"
            class="item-edit-input" @keyup.enter="saveEditCat(c.id)"
            @keyup.escape="cancelEditCat" @blur="saveEditCat(c.id)" />
          <button v-if="editingCatId !== c.id" class="btn-item-edit" @click.stop="startEditCat(c)" title="重命名">✎</button>
          <button v-if="editingCatId !== c.id" class="btn-item-del" @click.stop="deleteCat(c.id, c.name)" title="删除">
            <svg width="12" height="13" viewBox="0 0 12 13" fill="none"><path d="M1.5 3.5h9M4.5 3V2a.5.5 0 01.5-.5h2a.5.5 0 01.5.5v1m-4 3v4m3-4v4M2 3.5l.7 8a.5.5 0 00.5.5h5.6a.5.5 0 00.5-.5l.7-8H2z" stroke="currentColor" stroke-width="1.1" stroke-linecap="round" stroke-linejoin="round"/></svg>
          </button>
        </li>
        <li v-if="categories.length === 0" class="empty">暂无分类</li>
      </ul>
      <div class="filter-input">
        <input v-model="newCatName" placeholder="新建分类" @keyup.enter="addCat" />
        <button type="button" class="btn-mini" @click="addCat">+</button>
      </div>
    </div>

    <!-- 标签 -->
    <div class="filter-group">
      <h3>标签</h3>
      <div class="sidebar-toolbar">
        <label><input type="checkbox" :checked="allTagsSel" @change="toggleAllTags" /> 全选</label>
        <button v-if="selTags.size > 0" class="btn-del" @click="batchDeleteTags">删除 ({{ selTags.size }})</button>
      </div>
      <ul>
        <li v-for="t in tags" :key="t.id"
            :class="{ active: selectedTagIds.includes(t.id) }">
          <input type="checkbox" class="item-check" :checked="selTags.has(t.id)" @click.stop @change="toggleSelTag(t.id)" />
          <span v-if="editingTagId !== t.id" class="tag-dot" :style="{ background: t.color }"></span>
          <span v-if="editingTagId !== t.id" class="item-name" @click="toggleTag(t.id)">{{ t.name }}</span>
          <template v-else>
            <input type="color" v-model="editTagColor" class="edit-color-pick" />
            <input :id="'tag-edit-'+t.id" v-model="editTagName"
              class="item-edit-input" @keyup.enter="saveEditTag(t.id)"
              @keyup.escape="cancelEditTag" @blur="saveEditTag(t.id)" />
          </template>
          <button v-if="editingTagId !== t.id" class="btn-item-edit" @click.stop="startEditTag(t)" title="重命名">✎</button>
          <button v-if="editingTagId !== t.id" class="btn-item-del" @click.stop="deleteTag(t.id, t.name)" title="删除">
            <svg width="12" height="13" viewBox="0 0 12 13" fill="none"><path d="M1.5 3.5h9M4.5 3V2a.5.5 0 01.5-.5h2a.5.5 0 01.5.5v1m-4 3v4m3-4v4M2 3.5l.7 8a.5.5 0 00.5.5h5.6a.5.5 0 00.5-.5l.7-8H2z" stroke="currentColor" stroke-width="1.1" stroke-linecap="round" stroke-linejoin="round"/></svg>
          </button>
        </li>
        <li v-if="tags.length === 0" class="empty">暂无标签</li>
      </ul>
      <div class="filter-input">
        <input type="color" v-model="newTagColor" class="color-pick" />
        <input v-model="newTagName" placeholder="新建标签" @keyup.enter="addTag" />
        <button type="button" class="btn-mini" @click="addTag">+</button>
      </div>
    </div>
  </aside>
</template>
