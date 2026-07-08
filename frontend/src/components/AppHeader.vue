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

<style scoped>
.header h1.rgb {
  background: linear-gradient(90deg, #f00, #f80, #ff0, #0f0, #08f, #80f, #f00);
  background-size: 300% 100%;
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  animation: rgb-sweep 2s linear infinite;
}
@keyframes rgb-sweep {
  0% {
    background-position: 0% 50%;
  }
  100% {
    background-position: 200% 50%;
  }
}
</style>
