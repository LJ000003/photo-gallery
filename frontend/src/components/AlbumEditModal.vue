<script setup>
import { ref, onMounted } from 'vue';
import gsap from 'gsap';
import { api } from '../api.js';
import { useToastStore } from '../stores/toast.js';

const toast = useToastStore();
const props = defineProps({ album: Object });
const emit = defineEmits(['close', 'saved', 'deleted']);

const name = ref('');
const description = ref('');
const allPhotos = ref([]);
const selectedPhotoIds = ref(new Set());
const submitting = ref(false);

onMounted(async () => {
  name.value = props.album.name || '';
  description.value = props.album.description || '';

  try {
    const res = await api('/api/photos?size=1000');
    const data = await res.json();
    allPhotos.value = data.data?.content || [];
    selectedPhotoIds.value = new Set(
      allPhotos.value.filter(p => p.albums && p.albums.some(a => a.id === props.album.id)).map(p => p.id)
    );
  } catch (e) {
    console.error('Failed to load photos for album editor', e);
  }

  const el = document.querySelector('#albumEditModal .modal-content');
  if (el) gsap.fromTo(el, { scale: 0.85, opacity: 0 }, { scale: 1, opacity: 1, duration: 0.35, ease: 'expo.out' });
});

function togglePhoto(id) {
  const s = new Set(selectedPhotoIds.value);
  s.has(id) ? s.delete(id) : s.add(id);
  selectedPhotoIds.value = s;
}

function onClose() {
  emit('close');
}

async function onSubmit() {
  if (!name.value.trim()) { toast.error('请输入相册名称'); return; }
  submitting.value = true;
  try {
    const body = { name: name.value.trim(), description: description.value.trim() };
    if (props.album.id) {
      const res = await api(`/api/albums/${props.album.id}`, { method: 'PUT', body: JSON.stringify({ ...body, photoIds: [...selectedPhotoIds.value] }) });
      if (!res.ok) throw new Error('保存失败');
    } else {
      const res = await api('/api/albums', { method: 'POST', body: JSON.stringify({ ...body, photoIds: [...selectedPhotoIds.value] }) });
      if (!res.ok) throw new Error('创建失败');
    }
    emit('saved');
  } catch (err) {
    toast.error(err.message);
  } finally {
    submitting.value = false;
  }
}

async function onDelete() {
  if (!confirm('确定删除相册「' + props.album.name + '」？')) return;
  try {
    const res = await api(`/api/albums/${props.album.id}`, { method: 'DELETE' });
    if (!res.ok) throw new Error('删除失败');
    emit('deleted');
    toast.success('已删除');
  } catch (err) {
    toast.error(err.message);
  }
}

function tokenParam() {
  const t = localStorage.getItem('jwt_token');
  return t ? `?token=${t}` : '';
}
</script>

<template>
  <div id="albumEditModal" class="modal">
    <div class="modal-backdrop" @click="onClose"></div>
    <div class="modal-content modal-small">
      <button class="modal-close" @click="onClose">&times;</button>
      <h2>编辑相册</h2>
      <form @submit.prevent="onSubmit">
        <label>名称</label>
        <input v-model="name" type="text" required />
        <label>描述</label>
        <input v-model="description" type="text" maxlength="500" />
        <label>已选照片 ({{ selectedPhotoIds.size }})</label>
        <div class="album-photo-picker">
          <div v-for="p in allPhotos" :key="p.id"
            class="album-photo-item"
            :class="{ selected: selectedPhotoIds.has(p.id) }"
            @click="togglePhoto(p.id)">
            <img :src="`/api/photos/${p.id}/thumbnail${tokenParam()}`" :alt="p.name" loading="lazy" />
            <span class="album-photo-name">{{ p.name }}</span>
            <span class="album-photo-check">{{ selectedPhotoIds.has(p.id) ? '✓' : '' }}</span>
          </div>
          <div v-if="allPhotos.length === 0" class="album-photo-empty">还没有照片</div>
        </div>
        <div class="album-edit-actions">
          <button type="submit" :disabled="submitting">保存</button>
          <button v-if="album.id" type="button" class="btn-del" @click="onDelete">删除相册</button>
        </div>
      </form>
    </div>
  </div>
</template>
