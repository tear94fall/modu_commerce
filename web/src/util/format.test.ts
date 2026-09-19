import { describe, expect, it } from 'vitest'
import { formatPrice } from './format'

describe('formatPrice', () => {
  it('groups thousands and appends 원', () => {
    expect(formatPrice(89000)).toBe('89,000원')
    expect(formatPrice(0)).toBe('0원')
  })
})

import { formatDateTime } from './format'

describe('formatDateTime', () => {
  it('prints yyyy.MM.dd HH:mm and empty for missing or bad input', () => {
    const local = new Date('2026-09-19T14:22:00Z')
    const pad = (n: number) => String(n).padStart(2, '0')
    expect(formatDateTime('2026-09-19T14:22:00')).toBe(`${local.getFullYear()}.${pad(local.getMonth() + 1)}.${pad(local.getDate())} ${pad(local.getHours())}:${pad(local.getMinutes())}`)
    expect(formatDateTime('2026-09-19T14:22:00+09:00')).toBe(formatDateTime('2026-09-19T05:22:00Z'))
    expect(formatDateTime(null)).toBe('')
    expect(formatDateTime('nope')).toBe('')
  })
})
