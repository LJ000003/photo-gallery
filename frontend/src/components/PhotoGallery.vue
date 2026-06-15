<script setup>
import { ref, computed, watch, nextTick, onMounted, onUnmounted, defineAsyncComponent } from 'vue';
import gsap from 'gsap';
import PhotoCard from './PhotoCard.vue';
import { useConfirm } from '../useConfirm.js';

const confirmFn = useConfirm();
const LottieLoader = defineAsyncComponent(() => import('./LottieLoader.vue'));

const props = defineProps({
  photos: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  hasMore: { type: Boolean, default: false },
  totalCount: { type: Number, default: 0 }
});
const emit = defineEmits(['view', 'edit', 'delete', 'loadMore', 'batch-delete']);

const selectedIds = ref(new Set());

function isSelected(id) {
  return selectedIds.value.has(id);
}

function toggleSelect(id) {
  const s = selectedIds.value;
  if (s.has(id)) s.delete(id);
  else s.add(id);
  selectedIds.value = new Set(s);
}

const allSelected = computed(() =>
  props.photos.length > 0 && props.photos.every(p => selectedIds.value.has(p.id))
);

function toggleAll() {
  if (allSelected.value) {
    selectedIds.value = new Set();
  } else {
    selectedIds.value = new Set(props.photos.map(p => p.id));
  }
}

async function batchDelete() {
  if (selectedIds.value.size === 0) return;
  const ids = [...selectedIds.value];
  if (!await confirmFn(`确定要删除选中的 ${ids.length} 张照片吗？`, '批量删除')) return;
  emit('batch-delete', ids);
  selectedIds.value = new Set();
}

watch(() => props.photos, () => {
  const currentIds = new Set(props.photos.map(p => p.id));
  let changed = false;
  for (const id of selectedIds.value) {
    if (!currentIds.has(id)) { changed = true; break; }
  }
  if (changed) {
    const filtered = new Set([...selectedIds.value].filter(id => currentIds.has(id)));
    selectedIds.value = filtered;
  }
});

function onScroll() {
  if (!props.hasMore || props.loading) return;
  const scrollBottom = window.innerHeight + window.scrollY;
  const docBottom = document.documentElement.scrollHeight;
  if (scrollBottom >= docBottom - 200) {
    emit('loadMore');
  }
}

onMounted(() => {
  window.addEventListener('scroll', onScroll, { passive: true });
});

onUnmounted(() => {
  window.removeEventListener('scroll', onScroll);
});

let prevCount = 0;
watch(() => props.photos.length, () => {
  if (props.photos.length === prevCount) return;
  const newCards = props.photos.slice(prevCount);
  prevCount = props.photos.length;
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
    <h2>我的照片 <span v-if="totalCount">({{ totalCount }})</span></h2>
    <div class="gallery-toolbar">
      <label>
        <input type="checkbox" :checked="allSelected" @change="toggleAll" />
        全选
      </label>
      <button v-if="selectedIds.size > 0" class="btn-del" @click="batchDelete">
        批量删除 ({{ selectedIds.size }})
      </button>
    </div>
    <div class="gallery">
      <PhotoCard
        v-for="(p, i) in photos"
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
    <div v-if="loading" class="sentinel">
      <LottieLoader name="loading" :size="60" />
    </div>
    <div v-else-if="!hasMore && photos.length > 0" class="end-hint">没有更多了</div>
    <div v-if="!hasMore && !loading && photos.length === 0" class="empty-state">
      <LottieLoader name="empty" :size="160" />
      <p class="empty-hint">还没有照片，上传第一张吧</p>
    </div>
  </section>
</template>
