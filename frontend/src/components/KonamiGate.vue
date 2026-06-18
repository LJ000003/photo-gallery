<script setup>
import { ref, onMounted, onUnmounted } from 'vue';
import gsap from 'gsap';

const emit = defineEmits(['unlocked']);

const TARGET = ['up','up','down','down','left','right','left','right','B','A','B','A'];
const pressed = ref([]);
const activeBtn = ref(null);
const shake = ref(false);
const success = ref(false);
const history = ref([]); // for visual indicators

function press(dir) {
  if (success.value) return;
  activeBtn.value = dir;
  setTimeout(() => { activeBtn.value = null; }, 150);

  pressed.value.push(dir);

  // Check prefix match
  const idx = pressed.value.length - 1;
  if (pressed.value[idx] !== TARGET[idx]) {
    // Wrong input
    shake.value = true;
    setTimeout(() => { shake.value = false; }, 500);
    pressed.value = [];
    history.value = [];
    return;
  }

  history.value = [...pressed.value];

  // Check if complete
  if (pressed.value.length === TARGET.length) {
    success.value = true;
    setTimeout(() => { emit('unlocked'); }, 1500);
  }
}

function handleKey(e) {
  const map = {
    ArrowUp: 'up', ArrowDown: 'down', ArrowLeft: 'left', ArrowRight: 'right',
    b: 'B', B: 'B', a: 'A', A: 'A'
  };
  const dir = map[e.key];
  if (dir) { e.preventDefault(); press(dir); }
}

onMounted(() => { window.addEventListener('keydown', handleKey); });
onUnmounted(() => { window.removeEventListener('keydown', handleKey); });
</script>

<template>
  <div class="arcade-gate" :class="{ shake, success }">
    <div class="arcade-screen">
      <div class="arcade-title">{{ success ? 'CONGRATULATIONS' : 'PRESS START' }}</div>
      <div v-if="success" class="hp-popup">HP +30 ♥</div>
      <div v-else class="progress-bar">
        <span v-for="i in TARGET.length" :key="i"
          class="progress-dot"
          :class="{
            filled: history.length >= i,
            wrong: shake && i === history.length + 1
          }">
          {{ history[i-1] === 'up' ? '↑' : history[i-1] === 'down' ? '↓' : history[i-1] === 'left' ? '←' : history[i-1] === 'right' ? '→' : history[i-1] || '·' }}
        </span>
      </div>
    </div>

    <div class="arcade-panel">
      <!-- D-Pad -->
      <div class="dpad">
        <button class="arcade-btn dir up" :class="{ active: activeBtn === 'up' }"
          @pointerdown.prevent="press('up')">▲</button>
        <button class="arcade-btn dir left" :class="{ active: activeBtn === 'left' }"
          @pointerdown.prevent="press('left')">◀</button>
        <div class="dpad-center"></div>
        <button class="arcade-btn dir right" :class="{ active: activeBtn === 'right' }"
          @pointerdown.prevent="press('right')">▶</button>
        <button class="arcade-btn dir down" :class="{ active: activeBtn === 'down' }"
          @pointerdown.prevent="press('down')">▼</button>
      </div>

      <!-- A / B -->
      <div class="ab-btns">
        <button class="arcade-btn ab b-btn" :class="{ active: activeBtn === 'B' }"
          @pointerdown.prevent="press('B')">B</button>
        <button class="arcade-btn ab a-btn" :class="{ active: activeBtn === 'A' }"
          @pointerdown.prevent="press('A')">A</button>
      </div>
    </div>

    <div class="arcade-footer">也支持键盘：↑ ↓ ← → 方向键 + B / A</div>
  </div>
</template>
