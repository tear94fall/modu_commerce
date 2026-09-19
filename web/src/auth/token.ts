import { bridge } from '../bridge/app'

const KEY = 'modu-commerce-token'

/** 앱 안에서는 브리지가 토큰의 주인이고, 브라우저에서는 localStorage 다(개발용 붙여넣기 → 다음 단계에서 웹 로그인). */
export function getToken(): string | null {
  const b = bridge()
  if (b) return b.getAccessToken() || null
  return localStorage.getItem(KEY)
}

export const setToken = (t: string) => localStorage.setItem(KEY, t)

export const clearToken = () => localStorage.removeItem(KEY)

/** 401 뒤의 갱신. 앱이면 브리지가 refresh_token 으로 한 번 갱신하고, 브라우저에는 갱신 수단이 없다. */
export function refreshToken(): string | null {
  const b = bridge()
  if (!b) return null
  return b.refreshAccessToken() || null
}

/** 갱신도 실패했을 때. 앱은 로그인 화면으로, 브라우저는 토큰을 지우고 /login 으로. */
export function sessionExpired() {
  const b = bridge()
  if (b) {
    b.onSessionExpired()
    return
  }
  clearToken()
  if (!location.pathname.startsWith('/login')) location.assign('/login')
}
