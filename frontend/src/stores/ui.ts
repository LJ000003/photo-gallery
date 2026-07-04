import { defineStore } from 'pinia'
import { ref, type Ref } from 'vue'
import type { Photo } from '../types/photo'

export const useUiStore = defineStore('ui', () => {
  const viewPhoto: Ref<Photo | null> = ref(null)
  const editPhoto: Ref<Photo | null> = ref(null)
  const showBackTop = ref(false)
  const sidebarOpen = ref(false)
  const unlocked = ref(localStorage.getItem('konami_unlocked') === 'true')
  const token: Ref<string | null> = ref(localStorage.getItem('jwt_token'))

  function reLock(): void {
    localStorage.removeItem('konami_unlocked')
    localStorage.removeItem('jwt_token')
    unlocked.value = false
    token.value = null
  }

  function unlock(): void {
    unlocked.value = true
    localStorage.setItem('konami_unlocked', 'true')
  }

  function setToken(t: string): void {
    token.value = t
    localStorage.setItem('jwt_token', t)
  }

  return {
    viewPhoto,
    editPhoto,
    showBackTop,
    sidebarOpen,
    unlocked,
    token,
    reLock,
    unlock,
    setToken,
  }
})
