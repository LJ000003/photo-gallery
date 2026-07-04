import { ref, type Ref } from 'vue'
import { api } from './api'
import type { Tag } from './types/tag'
import type { Category } from './types/category'
import type { Album } from './types/album'
import type { ApiResponse } from './types/api'

const tags: Ref<Tag[]> = ref([])
const categories: Ref<Category[]> = ref([])
const albums: Ref<Album[]> = ref([])
let loadPromise: Promise<{ tags: Ref<Tag[]>; categories: Ref<Category[]>; albums: Ref<Album[]> }> | null = null

async function loadAll(): Promise<{ tags: Ref<Tag[]>; categories: Ref<Category[]>; albums: Ref<Album[]> }> {
  if (loadPromise) return loadPromise
  loadPromise = (async () => {
    const [tRes, cRes, aRes] = await Promise.all([
      api('/api/tags'), api('/api/categories'), api('/api/albums')
    ])
    tags.value = ((await tRes.json()) as ApiResponse<Tag[]>).data
    categories.value = ((await cRes.json()) as ApiResponse<Category[]>).data
    albums.value = ((await aRes.json()) as ApiResponse<Album[]>).data
    return { tags, categories, albums }
  })()
  return loadPromise
}

async function refreshTags(): Promise<void> {
  const res = await api('/api/tags')
  tags.value = ((await res.json()) as ApiResponse<Tag[]>).data
}

async function refreshCategories(): Promise<void> {
  const res = await api('/api/categories')
  categories.value = ((await res.json()) as ApiResponse<Category[]>).data
}

async function refreshAlbums(): Promise<void> {
  const res = await api('/api/albums')
  albums.value = ((await res.json()) as ApiResponse<Album[]>).data
}

export function useStore() {
  return { tags, categories, albums, loadAll, refreshTags, refreshCategories, refreshAlbums }
}
