/**
 * 출석 달력용 날짜 계산. 서버 날짜는 한국 날짜 "YYYY-MM-DD" 문자열이라
 * 기기 시간대와 상관없게 UTC 자정으로만 다룬다.
 */

const DAY_MS = 24 * 60 * 60 * 1000

const toUtc = (date: string) => {
  const [y, m, d] = date.split('-').map(Number)
  return Date.UTC(y, m - 1, d)
}

const fromUtc = (ms: number) => new Date(ms).toISOString().slice(0, 10)

/** "YYYY-MM" */
export const monthOf = (date: string) => date.slice(0, 7)

/** 0 = 일요일 */
export const weekdayOf = (date: string) => new Date(toUtc(date)).getUTCDay()

export const dayOf = (date: string) => Number(date.slice(8, 10))

/** start..end 의 모든 날(양 끝 포함). */
export function datesBetween(start: string, end: string): string[] {
  const out: string[] = []
  const last = toUtc(end)
  for (let t = toUtc(start); t <= last; t += DAY_MS) out.push(fromUtc(t))
  return out
}

/** 기간에 걸친 달들, 오름차순("YYYY-MM"). */
export function monthsBetween(start: string, end: string): string[] {
  const out: string[] = []
  let [y, m] = start.split('-').map(Number)
  const [ey, em] = end.split('-').map(Number)
  while (y < ey || (y === ey && m <= em)) {
    out.push(`${y}-${String(m).padStart(2, '0')}`)
    m += 1
    if (m > 12) {
      m = 1
      y += 1
    }
  }
  return out
}

/** "2026-09" → "2026년 9월" */
export const formatMonth = (month: string) => `${month.slice(0, 4)}년 ${Number(month.slice(5, 7))}월`

export const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']
