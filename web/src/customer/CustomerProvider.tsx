import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { onCustomerRequired } from '../api/client'
import { getMyCustomer, type CustomerMe } from '../api/customer'
import { CustomerContext, type CustomerStatus } from './context'
import { isExemptPath, isProtectedPath, welcomeUrl } from './paths'

/**
 * 로그인 뒤 한 번 GET /me/customer 로 가입 여부를 불러 둔다. 그리고 어떤 API 든 403 CUSTOMER_REQUIRED 를 받으면
 * 가입 화면(/welcome?next=지금 경로)으로 보낸다. 가입 화면·약관·로그인에서는 보내지 않는다(루프 방지).
 */
export function CustomerProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<CustomerStatus>('loading')
  const [customer, setCustomerValue] = useState<CustomerMe | null>(null)
  const navigate = useNavigate()
  const location = useLocation()
  const path = `${location.pathname}${location.search}`
  // 403 처리 함수는 한 번만 등록하므로 최신 경로·navigate 는 ref 로 본다.
  const here = useRef(path)
  const navigateRef = useRef(navigate)
  useEffect(() => {
    here.current = path
    navigateRef.current = navigate
  }, [path, navigate])
  const hasValue = useRef(false)

  const apply = useCallback((c: CustomerMe | null) => {
    hasValue.current = true
    setCustomerValue(c)
    setStatus(c ? 'customer' : 'none')
  }, [])

  const refresh = useCallback(async () => {
    try {
      apply(await getMyCustomer())
    } catch {
      if (!hasValue.current) setStatus('error')
    }
  }, [apply])

  useEffect(() => {
    refresh()
  }, [refresh])

  useEffect(
    () =>
      onCustomerRequired(() => {
        hasValue.current = true
        setCustomerValue(null)
        setStatus('none')
        const current = here.current
        if (isExemptPath(current)) return
        // 가입이 필요한 화면에서 막혔으면 그 화면을 가입 화면으로 바꾼다(뒤로 가기가 다시 막히지 않게).
        navigateRef.current(welcomeUrl(current), { replace: isProtectedPath(current) })
      }),
    [],
  )

  const value = useMemo(() => ({ status, customer, refresh, setCustomer: (c: CustomerMe) => apply(c) }), [status, customer, refresh, apply])
  return <CustomerContext.Provider value={value}>{children}</CustomerContext.Provider>
}

/** 로그인한 화면들을 감싸는 레이아웃 라우트. */
export default function CustomerLayout() {
  return (
    <CustomerProvider>
      <Outlet />
    </CustomerProvider>
  )
}
