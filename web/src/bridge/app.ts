/**
 * Android 껍데기가 WebView 에 심는 `window.ModuApp`(JavascriptInterface). 브라우저에서 열면 없다.
 * 메서드 이름·시그니처는 android `ModuAppBridge` 와 같아야 한다. 값은 전부 문자열이다(브리지 제약).
 */
export interface ModuAppBridge {
  /** 저장된 액세스 토큰. 없으면 빈 문자열. */
  getAccessToken(): string
  /**
   * [failedToken] 으로 401 을 받았을 때 쓸 액세스 토큰. 앱이 이미 다른 토큰을 갖고 있으면(다른 요청이 먼저 갱신) 그걸 주고,
   * 아니면 refresh_token 으로 갱신한다. 실패하면 빈 문자열(앱이 세션을 지우고 로그인으로 보낸다).
   * 병렬 요청이 같은 401 을 받아도 갱신은 한 번만 일어나야 하므로 반드시 실제로 실패한 토큰을 넘긴다.
   */
  refreshAccessToken(failedToken: string): string
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
