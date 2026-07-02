<script setup>
import { ref, computed, watch, nextTick, onMounted, onUnmounted, defineAsyncComponent } from 'vue';
import gsap from 'gsap';
import PhotoCard from './PhotoCard.vue';
import { usePhotoStore } from '../stores/photo.js';

const LottieLoader = defineAsyncComponent(() => import('./LottieLoader.vue'));

const photo = usePhotoStore();
const emit = defineEmits(['view', 'edit', 'delete', 'loadMore', 'batch-delete', 'generate-share']);

const selectedIds = ref(new Set());

function isSelected(id) { return selectedIds.value.has(id); }
function toggleSelect(id) {
  const s = selectedIds.value;
  s.has(id) ? s.delete(id) : s.add(id);
  selectedIds.value = new Set(s);
}
const allSelected = computed(() =>
  photo.photos.length > 0 && photo.photos.every(p => selectedIds.value.has(p.id))
);
function toggleAll() {
  selectedIds.value = allSelected.value
    ? new Set()
    : new Set(photo.photos.map(p => p.id));
}
function batchDelete() {
  if (selectedIds.value.size === 0) return;
  emit('batch-delete', [...selectedIds.value]);
  selectedIds.value = new Set();
}

function generateShare() {
  if (selectedIds.value.size === 0) return;
  emit('generate-share', [...selectedIds.value]);
}

const sortOptions = [
  { key: 'time', label: '时间' },
  { key: 'name', label: '名称' },
  { key: 'size', label: '大小' }
];

watch(() => photo.photos, () => {
  const currentIds = new Set(photo.photos.map(p => p.id));
  let changed = false;
  for (const id of selectedIds.value) {
    if (!currentIds.has(id)) { changed = true; break; }
  }
  if (changed) {
    selectedIds.value = new Set([...selectedIds.value].filter(id => currentIds.has(id)));
  }
});

function onScroll() {
  if (!photo.hasMore || photo.loading) return;
  if (window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 200) {
    emit('loadMore');
  }
}

onMounted(() => { window.addEventListener('scroll', onScroll, { passive: true }); });
onUnmounted(() => { window.removeEventListener('scroll', onScroll); });

let prevCount = 0;
watch(() => photo.photos.length, () => {
  if (photo.photos.length === prevCount) return;
  const newCards = photo.photos.slice(prevCount);
  prevCount = photo.photos.length;
  nextTick(() => {
    gsap.fromTo(
      newCards.map((_, i) => `.photo-card[data-insert="${prevCount - newCards.length + i}"]`),
      { y: 40, opacity: 0 },
      { y: 0, opacity: 1, stagger: 0.06, duration: 0.6, ease: 'expo.out' }
    );
  });
});
</script>

<template>
  <section class="gallery-section">
    <h2>我的照片 <span v-if="photo.totalCount">({{ photo.totalCount }})</span></h2>
    <div class="gallery-toolbar">
      <label>
        <input type="checkbox" :checked="allSelected" @change="toggleAll" />
        全选
      </label>
      <button v-if="selectedIds.size > 0" class="btn-share" @click="generateShare">
        生成分享链接
      </button>
      <button v-if="selectedIds.size > 0" class="btn-del" @click="batchDelete">
        批量删除 ({{ selectedIds.size }})
      </button>
      <div class="sort-switch">
        <span class="sort-label">排序方式：</span>
        <div class="sort-track">
          <div class="sort-slider" :style="{ transform: `translateX(${sortOptions.findIndex(o => o.key === photo.sortBy) * 100}%)` }"></div>
          <button v-for="opt in sortOptions" :key="opt.key"
            class="sort-opt" :class="{ active: photo.sortBy === opt.key }"
            @click="photo.setSort(opt.key)">
            {{ opt.label }}
            <span v-if="photo.sortBy === opt.key" class="sort-arrows">
              <i class="iconfont icon-jiantou_qiehuanxiangshang_o sort-arrow-down" :class="{ active: photo.sortOrder === 'asc' }"></i>
              <i class="iconfont icon-jiantou_qiehuanxiangshang_o" :class="{ active: photo.sortOrder === 'desc' }"></i>
            </span>
          </button>
        </div>
      </div>
    </div>

    <div class="gallery">
      <!-- 骨架屏 -->
      <div v-if="photo.loading && photo.photos.length === 0" v-for="i in 6" :key="'s'+i" class="skeleton-card">
        <div class="skeleton-img"></div>
        <div class="skeleton-body">
          <div class="skeleton-line"></div>
          <div class="skeleton-line"></div>
        </div>
      </div>
      <PhotoCard
        v-for="(p, i) in photo.photos"
        :key="p.id"
        :photo="p"
        :selected="isSelected(p.id)"
        :data-insert="i"
        @view="emit('view', p)"
        @edit="emit('edit', p)"
        @delete="emit('delete', p.id)"
        @toggle-select="toggleSelect"
      />
    </div>
    <div v-if="photo.loading && photo.photos.length > 0" class="sentinel">
      <LottieLoader name="loading" :size="60" />
    </div>
    <div v-else-if="!photo.hasMore && photo.photos.length > 0" class="end-hint">没有更多了</div>
    <div v-if="!photo.hasMore && !photo.loading && photo.photos.length === 0" class="empty-state">
      <LottieLoader name="empty" :size="160" />
      <p class="empty-hint">还没有照片，上传第一张吧</p>
    </div>
  </section>
</template>
