export type SortField = 'time' | 'name' | 'size'
export type SortOrder = 'asc' | 'desc'

export interface TimelineExifItem {
  id: number
  photoId: number
  photoName: string
  photoThumbnail: string
  dateTaken: string
  cameraModel?: string
  /** 图片 URL 短时签名（后端仅管理员上下文签发） */
  mediaToken?: string
}

export interface MapExifItem {
  photoId: number
  photoName: string
  photoThumbnail: string
  latitude: number
  longitude: number
  /** 图片 URL 短时签名（后端仅管理员上下文签发） */
  mediaToken?: string
}
