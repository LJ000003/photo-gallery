import { defineStore } from 'pinia';
import { ref } from 'vue';

export const usePhotoStore = defineStore('photo', () => {
  const photos = ref([]);
  const page = ref(0);
  const hasMore = ref(true);
  const loading = ref(false);
  const totalCount = ref(0);
  const sortBy = ref('time');
  const selectedTagIds = ref([]);
  const selectedCategoryIds = ref([]);
  const selectedPhotoIds = ref(new Set());

  let requestId = 0;

  async function loadMore() {
    if (loading.value || !hasMore.value) return;
    loading.value = true;
    const myId = ++requestId;
    try {
      const sortMap = { time: 'createdAt,desc', name: 'name,asc', size: 'fileSize,desc' };
      let url = `/api/photos?page=${page.value}&size=20&sort=${sortMap[sortBy.value] || 'createdAt,desc'}`;
      selectedTagIds.value.forEach(id => { url += `&tagIds=${id}`; });
      selectedCategoryIds.value.forEach(id => { url += `&categoryIds=${id}`; });
      const res = await fetch(url);
      if (myId !== requestId) return;
      const json = await res.json();
      const { content, totalPages, totalElements } = json.data;
      if (content && content.length) photos.value.push(...content);
      page.value++;
      hasMore.value = page.value < totalPages;
      totalCount.value = totalElements;
    } catch {
    } finally {
      if (myId === requestId) loading.value = false;
    }
  }

  function resetAndReload() {
    requestId++;
    photos.value = [];
    page.value = 0;
    hasMore.value = true;
    loading.value = false;
    loadMore();
  }

  function removePhoto(id) {
    photos.value = photos.value.filter(p => p.id !== id);
    totalCount.value--;
  }

  function removePhotos(ids) {
    const set = new Set(ids);
    photos.value = photos.value.filter(p => !set.has(p.id));
    totalCount.value -= ids.length;
  }

  return {
    photos, page, hasMore, loading, totalCount, sortBy,
    selectedTagIds, selectedCategoryIds, selectedPhotoIds,
    loadMore, resetAndReload, removePhoto, removePhotos
  };
});
