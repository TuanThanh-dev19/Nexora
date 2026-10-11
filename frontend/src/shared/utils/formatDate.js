export function formatDate(value, locale = 'vi-VN') {
  if (!value || Number.isNaN(new Date(value).getTime())) return '—'
  return new Intl.DateTimeFormat(locale, {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone: 'Asia/Bangkok',
  }).format(new Date(value))
}
