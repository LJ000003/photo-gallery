<script setup lang="ts">
import { ref } from 'vue'

const clicks = ref(0)
const easterEgg = ref(false)

function onClick(): void {
  clicks.value++
  if (clicks.value >= 30) easterEgg.value = true
}
</script>

<template>
  <header class="header">
    <h1 :class="{ rgb: easterEgg }" @click="onClick">照片管理器</h1>
  </header>
</template>
