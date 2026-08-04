import type { Category } from './category'
import type { Tag } from './tag'
import type { Album } from './album'

export interface ExifData {
  dateTaken?: string
  cameraModel?: string
  lensModel?: string
  focalLength?: string
  aperture?: string
  shutterSpeed?: string
  iso?: number
  latitude?: number
  longitude?: number
}

export interface Photo {
  id: number
  name: string
  description: string
  fileSize: number
  category?: Category
  tags?: Tag[]
  albums?: Album[]
  exifData?: ExifData
  createdAt?: string
  updatedAt?: string
  deletedAt?: string
  processingStatus?: string
  errorMessage?: string
  /** 图片 URL 短时签名（HMAC 时间桶），后端仅管理员上下文签发 */
  mediaToken?: string
}

export interface PhotoPatch {
  name?: string
  description?: string
  tagIds?: number[]
  categoryId?: number | null
  albumIds?: number[]
}

export type CategoryOp = 'NONE' | 'SET' | 'CLEAR'

/** 批量编辑请求：标签/相册按添加/移除列表操作，分类按三态处理 */
export interface BatchPhotoUpdateRequest {
  photoIds: number[]
  addTagIds: number[]
  removeTagIds: number[]
  addAlbumIds: number[]
  removeAlbumIds: number[]
  categoryOp: CategoryOp
  categoryId?: number | null
}
