import { defineStore } from 'pinia';
import { ref } from 'vue';

export const useUiStore = defineStore('ui', () => {
  const viewPhoto = ref(null);
  const editPhoto = ref(null);
  const showBackTop = ref(false);
  const sidebarOpen = ref(false);
  const unlocked = ref(localStorage.getItem('konami_unlocked') === 'true');

  function reLock() {
    localStorage.removeItem('konami_unlocked');
    unlocked.value = false;
  }

  function unlock() {
    unlocked.value = true;
  }

  return { viewPhoto, editPhoto, showBackTop, sidebarOpen, unlocked, reLock, unlock };
});
