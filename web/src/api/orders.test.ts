import { afterEach, describe, expect, it, vi } from 'vitest'
import { fakeServer } from '../test-fixtures/server'
import { ApiError, isPriceChanged } from './client'
import { createOrder } from './orders'

describe('createOrder', () => {
  afterEach(() => vi.restoreAllMocks())

  it('sends the Idempotency-Key header and the shown payment amount', async () => {
    const { fetchMock } = fakeServer({ 'POST /api-public/v1/orders': () => ({ id: 1, status: 'PAID', items: [] }) })
    await createOrder(1, [{ skuId: 3, quantity: 1 }], [], 500, null, { idempotencyKey: 'key-1', expectedPaymentAmount: 9500 })

    const init = fetchMock.mock.calls[0][1]
    expect(new Headers(init?.headers).get('Idempotency-Key')).toBe('key-1')
    expect(JSON.parse(String(init?.body))).toEqual({ addressId: 1, items: [{ skuId: 3, quantity: 1 }], cartItemIds: [], usePoints: 500, expectedPaymentAmount: 9500 })
  })

  it('sends neither without options (older callers)', async () => {
    const { fetchMock } = fakeServer({ 'POST /api-public/v1/orders': () => ({ id: 1, status: 'PAID', items: [] }) })
    await createOrder(1, [{ skuId: 3, quantity: 1 }], [])

    const init = fetchMock.mock.calls[0][1]
    expect(new Headers(init?.headers).has('Idempotency-Key')).toBe(false)
    expect(JSON.parse(String(init?.body))).not.toHaveProperty('expectedPaymentAmount')
  })
})

describe('isPriceChanged', () => {
  it('is a 409 with code PRICE_CHANGED only', () => {
    expect(isPriceChanged(new ApiError(409, JSON.stringify({ code: 'PRICE_CHANGED', message: 'x', paymentAmount: 1 })))).toBe(true)
    expect(isPriceChanged(new ApiError(409, JSON.stringify({ message: '이미 처리된 요청이에요.' })))).toBe(false)
    expect(isPriceChanged(new ApiError(400, JSON.stringify({ code: 'PRICE_CHANGED' })))).toBe(false)
  })
})
