import { useEffect, useState, type ReactNode } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { getWishlist } from '../api/catalog'
import { getMyCouponCount } from '../api/coupons'
import { getProfile, type Profile } from '../api/me'
import { getOrders, ORDER_STATUS_LABELS, type OrderStatus } from '../api/orders'
import { formatPoints, getMyPoints } from '../api/points'
import { getMyReviews } from '../api/reviews'
import { logoutSession } from '../auth/session'
import { bridge } from '../bridge/app'
import {
  ChevronRightIcon,
  GiftLineIcon,
  HeartLineIcon,
  HistoryLineIcon,
  PackageLineIcon,
  PinLineIcon,
  PointLineIcon,
  ReviewLineIcon,
  TicketLineIcon,
} from '../components/Icons'
import { Screen, TopBar } from '../components/Layout'

/** 주문 상태 집계에 받는 주문 수(서버 페이지 상한 100). 이보다 많으면 최근 100건만 센다. */
const ORDER_COUNT_SIZE = 100

/** 진행 단계로 보여 주는 상태. 취소는 따로 작게. */
const ORDER_STEPS: OrderStatus[] = ['PAID', 'SHIPPING', 'DELIVERED']

type Tone = 'amber' | 'red' | 'pink' | 'blue' | 'indigo' | 'violet' | 'teal' | 'green'

/** undefined = 불러오는 중, null = 못 불러옴("-"). 어느 하나가 죽어도 마이 탭은 뜬다. */
type Count = number | null | undefined

const countText = (n: Count, unit: (n: number) => string) => (n === undefined ? '' : n === null ? '-' : unit(n))
const n = (v: number) => v.toLocaleString('ko-KR')

export default function MyPage() {
  const [profile, setProfile] = useState<Profile | null>(null)
  const [points, setPoints] = useState<Count>(undefined)
  const [coupons, setCoupons] = useState<Count>(undefined)
  const [wishes, setWishes] = useState<Count>(undefined)
  const [reviews, setReviews] = useState<Count>(undefined)
  /** 상태별 주문 수. null = 못 불러옴. */
  const [orderCounts, setOrderCounts] = useState<Record<string, number> | null | undefined>(undefined)
  const [confirm, setConfirm] = useState(false)
  const navigate = useNavigate()

  useEffect(() => {
    getProfile().then(setProfile).catch(() => setProfile({ name: '', email: '', picture: '' }))
    getMyPoints().then(setPoints).catch(() => setPoints(null))
    getMyCouponCount().then(setCoupons).catch(() => setCoupons(null))
    getWishlist(0, 1).then((p) => setWishes(p.totalElements)).catch(() => setWishes(null))
    getMyReviews(0, 1).then((p) => setReviews(p.totalElements)).catch(() => setReviews(null))
    getOrders(0, ORDER_COUNT_SIZE)
      .then((p) => {
        const counts: Record<string, number> = {}
        for (const o of p.content) counts[o.status] = (counts[o.status] ?? 0) + 1
        setOrderCounts(counts)
      })
      .catch(() => setOrderCounts(null))
  }, [])

  /** 앱이면 토큰 폐기와 화면 전환을 앱이 맡고, 브라우저면 토큰만 지우고 로그인으로. */
  const logout = () => {
    const b = bridge()
    if (b) {
      b.logout()
      return
    }
    logoutSession().finally(() => navigate('/login', { replace: true }))
  }

  const orderCount = (s: OrderStatus) => (orderCounts === undefined ? '' : orderCounts === null ? '-' : n(orderCounts[s] ?? 0))

  return (
    <Screen tabs className="my">
      <TopBar title="마이 페이지" />
      <div className="my-hero">
        {profile?.picture ? <img className="avatar" src={profile.picture} alt="" /> : <div className="avatar" aria-hidden="true" />}
        <div className="who">
          <div className="name">{profile?.name || '모두 회원'}</div>
          <div className="email">{profile?.email}</div>
        </div>
      </div>

      <nav className="benefits" aria-label="내 혜택">
        <Benefit to="/points" tone="amber" icon={<PointLineIcon />} label="포인트" value={countText(points, formatPoints)} />
        <Benefit to="/my/coupons" tone="red" icon={<TicketLineIcon />} label="쿠폰" value={countText(coupons, (v) => `${n(v)}장`)} />
        <Benefit to="/wishlist" tone="pink" icon={<HeartLineIcon />} label="찜" value={countText(wishes, (v) => `${n(v)}개`)} />
        <Benefit to="/my/reviews" tone="blue" icon={<ReviewLineIcon />} label="리뷰" value={countText(reviews, (v) => `${n(v)}개`)} />
      </nav>

      <section className="my-card order-status" aria-label="주문·배송">
        <div className="my-card-head">
          <h2>주문·배송</h2>
          <Link to="/orders" className="more">
            전체 <ChevronRightIcon />
          </Link>
        </div>
        <Link to="/orders" className="steps" aria-label={ORDER_STEPS.map((s) => `${ORDER_STATUS_LABELS[s]} ${orderCount(s)}`.trim()).join(', ')}>
          {ORDER_STEPS.map((s, i) => (
            <span key={s} className="step-wrap">
              {i > 0 && (
                <span className="step-arrow" aria-hidden="true">
                  <ChevronRightIcon />
                </span>
              )}
              <span className={`step ${s}`}>
                <span className="num">{orderCount(s)}</span>
                <span className="lbl">{ORDER_STATUS_LABELS[s]}</span>
              </span>
            </span>
          ))}
        </Link>
        <Link to="/orders" className="cancelled">
          {ORDER_STATUS_LABELS.CANCELLED} <b>{orderCount('CANCELLED')}</b>
        </Link>
      </section>

      <MenuGroup title="쇼핑">
        <MenuRow to="/orders" tone="indigo" icon={<PackageLineIcon />} label="주문 내역" />
        <MenuRow to="/wishlist" tone="pink" icon={<HeartLineIcon />} label="찜한 상품" />
        <MenuRow to="/my/reviews" tone="blue" icon={<ReviewLineIcon />} label="내 리뷰" />
      </MenuGroup>
      <MenuGroup title="혜택">
        <MenuRow to="/my/coupons" tone="red" icon={<TicketLineIcon />} label="쿠폰함" />
        <MenuRow to="/coupons" tone="violet" icon={<GiftLineIcon />} label="쿠폰 받으러 가기" />
        <MenuRow to="/points" tone="amber" icon={<HistoryLineIcon />} label="포인트 내역" />
      </MenuGroup>
      <MenuGroup title="설정">
        <MenuRow to="/addresses" tone="green" icon={<PinLineIcon />} label="배송지 관리" />
      </MenuGroup>

      <button type="button" className="logout-link" onClick={() => setConfirm(true)}>
        로그아웃
      </button>

      {confirm && (
        <div className="dialog-scrim" onClick={() => setConfirm(false)}>
          <div className="dialog" role="dialog" onClick={(e) => e.stopPropagation()}>
            <h3>로그아웃</h3>
            <p>로그아웃할까요?</p>
            <div className="actions">
              <button type="button" onClick={() => setConfirm(false)}>
                취소
              </button>
              <button type="button" style={{ color: 'var(--brand)' }} onClick={logout}>
                로그아웃
              </button>
            </div>
          </div>
        </div>
      )}
    </Screen>
  )
}

function Benefit({ to, tone, icon, label, value }: { to: string; tone: Tone; icon: ReactNode; label: string; value: string }) {
  return (
    <Link to={to} className="benefit" aria-label={value ? `${label} ${value}` : label}>
      <span className={`tone-dot ${tone}`}>{icon}</span>
      <span className="lbl">{label}</span>
      <span className="val">{value}</span>
    </Link>
  )
}

function MenuGroup({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="my-card menu-group" aria-label={title}>
      <h2 className="group-title">{title}</h2>
      {children}
    </section>
  )
}

function MenuRow({ to, tone, icon, label }: { to: string; tone: Tone; icon: ReactNode; label: string }) {
  return (
    <Link to={to} className="my-row">
      <span className={`tone-dot ${tone}`}>{icon}</span>
      <span className="lbl">{label}</span>
      <span className="chev">
        <ChevronRightIcon />
      </span>
    </Link>
  )
}
