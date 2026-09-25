import { useCallback, useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { setWish } from '../api/catalog'
import { ApiError } from '../api/client'
import { formatPoints } from '../api/points'
import { checkAttendance, formatPeriod, getPromotion, STATUS_LABELS, TYPE_LABELS, type AttendanceInfo, type PromotionDetail } from '../api/promotions'
import AttendanceCalendar from '../components/AttendanceCalendar'
import { EmptyBox, ErrorBox, Loading } from '../components/Boxes'
import { Screen, TopBar } from '../components/Layout'
import { ProductGrid } from '../components/ProductCard'
import { BannerArt } from '../components/PromotionBanner'
import Toast from '../components/Toast'

/** 기획전 · 이벤트 상세. 기획전은 상품 격자, 출석 이벤트는 달력과 출석 버튼. */
export default function PromotionPage() {
  const { id } = useParams()
  const promotionId = Number(id)
  const [detail, setDetail] = useState<PromotionDetail | null>(null)
  const [status, setStatus] = useState<'loading' | 'ok' | 'notFound' | 'error'>('loading')
  const [message, setMessage] = useState<string | null>(null)

  const load = useCallback(() => {
    setStatus('loading')
    getPromotion(promotionId)
      .then((d) => {
        setDetail(d)
        setStatus('ok')
      })
      .catch((e: unknown) => setStatus(e instanceof ApiError && e.status === 404 ? 'notFound' : 'error'))
  }, [promotionId])
  useEffect(load, [load])

  const clearMessage = useCallback(() => setMessage(null), [])

  if (status !== 'ok' || !detail) {
    return (
      <Screen>
        <TopBar title="" back />
        {status === 'notFound' ? (
          <ErrorBox message="기획전을 찾을 수 없습니다" />
        ) : status === 'error' ? (
          <ErrorBox message="기획전을 불러오지 못했습니다." onRetry={load} />
        ) : (
          <Loading />
        )}
      </Screen>
    )
  }

  const toggleWish = (productId: number) => {
    const current = detail.products.find((p) => p.id === productId)
    if (!current) return
    const next = !current.wished
    const patch = (wished: boolean) =>
      setDetail((d) => (d ? { ...d, products: d.products.map((p) => (p.id === productId ? { ...p, wished } : p)) } : d))
    patch(next)
    setWish(productId, next).catch(() => {
      patch(!next)
      setMessage('찜을 바꾸지 못했습니다.')
    })
  }

  const setAttendance = (attendance: AttendanceInfo) => setDetail((d) => (d ? { ...d, attendance } : d))

  return (
    <Screen>
      <TopBar title={TYPE_LABELS[detail.type]} back />
      <div className="promo-hero">
        <BannerArt banner={detail} />
      </div>
      <section className="promo-info">
        <div className="promo-meta">
          <span className={`promo-status ${detail.status}`}>{STATUS_LABELS[detail.status]}</span>
          <span className="promo-period">{formatPeriod(detail.startDate, detail.endDate)}</span>
        </div>
        <h1>{detail.title}</h1>
        {detail.subtitle && <p className="promo-subtitle">{detail.subtitle}</p>}
        {detail.description && <p className="promo-desc">{detail.description}</p>}
      </section>
      {detail.type === 'EXHIBITION' ? (
        <section className="promo-products">
          {detail.products.length === 0 ? <EmptyBox message="준비 중인 상품이 없습니다" /> : <ProductGrid products={detail.products} onToggleWish={toggleWish} />}
        </section>
      ) : (
        detail.attendance && (
          <AttendanceSection promotion={detail} attendance={detail.attendance} onChange={setAttendance} onMessage={setMessage} />
        )
      )}
      <Toast message={message} onDone={clearMessage} />
    </Screen>
  )
}

interface AttendanceSectionProps {
  promotion: PromotionDetail
  attendance: AttendanceInfo
  onChange: (next: AttendanceInfo) => void
  onMessage: (message: string) => void
}

function AttendanceSection({ promotion, attendance, onChange, onMessage }: AttendanceSectionProps) {
  const [working, setWorking] = useState(false)
  const ongoing = promotion.status === 'ONGOING'
  const canCheck = ongoing && !attendance.checkedToday && !working

  const markToday = (checkedDates: string[]) => {
    const dates = checkedDates.includes(attendance.today) ? checkedDates : [...checkedDates, attendance.today].sort()
    onChange({ ...attendance, checkedToday: true, checkedDates: dates })
  }

  const check = async () => {
    if (!canCheck) return
    setWorking(true)
    try {
      const result = await checkAttendance(promotion.id)
      markToday(result.checkedDates)
      onMessage(
        result.rewardPoints > 0
          ? `출석 완료! ${formatPoints(result.rewardPoints)} 적립`
          : result.rewardMessage
            ? `출석 완료 · ${result.rewardMessage}`
            : '출석 완료',
      )
    } catch (e) {
      if (e instanceof ApiError && e.status === 409) {
        markToday(attendance.checkedDates)
        onMessage('오늘은 이미 출석했습니다')
      } else if (e instanceof ApiError && e.status === 503) {
        onMessage('잠시 후 다시 시도해 주세요')
      } else {
        onMessage(e instanceof ApiError && e.status !== 401 && e.message && !e.message.startsWith('HTTP ') ? e.message : '출석하지 못했습니다')
      }
    } finally {
      setWorking(false)
    }
  }

  const label = attendance.checkedToday ? '오늘 출석 완료' : !ongoing ? '진행 기간이 아닙니다' : '출석 체크하기'

  return (
    <section className="block attend">
      <div className="attend-reward">{attendance.rewardPoints ? `매일 출석하면 ${formatPoints(attendance.rewardPoints)}` : '출석 체크'}</div>
      <div className="attend-count">
        출석 <b>{attendance.checkedDates.length}</b>일 / {attendance.totalDays}일
      </div>
      <AttendanceCalendar startDate={promotion.startDate} endDate={promotion.endDate} today={attendance.today} checkedDates={attendance.checkedDates} />
      <button type="button" className="btn primary block attend-btn" disabled={!canCheck} onClick={check}>
        {label}
      </button>
    </section>
  )
}
