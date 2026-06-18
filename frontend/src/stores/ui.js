import { defineStore } from 'pinia';
import { ref } from 'vue';

export const useUiStore = defineStore('ui', () => {
  const viewPhoto = ref(null);
  const editPhoto = ref(null);
  const showBackTop = ref(false);
  const sidebarOpen = ref(false);
  const unlocked = ref(localStorage.getItem('konami_unlocked') === 'true');
  const token = ref(localStorage.getItem('jwt_token'));

  function reLock() {
    localStorage.removeItem('konami_unlocked');
    localStorage.removeItem('jwt_token');
    unlocked.value = false;
    token.value = null;
  }

  function unlock() {
    unlocked.value = true;
    localStorage.setItem('konami_unlocked', 'true');
  }

  function setToken(t) {
    token.value = t;
    localStorage.setItem('jwt_token', t);
  }

  return { viewPhoto, editPhoto, showBackTop, sidebarOpen, unlocked, token, reLock, unlock, setToken };
});
