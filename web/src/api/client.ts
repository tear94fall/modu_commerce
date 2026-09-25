import { getToken, refreshToken, sessionExpired } from '../auth/token'

export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')

export class ApiError extends Error {
  status: number
  body: string
  constructor(status: number, body: string) {
    super(serverMessage(body) ?? `HTTP ${status}`)
    this.status = status
    this.body = body
  }
}

/** 서버 오류 본문 `{"message": "..."}` 의 문구. 400 검증 오류를 그대로 보여 줄 때 쓴다. */
export function serverMessage(body: string): string | undefined {
  try {
    const parsed = JSON.parse(body) as { message?: unknown }
    return typeof parsed.message === 'string' && parsed.message.length > 0 ? parsed.message : undefined
  } catch {
    return undefined
  }
}

/** 서버가 준 문구가 있으면 그것, 없으면(네트워크 · 401 · 본문 없음) fallback. */
export function errorMessage(e: unknown, fallback: string): string {
  return e instanceof ApiError && e.status !== 401 && e.message && !e.message.startsWith('HTTP ') ? e.message : fallback
}

/**
 * commerce-service / auth-service 호출. 같은 출처(프록시)라 경로만 준다.
 * 401 이면 토큰을 한 번 갱신해 재시도하고, 그래도 401 이면 세션 만료 처리.
 */
export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token = getToken()
  let res = await send(path, init, token)
  if (res.status === 401) {
    const fresh = await refreshToken(token)
    if (fresh) res = await send(path, init, fresh)
  }
  if (res.status === 401) {
    sessionExpired()
    throw new ApiError(401, '')
  }
  if (!res.ok) throw new ApiError(res.status, await res.text())
  const text = await res.text()
  if (res.status === 204 || text.length === 0) return undefined as T
  return JSON.parse(text) as T
}

function send(path: string, init: RequestInit, token: string | null) {
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  if (token) headers.set('Authorization', `Bearer ${token}`)
  return fetch(`${API_BASE_URL}${path}`, { ...init, headers })
}
