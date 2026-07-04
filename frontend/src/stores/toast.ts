import { defineStore } from 'pinia'
import { ref, type Ref } from 'vue'
import type { Toast, ToastType } from '../types/toast'

export const useToastStore = defineStore('toast', () => {
  const toasts: Ref<Toast[]> = ref([])
  let id = 0

  function add(message: string, type: ToastType = 'info', duration: number = 3000): void {
    const item: Toast = { id: ++id, message, type }
    toasts.value.push(item)
    setTimeout(() => {
      remove(item.id)
    }, duration)
  }

  function remove(rid: number): void {
    toasts.value = toasts.value.filter((t) => t.id !== rid)
  }

  function success(msg: string): void {
    add(msg, 'success')
  }
  function error(msg: string): void {
    add(msg, 'error')
  }
  function info(msg: string): void {
    add(msg, 'info')
  }

  return { toasts, success, error, info, remove }
})
