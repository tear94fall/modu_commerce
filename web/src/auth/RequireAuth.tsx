import { Navigate, Outlet } from 'react-router-dom'
import { hasBridge } from '../bridge/app'
import { getToken } from './token'

/** 앱(브리지)이면 항상 통과. 브라우저는 토큰이 있어야 한다. */
export default function RequireAuth() {
  if (!hasBridge() && !getToken()) return <Navigate to="/login" replace />
  return <Outlet />
}
