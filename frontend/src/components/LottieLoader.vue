<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import lottie, { type AnimationItem } from 'lottie-web'

const props = defineProps<{
  name: string
  size?: number
}>()

const container = ref<HTMLElement | null>(null)
let anim: AnimationItem | null = null

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const animations: Record<string, any> = {
  loading: {
    v: '5.5.2',
    fr: 30,
    ip: 0,
    op: 60,
    w: 80,
    h: 80,
    nm: 'Loading',
    ddd: 0,
    assets: [],
    layers: [
      {
        ddd: 0,
        ind: 1,
        ty: 4,
        nm: 'ring',
        sr: 1,
        ks: {
          o: {
            a: 1,
            k: [
              { t: 0, s: [100] },
              { t: 30, s: [25] },
              { t: 60, s: [100] },
            ],
            ix: 11,
          },
          r: {
            a: 1,
            k: [
              { t: 0, s: [0] },
              { t: 60, s: [360] },
            ],
            ix: 10,
          },
          p: { a: 0, k: [40, 40, 0], ix: 2 },
          a: { a: 0, k: [0, 0, 0], ix: 1 },
          s: { a: 0, k: [100, 100, 100], ix: 6 },
        },
        shapes: [
          { ty: 'el', p: { a: 0, k: [0, 0] }, s: { a: 0, k: [30, 30] }, d: 1 },
          {
            ty: 'st',
            c: { a: 0, k: [0, 0.831, 1, 1] },
            o: { a: 0, k: 100 },
            w: { a: 0, k: 3 },
            lc: 2,
            lj: 1,
          },
          {
            ty: 'tm',
            s: {
              a: 1,
              k: [
                { t: 0, s: [0] },
                { t: 45, s: [75] },
                { t: 60, s: [95] },
              ],
              ix: 1,
            },
            e: {
              a: 1,
              k: [
                { t: 0, s: [5] },
                { t: 30, s: [85] },
                { t: 60, s: [100] },
              ],
              ix: 2,
            },
            o: { a: 0, k: 0 },
            m: 1,
          },
        ],
        ip: 0,
        op: 61,
        st: 0,
      },
    ],
  },

  empty: {
    v: '5.5.2',
    fr: 30,
    ip: 0,
    op: 90,
    w: 200,
    h: 200,
    nm: 'Empty',
    ddd: 0,
    assets: [],
    layers: [
      {
        ddd: 0,
        ind: 1,
        ty: 4,
        nm: 'icon',
        sr: 1,
        ks: {
          o: {
            a: 1,
            k: [
              { t: 0, s: [0] },
              { t: 15, s: [100] },
            ],
            ix: 11,
          },
          r: { a: 0, k: 0, ix: 10 },
          p: { a: 0, k: [100, 100, 0], ix: 2 },
          a: { a: 0, k: [0, 0, 0], ix: 1 },
          s: {
            a: 1,
            k: [
              { t: 0, s: [70, 70, 100] },
              { t: 20, s: [105, 105, 100] },
              { t: 30, s: [95, 95, 100] },
              { t: 45, s: [100, 100, 100] },
            ],
            ix: 6,
          },
        },
        shapes: [
          { ty: 'rc', p: { a: 0, k: [0, -8] }, s: { a: 0, k: [36, 28] }, r: { a: 0, k: 8 } },
          {
            ty: 'st',
            c: { a: 0, k: [0, 0.831, 1, 1] },
            o: { a: 0, k: 80 },
            w: { a: 0, k: 2.5 },
            lc: 1,
            lj: 1,
            d: [{ n: 'd', nm: 'dash', v: { a: 0, k: 8 } }],
          },
          { ty: 'fl', c: { a: 0, k: [0, 0.831, 1, 0.12] }, o: { a: 0, k: 100 }, r: 1 },
          { ty: 'el', p: { a: 0, k: [0, -10] }, s: { a: 0, k: [14, 14] }, d: 1 },
          {
            ty: 'st',
            c: { a: 0, k: [0.66, 0.333, 0.969, 1] },
            o: { a: 0, k: 90 },
            w: { a: 0, k: 2 },
            lc: 1,
            lj: 1,
          },
          { ty: 'fl', c: { a: 0, k: [0.66, 0.333, 0.969, 0.15] }, o: { a: 0, k: 100 }, r: 1 },
          {
            ty: 'gr',
            o: { a: 0, k: 100 },
            g: { p: 3, k: { a: 0, k: [0, 1, 1, 1, 0.5, 0.33, 0.97, 1, 1, 1, 0.24, 0.61] } },
            s: { a: 0, k: [0, -20] },
            e: { a: 0, k: [0, 18] },
            t: 1,
          },
        ],
        ip: 0,
        op: 91,
        st: 0,
      },
    ],
  },

  uploading: {
    v: '5.5.2',
    fr: 30,
    ip: 0,
    op: 60,
    w: 24,
    h: 24,
    nm: 'Upload',
    ddd: 0,
    assets: [],
    layers: [
      {
        ddd: 0,
        ind: 1,
        ty: 4,
        nm: 'spinner',
        sr: 1,
        ks: {
          o: { a: 0, k: 100, ix: 11 },
          r: {
            a: 1,
            k: [
              { t: 0, s: [0] },
              { t: 60, s: [360] },
            ],
            ix: 10,
          },
          p: { a: 0, k: [12, 12, 0], ix: 2 },
          a: { a: 0, k: [0, 0, 0], ix: 1 },
          s: { a: 0, k: [100, 100, 100], ix: 6 },
        },
        shapes: [
          { ty: 'el', p: { a: 0, k: [0, 0] }, s: { a: 0, k: [14, 14] }, d: 1 },
          {
            ty: 'st',
            c: { a: 0, k: [1, 1, 1, 1] },
            o: { a: 0, k: 100 },
            w: { a: 0, k: 2.5 },
            lc: 2,
            lj: 1,
          },
          {
            ty: 'tm',
            s: {
              a: 1,
              k: [
                { t: 0, s: [0] },
                { t: 60, s: [80] },
              ],
              ix: 1,
            },
            e: {
              a: 1,
              k: [
                { t: 0, s: [10] },
                { t: 35, s: [95] },
                { t: 60, s: [100] },
              ],
              ix: 2,
            },
            o: { a: 0, k: 0 },
            m: 1,
          },
        ],
        ip: 0,
        op: 61,
        st: 0,
      },
    ],
  },
}

onMounted(() => {
  if (!container.value || !animations[props.name]) return
  try {
    anim = lottie.loadAnimation({
      container: container.value,
      animationData: animations[props.name],
      renderer: 'svg',
      loop: true,
      autoplay: true,
    })
  } catch {
    // lottie 初始化失败 — 静默降级
  }
})

onUnmounted(() => {
  if (anim) anim.destroy()
})
</script>

<template>
  <div
    ref="container"
    class="lottie-container"
    :style="{ width: (size || 80) + 'px', height: (size || 80) + 'px' }"
  ></div>
</template>

<style scoped>
.lottie-container {
  display: inline-block;
  vertical-align: middle;
  pointer-events: none;
}
.lottie-container svg {
  pointer-events: none;
}
</style>
