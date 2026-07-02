<script setup>
import { ref } from 'vue';
import gsap from 'gsap';
import { webpUrl } from '../webp.js';
import { useToastStore } from '../stores/toast.js';
import { usePhotoStore } from '../stores/photo.js';
import { useConfirm } from '../useConfirm.js';
import ImageEditor from './ImageEditor.vue';

const confirmFn = useConfirm();
const toast = useToastStore();
const photoStore = usePhotoStore();
const props = defineProps({ photo: Object, selected: Boolean });
const emit = defineEmits(['view', 'edit', 'delete', 'toggle-select']);

function tokenParam() {
  const t = localStorage.getItem('jwt_token') || localStorage.getItem('token');
  let q = t ? `?token=${t}` : '';
  const v = props.photo.fileSize ? `v=${props.photo.fileSize}` : '';
  if (v) q += q ? `&${v}` : `?${v}`;
  return q;
}

const cardRef = ref(null);
const editorVisible = ref(false);
const editorSrc = ref('');

function openImageEditor() {
  editorSrc.value = `${webpUrl(props.photo.id)}${tokenParam()}`;
  editorVisible.value = true;
}

async function onImageEditDone({ params }) {
  editorVisible.value = false;
  try {
    const res = await fetch(`/api/photos/${props.photo.id}/transform`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${localStorage.getItem('jwt_token') || localStorage.getItem('token')}`
      },
      body: JSON.stringify(params)
    });
    if (res.ok) {
      toast.success('图片编辑完成');
      photoStore.resetAndReload();
    } else {
      toast.error('编辑失败');
    }
  } catch (e) {
    toast.error('编辑失败');
  }
}

function formatSize(bytes) {
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB';
  return (bytes / 1048576).toFixed(1) + ' MB';
}

function tiltOn(e) {
  const card = cardRef.value;
  const rect = card.getBoundingClientRect();
  const x = (e.clientX - rect.left) / rect.width - 0.5;
  const y = (e.clientY - rect.top) / rect.height - 0.5;
  gsap.to(card, { rotateY: x * 8, rotateX: -y * 8, duration: 0.2, overwrite: 'auto', ease: 'power1.out' });
}

function tiltOff() {
  gsap.to(cardRef.value, { rotateY: 0, rotateX: 0, duration: 0.4, overwrite: 'auto', ease: 'power1.out' });
}

async function onDelete() {
  if (!await confirmFn('确定要删除这张照片吗？', '删除照片')) return;
  await gsap.to(cardRef.value, {
    scale: 0.7,
    opacity: 0,
    rotateX: 40,
    y: -30,
    filter: 'blur(8px)',
    duration: 0.4,
    ease: 'power1.in'
  });
  emit('delete', props.photo.id);
}
</script>

<template>
  <div
    ref="cardRef"
    class="photo-card"
    :class="{ selected: selected }"
    :data-insert="$attrs['data-insert']"
    @mousemove="tiltOn"
    @mouseleave="tiltOff"
  >
    <div class="card-check" @click.stop="emit('toggle-select', photo.id)">
      <span class="check-mark" v-if="selected">✓</span>
    </div>
    <div class="photo-thumb" @click="$emit('view')">
      <img :src="`/api/photos/${photo.id}/thumbnail${tokenParam()}`" :alt="photo.name" loading="lazy" />
      <div class="photo-overlay">
        <button class="btn-view" @click.stop="$emit('view')">查看</button>
      </div>
    </div>
    <div class="photo-body">
      <h4 class="photo-name">{{ photo.name }}</h4>
      <p class="photo-meta">{{ formatSize(photo.fileSize) }}<span v-if="photo.category"> · {{ photo.category.name }}</span></p>
      <div v-if="photo.tags && photo.tags.length" class="card-tags">
        <span v-for="t in photo.tags" :key="t.id" class="card-tag" :style="{ background: t.color }">{{ t.name }}</span>
      </div>
      <div class="photo-actions">
        <button class="btn-edit" @click="$emit('edit')">编辑</button>
        <button class="btn-img-edit" @click="openImageEditor" title="编辑图片">✂</button>
        <button class="btn-del" @click="onDelete">删除</button>
      </div>
    </div>
  </div>
  <ImageEditor :src="editorSrc" :visible="editorVisible" @close="editorVisible = false" @done="onImageEditDone" />
</template>
