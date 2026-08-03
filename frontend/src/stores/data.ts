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
      const tJson: ApiResponse<Tag[]> = await tRes.json()
      const cJson: ApiResponse<Category[]> = await cRes.json()
      const aJson: ApiResponse<Album[]> = await aRes.json()
      tags.value = tJson.data || []
      categories.value = cJson.data || []
      albums.value = aJson.data || []
    })()
    try {
      await loadPromise
    } finally {
      // 失败也重置缓存：下次 loadAll() 重新请求，
      // 避免永远拿到同一个被拒的 Promise（标签/分类/相册下拉永久空白）
      loadPromise = null
    }
  }

  async function refreshTags(): Promise<void> {
    const res = await api('/api/tags')
    const json: ApiResponse<Tag[]> = await res.json()
    tags.value = json.data || []
  }

  async function refreshCategories(): Promise<void> {
    const res = await api('/api/categories')
    const json: ApiResponse<Category[]> = await res.json()
    categories.value = json.data || []
  }

  async function refreshAlbums(): Promise<void> {
    const res = await api('/api/albums')
    const json: ApiResponse<Album[]> = await res.json()
    albums.value = json.data || []
  }

  return { tags, categories, albums, loadAll, refreshTags, refreshCategories, refreshAlbums }
})
