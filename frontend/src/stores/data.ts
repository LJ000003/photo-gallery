import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api } from '../api'
import type { Tag } from '../types/tag'
import type { Category } from '../types/category'
import type { Album } from '../types/album'
import type { ApiResponse } from '../types/api'

export const useDataStore = defineStore('data', () => {
  const tags = ref<Tag[]>([])
  const categories = ref<Category[]>([])
  const albums = ref<Album[]>([])
  let loadPromise: Promise<void> | null = null

  async function loadAll(): Promise<void> {
    if (loadPromise) return loadPromise
    loadPromise = (async () => {
      const [tRes, cRes, aRes] = await Promise.all([
        api('/api/tags'),
        api('/api/categories'),
        api('/api/albums'),
      ])
      tags.value = ((await tRes.json()) as ApiResponse<Tag[]>).data
      categories.value = ((await cRes.json()) as ApiResponse<Category[]>).data
      albums.value = ((await aRes.json()) as ApiResponse<Album[]>).data
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

  return { tags, categories, albums, loadAll, refreshTags, refreshCategories, refreshAlbums }
})
