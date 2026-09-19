import { bridge } from '../bridge/app'
import { clearSession, refreshSession, save, storedAccessToken } from './session'

/** 앱 안에서는 브리지가 토큰의 주인이고, 브라우저에서는 localStorage(Google 로그인 또는 개발용 붙여넣기)다. */
export function getToken(): string | null {
  const b = bridge()
  if (b) return b.getAccessToken() || null
  return storedAccessToken()
}

/** 개발용: 액세스 토큰만 저장(refresh 없음 → 만료되면 다시 로그인). */
export const setToken = (t: string) => save({ accessToken: t, refreshToken: null })

export const clearToken = () => clearSession()

/** [failedToken] 으로 401 을 받은 뒤의 갱신. 앱은 브리지가, 브라우저는 저장된 refresh_token 으로 한 번만 갱신한다. */
export async function refreshToken(failedToken: string | null): Promise<string | null> {
  const b = bridge()
  if (b) return b.refreshAccessToken(failedToken ?? '') || null
  return refreshSession(failedToken)
}

/** 갱신도 실패했을 때. 앱은 로그인 화면으로, 브라우저는 토큰을 지우고 /login 으로. */
export function sessionExpired() {
  const b = bridge()
  if (b) {
    b.onSessionExpired()
    return
  }
  clearSession()
  if (!location.pathname.startsWith('/login')) location.assign('/login')
}
