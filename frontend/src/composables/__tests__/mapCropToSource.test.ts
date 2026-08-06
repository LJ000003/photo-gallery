import { describe, expect, it } from 'vitest'
import { mapCropToSource, type CropBox } from '../useImageEditorCanvas'
import type { MirrorMode } from '../../types/transform'

/**
 * 裁剪坐标逆映射的几何验证：
 * 后端顺序 = 按原图坐标裁剪 → 旋转 → 镜像（PhotoTransformService）；
 * 前端裁剪框画在「旋转/镜像后」的输出图上。
 * mapCropToSource 必须满足：原图矩形 C_src 经正向变换（先旋转后镜像，
 * 归一化坐标）恰好落在输出矩形 C_out 上——四个角点一一对应。
 */
function forward(
  s: number,
  t: number,
  rotation: number,
  mirror: MirrorMode,
): { u: number; v: number } {
  let u: number
  let v: number
  switch (rotation) {
    case 90:
      u = 1 - t
      v = s
      break
    case 180:
      u = 1 - s
      v = 1 - t
      break
    case 270:
      u = t
      v = 1 - s
      break
    default:
      u = s
      v = t
  }
  if (mirror === 'horizontal') u = 1 - u
  else if (mirror === 'vertical') v = 1 - v
  return { u, v }
}

function expectRoundTrip(crop: CropBox, rotation: number, mirror: MirrorMode): void {
  const src = mapCropToSource(crop, rotation, mirror)
  const corners = (b: CropBox): [number, number][] => [
    [b.x, b.y],
    [b.x + b.w, b.y],
    [b.x, b.y + b.h],
    [b.x + b.w, b.y + b.h],
  ]
  // 集合匹配（非同序）：镜像变换行列式为 -1 会翻转角点顺序
  const expected = new Set(corners(crop).map(([u, v]) => `${u.toFixed(9)},${v.toFixed(9)}`))
  for (const [s, t] of corners(src)) {
    const { u, v } = forward(s, t, rotation, mirror)
    expect(expected.has(`${u.toFixed(9)},${v.toFixed(9)}`)).toBe(true)
  }
  // 映射结果必须落在 [0,1]
  expect(src.x).toBeGreaterThanOrEqual(0)
  expect(src.y).toBeGreaterThanOrEqual(0)
  expect(src.x + src.w).toBeLessThanOrEqual(1)
  expect(src.y + src.h).toBeLessThanOrEqual(1)
}

const ROTATIONS = [0, 90, 180, 270]
const MIRRORS: MirrorMode[] = ['none', 'horizontal', 'vertical']

describe('mapCropToSource', () => {
  it.each(ROTATIONS.flatMap((r) => MIRRORS.map((m) => [r, m] as const)))(
    'rotation=%s mirror=%s 几何往返一致',
    (rotation, mirror) => {
      const crop = { x: 0.25, y: 0.375, w: 0.5, h: 0.25 }
      expectRoundTrip(crop, rotation, mirror)
    },
  )

  it('0° 无镜像为恒等映射', () => {
    const crop = { x: 0.1, y: 0.2, w: 0.6, h: 0.4 }
    expect(mapCropToSource(crop, 0, 'none')).toEqual(crop)
  })

  it('90° 旋转交换宽高并翻转位置', () => {
    // 输出 (x,y,w,h) → 原图 (y, 1-x-w, h, w)
    expect(mapCropToSource({ x: 0.25, y: 0.5, w: 0.5, h: 0.25 }, 90, 'none')).toEqual({
      x: 0.5,
      y: 0.25,
      w: 0.25,
      h: 0.5,
    })
  })

  it('180° 旋转双向翻转', () => {
    const r = mapCropToSource({ x: 0.1, y: 0.2, w: 0.3, h: 0.4 }, 180, 'none')
    expect(r.x).toBeCloseTo(0.6, 9)
    expect(r.y).toBeCloseTo(0.4, 9)
    expect(r.w).toBe(0.3)
    expect(r.h).toBe(0.4)
  })

  it('270° 旋转交换宽高（逆时针）', () => {
    expect(mapCropToSource({ x: 0.25, y: 0.5, w: 0.5, h: 0.25 }, 270, 'none')).toEqual({
      x: 0.25,
      y: 0.25,
      w: 0.25,
      h: 0.5,
    })
  })

  it('水平镜像取反 x 方向', () => {
    expect(mapCropToSource({ x: 0.1, y: 0.2, w: 0.3, h: 0.4 }, 0, 'horizontal')).toEqual({
      x: 0.6,
      y: 0.2,
      w: 0.3,
      h: 0.4,
    })
  })

  it('垂直镜像取反 y 方向', () => {
    const r = mapCropToSource({ x: 0.1, y: 0.2, w: 0.3, h: 0.4 }, 0, 'vertical')
    expect(r.x).toBe(0.1)
    expect(r.y).toBeCloseTo(0.4, 9)
    expect(r.w).toBe(0.3)
    expect(r.h).toBe(0.4)
  })

  it('边角用例（贴近边界）不越界', () => {
    for (const rotation of ROTATIONS) {
      for (const mirror of MIRRORS) {
        expectRoundTrip({ x: 0.05, y: 0.05, w: 0.9, h: 0.9 }, rotation, mirror)
        expectRoundTrip({ x: 0.001, y: 0.002, w: 0.998, h: 0.997 }, rotation, mirror)
      }
    }
  })
})
