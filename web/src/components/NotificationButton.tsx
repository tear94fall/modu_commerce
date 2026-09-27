import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getUnreadNotificationCount } from '../api/push'
import { BellLineIcon } from './Icons'

/** 상단바 알림함 아이콘 + 안 읽은 수 뱃지. 들어올 때와 앱·탭으로 다시 돌아왔을 때 센다. */
export default function NotificationButton() {
  const [count, setCount] = useState(0)
  useEffect(() => {
    let alive = true
    const refresh = () => {
      getUnreadNotificationCount()
        .then((n) => {
          if (alive) setCount(n)
        })
        .catch(() => {})
    }
    refresh()
    const onVisible = () => {
      if (document.visibilityState === 'visible') refresh()
    }
    document.addEventListener('visibilitychange', onVisible)
    return () => {
      alive = false
      document.removeEventListener('visibilitychange', onVisible)
    }
  }, [])
  return (
    <Link to="/notifications" className="icon-btn" aria-label="알림">
      <BellLineIcon />
      {count > 0 && <span className="badge">{count > 9 ? '9+' : count}</span>}
    </Link>
  )
}
