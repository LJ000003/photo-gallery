export interface Album {
  id: number
  name: string
  description: string
  coverPhotoId?: number | null
  createdAt: string
  deletedAt?: string
  photoCount: number
  /** 封面图短时签名（后端仅管理员上下文签发） */
  mediaToken?: string
}
