export type MirrorMode = 'horizontal' | 'vertical' | 'none'

export interface TransformParams {
  rotate: number
  mirror: MirrorMode
  cx: number | null
  cy: number | null
  cw: number | null
  ch: number | null
}

export interface ImageEditResult {
  blob: Blob
  params: TransformParams
}
