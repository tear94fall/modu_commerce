import { afterEach, describe, expect, it, vi } from 'vitest'
import { newIdempotencyKey } from './idempotency'

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/

describe('newIdempotencyKey', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('makes a fresh v4 UUID each time', () => {
    const a = newIdempotencyKey()
    expect(a).toMatch(UUID)
    expect(newIdempotencyKey()).not.toBe(a)
  })

  it('falls back to getRandomValues where randomUUID is missing (http WebView)', () => {
    vi.stubGlobal('crypto', { getRandomValues: (b: Uint8Array) => b.fill(7) })
    expect(newIdempotencyKey()).toMatch(UUID)
  })
})
