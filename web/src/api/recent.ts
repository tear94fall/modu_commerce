/**
 * 방금 한 쓰기의 짧은 기억(복제 지연 덮개).
 *
 * commerce-service 는 조회를 비동기 MySQL 레플리카에서 한다. 그래서 쓰고 바로 다시 읽으면(리뷰 등록 → 목록, 쿠폰 등록 → 쿠폰함)
 * 레플리카가 아직 옛 값을 줄 수 있다. 쓰기 응답으로 받은 값을 [RECENT_MS] 동안 기억해 두고, 그 사이 받은 목록·상세에
 * 덮어 씌운다 — 늦은 서버 응답이 방금 한 쓰기를 되돌리지 못한다. 시간이 지나면 서버 값을 그대로 믿는다.
 */
export const RECENT_MS = 10_000

const stores = new Set<{ clear(): void }>()

/** 로그아웃·다른 계정 로그인 때 비운다. */
export function clearRecentWrites() {
  for (const s of stores) s.clear()
}

/** 함께 비울 것(덮개가 쓰는 그 밖의 기억). */
export function clearWithRecentWrites(store: { clear(): void }) {
  stores.add(store)
}

/** 키마다 값과 쓴 시각. [RECENT_MS] 가 지난 값은 없는 것으로 본다. */
export class RecentMap<K, V> {
  private readonly map = new Map<K, { value: V; at: number }>()

  constructor() {
    stores.add(this)
  }

  set(key: K, value: V) {
    this.map.delete(key) // 넣은 순서 = 쓴 순서(새 것이 뒤)
    this.map.set(key, { value, at: Date.now() })
  }

  delete(key: K) {
    this.map.delete(key)
  }

  get(key: K): V | undefined {
    const e = this.map.get(key)
    if (!e) return undefined
    if (Date.now() - e.at >= RECENT_MS) {
      this.map.delete(key)
      return undefined
    }
    return e.value
  }

  has(key: K) {
    return this.get(key) !== undefined
  }

  /** 살아 있는 것들, 새로 쓴 것부터. */
  entries(): [K, V][] {
    const out: [K, V][] = []
    for (const [k, e] of this.map) {
      if (Date.now() - e.at < RECENT_MS) out.push([k, e.value])
      else this.map.delete(k)
    }
    return out.reverse()
  }

  clear() {
    this.map.clear()
  }
}

/**
 * "내 ○○ 수" 힌트(마이 탭의 리뷰·찜 수). 마지막으로 본 총수에 내가 한 쓰기(+1/-1)를 더해 둔다.
 * 쓰기 뒤 [RECENT_MS] 안에 받은 총수는 이 힌트로 바꾼다 — 레플리카가 늦으면 목록 첫 장만으로는 지운 것이 반영됐는지 알 수 없다.
 * 너무 오래전에 본 총수(다른 기기에서 바뀌었을 수 있음)는 믿지 않는다.
 */
export class CountHint {
  private value: number | null = null
  private seenAt = 0
  private wroteAt = 0

  constructor() {
    stores.add(this)
  }

  write(delta: number) {
    const now = Date.now()
    if (this.value !== null && now - this.seenAt > HINT_MAX_AGE_MS) this.value = null
    if (this.value !== null) this.value = Math.max(0, this.value + delta)
    this.wroteAt = now
    this.seenAt = now
  }

  /** 서버(와 목록 덮개)가 준 총수를 받아 보여 줄 총수를 돌려준다. */
  observe(total: number): number {
    const now = Date.now()
    if (this.value !== null && now - this.wroteAt < RECENT_MS) return this.value
    this.value = total
    this.seenAt = now
    return total
  }

  clear() {
    this.value = null
    this.seenAt = 0
    this.wroteAt = 0
  }
}

const HINT_MAX_AGE_MS = 5 * 60_000

/**
 * 서버가 준 한 장([page])에 방금 추가한 것들 중 빠진 것을 제자리에 끼운다. [before] 는 목록 정렬(a 가 b 보다 앞인가).
 * 끼울 자리가 이 장 안이면 서버가 이미 가졌다면 이 장에 있었을 것이므로, 없다는 것은 레플리카가 아직 모른다는 뜻이다.
 * 자리가 앞 장이거나(두 번째 장 이후의 맨 앞) 뒤 장이면(다음 장이 있는데 맨 뒤) 알 수 없으니 끼우지 않는다.
 * [candidates] 는 오래된 것부터 준다 — 늘 맨 앞에 오는 정렬(찜 목록)이면 새 것이 맨 앞에 선다.
 */
export function insertMissing<T>(
  page: { content: T[]; number: number; totalPages: number },
  candidates: T[],
  before: (a: T, b: T) => boolean,
  key: (t: T) => number,
): { content: T[]; inserted: number } {
  const lastPage = page.number + 1 >= page.totalPages
  const have = new Set(page.content.map(key))
  let content = page.content
  let inserted = 0
  for (const c of candidates) {
    if (have.has(key(c))) continue
    let pos = content.findIndex((x) => before(c, x))
    if (pos === -1) {
      if (!lastPage || (page.number > 0 && content.length === 0)) continue
      pos = content.length
    } else if (pos === 0 && page.number > 0) {
      continue
    }
    content = [...content.slice(0, pos), c, ...content.slice(pos)]
    inserted++
  }
  return { content, inserted }
}
