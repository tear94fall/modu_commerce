import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from '../api/client'
import { CLIENT_ID, GRANT_GOOGLE, GRANT_REFRESH, loginWithGoogle, logoutSession, refreshSession, storedAccessToken } from './session'

const json = (status: number, body: unknown) => new Response(JSON.stringify(body), { status })
const formOf = (call: [RequestInfo | URL, RequestInit?]) => Object.fromEntries(new URLSearchParams(String(call[1]?.body)))

describe('browser session', () => {
  beforeEach(() => {
    localStorage.clear()
    delete window.ModuApp
  })
  afterEach(() => vi.restoreAllMocks())

  it('exchanges a Google id token with the commerce client and stores both tokens', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(json(200, { access_token: 'a1', refresh_token: 'r1' }))

    await loginWithGoogle('google-id')
    expect(fetchMock.mock.calls[0][0]).toBe('/auth-service/oauth2/token')
    expect(formOf(fetchMock.mock.calls[0])).toEqual({ grant_type: GRANT_GOOGLE, client_id: CLIENT_ID, id_token: 'google-id' })
    expect(storedAccessToken()).toBe('a1')
  })

  it('refreshes through the api client on 401 and retries with the new token', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(json(200, { access_token: 'a1', refresh_token: 'r1' }))
    await loginWithGoogle('google-id')
    fetchMock.mockClear()

    fetchMock
      .mockResolvedValueOnce(new Response('', { status: 401 }))
      .mockResolvedValueOnce(json(200, { access_token: 'a2' }))
      .mockResolvedValueOnce(json(200, { ok: true }))

    await expect(api('/api/v1/cart')).resolves.toEqual({ ok: true })
    expect(formOf(fetchMock.mock.calls[1])).toEqual({ grant_type: GRANT_REFRESH, client_id: CLIENT_ID, refresh_token: 'r1' })
    expect((fetchMock.mock.calls[2][1]?.headers as Headers).get('Authorization')).toBe('Bearer a2')
    expect(storedAccessToken()).toBe('a2')
  })

  it('refreshes once for parallel 401s and hands the rotated token to the others', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(json(200, { access_token: 'a1', refresh_token: 'r1' }))
    await loginWithGoogle('google-id')
    fetchMock.mockClear()
    fetchMock.mockResolvedValueOnce(json(200, { access_token: 'a2', refresh_token: 'r2' }))

    const results = await Promise.all([refreshSession('a1'), refreshSession('a1'), refreshSession('a1')])
    expect(results).toEqual(['a2', 'a2', 'a2'])
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(await refreshSession('a1')).toBe('a2') // 이미 바뀐 토큰이면 서버를 부르지 않는다
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('returns null without a refresh token and clears everything on logout', async () => {
    expect(await refreshSession(null)).toBeNull()
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(json(200, { access_token: 'a1', refresh_token: 'r1' })).mockResolvedValueOnce(new Response(null, { status: 200 }))
    await loginWithGoogle('google-id')
    await logoutSession()
    expect(storedAccessToken()).toBeNull()
    expect(await refreshSession('a1')).toBeNull()
  })
})
