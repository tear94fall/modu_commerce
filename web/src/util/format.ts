/** 89000 → "89,000원". 앱·백오피스와 같은 표기. */
export const formatPrice = (price: number) => `${price.toLocaleString('ko-KR')}원`

/** 시간대 표시 없는 서버 UTC 시각을 Date 로. 비었거나 잘못된 값이면 null. */
export function parseServerTime(iso?: string | null): Date | null {
  if (!iso) return null
  const d = new Date(/(Z|[+-]\d\d:?\d\d)$/.test(iso) ? iso : `${iso}Z`)
  return Number.isNaN(d.getTime()) ? null : d
}

/** 서버 시각은 UTC 인데 시간대 표시가 없다("2026-09-19T05:22:10"). 붙여서 기기 시간대로 보여 준다 → "2026.09.19 14:22". */
export function formatDateTime(iso?: string | null): string {
  const d = parseServerTime(iso)
  if (!d) return ''
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}.${pad(d.getMonth() + 1)}.${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** 날짜만("2026.09.19"). 서버 UTC 시각을 기기 시간대로 바꾼 뒤 자른다. */
export const formatDate = (iso?: string | null) => formatDateTime(iso).slice(0, 10)

/**
 * 알림함의 상대 시각. 서버 UTC 시각 기준으로 "방금" / "N분 전" / "N시간 전"(하루 안),
 * 그 뒤로는 기기 시간대 날짜 "M월 D일"(올해) / "YYYY.M.D"(다른 해).
 */
export function formatRelativeTime(iso?: string | null, now: Date = new Date()): string {
  const d = parseServerTime(iso)
  if (!d) return ''
  const minutes = Math.floor((now.getTime() - d.getTime()) / 60_000)
  if (minutes < 1) return '방금'
  if (minutes < 60) return `${minutes}분 전`
  if (minutes < 24 * 60) return `${Math.floor(minutes / 60)}시간 전`
  if (d.getFullYear() === now.getFullYear()) return `${d.getMonth() + 1}월 ${d.getDate()}일`
  return `${d.getFullYear()}.${d.getMonth() + 1}.${d.getDate()}`
}
