/** 89000 → "89,000원". 앱·백오피스와 같은 표기. */
export const formatPrice = (price: number) => `${price.toLocaleString('ko-KR')}원`

/** 서버 시각은 UTC 인데 시간대 표시가 없다("2026-09-19T05:22:10"). 붙여서 기기 시간대로 보여 준다 → "2026.09.19 14:22". */
export function formatDateTime(iso?: string | null): string {
  if (!iso) return ''
  const d = new Date(/(Z|[+-]\d\d:?\d\d)$/.test(iso) ? iso : `${iso}Z`)
  if (Number.isNaN(d.getTime())) return ''
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}.${pad(d.getMonth() + 1)}.${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
