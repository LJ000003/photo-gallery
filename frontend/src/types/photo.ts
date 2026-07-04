import type { Category } from './category'
import type { Tag } from './tag'
import type { Album } from './album'

export interface Photo {
  id: number
  name: string
  description: string
  fileSize: number
  category?: Category
  tags?: Tag[]
  albums?: Album[]
  createdAt?: string
  updatedAt?: string
}

export interface PhotoPatch {
  name?: string
  description?: string
  tagIds?: number[]
  categoryId?: number | null
  albumIds?: number[]
}
