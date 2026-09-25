import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ModuAppBridge } from '../bridge/app'
import { setToken } from '../auth/token'
import { api, ApiError, serverMessage } from './client'

const json = (status: number, body: unknown) => new Response(JSON.stringify(body), { status })

const fakeBridge = (over: Partial<ModuAppBridge> = {}): ModuAppBridge => ({
  getAccessToken: () => 'app-token',
  refreshAccessToken: (failed: string) => (failed === 'app-token' ? 'fresh-token' : ''),
  onSessionExpired: vi.fn(),
  logout: vi.fn(),
  getProfile: () => '{}',
  ...over,
})

describe('api', () => {
  beforeEach(() => {
    localStorage.clear()
    delete window.ModuApp
  })
  afterEach(() => vi.restoreAllMocks())

  it('sends the bridge token when running inside the app', async () => {
    window.ModuApp = fakeBridge()
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(json(200, { ok: 1 }))

    await expect(api<{ ok: number }>('/api/v1/cart')).resolves.toEqual({ ok: 1 })
    const headers = fetchMock.mock.calls[0][1]?.headers as Headers
    expect(headers.get('Authorization')).toBe('Bearer app-token')
  })

  it('falls back to localStorage without a bridge', async () => {
    setToken('web-token')
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(json(200, []))

    await api('/api/v1/categories')
    expect((fetchMock.mock.calls[0][1]?.headers as Headers).get('Authorization')).toBe('Bearer web-token')
  })

  it('refreshes once through the bridge on 401 and retries with the new token', async () => {
    window.ModuApp = fakeBridge()
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response('', { status: 401 }))
      .mockResolvedValueOnce(json(200, { ok: true }))

    await expect(api('/api/v1/cart')).resolves.toEqual({ ok: true })
    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect((fetchMock.mock.calls[1][1]?.headers as Headers).get('Authorization')).toBe('Bearer fresh-token')
  })

  it('tells the app the session expired when refresh fails', async () => {
    const onSessionExpired = vi.fn()
    window.ModuApp = fakeBridge({ refreshAccessToken: () => '', onSessionExpired })
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('', { status: 401 }))

    await expect(api('/api/v1/cart')).rejects.toMatchObject({ status: 401 })
    expect(onSessionExpired).toHaveBeenCalledTimes(1)
  })

  it('exposes the server message of a 400 body', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(json(400, { message: '수량은 1 이상이어야 합니다' }))

    const err = await api('/api/v1/cart/items', { method: 'POST', body: '{}' }).catch((e: unknown) => e)
    expect(err).toBeInstanceOf(ApiError)
    expect((err as ApiError).message).toBe('수량은 1 이상이어야 합니다')
    expect(serverMessage('not json')).toBeUndefined()
  })

  it('returns undefined for an empty 204', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null, { status: 204 }))
    await expect(api('/api/v1/wishlist/3', { method: 'DELETE' })).resolves.toBeUndefined()
  })
})
