import { defineStore } from 'pinia';
import { ref } from 'vue';
import { api } from '../api.js';

function readUrlParams() {
  const p = new URLSearchParams(window.location.search);
  return {
    view: p.get('view') || 'grid',
    q: p.get('q') || '',
    sortBy: p.get('sortBy') || 'time',
    sortOrder: p.get('sortOrder') || 'asc',
    tags: p.get('tags') ? p.get('tags').split(',').map(Number) : [],
    cats: p.get('cats') ? p.get('cats').split(',').map(Number) : []
  };
}

function syncUrl(state) {
  const p = new URLSearchParams();
  if (state.view) p.set('view', state.view);
  if (state.q) p.set('q', state.q);
  if (state.sortBy && state.sortBy !== 'time') p.set('sortBy', state.sortBy);
  if (state.sortOrder && state.sortOrder !== 'asc') p.set('sortOrder', state.sortOrder);
  if (state.tags && state.tags.length) p.set('tags', state.tags.join(','));
  if (state.cats && state.cats.length) p.set('cats', state.cats.join(','));
  const qs = p.toString();
  const url = window.location.pathname + (qs ? '?' + qs : '');
  history.replaceState(null, '', url);
}

const init = readUrlParams();

export const usePhotoStore = defineStore('photo', () => {
  const photos = ref([]);
  const page = ref(0);
  const hasMore = ref(true);
  const loading = ref(false);
  const totalCount = ref(0);
  const sortBy = ref(init.sortBy);
  const sortOrder = ref(init.sortOrder);
  const selectedTagIds = ref(init.tags);
  const selectedCategoryIds = ref(init.cats);
  const selectedPhotoIds = ref(new Set());
  const searchQuery = ref(init.q);
  const viewMode = ref(init.view);

  let requestId = 0;

  async function loadMore() {
    if (loading.value || !hasMore.value) return;
    loading.value = true;
    const myId = ++requestId;
    try {
      const fieldMap = { time: 'createdAt', name: 'name', size: 'fileSize' };
      const order = sortBy.value === 'time'
        ? (sortOrder.value === 'asc' ? 'desc' : 'asc')
        : sortOrder.value;
      const sortStr = `${fieldMap[sortBy.value]},${order}`;
      let url = `/api/photos?page=${page.value}&size=20&sort=${sortStr}`;
      selectedTagIds.value.forEach(id => { url += `&tagIds=${id}`; });
      selectedCategoryIds.value.forEach(id => { url += `&categoryIds=${id}`; });
      if (searchQuery.value) url += `&q=${encodeURIComponent(searchQuery.value)}`;
      const res = await api(url);
      if (myId !== requestId) return;
      const json = await res.json();
      const { content, totalPages, totalElements } = json.data;
      if (content && content.length) photos.value.push(...content);
      page.value++;
      hasMore.value = page.value < totalPages;
      totalCount.value = totalElements;
    } catch (err) {
      if (err.message !== '登录已过期，请重新解锁') {
        console.error('加载照片失败:', err);
      }
    } finally {
      if (myId === requestId) loading.value = false;
    }
  }

  function syncUrlState() {
    syncUrl({
      view: viewMode.value,
      q: searchQuery.value,
      sortBy: sortBy.value,
      sortOrder: sortOrder.value,
      tags: selectedTagIds.value,
      cats: selectedCategoryIds.value
    });
  }

  function resetAndReload() {
    requestId++;
    photos.value = [];
    page.value = 0;
    hasMore.value = true;
    loading.value = false;
    syncUrlState();
    loadMore();
  }

  function setSort(key) {
    if (sortBy.value === key) {
      sortOrder.value = sortOrder.value === 'asc' ? 'desc' : 'asc';
    } else {
      sortBy.value = key;
      sortOrder.value = 'asc';
    }
    resetAndReload();
  }

  function setSearch(q) {
    searchQuery.value = q;
    resetAndReload();
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

  // 首次加载时同步默认状态到 URL
  syncUrlState();

  return {
    photos, page, hasMore, loading, totalCount, sortBy, sortOrder,
    selectedTagIds, selectedCategoryIds, selectedPhotoIds,
    searchQuery, viewMode, loadMore, resetAndReload, setSort, setSearch,
    removePhoto, removePhotos, syncUrlState
  };
});
