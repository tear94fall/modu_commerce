/**
 * 가입(약관 동의) 흐름의 경로 규칙.
 * - 가입 화면 자체와 약관·로그인은 가입 화면으로 다시 보내지 않는다(루프 방지).
 * - `?next=` 는 이 앱의 알려진 화면으로만 돌려보낸다(열린 리다이렉트 방지).
 */

/** 가입 전이어도 가입 화면으로 보내지 않는 경로. */
const EXEMPT = /^\/(welcome|terms|privacy|login)(\/|\?|$)/

/** 가입해야 들어갈 수 있는 화면. App 의 RequireCustomer 아래 경로와 같아야 한다. */
const PROTECTED = /^\/(cart|checkout|orders|wishlist|my\/coupons|my\/reviews|reviews|notifications|addresses|settings\/notifications|membership)(\/|\?|$)/

/** next 로 돌아갈 수 있는 첫 경로 조각. '' 은 홈. */
const NEXT_ROOTS = new Set([
  '',
  'categories',
  'products',
  'reviews',
  'promotions',
  'search',
  'wishlist',
  'my',
  'points',
  'coupons',
  'cart',
  'checkout',
  'orders',
  'addresses',
  'notifications',
  'settings',
  'membership',
])

/** 경로·쿼리에 쓸 수 있는 글자만. 스킴·역슬래시·공백·`//` 는 받지 않는다. */
const SAFE_CHARS = /^\/[A-Za-z0-9\-._~%/]*(\?[A-Za-z0-9\-._~%=&,+]*)?$/

export const isExemptPath = (path: string) => EXEMPT.test(path)

export const isProtectedPath = (path: string) => PROTECTED.test(path)

/** 가입 뒤 돌아갈 경로. 허용되지 않은 값이면 홈. */
export function safeNext(next: string | null | undefined): string {
  if (!next || !SAFE_CHARS.test(next) || next.startsWith('//') || isExemptPath(next)) return '/'
  const root = next.slice(1).split(/[/?]/)[0]
  return NEXT_ROOTS.has(root) ? next : '/'
}

/** 가입 화면 주소. 지금 경로를 next 로 넘긴다. */
export const welcomeUrl = (current: string) => {
  const next = safeNext(current)
  return next === '/' ? '/welcome' : `/welcome?next=${encodeURIComponent(next)}`
}
