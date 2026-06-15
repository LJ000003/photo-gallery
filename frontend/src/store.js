import { ref } from 'vue';

const tags = ref([]);
const categories = ref([]);
let loadPromise = null;

async function loadAll() {
  if (loadPromise) return loadPromise;
  loadPromise = (async () => {
    const [tRes, cRes] = await Promise.all([fetch('/api/tags'), fetch('/api/categories')]);
    tags.value = (await tRes.json()).data;
    categories.value = (await cRes.json()).data;
    return { tags, categories };
  })();
  return loadPromise;
}

async function refreshTags() {
  const res = await fetch('/api/tags');
  tags.value = (await res.json()).data;
}

async function refreshCategories() {
  const res = await fetch('/api/categories');
  categories.value = (await res.json()).data;
}

export function useStore() {
  return { tags, categories, loadAll, refreshTags, refreshCategories };
}
