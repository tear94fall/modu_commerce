import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearRecentWrites, CountHint, insertMissing, RECENT_MS, RecentMap } from './recent'

describe('recent writes', () => {
  beforeEach(() => vi.useFakeTimers({ toFake: ['Date'] }))
  afterEach(() => {
    vi.useRealTimers()
    clearRecentWrites()
  })

  it('forgets a write after the lag window and lists the newest first', () => {
    const m = new RecentMap<number, string>()
    m.set(1, 'a')
    vi.advanceTimersByTime(1000)
    m.set(2, 'b')
    expect(m.entries()).toEqual([
      [2, 'b'],
      [1, 'a'],
    ])
    vi.advanceTimersByTime(RECENT_MS - 1000)
    expect(m.get(1)).toBeUndefined()
    expect(m.get(2)).toBe('b')
    clearRecentWrites()
    expect(m.get(2)).toBeUndefined()
  })

  it('keeps the count I just changed while the server still answers the old one', () => {
    const hint = new CountHint()
    expect(hint.observe(5)).toBe(5)
    hint.write(-1)
    expect(hint.observe(5)).toBe(4) // 레플리카가 아직 지우기 전
    expect(hint.observe(4)).toBe(4)
    vi.advanceTimersByTime(RECENT_MS)
    expect(hint.observe(7)).toBe(7) // 창이 지나면 서버를 믿는다
  })

  it('does not guess a count it never saw', () => {
    const hint = new CountHint()
    hint.write(1)
    expect(hint.observe(3)).toBe(3)
  })

  it('inserts a missing new item only where this page would have shown it', () => {
    const byId = (a: { id: number }, b: { id: number }) => a.id > b.id
    const key = (x: { id: number }) => x.id
    const mine = { id: 9 }
    expect(insertMissing({ content: [{ id: 5 }], number: 0, totalPages: 3 }, [mine], byId, key)).toEqual({ content: [mine, { id: 5 }], inserted: 1 })
    expect(insertMissing({ content: [], number: 0, totalPages: 0 }, [mine], byId, key)).toEqual({ content: [mine], inserted: 1 })
    // 이미 있으면 그대로, 앞 장 자리면 끼우지 않는다
    expect(insertMissing({ content: [{ id: 9 }, { id: 5 }], number: 0, totalPages: 1 }, [mine], byId, key).inserted).toBe(0)
    expect(insertMissing({ content: [{ id: 5 }], number: 1, totalPages: 3 }, [mine], byId, key).inserted).toBe(0)
    // 뒤 장 자리(다음 장이 있는데 맨 뒤)면 알 수 없다
    const old = { id: 1 }
    expect(insertMissing({ content: [{ id: 5 }], number: 0, totalPages: 2 }, [old], byId, key).inserted).toBe(0)
    expect(insertMissing({ content: [{ id: 5 }], number: 0, totalPages: 1 }, [old], byId, key).content).toEqual([{ id: 5 }, old])
  })
})
