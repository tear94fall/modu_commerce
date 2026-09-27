import { createContext, useContext } from 'react'
import type { CustomerMe } from '../api/customer'

/**
 * - unknown: CustomerProvider 밖(단독 화면 테스트 등). 가입 여부를 모르니 예전처럼 동작한다.
 * - loading: 앱 시작 직후 GET /me/customer 응답 전.
 * - customer: 가입(약관 동의) 완료.
 * - none: 가입 전(404/403 CUSTOMER_REQUIRED).
 * - error: 못 불러옴(네트워크 등). 막지 않고 서버 403 에 맡긴다.
 */
export type CustomerStatus = 'unknown' | 'loading' | 'customer' | 'none' | 'error'

export interface CustomerState {
  status: CustomerStatus
  customer: CustomerMe | null
  /** 다시 불러온다. 이미 값이 있으면 화면을 로딩으로 돌리지 않고 조용히 바꾼다. */
  refresh: () => Promise<void>
  /** 가입 직후처럼 이미 받은 값을 바로 넣는다. */
  setCustomer: (customer: CustomerMe) => void
}

export const CustomerContext = createContext<CustomerState>({
  status: 'unknown',
  customer: null,
  refresh: async () => {},
  setCustomer: () => {},
})

export const useCustomer = () => useContext(CustomerContext)
