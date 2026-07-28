export interface Album {
  id: number
  name: string
  description: string
  coverPhotoId?: number | null
  createdAt: string
  deletedAt?: string
  photoCount: number
}
