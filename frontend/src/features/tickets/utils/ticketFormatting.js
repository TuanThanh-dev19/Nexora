import { formatDate } from '../../../shared/utils/formatDate'

export function formatTicketTime(value) {
  if (!value || Number.isNaN(new Date(value).getTime())) return '—'
  return formatDate(value)
}

export function formatTicketDate(value) {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value))
    return null
  const date = new Date(`${value}T00:00:00Z`)
  if (Number.isNaN(date.getTime()) || date.toISOString().slice(0, 10) !== value)
    return null
  return new Intl.DateTimeFormat('vi-VN', {
    dateStyle: 'medium',
    timeZone: 'UTC',
  }).format(date)
}
