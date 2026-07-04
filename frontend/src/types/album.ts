export interface Album {
  id: number
  name: string
  description: string
  coverPhotoId?: number | null
  createdAt: string
  photoCount: number
}
