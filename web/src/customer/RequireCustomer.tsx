import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useCustomer } from './context'
import { welcomeUrl } from './paths'

/**
 * 가입(약관 동의)해야 쓰는 화면. 가입 전으로 확인되면 가입 화면으로 바꾼다.
 * 확인 중(loading)·못 불러옴(error)이면 그대로 보여 주고, 서버가 403 을 주면 CustomerProvider 가 보낸다.
 */
export default function RequireCustomer() {
  const { status } = useCustomer()
  const location = useLocation()
  if (status === 'none') return <Navigate to={welcomeUrl(`${location.pathname}${location.search}`)} replace />
  return <Outlet />
}
