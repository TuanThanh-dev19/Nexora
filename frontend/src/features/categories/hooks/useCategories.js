import { useQuery } from '@tanstack/react-query'
import { categoryApi } from '../api/categoryApi'

export const categoryKeys = {
  all: ['categories'],
  versions: (id) => ['categories', String(id), 'versions'],
  form: (id) => ['category-form', String(id)],
}
export const useCategories = () =>
  useQuery({ queryKey: categoryKeys.all, queryFn: categoryApi.list })
export const useVersions = (id) =>
  useQuery({
    queryKey: categoryKeys.versions(id),
    queryFn: () => categoryApi.versions(id),
  })
export const useCategoryForm = (id) =>
  useQuery({
    queryKey: categoryKeys.form(id),
    queryFn: () => categoryApi.form(id),
    staleTime: Infinity,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  })
export const useSkills = () =>
  useQuery({ queryKey: ['active-skills'], queryFn: categoryApi.skills })
export const useSlas = () =>
  useQuery({ queryKey: ['active-slas'], queryFn: categoryApi.slas })
