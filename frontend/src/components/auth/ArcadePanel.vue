<script setup lang="ts">
/**
 * 红白机按键面板：D-Pad 十字键 + A/B（纯展示，press 事件上抛给 KonamiGate）
 * 样式自 KonamiGate 拆分而来，行为不变（pointerdown 触发）
 */
defineProps<{ activeBtn: string | null }>()
const emit = defineEmits<{ press: [dir: string] }>()

function press(dir: string): void {
  emit('press', dir)
}
</script>

<template>
  <div class="arcade-panel">
    <div class="dpad">
      <button
        class="arcade-btn dir up"
        :class="{ active: activeBtn === 'up' }"
        @pointerdown.prevent="press('up')"
      >
        ▲
      </button>
      <button
        class="arcade-btn dir left"
        :class="{ active: activeBtn === 'left' }"
        @pointerdown.prevent="press('left')"
      >
        ◀
      </button>
      <div class="dpad-center"></div>
      <button
        class="arcade-btn dir right"
        :class="{ active: activeBtn === 'right' }"
        @pointerdown.prevent="press('right')"
      >
        ▶
      </button>
      <button
        class="arcade-btn dir down"
        :class="{ active: activeBtn === 'down' }"
        @pointerdown.prevent="press('down')"
      >
        ▼
      </button>
    </div>

    <div class="ab-btns">
      <button
        class="arcade-btn ab b-btn"
        :class="{ active: activeBtn === 'B' }"
        @pointerdown.prevent="press('B')"
      >
        B
      </button>
      <button
        class="arcade-btn ab a-btn"
        :class="{ active: activeBtn === 'A' }"
        @pointerdown.prevent="press('A')"
      >
        A
      </button>
    </div>
  </div>
</template>

<style scoped>
/* D-Pad：NES 十字键 */
.arcade-panel {
  display: flex;
  align-items: center;
  gap: 72px;
  position: relative;
}
.dpad {
  display: grid;
  grid-template-columns: 64px 64px 64px;
  grid-template-rows: 64px 64px 64px;
}
.dpad-center {
  grid-column: 2;
  grid-row: 2;
  background: #26262c;
  border-radius: 2px;
}
.arcade-btn {
  border: none;
  cursor: pointer;
  font-family: inherit;
  transition: all 0.1s steps(2);
  outline: none;
}
.arcade-btn.dir {
  background: #26262c;
  border: 2px solid #3a3a42;
  color: #f0e9d8;
  font-size: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  line-height: 1;
}
.arcade-btn.dir:hover {
  border-color: #6a6a74;
}
.arcade-btn.dir:active,
.arcade-btn.dir.active {
  background: #e60012;
  border-color: #e60012;
  color: #101014;
}
.dpad .up {
  grid-column: 2;
  grid-row: 1;
}
.dpad .left {
  grid-column: 1;
  grid-row: 2;
}
.dpad .right {
  grid-column: 3;
  grid-row: 2;
}
.dpad .down {
  grid-column: 2;
  grid-row: 3;
}

/* A/B：红白机经典红键 */
.ab-btns {
  display: flex;
  gap: 28px;
  align-items: center;
}
.arcade-btn.ab {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  font-size: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  line-height: 1;
}
.b-btn {
  background: #b8000f;
  border: 4px solid #e60012;
  color: #f0e9d8;
}
.a-btn {
  background: #e60012;
  border: 4px solid #ff2e3d;
  color: #f0e9d8;
}
.b-btn:active,
.b-btn.active {
  background: #ff2e3d;
  border-color: #ff5a66;
}
.a-btn:active,
.a-btn.active {
  background: #ff2e3d;
  border-color: #ff5a66;
}

@media (max-width: 768px) {
  .arcade-panel {
    gap: 32px;
  }
  .dpad {
    grid-template-columns: 52px 52px 52px;
    grid-template-rows: 52px 52px 52px;
  }
  .arcade-btn.ab {
    width: 52px;
    height: 52px;
    font-size: 15px;
  }
}
</style>
