import { useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { bridge, type NotificationPermissionResult } from './app'

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

/** 앱(브리지)이 알림 권한 메서드를 가졌을 때만 권한 값. 브라우저·이전 버전 앱이면 null. */
export const readNotificationPermission = (): NotificationPermissionResult | null => bridge()?.getNotificationPermission?.() ?? null

/** 권한 창에 사용자가 답하기를 기다리는 최대 시간. 넘으면 앱에 다시 물어본다. */
export const PERMISSION_WAIT_MS = 60_000

/**
 * 앱 안이고 OS 알림 권한을 아직 물어본 적 없을('default') 때만 권한 창을 띄우고 답을 기다린다.
 * 물어봤으면 결과, 묻지 않았으면(브라우저 · 이전 버전 앱 · 이미 답함) null.
 */
export async function requestNotificationPermissionIfDefault(timeoutMs = PERMISSION_WAIT_MS): Promise<NotificationPermissionResult | null> {
  const b = bridge()
  if (!b?.requestNotificationPermission || readNotificationPermission() !== 'default') return null
  const waiting = waitForNotificationPermission(timeoutMs, () => readNotificationPermission() ?? 'default')
  b.requestNotificationPermission()
  return waiting
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
