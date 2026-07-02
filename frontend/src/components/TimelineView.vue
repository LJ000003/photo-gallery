<script setup>
import { ref, onMounted, computed, watch } from 'vue';
import { useUiStore } from '../stores/ui.js';

const props = defineProps({
  sortOrder: { type: String, default: 'desc' }
});
const emit = defineEmits(['view']);
const ui = useUiStore();

const items = ref([]);
const loading = ref(true);

function groupByMonth(list) {
  const groups = new Map();
  for (const item of list) {
    if (!item.dateTaken) continue;
    const key = item.dateTaken.substring(0, 7); // YYYY-MM
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push(item);
  }
  // 组内也按日期排序
  const dir = props.sortOrder === 'asc' ? 1 : -1;
  for (const photos of groups.values()) {
    photos.sort((a, b) => dir * a.dateTaken.localeCompare(b.dateTaken));
  }
  const monthDir = props.sortOrder === 'asc' ? 1 : -1;
  return [...groups.entries()].sort((a, b) => monthDir * a[0].localeCompare(b[0]));
}

const grouped = computed(() => groupByMonth(items.value));

function tokenParam() {
  const t = ui.token;
  return t ? `?token=${t}` : '';
}

async function fetchTimeline() {
  loading.value = true;
  try {
    const res = await fetch(`/api/photos/timeline?sortOrder=${props.sortOrder}`, {
      headers: { Authorization: `Bearer ${ui.token}` }
    });
    const data = await res.json();
    if (data.code === 200) items.value = data.data || [];
  } catch (e) {
    console.error('Failed to load timeline', e);
  } finally {
    loading.value = false;
  }
}

onMounted(fetchTimeline);
watch(() => props.sortOrder, fetchTimeline);
</script>

<template>
  <div class="timeline-wrap">
    <div v-if="loading" class="timeline-loading">加载中...</div>
    <div v-else-if="grouped.length === 0" class="timeline-empty">
      没有可提取时间信息的照片，请上传带有 EXIF 拍摄日期的 JPEG/WebP 图片
    </div>
    <div v-else class="timeline">
      <div class="timeline-line"></div>
      <div v-for="[month, photos] in grouped" :key="month" class="timeline-group">
        <div class="timeline-month">{{ month }}</div>
        <div class="timeline-cards">
          <div
            v-for="exif in photos" :key="exif.id"
            class="timeline-card"
            @click="emit('view', { id: exif.photoId, name: exif.photoName })"
          >
            <img
              :src="`${exif.photoThumbnail}${tokenParam()}`"
              :alt="exif.photoName"
              loading="lazy"
            />
            <div class="timeline-info">
              <p class="timeline-name">{{ exif.photoName }}</p>
              <p class="timeline-date">{{ exif.dateTaken }}</p>
              <p v-if="exif.cameraModel" class="timeline-camera">{{ exif.cameraModel }}</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
