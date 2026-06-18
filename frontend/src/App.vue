<script setup>
import { onMounted, ref } from 'vue';
import gsap from 'gsap';
import { ScrollToPlugin } from 'gsap/ScrollToPlugin';
gsap.registerPlugin(ScrollToPlugin);

import AppHeader from './components/AppHeader.vue';
import UploadCard from './components/UploadCard.vue';
import PhotoGallery from './components/PhotoGallery.vue';
import ViewModal from './components/ViewModal.vue';
import EditModal from './components/EditModal.vue';
import FilterSidebar from './components/FilterSidebar.vue';
import KonamiGate from './components/KonamiGate.vue';
import ShareViewer from './components/ShareViewer.vue';
import ToastProvider from './components/ToastProvider.vue';

import { usePhotoStore } from './stores/photo.js';
import { useUiStore } from './stores/ui.js';
import { useToastStore } from './stores/toast.js';
import { api, requestToken } from './api.js';

const photo = usePhotoStore();
const ui = useUiStore();
const toast = useToastStore();

const isShare = window.location.pathname.startsWith('/share/');

function scrollToTop() {
  if ('ontouchstart' in window) {
    window.scrollTo({ top: 0, behavior: 'smooth' });
  } else {
    gsap.to(window, { scrollTo: 0, duration: 0.6, ease: 'power3.out' });
  }
}

function onUploaded() { photo.resetAndReload(); }
function onSaved() { ui.editPhoto = null; photo.resetAndReload(); }

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
    const res = await api(`/api/photos/${id}`, { method: 'DELETE' });
    if (!res.ok) throw new Error(await extractErrorMessage(res));
    photo.removePhoto(id);
    toast.success('删除成功');
  } catch (err) {
    toast.error(err.message);
  }
}

async function onBatchDelete(ids) {
  try {
    const res = await api('/api/photos/batch', {
      method: 'DELETE',
      body: JSON.stringify(ids)
    });
    if (!res.ok) throw new Error(await extractErrorMessage(res));
    photo.removePhotos(ids);
    toast.success(`已删除 ${ids.length} 张照片`);
  } catch (err) {
    toast.error(err.message);
  }
}

// 分享链接
const shareModal = ref(null);
const shareUrl = ref('');
const shareLoading = ref(false);

async function onGenerateShare(ids) {
  if (ids.length === 0) return;
  shareLoading.value = true;
  shareUrl.value = '';
  shareModal.value = { photoIds: ids };
  try {
    const res = await api('/api/share/generate', {
      method: 'POST',
      body: JSON.stringify({ photoIds: ids, expireDays: 7 })
    });
    if (!res.ok) {
      const msg = await extractErrorMessage(res);
      throw new Error(msg);
    }
    const json = await res.json();
    shareUrl.value = window.location.origin + json.data.url;
  } catch (err) {
    toast.error(err.message);
    shareModal.value = null;
  } finally {
    shareLoading.value = false;
  }
}

function copyShareLink() {
  navigator.clipboard.writeText(shareUrl.value).then(() => {
    toast.success('链接已复制，分享给朋友吧');
    shareModal.value = null;
  }).catch(() => toast.error('复制失败，请手动复制'));
}

async function onUnlock() {
  // 并行：初始化动效 + 调用后端拿 JWT
  initEffects();
  try {
    const token = await requestToken();
    ui.setToken(token);
    ui.unlock();
    toast.success('认证成功');
  } catch (err) {
    toast.error('认证失败，请重试');
  }
}

// 背景、光标、入场动画
let firstLoad = true;
function initEffects() {
  if (!firstLoad) return;
  firstLoad = false;

  const orbsHTML = `<div class="bg-orbs">
    <div class="orb orb-1"></div><div class="orb orb-2"></div><div class="orb orb-3"></div>
  </div>`;
  document.body.insertAdjacentHTML('afterbegin', orbsHTML);

  gsap.to('.orb-1', { keyframes: [
    { xPercent: -10, yPercent: -5, scale: 1 }, { xPercent: 10, yPercent: 5, scale: 1.15 },
    { xPercent: -5, yPercent: 10, scale: 0.9 }, { xPercent: 5, yPercent: -5, scale: 1.05 },
    { xPercent: -10, yPercent: -5, scale: 1 }
  ], duration: 12, repeat: -1, ease: 'sine.inOut' });
  gsap.to('.orb-2', { keyframes: [
    { xPercent: 5, yPercent: 5, scale: 1 }, { xPercent: -8, yPercent: -8, scale: 0.85 },
    { xPercent: 3, yPercent: 3, scale: 1.1 }, { xPercent: -5, yPercent: 5, scale: 0.95 },
    { xPercent: 5, yPercent: 5, scale: 1 }
  ], duration: 15, repeat: -1, ease: 'sine.inOut' });
  gsap.to('.orb-3', { keyframes: [
    { xPercent: 3, yPercent: -5, scale: 1 }, { xPercent: -6, yPercent: 3, scale: 1.1 },
    { xPercent: 8, yPercent: -8, scale: 0.85 }, { xPercent: -3, yPercent: 5, scale: 1.05 },
    { xPercent: 3, yPercent: -5, scale: 1 }
  ], duration: 10, repeat: -1, ease: 'sine.inOut' });

  // 光标拖尾 — 仅桌面
  if (!('ontouchstart' in window)) {
    const trails = [];
    let mx = 0, my = 0;
    for (let i = 0; i < 12; i++) {
      const dot = document.createElement('div');
      dot.className = 'cursor-trail';
      dot.style.opacity = (1 - i / 12) * 0.5;
      dot.style.transform = `translate(-50%,-50%) scale(${1 - i / 12})`;
      document.body.appendChild(dot);
      trails.push({ el: dot, x: 0, y: 0 });
    }
    document.addEventListener('mousemove', e => { mx = e.clientX; my = e.clientY; });
    (function tick() {
      let tx = mx, ty = my;
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
  }

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

  gsap.fromTo('.header h1', { y: -60, opacity: 0 }, { y: 0, opacity: 1, duration: 1, ease: 'expo.out' });
  gsap.fromTo('.upload-card', { y: 40, opacity: 0 }, { y: 0, opacity: 1, duration: 0.7, delay: 0.3, ease: 'expo.out' });
  gsap.fromTo('.gallery-section h2', { y: 30, opacity: 0 }, { y: 0, opacity: 1, duration: 0.6, delay: 0.5, ease: 'power1.out' });
}

onMounted(() => {
  if (isShare) return; // 分享模式，不初始化管理功能

  // 旧版本迁移：已解锁但无 token → 重锁
  if (ui.unlocked && !ui.token) {
    ui.reLock();
  }
  if (ui.unlocked) initEffects();
  document.addEventListener('keydown', e => {
    if (e.key === 'Escape') { ui.viewPhoto = null; ui.editPhoto = null; }
  });
  window.addEventListener('scroll', () => { ui.showBackTop = window.scrollY > 400; });
  if (ui.unlocked) photo.loadMore();
});
</script>

<template>
  <ShareViewer v-if="isShare" />
  <KonamiGate v-else-if="!ui.unlocked" @unlocked="onUnlock" />
  <template v-else>
    <span class="relock-wrap">
      <button class="relock-btn" @click="ui.reLock">🔒</button>
      <span class="relock-hint">再来一次！</span>
    </span>
    <AppHeader />
    <main class="page">
      <button class="sidebar-toggle" @click="ui.sidebarOpen = !ui.sidebarOpen">
        {{ ui.sidebarOpen ? '✕' : '☰' }}
      </button>
      <div v-if="ui.sidebarOpen" class="sidebar-backdrop" @click="ui.sidebarOpen = false"></div>
      <FilterSidebar
        :class="{ open: ui.sidebarOpen }"
        :selected-tag-ids="photo.selectedTagIds"
        :selected-category-ids="photo.selectedCategoryIds"
        @update:selected-tag-ids="photo.selectedTagIds = $event; photo.resetAndReload(); ui.sidebarOpen = false"
        @update:selected-category-ids="photo.selectedCategoryIds = $event; photo.resetAndReload(); ui.sidebarOpen = false"
      />
      <div class="main-content">
        <UploadCard @uploaded="onUploaded" />
        <PhotoGallery
          @view="ui.viewPhoto = $event"
          @edit="ui.editPhoto = $event"
          @delete="onDelete"
          @load-more="photo.loadMore"
          @batch-delete="onBatchDelete"
          @generate-share="onGenerateShare"
        />
      </div>
    </main>
    <button v-show="ui.showBackTop" class="back-top" @click="scrollToTop">↑</button>
    <ViewModal v-if="ui.viewPhoto" :photo="ui.viewPhoto" @close="ui.viewPhoto = null" />
    <EditModal v-if="ui.editPhoto" :photo="ui.editPhoto" @close="ui.editPhoto = null" @saved="onSaved" />
    <ToastProvider />
    <div v-if="shareModal" class="modal" @click.self="shareModal = null">
      <div class="modal-content modal-small">
        <h3>分享 {{ shareModal.photoIds.length }} 张照片</h3>
        <p class="share-hint">链接 7 天内有效，拿到链接的人可直接查看</p>
        <div v-if="shareLoading" class="share-loading">生成中...</div>
        <div v-else-if="shareUrl" class="share-row">
          <input :value="shareUrl" readonly class="share-input" @focus="$event.target.select()" />
          <button class="btn-primary" @click="copyShareLink">复制链接</button>
        </div>
        <button class="modal-close" @click="shareModal = null">✕</button>
      </div>
    </div>
  </template>
</template>
