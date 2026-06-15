<script setup>
import { ref, onMounted } from 'vue';
import gsap from 'gsap';
import { ScrollToPlugin } from 'gsap/ScrollToPlugin';
gsap.registerPlugin(ScrollToPlugin);
import AppHeader from './components/AppHeader.vue';
import UploadCard from './components/UploadCard.vue';
import PhotoGallery from './components/PhotoGallery.vue';
import ViewModal from './components/ViewModal.vue';
import EditModal from './components/EditModal.vue';
import FilterSidebar from './components/FilterSidebar.vue';

const photos = ref([]);
const selectedTagIds = ref([]);
const selectedCategoryIds = ref([]);
const viewPhoto = ref(null);
const editPhoto = ref(null);
const page = ref(0);
const hasMore = ref(true);
const loading = ref(false);
const totalCount = ref(0);
const showBackTop = ref(false);
const sidebarOpen = ref(false);
let requestId = 0;

function scrollToTop() {
  gsap.to(window, { scrollTo: 0, duration: 0.6, ease: 'power3.out' });
}

async function loadMore() {
  if (loading.value || !hasMore.value) return;
  loading.value = true;
  const myId = ++requestId;
  try {
    let url = `/api/photos?page=${page.value}&size=20`;
    selectedTagIds.value.forEach(id => { url += `&tagIds=${id}`; });
    selectedCategoryIds.value.forEach(id => { url += `&categoryIds=${id}`; });
    const res = await fetch(url);
    if (myId !== requestId) return; // 有更新的请求，丢弃旧结果
    if (!res.ok) {
      console.error('加载照片失败:', res.status);
      return;
    }
    const json = await res.json();
    const { content, totalPages, totalElements } = json.data;
    if (content && content.length) photos.value.push(...content);
    page.value++;
    hasMore.value = page.value < totalPages;
    totalCount.value = totalElements;
  } catch (err) {
    console.error('加载照片异常:', err);
  } finally {
    if (myId === requestId) loading.value = false;
  }
}

function resetAndReload() {
  photos.value = [];
  page.value = 0;
  hasMore.value = true;
  loading.value = false;
  loadMore();
}

function onView(photo)   { viewPhoto.value = photo; }
function onEdit(photo)   { editPhoto.value = photo; }
function onUploaded()    { resetAndReload(); }
function onSaved()       { editPhoto.value = null; resetAndReload(); }

async function extractErrorMessage(res) {
  try {
    const data = await res.json();
    return data.message || `请求失败（${res.status}）`;
  } catch {
    return `服务器返回异常（${res.status}），请稍后重试`;
  }
}

async function onDelete(id) {
  try {
    const res = await fetch(`/api/photos/${id}`, { method: 'DELETE' });
    if (!res.ok) {
      const msg = await extractErrorMessage(res);
      throw new Error(msg);
    }
    photos.value = photos.value.filter(p => p.id !== id);
    totalCount.value--;
  } catch (err) {
    alert(err.message);
  }
}

async function onBatchDelete(ids) {
  try {
    const res = await fetch('/api/photos/batch', {
      method: 'DELETE',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(ids)
    });
    if (!res.ok) {
      const msg = await extractErrorMessage(res);
      throw new Error(msg);
    }
    const idSet = new Set(ids);
    photos.value = photos.value.filter(p => !idSet.has(p.id));
    totalCount.value -= ids.length;
  } catch (err) {
    alert(err.message);
  }
}

// 背景、光标、入场动画（首次加载时执行）
let firstLoad = true;
function initEffects() {
  if (!firstLoad) return;
  firstLoad = false;

  const orbsHTML = `
    <div class="bg-orbs">
      <div class="orb orb-1"></div>
      <div class="orb orb-2"></div>
      <div class="orb orb-3"></div>
    </div>`;
  document.body.insertAdjacentHTML('afterbegin', orbsHTML);

  gsap.to('.orb-1', {
    keyframes: [
      { xPercent: -10, yPercent: -5, scale: 1 },
      { xPercent: 10, yPercent: 5, scale: 1.15 },
      { xPercent: -5, yPercent: 10, scale: 0.9 },
      { xPercent: 5, yPercent: -5, scale: 1.05 },
      { xPercent: -10, yPercent: -5, scale: 1 },
    ],
    duration: 12, repeat: -1, ease: 'sine.inOut'
  });
  gsap.to('.orb-2', {
    keyframes: [
      { xPercent: 5, yPercent: 5, scale: 1 },
      { xPercent: -8, yPercent: -8, scale: 0.85 },
      { xPercent: 3, yPercent: 3, scale: 1.1 },
      { xPercent: -5, yPercent: 5, scale: 0.95 },
      { xPercent: 5, yPercent: 5, scale: 1 },
    ],
    duration: 15, repeat: -1, ease: 'sine.inOut'
  });
  gsap.to('.orb-3', {
    keyframes: [
      { xPercent: 3, yPercent: -5, scale: 1 },
      { xPercent: -6, yPercent: 3, scale: 1.1 },
      { xPercent: 8, yPercent: -8, scale: 0.85 },
      { xPercent: -3, yPercent: 5, scale: 1.05 },
      { xPercent: 3, yPercent: -5, scale: 1 },
    ],
    duration: 10, repeat: -1, ease: 'sine.inOut'
  });

  const trails = [];
  let mouseX = 0, mouseY = 0;
  for (let i = 0; i < 12; i++) {
    const dot = document.createElement('div');
    dot.className = 'cursor-trail';
    dot.style.opacity = (1 - i / 12) * 0.5;
    dot.style.transform = `translate(-50%, -50%) scale(${1 - i / 12})`;
    document.body.appendChild(dot);
    trails.push({ el: dot, x: 0, y: 0 });
  }
  document.addEventListener('mousemove', e => { mouseX = e.clientX; mouseY = e.clientY; });
  (function tick() {
    let tx = mouseX, ty = mouseY;
    for (let i = 0; i < trails.length; i++) {
      const t = trails[i];
      t.x += (tx - t.x) * (0.35 - i * 0.02);
      t.y += (ty - t.y) * (0.35 - i * 0.02);
      t.el.style.left = t.x + 'px';
      t.el.style.top = t.y + 'px';
      tx = t.x; ty = t.y;
    }
    requestAnimationFrame(tick);
  })();

  document.addEventListener('click', e => {
    const btn = e.target.closest('button');
    if (!btn) return;
    const ripple = document.createElement('span');
    ripple.className = 'ripple';
    const rect = btn.getBoundingClientRect();
    const size = Math.max(rect.width, rect.height);
    ripple.style.width = ripple.style.height = size + 'px';
    ripple.style.left = (e.clientX - rect.left - size / 2) + 'px';
    ripple.style.top = (e.clientY - rect.top - size / 2) + 'px';
    btn.appendChild(ripple);
    ripple.addEventListener('animationend', () => ripple.remove());
  });

  gsap.fromTo('.header h1',
    { y: -60, opacity: 0 },
    { y: 0, opacity: 1, duration: 1, ease: 'expo.out' }
  );
  gsap.fromTo('.upload-card',
    { y: 40, opacity: 0 },
    { y: 0, opacity: 1, duration: 0.7, delay: 0.3, ease: 'expo.out' }
  );
  gsap.fromTo('.gallery-section h2',
    { y: 30, opacity: 0 },
    { y: 0, opacity: 1, duration: 0.6, delay: 0.5, ease: 'power1.out' }
  );
}

onMounted(() => {
  initEffects();
  document.addEventListener('keydown', e => {
    if (e.key === 'Escape') {
      viewPhoto.value = null;
      editPhoto.value = null;
    }
  });
  window.addEventListener('scroll', () => {
    showBackTop.value = window.scrollY > 400;
  });
  loadMore();
});
</script>

<template>
  <AppHeader />
  <main class="page">
    <button class="sidebar-toggle" @click="sidebarOpen = !sidebarOpen">
      {{ sidebarOpen ? '✕' : '☰' }}
    </button>
    <div v-if="sidebarOpen" class="sidebar-backdrop" @click="sidebarOpen = false"></div>
    <FilterSidebar
      :class="{ open: sidebarOpen }"
      :selected-tag-ids="selectedTagIds"
      :selected-category-ids="selectedCategoryIds"
      @update:selected-tag-ids="selectedTagIds = $event; resetAndReload(); sidebarOpen = false"
      @update:selected-category-ids="selectedCategoryIds = $event; resetAndReload(); sidebarOpen = false"
    />
    <div class="main-content">
      <UploadCard @uploaded="onUploaded" />
      <PhotoGallery
        :photos="photos"
        :loading="loading"
        :has-more="hasMore"
        :total-count="totalCount"
        @view="onView"
        @edit="onEdit"
        @delete="onDelete"
        @load-more="loadMore"
        @batch-delete="onBatchDelete"
      />
    </div>
  </main>
  <button v-show="showBackTop" class="back-top" @click="scrollToTop" title="回到顶部">
    ↑
  </button>
  <ViewModal v-if="viewPhoto" :photo="viewPhoto" @close="viewPhoto = null" />
  <EditModal v-if="editPhoto" :photo="editPhoto" @close="editPhoto = null" @saved="onSaved" />
</template>
