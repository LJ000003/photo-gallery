<script setup lang="ts">
import { useToastStore } from '../stores/toast'

const toastStore = useToastStore()
</script>

<template>
  <div class="toast-container">
    <div
      v-for="t in toastStore.toasts"
      :key="t.id"
      class="toast-item"
      :class="t.type"
      @click="toastStore.remove(t.id)"
    >
      {{ t.message }}
    </div>
  </div>
</template>
