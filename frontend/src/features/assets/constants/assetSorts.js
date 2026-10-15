export const ASSET_SORT_OPTIONS = [
  { value: 'createdAt,desc', label: 'Mới tạo trước' },
  { value: 'createdAt,asc', label: 'Cũ trước' },
  { value: 'updatedAt,desc', label: 'Mới cập nhật trước' },
  { value: 'updatedAt,asc', label: 'Cập nhật cũ trước' },
  { value: 'assetCode,asc', label: 'Mã tài sản (A-Z)' },
  { value: 'assetCode,desc', label: 'Mã tài sản (Z-A)' },
  { value: 'id,asc', label: 'ID tăng dần' },
  { value: 'id,desc', label: 'ID giảm dần' },
]

export const ALLOWED_ASSET_SORT_FIELDS = new Set([
  'createdAt',
  'updatedAt',
  'assetCode',
  'id',
])

export const ALLOWED_ASSET_QUERY_PARAMS = new Set([
  'page',
  'size',
  'sort',
  'locationId',
  'status',
])
