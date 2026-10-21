import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { technicianApi } from '../api/technicianApi'

export const technicianKeys = {
  all: ['technicians'],
  list: (params) => [...technicianKeys.all, 'list', params],
  detail: (id) => [...technicianKeys.all, 'detail', String(id)],
  skills: () => ['skills', 'active'],
  locations: (parentId) => ['locations', 'active', parentId ?? null],
}

export function useTechnicians(params = {}) {
  return useQuery({
    queryKey: technicianKeys.list(params),
    queryFn: ({ signal }) => technicianApi.list(params, signal),
  })
}

export function useTechnician(technicianId, options = {}) {
  return useQuery({
    queryKey: technicianKeys.detail(technicianId),
    queryFn: ({ signal }) => technicianApi.getById(technicianId, signal),
    enabled: Boolean(technicianId),
    ...options,
  })
}

export function useActiveSkills() {
  return useQuery({
    queryKey: technicianKeys.skills(),
    queryFn: ({ signal }) => technicianApi.getSkills(signal),
  })
}

export function useTechnicianLocations(parentId) {
  return useQuery({
    queryKey: technicianKeys.locations(parentId),
    queryFn: ({ signal }) => technicianApi.getLocations(parentId, signal),
  })
}

export function useUpdateTechnicianCapacity() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ technicianId, payload }) =>
      technicianApi.updateCapacity(technicianId, payload),
    onSuccess: (updatedData, { technicianId }) => {
      if (updatedData) {
        queryClient.setQueryData(
          technicianKeys.detail(technicianId),
          (old) => (old ? { ...old, ...updatedData } : updatedData),
        )
      }
      queryClient.invalidateQueries({ queryKey: technicianKeys.all })
    },
  })
}

export function useUpdateTechnicianSkills() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ technicianId, payload }) =>
      technicianApi.updateSkills(technicianId, payload),
    onSuccess: (updatedData, { technicianId }) => {
      if (updatedData) {
        queryClient.setQueryData(
          technicianKeys.detail(technicianId),
          (old) => (old ? { ...old, ...updatedData } : updatedData),
        )
      }
      queryClient.invalidateQueries({ queryKey: technicianKeys.all })
    },
  })
}

export function useUpdateTechnicianServiceAreas() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ technicianId, payload }) =>
      technicianApi.updateServiceAreas(technicianId, payload),
    onSuccess: (updatedData, { technicianId }) => {
      if (updatedData) {
        queryClient.setQueryData(
          technicianKeys.detail(technicianId),
          (old) => (old ? { ...old, ...updatedData } : updatedData),
        )
      }
      queryClient.invalidateQueries({ queryKey: technicianKeys.all })
    },
  })
}
