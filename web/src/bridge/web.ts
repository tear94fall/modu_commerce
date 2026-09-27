import { useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import type { NotificationPermissionResult } from './app'

/** 푸시가 열 수 있는 경로. android `DeepLinks` 와 같은 목록이어야 한다. */
const ALLOWED_PATH = /^\/(coupons|products\/\d+|promotions\/\d+)?$/

export const isAllowedPushPath = (path: unknown): path is string => typeof path === 'string' && ALLOWED_PATH.test(path)

const toPermission = (result: string): NotificationPermissionResult => (result === 'granted' || result === 'denied' ? result : 'default')

type PermissionListener = (result: NotificationPermissionResult) => void
const permissionListeners = new Set<PermissionListener>()

/**
 * 다음 `onNotificationPermission` 을 기다린다. 앱이 끝내 부르지 않을 때(권한 창이 안 뜨는 기기 등)를 위해
 * [timeoutMs] 뒤에는 [fallback] 값으로 끝낸다.
 */
export function waitForNotificationPermission(timeoutMs: number, fallback: () => NotificationPermissionResult): Promise<NotificationPermissionResult> {
  return new Promise((resolve) => {
    const done = (result: NotificationPermissionResult) => {
      clearTimeout(timer)
      permissionListeners.delete(done)
      resolve(result)
    }
    const timer = setTimeout(() => done(fallback()), timeoutMs)
    permissionListeners.add(done)
  })
}

/** `window.ModuWeb` 를 심는다. BrowserRouter 안에서 한 번 렌더한다. */
export function ModuWebBridge() {
  const navigate = useNavigate()
  // BrowserRouter 의 navigate 는 위치가 바뀔 때마다 새로 만들어진다. 한 번 심은 객체가 항상 최신 것을 쓰게 ref 로 둔다.
  const navigateRef = useRef(navigate)
  useEffect(() => {
    navigateRef.current = navigate
  }, [navigate])
  useEffect(() => {
    window.ModuWeb = {
      navigate: (path) => {
        if (isAllowedPushPath(path)) navigateRef.current(path)
      },
      onNotificationPermission: (result) => {
        const r = toPermission(result)
        for (const listener of [...permissionListeners]) listener(r)
      },
    }
    return () => {
      delete window.ModuWeb
    }
  }, [])
  return null
}
