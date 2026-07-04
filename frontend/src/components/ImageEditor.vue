<script setup lang="ts">
import { ref, watch, onMounted, nextTick } from 'vue'
import type { TransformParams, MirrorMode, ImageEditResult } from '../types/transform'

const props = defineProps<{
  src: string
  visible?: boolean
}>()
const emit = defineEmits<{
  close: []
  done: [result: ImageEditResult]
}>()

const canvas = ref<HTMLCanvasElement | null>(null)
const rotation = ref(0)
const mirrorH = ref(false)
const mirrorV = ref(false)
const cropMode = ref(false)
const crop = ref({ x: 0, y: 0, w: 0, h: 0 })
const dragging = ref(false)
const dragCorner = ref<string | null>(null)
const dragStart = ref({ x: 0, y: 0, mx: 0, my: 0, mw: 0, mh: 0 })

let img: HTMLImageElement | null = null
let imgNatural = { w: 0, h: 0 }

function resetCrop(): void {
  crop.value = { x: 0.1, y: 0.1, w: 0.8, h: 0.8 }
}

function initImage(): void {
  rotation.value = 0
  mirrorH.value = false
  mirrorV.value = false
  cropMode.value = false
  resetCrop()
  if (img) {
    img.onload = null
    img.src = ''
  }
  img = new Image()
  img.onload = () => {
    imgNatural = { w: img!.naturalWidth, h: img!.naturalHeight }
    nextTick(() => draw())
  }
  img.onerror = () => {
    /* silently handle load errors */
  }
  if (props.src) img.src = props.src
}

watch(
  () => props.visible,
  (v) => {
    if (v) nextTick(() => initImage())
  },
)

onMounted(() => {
  if (props.visible) nextTick(() => initImage())
})

watch(
  () => props.src,
  (v) => {
    if (v && props.visible && img) {
      img.src = v
    }
  },
)

function draw(): void {
  if (!canvas.value || !img) return
  const c = canvas.value
  const ctx = c.getContext('2d')
  if (!ctx) return
  const pc = c.parentElement
  if (!pc) return
  c.width = pc.clientWidth - 40
  c.height = pc.clientHeight - 140
  if (c.width < 100 || c.height < 100) return

  ctx.clearRect(0, 0, c.width, c.height)
  ctx.fillStyle = '#1a1a2e'
  ctx.fillRect(0, 0, c.width, c.height)

  const iw = imgNatural.w,
    ih = imgNatural.h
  let dw: number, dh: number, dx: number, dy: number
  if (iw / ih > c.width / c.height) {
    dw = c.width * 0.85
    dh = dw * (ih / iw)
  } else {
    dh = c.height * 0.85
    dw = dh * (iw / ih)
  }
  dx = (c.width - dw) / 2
  dy = (c.height - dh) / 2

  ctx.save()
  ctx.translate(dx + dw / 2, dy + dh / 2)
  if (mirrorH.value) ctx.scale(-1, 1)
  if (mirrorV.value) ctx.scale(1, -1)
  ctx.rotate((rotation.value * Math.PI) / 180)
  ctx.drawImage(img, -dw / 2, -dh / 2, dw, dh)
  ctx.restore()

  if (cropMode.value) {
    const cr = crop.value
    const cx = dx + cr.x * dw,
      cy = dy + cr.y * dh
    const cw = cr.w * dw,
      ch = cr.h * dh
    ctx.strokeStyle = '#00d4ff'
    ctx.lineWidth = 2
    ctx.setLineDash([6, 3])
    ctx.strokeRect(cx, cy, cw, ch)
    ctx.setLineDash([])
    ctx.fillStyle = 'rgba(0,0,0,0.4)'
    ctx.fillRect(0, 0, c.width, cy)
    ctx.fillRect(0, cy + ch, c.width, c.height - cy - ch)
    ctx.fillRect(0, cy, cx, ch)
    ctx.fillRect(cx + cw, cy, c.width - cx - cw, ch)

    const handles = [
      { x: cx, y: cy, c: 'tl' },
      { x: cx + cw, y: cy, c: 'tr' },
      { x: cx, y: cy + ch, c: 'bl' },
      { x: cx + cw, y: cy + ch, c: 'br' },
    ]
    for (const h of handles) {
      ctx.fillStyle = '#00d4ff'
      ctx.fillRect(h.x - 5, h.y - 5, 10, 10)
    }
  }
}

function getPos(e: MouseEvent): { x: number; y: number; rx: number; ry: number } {
  const r = canvas.value!.getBoundingClientRect()
  return { x: e.clientX - r.left, y: e.clientY - r.top, rx: r.width, ry: r.height }
}

function imgRect(): { dx: number; dy: number; dw: number; dh: number } {
  const pc = canvas.value!.parentElement!
  const cw = pc.clientWidth - 40,
    ch = pc.clientHeight - 140
  const rotated = rotation.value === 90 || rotation.value === 270
  const iw = rotated ? imgNatural.h : imgNatural.w
  const ih = rotated ? imgNatural.w : imgNatural.h
  let dw: number, dh: number, dx: number, dy: number
  if (iw / ih > cw / ch) {
    dw = cw * 0.85
    dh = dw * (ih / iw)
  } else {
    dh = ch * 0.85
    dw = dh * (iw / ih)
  }
  dx = (cw - dw) / 2
  dy = (ch - dh) / 2
  return { dx, dy, dw, dh }
}

function onMouseDown(e: MouseEvent): void {
  if (!cropMode.value) return
  const p = getPos(e)
  const ir = imgRect()
  const cr = crop.value
  const cx = ir.dx + cr.x * ir.dw,
    cy = ir.dy + cr.y * ir.dh
  const cw = cr.w * ir.dw,
    ch = cr.h * ir.dh
  const handles: Record<string, [number, number]> = {
    tl: [cx, cy],
    tr: [cx + cw, cy],
    bl: [cx, cy + ch],
    br: [cx + cw, cy + ch],
  }

  let corner: string | null = null
  for (const [k, [hx, hy]] of Object.entries(handles)) {
    if (Math.abs(p.x - hx) < 10 && Math.abs(p.y - hy) < 10) {
      corner = k
      break
    }
  }
  if (!corner && p.x >= cx && p.x <= cx + cw && p.y >= cy && p.y <= cy + ch) {
    corner = 'move'
  }

  if (corner) {
    dragging.value = true
    dragCorner.value = corner
    dragStart.value = { x: p.x, y: p.y, mx: cr.x, my: cr.y, mw: cr.w, mh: cr.h }
    e.preventDefault()
  } else if (p.x >= ir.dx && p.x <= ir.dx + ir.dw && p.y >= ir.dy && p.y <= ir.dy + ir.dh) {
    const nx = (p.x - ir.dx) / ir.dw
    const ny = (p.y - ir.dy) / ir.dh
    crop.value = { x: nx, y: ny, w: 0.01, h: 0.01 }
    dragging.value = true
    dragCorner.value = 'br'
    dragStart.value = { x: p.x, y: p.y, mx: nx, my: ny, mw: 0.01, mh: 0.01 }
    e.preventDefault()
  }
}

function onMouseMove(e: MouseEvent): void {
  if (!dragging.value) return
  const p = getPos(e)
  const ir = imgRect()
  const dx = (p.x - dragStart.value.x) / ir.dw
  const dy = (p.y - dragStart.value.y) / ir.dh
  const { mx, my, mw, mh } = dragStart.value

  if (dragCorner.value === 'move') {
    crop.value = {
      x: Math.max(0, Math.min(mx + dx, 1 - mw)),
      y: Math.max(0, Math.min(my + dy, 1 - mh)),
      w: mw,
      h: mh,
    }
  } else {
    let nx = mx,
      ny = my,
      nw = mw,
      nh = mh
    if (dragCorner.value?.includes('l')) {
      nx = Math.max(0, Math.min(mx + dx, mx + mw - 0.02))
      nw = mx + mw - nx
    }
    if (dragCorner.value?.includes('r')) {
      nw = Math.max(0.02, Math.min(mw + dx, 1 - mx))
    }
    if (dragCorner.value?.includes('t')) {
      ny = Math.max(0, Math.min(my + dy, my + mh - 0.02))
      nh = my + mh - ny
    }
    if (dragCorner.value?.includes('b')) {
      nh = Math.max(0.02, Math.min(mh + dy, 1 - my))
    }
    crop.value = { x: nx, y: ny, w: nw, h: nh }
  }
  draw()
}

function onMouseUp(): void {
  dragging.value = false
  dragCorner.value = null
}

function doRotate(dir: number): void {
  rotation.value = (rotation.value + dir + 360) % 360
  draw()
}
function doMirror(dir: 'h' | 'v'): void {
  if (dir === 'h') mirrorH.value = !mirrorH.value
  else mirrorV.value = !mirrorV.value
  draw()
}
function doReset(): void {
  rotation.value = 0
  mirrorH.value = false
  mirrorV.value = false
  resetCrop()
  draw()
}
function toggleCropMode(): void {
  cropMode.value = !cropMode.value
  draw()
}

function confirm(): void {
  if (!img) return
  const srcW = imgNatural.w,
    srcH = imgNatural.h
  const rotated = rotation.value === 90 || rotation.value === 270
  const outW = rotated ? srcH : srcW
  const outH = rotated ? srcW : srcH

  const srcCanvas = document.createElement('canvas')
  srcCanvas.width = srcW
  srcCanvas.height = srcH
  const srcCtx = srcCanvas.getContext('2d')!
  srcCtx.drawImage(img, 0, 0)

  const out = document.createElement('canvas')
  out.width = outW
  out.height = outH
  const ctx = out.getContext('2d')!

  ctx.save()
  ctx.translate(outW / 2, outH / 2)
  if (mirrorH.value) ctx.scale(-1, 1)
  if (mirrorV.value) ctx.scale(1, -1)
  ctx.rotate((rotation.value * Math.PI) / 180)
  ctx.drawImage(srcCanvas, -srcW / 2, -srcH / 2, srcW, srcH)
  ctx.restore()

  let finalCanvas = out
  if (cropMode.value) {
    const cx = Math.floor(crop.value.x * outW)
    const cy = Math.floor(crop.value.y * outH)
    const cw = Math.floor(crop.value.w * outW)
    const ch = Math.floor(crop.value.h * outH)
    finalCanvas = document.createElement('canvas')
    finalCanvas.width = cw
    finalCanvas.height = ch
    finalCanvas.getContext('2d')!.drawImage(out, cx, cy, cw, ch, 0, 0, cw, ch)
  }

  const mirror: MirrorMode = mirrorH.value ? 'horizontal' : mirrorV.value ? 'vertical' : 'none'
  const transformParams: TransformParams = {
    rotate: rotation.value,
    mirror,
    cx: cropMode.value ? crop.value.x : null,
    cy: cropMode.value ? crop.value.y : null,
    cw: cropMode.value ? crop.value.w : null,
    ch: cropMode.value ? crop.value.h : null,
  }

  finalCanvas.toBlob(
    (blob) => {
      if (blob) emit('done', { blob, params: transformParams })
    },
    'image/jpeg',
    0.92,
  )
}
</script>

<template>
  <Teleport to="body">
    <div v-if="visible" class="editor-overlay" @wheel.prevent>
      <div class="editor-backdrop" @click="emit('close')"></div>
      <div class="editor-panel">
        <div class="editor-toolbar">
          <button type="button" title="左旋90°" @click="doRotate(90)">↺</button>
          <button type="button" title="右旋90°" @click="doRotate(-90)">↻</button>
          <button
            type="button"
            :class="{ active: mirrorH }"
            title="水平镜像"
            @click="doMirror('h')"
          >
            ↔
          </button>
          <button
            type="button"
            :class="{ active: mirrorV }"
            title="垂直镜像"
            @click="doMirror('v')"
          >
            ↕
          </button>
          <button type="button" :class="{ active: cropMode }" title="裁剪" @click="toggleCropMode">
            ✂
          </button>
          <button type="button" title="重置" @click="doReset">⟳</button>
          <span style="flex: 1"></span>
          <button type="button" class="btn-confirm" @click="confirm">确认</button>
          <button type="button" class="btn-cancel" @click="emit('close')">取消</button>
        </div>
        <div class="editor-canvas-wrap">
          <canvas
            ref="canvas"
            @mousedown="onMouseDown"
            @mousemove="onMouseMove"
            @mouseup="onMouseUp"
            @mouseleave="onMouseUp"
          ></canvas>
        </div>
      </div>
    </div>
  </Teleport>
</template>
