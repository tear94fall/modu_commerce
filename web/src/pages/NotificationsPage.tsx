import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { getNotifications, markAllNotificationsRead, markNotificationRead, type NotificationItem } from '../api/push'
import { isAllowedPushPath } from '../bridge/web'
import { ErrorBox, Loading } from '../components/Boxes'
import { BellLineIcon } from '../components/Icons'
import { Screen, TopBar } from '../components/Layout'
import Toast from '../components/Toast'
import { formatRelativeTime } from '../util/format'

/** 알림함. 최근 30일 동안 받은 광고 푸시, 최신순. 누르면 읽음으로 바꾸고 푸시가 가리키는 화면을 연다. */
export default function NotificationsPage() {
  const navigate = useNavigate()
  const [items, setItems] = useState<NotificationItem[] | null>(null)
  const [hasNext, setHasNext] = useState(false)
  const [page, setPage] = useState(0)
  const [loadingMore, setLoadingMore] = useState(false)
  const [error, setError] = useState(false)
  const [toast, setToast] = useState<string | null>(null)
  const clearToast = useCallback(() => setToast(null), [])

  /** 첫 장은 effect 에서 부르므로 로딩·오류 표시는 호출하는 쪽(retry / loadMore)이 바꾼다. */
  const load = useCallback((p: number) => {
    getNotifications(p)
      .then((res) => {
        setItems((cur) => {
          if (p === 0) return res.content
          // 그사이 새 알림이 와서 한 칸씩 밀렸을 수 있다. 이미 있는 건 빼고 붙인다.
          const seen = new Set((cur ?? []).map((n) => n.id))
          return [...(cur ?? []), ...res.content.filter((n) => !seen.has(n.id))]
        })
        setHasNext(res.number + 1 < res.totalPages)
        setPage(p)
      })
      .catch(() => {
        if (p === 0) setError(true)
        else setToast('알림을 더 불러오지 못했습니다.')
      })
      .finally(() => setLoadingMore(false))
  }, [])
  useEffect(() => load(0), [load])

  const retry = () => {
    setError(false)
    load(0)
  }
  const loadMore = () => {
    setLoadingMore(true)
    load(page + 1)
  }

  const setRead = (match: (n: NotificationItem) => boolean, read: boolean) =>
    setItems((cur) => cur && cur.map((n) => (match(n) ? { ...n, read } : n)))

  const open = (item: NotificationItem) => {
    if (!item.read) {
      setRead((n) => n.id === item.id, true)
      markNotificationRead(item.id).catch(() => {})
    }
    if (isAllowedPushPath(item.path)) navigate(item.path)
  }

  const readAll = () => {
    const unreadIds = new Set((items ?? []).filter((n) => !n.read).map((n) => n.id))
    if (unreadIds.size === 0) return
    setRead((n) => unreadIds.has(n.id), true)
    markAllNotificationsRead().catch(() => {
      setRead((n) => unreadIds.has(n.id), false)
      setToast('읽음 처리하지 못했습니다.')
    })
  }

  const anyUnread = items?.some((n) => !n.read) ?? false

  return (
    <Screen className="my notifications">
      <TopBar
        title="알림"
        back
        actions={
          anyUnread && (
            <button type="button" className="text-btn" onClick={readAll}>
              모두 읽음
            </button>
          )
        }
      />
      {error && items === null ? (
        <ErrorBox message="알림을 불러오지 못했습니다." onRetry={retry} />
      ) : items === null ? (
        <Loading />
      ) : items.length === 0 ? (
        <div className="empty-cat notify-empty">
          <div className="art">
            <BellLineIcon />
          </div>
          <p className="title">받은 알림이 없어요</p>
          <p className="sub">최근 30일 알림만 보여요</p>
        </div>
      ) : (
        <>
          <ul className="my-card notify-list">
            {items.map((n) => (
              <li key={n.id}>
                <button type="button" className={`notify-item${n.read ? '' : ' unread'}`} onClick={() => open(n)}>
                  <div className="body">
                    <div className="meta">
                      <span className="ad-tag">광고</span>
                      <span className="time">{formatRelativeTime(n.receivedAt)}</span>
                      {!n.read && <span className="dot" role="img" aria-label="안 읽음" />}
                    </div>
                    <div className="title">{n.title}</div>
                    <div className="text">{n.body}</div>
                  </div>
                  {n.imageUrl && <img className="thumb" src={n.imageUrl} alt="" loading="lazy" />}
                </button>
              </li>
            ))}
          </ul>
          {loadingMore ? (
            <Loading />
          ) : (
            hasNext && (
              <button type="button" className="btn outline small load-more notify-more" onClick={loadMore}>
                더 보기
              </button>
            )
          )}
          <p className="notify-foot">최근 30일 알림만 보여요</p>
        </>
      )}
      <Toast message={toast} onDone={clearToast} />
    </Screen>
  )
}
