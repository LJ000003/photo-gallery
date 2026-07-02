import { ref } from 'vue';
import { api } from './api.js';

const tags = ref([]);
const categories = ref([]);
const albums = ref([]);
let loadPromise = null;

async function loadAll() {
  if (loadPromise) return loadPromise;
  loadPromise = (async () => {
    const [tRes, cRes, aRes] = await Promise.all([
      api('/api/tags'), api('/api/categories'), api('/api/albums')
    ]);
    tags.value = (await tRes.json()).data;
    categories.value = (await cRes.json()).data;
    albums.value = (await aRes.json()).data;
    return { tags, categories, albums };
  })();
  return loadPromise;
}

async function refreshTags() {
  const res = await api('/api/tags');
  tags.value = (await res.json()).data;
}

async function refreshCategories() {
  const res = await api('/api/categories');
  categories.value = (await res.json()).data;
}

async function refreshAlbums() {
  const res = await api('/api/albums');
  albums.value = (await res.json()).data;
}

export function useStore() {
  return { tags, categories, albums, loadAll, refreshTags, refreshCategories, refreshAlbums };
}
