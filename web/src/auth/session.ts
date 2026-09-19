/**
 * 브라우저 세션(브리지가 없을 때). auth-service /oauth2/token 을 같은 출처 프록시로 부른다.
 * 앱의 OAuthClient 와 같은 client_id·grant 문자열이어야 한다.
 */
export const CLIENT_ID = 'modu-commerce'
export const GRANT_GOOGLE = 'urn:modu:params:oauth:grant-type:google_id_token'
export const GRANT_REFRESH = 'refresh_token'

const TOKEN_URL = '/auth-service/oauth2/token'
const REVOKE_URL = '/auth-service/oauth2/revoke'
const ACCESS_KEY = 'modu-commerce-token'
const REFRESH_KEY = 'modu-commerce-refresh-token'

export interface TokenPair {
  accessToken: string
  refreshToken: string | null
}

const FORM = { 'Content-Type': 'application/x-www-form-urlencoded' }

async function tokenRequest(form: Record<string, string>): Promise<TokenPair> {
  const res = await fetch(TOKEN_URL, { method: 'POST', headers: FORM, body: new URLSearchParams(form).toString() })
  if (!res.ok) throw new Error(`token ${res.status}`)
  const body = (await res.json()) as { access_token?: string; refresh_token?: string }
  if (!body.access_token) throw new Error('no access_token')
  return { accessToken: body.access_token, refreshToken: body.refresh_token ?? null }
}

/** Google ID 토큰 → 커머스 토큰. 성공하면 저장한다. */
export async function loginWithGoogle(idToken: string): Promise<TokenPair> {
  const pair = await tokenRequest({ grant_type: GRANT_GOOGLE, client_id: CLIENT_ID, id_token: idToken })
  save(pair)
  return pair
}

let inflight: Promise<string | null> | null = null

/**
 * [failedToken] 으로 401 을 받은 뒤의 갱신. refresh 토큰은 한 번 쓰면 회전되므로 병렬 요청이 같은 401 을 받아도 갱신은 한 번만:
 * 저장된 토큰이 이미 바뀌었으면 그걸 주고, 갱신 중이면 그 결과를 같이 기다린다. 없거나 실패하면 null(호출 쪽이 세션을 정리한다).
 */
export function refreshSession(failedToken: string | null): Promise<string | null> {
  const current = storedAccessToken()
  if (current && current !== failedToken) return Promise.resolve(current)
  if (inflight) return inflight
  const refresh = localStorage.getItem(REFRESH_KEY)
  if (!refresh) return Promise.resolve(null)
  inflight = tokenRequest({ grant_type: GRANT_REFRESH, client_id: CLIENT_ID, refresh_token: refresh })
    .then((pair) => {
      save({ accessToken: pair.accessToken, refreshToken: pair.refreshToken ?? refresh })
      return pair.accessToken
    })
    .catch(() => null)
    .finally(() => {
      inflight = null
    })
  return inflight
}

/** revoke 는 실패해도 그만이다. 저장된 토큰은 반드시 지운다. */
export async function logoutSession(): Promise<void> {
  const refresh = localStorage.getItem(REFRESH_KEY)
  if (refresh) {
    await fetch(REVOKE_URL, { method: 'POST', headers: FORM, body: new URLSearchParams({ token: refresh, client_id: CLIENT_ID }).toString() }).catch(() => {})
  }
  clearSession()
}

export function save(pair: TokenPair) {
  localStorage.setItem(ACCESS_KEY, pair.accessToken)
  if (pair.refreshToken) localStorage.setItem(REFRESH_KEY, pair.refreshToken)
  else localStorage.removeItem(REFRESH_KEY)
}

export const storedAccessToken = () => localStorage.getItem(ACCESS_KEY)

export function clearSession() {
  localStorage.removeItem(ACCESS_KEY)
  localStorage.removeItem(REFRESH_KEY)
}
