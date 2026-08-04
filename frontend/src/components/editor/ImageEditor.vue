<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useImageEditorCanvas } from '../../composables/useImageEditorCanvas'
import EditorToolbar from './EditorToolbar.vue'
import type { ImageEditResult } from '../../types/transform'

/**
 * 画布图片编辑器（旋转/镜像/裁剪）——组装壳
 * 画布逻辑在 useImageEditorCanvas，工具栏在 EditorToolbar
 */
const props = defineProps<{
  src: string
  visible?: boolean
}>()
const emit = defineEmits<{
  close: []
  done: [result: ImageEditResult]
}>()

const canvas = ref<HTMLCanvasElement | null>(null)
const srcRef = computed(() => props.src)
const visibleRef = computed(() => props.visible)

const editor = useImageEditorCanvas(
  srcRef,
  visibleRef,
  canvas,
  (result) => emit('done', result),
)
// 顶层解构：模板自动解包 ref（composable 返回对象中的 ref 不会自动解包）
const { mirrorH, mirrorV, cropMode } = editor

function onKeydown(e: KeyboardEvent): void {
  if (e.key === 'Escape') emit('close')
}
onMounted(() => window.addEventListener('keydown', onKeydown))
onUnmounted(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <Teleport to="body">
    <div v-if="visible" class="editor-overlay" @wheel.prevent>
      <div class="editor-backdrop" @click="emit('close')"></div>
      <div class="editor-panel">
        <EditorToolbar
          :mirror-h="mirrorH"
          :mirror-v="mirrorV"
          :crop-mode="cropMode"
          @rotate-left="editor.doRotate(90)"
          @rotate-right="editor.doRotate(-90)"
          @mirror-h="editor.doMirror('h')"
          @mirror-v="editor.doMirror('v')"
          @toggle-crop="editor.toggleCropMode"
          @reset="editor.doReset"
          @confirm="editor.confirm"
          @cancel="emit('close')"
        />
        <div class="editor-canvas-wrap">
          <canvas
            ref="canvas"
            @pointerdown="editor.onPointerDown"
            @pointermove="editor.onPointerMove"
            @pointerup="editor.onPointerUp"
            @pointerleave="editor.onPointerUp"
          ></canvas>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.editor-overlay {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}
.editor-backdrop {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
}
.editor-panel {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  width: min(960px, 96vw);
  height: min(720px, 92vh);
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: 16px;
  box-shadow: 0 24px 80px rgba(0, 0, 0, 0.35);
  overflow: hidden;
  animation: panel-in 0.25s ease;
}
@keyframes panel-in {
  from {
    opacity: 0;
    transform: scale(0.96) translateY(8px);
  }
  to {
    opacity: 1;
    transform: scale(1) translateY(0);
  }
}
.editor-canvas-wrap {
  flex: 1;
  min-height: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  touch-action: none;
}
.editor-canvas-wrap canvas {
  max-width: 100%;
  max-height: 100%;
  border-radius: 8px;
  cursor: crosshair;
}

@media (max-width: 768px) {
  .editor-overlay {
    padding: 0;
  }
  .editor-panel {
    width: 100vw;
    height: 100dvh;
    border-radius: 0;
    border: none;
  }
  .editor-canvas-wrap {
    padding: 10px;
  }
}
</style>
