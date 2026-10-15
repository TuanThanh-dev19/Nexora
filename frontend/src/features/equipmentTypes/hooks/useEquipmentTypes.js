import { useQuery } from '@tanstack/react-query'
import { equipmentTypeApi } from '../api/equipmentTypeApi'

const retry = (count, error) =>
  count < 1 && (error?.response?.status == null || error.response.status >= 500)

export const equipmentTypeKeys = {
  all: ['equipment-types'],
  lists: () => [...equipmentTypeKeys.all, 'list'],
  list: (active) => [...equipmentTypeKeys.lists(), { active: Boolean(active) }],
}

export function useEquipmentTypes(active = true, enabled = true) {
  return useQuery({
    queryKey: equipmentTypeKeys.list(active),
    queryFn: ({ signal }) => equipmentTypeApi.list(active, signal),
    enabled,
    retry,
  })
}
