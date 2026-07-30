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
}

export interface PhotoPatch {
  name?: string
  description?: string
  tagIds?: number[]
  categoryId?: number | null
  albumIds?: number[]
}
