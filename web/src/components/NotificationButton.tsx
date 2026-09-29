import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getUnreadNotificationCount, UNREAD_CHANGED, type UnreadChange } from '../api/push'
import { BellLineIcon } from './Icons'

/**
 * 상단바 알림함 아이콘 + 안 읽은 수 뱃지. 들어올 때와 앱·탭으로 다시 돌아왔을 때 센다.
 * 알림을 읽으면 바로 줄이고, 읽음 요청이 끝난 뒤 다시 센다(서버는 master 에서 센다).
 */
export default function NotificationButton() {
  const [count, setCount] = useState(0)
  useEffect(() => {
    let alive = true
    /** 마지막으로 보낸 요청의 답만 쓴다 — 먼저 보낸(읽기 전) 답이 늦게 와서 줄인 뱃지를 되돌리지 않게. */
    let latest = 0
    const refresh = () => {
      const mine = ++latest
      getUnreadNotificationCount()
        .then((n) => {
          if (alive && mine === latest) setCount(n)
        })
        .catch(() => {})
    }
    refresh()
    const onVisible = () => {
      if (document.visibilityState === 'visible') refresh()
    }
    const onRead = (e: Event) => {
      const { delta = 0, all = false } = (e as CustomEvent<UnreadChange>).detail ?? {}
      setCount((c) => (all ? 0 : Math.max(0, c + delta)))
      refresh()
    }
    document.addEventListener('visibilitychange', onVisible)
    window.addEventListener(UNREAD_CHANGED, onRead)
    return () => {
      alive = false
      document.removeEventListener('visibilitychange', onVisible)
      window.removeEventListener(UNREAD_CHANGED, onRead)
    }
  }, [])
  return (
    <Link to="/notifications" className="icon-btn" aria-label="알림">
      <BellLineIcon />
      {count > 0 && <span className="badge">{count > 9 ? '9+' : count}</span>}
    </Link>
  )
}
