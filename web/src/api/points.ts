import type { Page } from './catalog'
import { formatDateTime } from '../util/format'
import { api } from './client'

/** point-service 의 원장 종류. 적립/사용/관리자 조정. */
export type PointTransactionType = 'EARN' | 'SPEND' | 'ADJUST'

export interface PointTransaction {
  id: number
  type: PointTransactionType
  /** 부호 있는 변동량. 사용·회수는 음수. */
  amount: number
  balanceAfter: number
  ruleCode: string | null
  refId: string | null
  memo: string | null
  createdDate: string | null
}

/** commerce-service 가 point-service 를 대신 불러 준다. 포인트 서비스가 죽어 있으면 503. */
export const getMyPoints = () => api<{ balance: number }>('/api/v1/me/points').then((r) => r.balance)

export const getPointHistory = (page = 0, size = 20) => api<Page<PointTransaction>>(`/api/v1/me/points/history?page=${page}&size=${size}`)

/** 580 → "580P". 백오피스와 같은 표기. */
export const formatPoints = (points: number) => `${points.toLocaleString('ko-KR')}P`

/** +30 / -30 처럼 부호를 붙인 변동량. */
export const formatSignedPoints = (amount: number) => `${amount > 0 ? '+' : amount < 0 ? '-' : ''}${formatPoints(Math.abs(amount))}`

/** 적립 규칙 코드의 우리말. 모르는 코드는 메모나 종류로 대신한다. */
export const RULE_LABELS: Record<string, string> = {
  SIGNUP: '가입 축하',
  DAILY_CHECKIN: '출석 체크',
  INVITE_FRIEND: '친구 초대',
  PROFILE_COMPLETE: '프로필 완성',
  FIRST_CHAT: '첫 대화',
}

const TYPE_LABELS: Record<PointTransactionType, string> = { EARN: '적립', SPEND: '사용', ADJUST: '조정' }

export function transactionTitle(t: PointTransaction): string {
  if (t.ruleCode && RULE_LABELS[t.ruleCode]) return RULE_LABELS[t.ruleCode]
  if (t.memo) return t.memo
  if (t.ruleCode) return t.ruleCode
  return TYPE_LABELS[t.type] ?? t.type
}

export const typeLabel = (type: PointTransactionType) => TYPE_LABELS[type] ?? type

/**
 * point-service 의 원장 시각은 커머스 주문과 달리 한국 시간(Asia/Seoul 시계)이고 시간대 표시가 없다.
 * 그대로 formatDateTime 에 주면 UTC 로 오해해 9시간 늦게 보이므로 +09:00 을 붙여 준다.
 */
export function formatPointDateTime(iso: string | null | undefined): string {
  if (!iso) return ''
  return formatDateTime(/(Z|[+-]\d\d:?\d\d)$/.test(iso) ? iso : `${iso}+09:00`)
}
