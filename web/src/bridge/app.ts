/**
 * Android 껍데기가 WebView 에 심는 `window.ModuApp`(JavascriptInterface). 브라우저에서 열면 없다.
 * 메서드 이름·시그니처는 android `ModuAppBridge` 와 같아야 한다. 값은 전부 문자열이다(브리지 제약).
 */
export interface ModuAppBridge {
  /** 저장된 액세스 토큰. 없으면 빈 문자열. */
  getAccessToken(): string
  /** refresh_token 으로 갱신한 새 액세스 토큰. 실패하면 빈 문자열(앱이 세션을 지우고 로그인으로 보낸다). */
  refreshAccessToken(): string
  /** 웹이 더는 진행할 수 없는 401 을 만났을 때. 앱이 로그인 화면으로 간다. */
  onSessionExpired(): void
  /** 마이 탭 로그아웃. 앱이 토큰 폐기 후 로그인 화면으로 간다. */
  logout(): void
  /** userinfo 프로필 JSON: {"name","email","picture"}. 없으면 "{}". */
  getProfile(): string
}

declare global {
  interface Window {
    ModuApp?: ModuAppBridge
  }
}

export const bridge = (): ModuAppBridge | undefined => window.ModuApp

export const hasBridge = () => bridge() !== undefined
