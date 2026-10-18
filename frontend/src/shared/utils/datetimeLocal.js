/** Convert an offset rule to the browser's local wall time without changing its instant. */
export function datetimeLocal(value) {
  if (!value) return undefined
  const date = new Date(value)
  if (!Number.isFinite(date.getTime())) return undefined
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000)
    .toISOString()
    .slice(0, -1)
}
