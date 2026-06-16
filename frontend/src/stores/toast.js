import { defineStore } from 'pinia';
import { ref } from 'vue';

export const useToastStore = defineStore('toast', () => {
  const toasts = ref([]);
  let id = 0;

  function add(message, type = 'info', duration = 3000) {
    const item = { id: ++id, message, type };
    toasts.value.push(item);
    setTimeout(() => { remove(item.id); }, duration);
  }

  function remove(rid) {
    toasts.value = toasts.value.filter(t => t.id !== rid);
  }

  function success(msg) { add(msg, 'success'); }
  function error(msg) { add(msg, 'error'); }
  function info(msg) { add(msg, 'info'); }

  return { toasts, success, error, info, remove };
});
