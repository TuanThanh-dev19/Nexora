function decimal(value) {
  const match = String(value)
    .replace(/^([+-]?)\./, (_, sign) => `${sign}0.`)
    .match(/^([+-]?)(\d+)(?:\.(\d*))?(?:e([+-]?\d+))?$/i)
  if (!match) throw new Error('Invalid decimal')
  const scale = (match[3]?.length ?? 0) - Number(match[4] ?? 0)
  if (Math.abs(scale) > 4000) throw new Error('Decimal exponent out of range')
  let integer = BigInt(
    (match[1] === '-' ? '-' : '') + match[2] + (match[3] ?? ''),
  )
  if (scale < 0) integer *= 10n ** BigInt(-scale)
  return { integer, scale: Math.max(0, scale) }
}

/** Mirrors BigDecimal remainder on the wire value, without an epsilon tolerance. */
export function decimalStepMatches(value, base, step) {
  try {
    const values = [value, base, step].map(decimal)
    const scale = Math.max(...values.map((v) => v.scale))
    const [number, start, increment] = values.map(
      (v) => v.integer * 10n ** BigInt(scale - v.scale),
    )
    return increment > 0n && (number - start) % increment === 0n
  } catch {
    return false
  }
}
