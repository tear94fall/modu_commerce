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

/** 서버 오류 본문의 `code`(예: CUSTOMER_REQUIRED). 없으면 undefined. */
export function serverCode(body: string): string | undefined {
  try {
    const parsed = JSON.parse(body) as { code?: unknown }
    return typeof parsed.code === 'string' ? parsed.code : undefined
  } catch {
    return undefined
  }
}

/** 결제 금액이 바뀌어 주문을 막았다(409). 본문의 paymentAmount 가 서버가 다시 계산한 금액이다. */
export const PRICE_CHANGED = 'PRICE_CHANGED'

export const isPriceChanged = (e: unknown): e is ApiError => e instanceof ApiError && e.status === 409 && serverCode(e.body) === PRICE_CHANGED

/** 커머스 가입(약관 동의) 전이라 막힌 요청. 서버는 403(가드) 또는 404(GET /me/customer) 에 이 코드를 준다. */
export const CUSTOMER_REQUIRED = 'CUSTOMER_REQUIRED'

export const isCustomerRequired = (e: unknown) => e instanceof ApiError && (e.status === 403 || e.status === 404) && serverCode(e.body) === CUSTOMER_REQUIRED

type CustomerRequiredHandler = () => void
let customerRequiredHandler: CustomerRequiredHandler | null = null

/** 403 CUSTOMER_REQUIRED 를 받았을 때 부를 함수(가입 화면으로 보내기). CustomerProvider 가 등록하고, 해제 함수를 돌려준다. */
export function onCustomerRequired(handler: CustomerRequiredHandler): () => void {
  customerRequiredHandler = handler
  return () => {
    if (customerRequiredHandler === handler) customerRequiredHandler = null
  }
}

/** api() 옵션. `quiet` 이면 403 CUSTOMER_REQUIRED 여도 가입 화면으로 보내지 않는다(상단바 뱃지 같은 배경 요청). */
export interface ApiInit extends RequestInit {
  quiet?: boolean
}

/** 서버가 준 문구가 있으면 그것, 없으면(네트워크 · 401 · 본문 없음) fallback. */
export function errorMessage(e: unknown, fallback: string): string {
  return e instanceof ApiError && e.status !== 401 && e.message && !e.message.startsWith('HTTP ') ? e.message : fallback
}

/**
 * commerce-service / auth-service 호출. 같은 출처(프록시)라 경로만 준다.
 * 401 이면 토큰을 한 번 갱신해 재시도하고, 그래도 401 이면 세션 만료 처리.
 * 403 CUSTOMER_REQUIRED(커머스 가입 전)면 등록된 처리(가입 화면 이동)를 부른 뒤 오류를 그대로 던진다.
 */
export async function api<T>(path: string, apiInit: ApiInit = {}): Promise<T> {
  const { quiet = false, ...init } = apiInit
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
  if (!res.ok) {
    const error = new ApiError(res.status, await res.text())
    if (res.status === 403 && !quiet && serverCode(error.body) === CUSTOMER_REQUIRED) customerRequiredHandler?.()
    throw error
  }
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
