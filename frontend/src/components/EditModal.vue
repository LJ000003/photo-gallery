<script setup>
import { ref, onMounted } from 'vue';
import gsap from 'gsap';
import { useStore } from '../store.js';
import { useToastStore } from '../stores/toast.js';
import { api } from '../api.js';

const toast = useToastStore();
const props = defineProps({ photo: Object });
const emit = defineEmits(['close', 'saved']);

const { tags: allTags, categories: allCats, albums: allAlbums, loadAll, refreshTags, refreshCategories, refreshAlbums } = useStore();
const editName = ref('');
const editDesc = ref('');
const selectedTagIds = ref([]);
const selectedCatId = ref(null);
const selectedAlbumIds = ref([]);
const newTagName = ref('');
const newTagColor = ref('#00d4ff');
const newCatName = ref('');
const newAlbumName = ref('');

onMounted(() => {
  editName.value = props.photo.name;
  editDesc.value = props.photo.description || '';
  selectedTagIds.value = (props.photo.tags || []).map(t => t.id);
  selectedCatId.value = props.photo.category?.id || null;
  selectedAlbumIds.value = (props.photo.albums || []).map(a => a.id);
  loadAll();

  const content = document.querySelector('#editModal .modal-content');
  const backdrop = document.querySelector('#editModal .modal-backdrop');
  gsap.fromTo(content,
    { scale: 0.85, opacity: 0 },
    { scale: 1, opacity: 1, duration: 0.35, ease: 'expo.out' }
  );
  gsap.fromTo(backdrop,
    { opacity: 0 },
    { opacity: 1, duration: 0.35, ease: 'none' }
  );
});

function toggleTag(id) {
  const idx = selectedTagIds.value.indexOf(id);
  if (idx > -1) selectedTagIds.value.splice(idx, 1);
  else selectedTagIds.value.push(id);
}

async function addTag() {
  if (!newTagName.value.trim()) return;
  const res = await api('/api/tags', {
    method: 'POST',
    body: JSON.stringify({ name: newTagName.value.trim(), color: newTagColor.value })
  });
  if (res.ok) {
    const json = await res.json();
    selectedTagIds.value.push(json.data.id);
    newTagName.value = '';
    refreshTags();
  }
}

function toggleAlbum(id) {
  const idx = selectedAlbumIds.value.indexOf(id);
  if (idx > -1) selectedAlbumIds.value.splice(idx, 1);
  else selectedAlbumIds.value.push(id);
}

async function addCat() {
  if (!newCatName.value.trim()) return;
  const res = await api('/api/categories', {
    method: 'POST',
    body: JSON.stringify({ name: newCatName.value.trim() })
  });
  if (res.ok) {
    const json = await res.json();
    selectedCatId.value = json.data.id;
    newCatName.value = '';
    refreshCategories();
  }
}

async function addAlbum() {
  if (!newAlbumName.value.trim()) return;
  const res = await api('/api/albums', {
    method: 'POST',
    body: JSON.stringify({ name: newAlbumName.value.trim() })
  });
  if (res.ok) {
    const json = await res.json();
    selectedAlbumIds.value.push(json.data.id);
    newAlbumName.value = '';
    refreshAlbums();
  }
}

function onClose() {
  const content = document.querySelector('#editModal .modal-content');
  const backdrop = document.querySelector('#editModal .modal-backdrop');
  gsap.to(content, {
    scale: 0.9, opacity: 0, duration: 0.2, ease: 'power1.in',
    onComplete: () => emit('close')
  });
  gsap.to(backdrop, { opacity: 0, duration: 0.2, ease: 'none' });
}

async function extractErrorMessage(res) {
  try {
    const data = await res.json();
    return data.message || `请求失败（${res.status}）`;
  } catch {
    return `服务器返回异常（${res.status}），请稍后重试`;
  }
}

async function onSubmit() {
  if (!editName.value.trim()) { toast.error('请输入照片名称'); return; }

  try {
    const body = {
      name: editName.value.trim(),
      description: editDesc.value.trim(),
      tagIds: selectedTagIds.value,
      categoryId: selectedCatId.value,
      albumIds: selectedAlbumIds.value
    };

    const res = await api(`/api/photos/${props.photo.id}`, {
      method: 'PUT',
      body: JSON.stringify(body)
    });
    if (!res.ok) {
      const msg = await extractErrorMessage(res);
      throw new Error(msg);
    }
    emit('saved');
  } catch (err) {
    toast.error(err.message);
  }
}
</script>

<template>
  <div id="editModal" class="modal">
    <div class="modal-backdrop" @click="onClose"></div>
    <div class="modal-content modal-small">
      <button class="modal-close" @click="onClose">&times;</button>
      <h2>编辑照片信息</h2>
      <form @submit.prevent="onSubmit">
        <label>名称</label>
        <input v-model="editName" type="text" required />
        <label>描述</label>
        <input v-model="editDesc" type="text" maxlength="500" />
        <label>分类</label>
        <select v-model="selectedCatId" class="mini-select" style="width:100%">
          <option :value="null">无分类</option>
          <option v-for="c in allCats" :key="c.id" :value="c.id">{{ c.name }}</option>
        </select>
        <div class="filter-input" style="margin-top:6px">
          <input v-model="newCatName" placeholder="新建分类" @keyup.enter="addCat" />
          <button type="button" class="btn-mini" @click="addCat">+</button>
        </div>
        <label>标签</label>
        <div class="tag-chips">
          <button v-for="t in allTags" :key="t.id" type="button"
            class="tag-chip"
            :class="{ on: selectedTagIds.includes(t.id) }"
            :style="selectedTagIds.includes(t.id) ? { background: t.color, borderColor: t.color } : {}"
            @click="toggleTag(t.id)">
            {{ t.name }}
          </button>
        </div>
        <div class="filter-input" style="margin-top:6px">
          <input type="color" v-model="newTagColor" class="color-pick" />
          <input v-model="newTagName" placeholder="新建标签" @keyup.enter="addTag" />
          <button type="button" class="btn-mini" @click="addTag">+</button>
        </div>
        <label>相册</label>
        <div class="tag-chips">
          <button v-for="a in allAlbums" :key="a.id" type="button"
            class="tag-chip album-chip"
            :class="{ on: selectedAlbumIds.includes(a.id) }"
            @click="toggleAlbum(a.id)">
            {{ a.name }}
          </button>
        </div>
        <div class="filter-input" style="margin-top:6px">
          <input v-model="newAlbumName" placeholder="新建相册" @keyup.enter="addAlbum" />
          <button type="button" class="btn-mini" @click="addAlbum">+</button>
        </div>
        <button type="submit">保存</button>
      </form>
    </div>
  </div>
</template>
