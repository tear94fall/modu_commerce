import { describe, expect, it } from 'vitest'
import { formatPointDateTime, formatPoints, formatSignedPoints, transactionTitle, type PointTransaction } from './points'

const tx = (over: Partial<PointTransaction>): PointTransaction => ({ id: 1, type: 'EARN', amount: 10, balanceAfter: 10, ruleCode: null, refId: null, memo: null, createdDate: null, ...over })

describe('points', () => {
  it('formats points with sign', () => {
    expect(formatPoints(1580)).toBe('1,580P')
    expect(formatSignedPoints(500)).toBe('+500P')
    expect(formatSignedPoints(-30)).toBe('-30P')
    expect(formatSignedPoints(0)).toBe('0P')
  })

  it('names a row by rule, then memo, then type', () => {
    expect(transactionTitle(tx({ ruleCode: 'DAILY_CHECKIN' }))).toBe('출석 체크')
    expect(transactionTitle(tx({ type: 'SPEND', amount: -30, memo: '주문 할인' }))).toBe('주문 할인')
    expect(transactionTitle(tx({ ruleCode: 'NEW_RULE' }))).toBe('NEW_RULE')
    expect(transactionTitle(tx({ type: 'ADJUST' }))).toBe('조정')
  })

  it('treats ledger times as Asia/Seoul, not UTC', () => {
    // 같은 순간을 UTC 로 적은 값과 같은 결과여야 한다(기기 시간대와 무관).
    expect(formatPointDateTime('2026-09-23T18:26:00')).toBe(formatPointDateTime('2026-09-23T09:26:00Z'))
    expect(formatPointDateTime(null)).toBe('')
  })
})
