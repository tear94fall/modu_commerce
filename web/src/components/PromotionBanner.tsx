import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { bannerBackground, TYPE_LABELS, type PromotionBanner } from '../api/promotions'

/** 자동으로 넘어가는 간격. */
export const AUTO_ADVANCE_MS = 4000
/** 이만큼(px) 넘게 움직이면 탭이 아니라 밀기로 본다. */
const DRAG_SLOP = 8
/** 한 장 너비의 이 비율 넘게 밀면 다음/이전 장으로 넘어간다. */
const SWIPE_RATIO = 0.2

type BannerLike = Pick<PromotionBanner, 'type' | 'title' | 'subtitle' | 'bannerImageUrl' | 'bannerColor'>

/**
 * 배너 그림: 이미지가 있으면 꽉 채우고 아래쪽 어두운 그러데이션 위에 글자를, 없으면 배너 색(기본 브랜드 레드) 위에 흰 글자.
 * 홈 캐러셀과 기획전 상세 머리가 같이 쓴다.
 */
export function BannerArt({ banner, children }: { banner: BannerLike; children?: ReactNode }) {
  const image = banner.bannerImageUrl
  return (
    <div className={`promo-art ${image ? 'has-image' : ''}`} style={{ background: bannerBackground(banner.bannerColor) }}>
      {image && <img src={image} alt="" loading="lazy" draggable={false} />}
      <div className="promo-text">
        <span className="promo-type">{TYPE_LABELS[banner.type]}</span>
        <div className="promo-title">{banner.title}</div>
        {banner.subtitle && <div className="promo-sub">{banner.subtitle}</div>}
      </div>
      {children}
    </div>
  )
}

function prefersReducedMotion(): boolean {
  try {
    return typeof window.matchMedia === 'function' && window.matchMedia('(prefers-reduced-motion: reduce)').matches
  } catch {
    return false
  }
}

/**
 * 홈 맨 위 배너 캐러셀. 4초마다 넘기고(끝에서 처음으로), 손가락으로 밀 수 있다.
 * 누르고 있는 동안은 멈췄다가 떼면 다시 4초를 센다. 동작 줄이기 설정이면 자동으로 넘기지 않는다.
 * 스크롤 스냅 대신 transform 을 쓴다: WebView 에서 끝→처음 되감기와 자동 넘김을 확실히 맞추려고.
 */
export default function PromotionCarousel({ banners }: { banners: PromotionBanner[] }) {
  const count = banners.length
  const [index, setIndex] = useState(0)
  const [dragX, setDragX] = useState(0)
  const [touching, setTouching] = useState(false)
  const [reduced] = useState(prefersReducedMotion)
  const viewport = useRef<HTMLDivElement>(null)
  const start = useRef<{ x: number; y: number } | null>(null)
  /** 가로로 밀기 시작했는지(true) / 세로 스크롤인지(false) / 아직 모름(null). */
  const horizontal = useRef<boolean | null>(null)
  const moved = useRef(false)

  const current = count > 0 ? Math.min(index, count - 1) : 0

  useEffect(() => {
    if (count < 2 || touching || reduced) return
    const t = setTimeout(() => setIndex((i) => (i + 1) % count), AUTO_ADVANCE_MS)
    return () => clearTimeout(t)
  }, [current, count, touching, reduced])

  const onTouchStart = (e: React.TouchEvent) => {
    const t = e.touches[0]
    start.current = { x: t.clientX, y: t.clientY }
    horizontal.current = null
    moved.current = false
    setTouching(true)
  }

  const onTouchMove = (e: React.TouchEvent) => {
    if (!start.current) return
    const t = e.touches[0]
    const dx = t.clientX - start.current.x
    const dy = t.clientY - start.current.y
    if (horizontal.current === null && (Math.abs(dx) > DRAG_SLOP || Math.abs(dy) > DRAG_SLOP)) {
      horizontal.current = Math.abs(dx) > Math.abs(dy)
    }
    if (Math.abs(dx) > DRAG_SLOP || Math.abs(dy) > DRAG_SLOP) moved.current = true
    if (horizontal.current && count > 1) setDragX(dx)
  }

  const onTouchEnd = () => {
    const width = viewport.current?.clientWidth || 1
    if (horizontal.current && count > 1 && Math.abs(dragX) > width * SWIPE_RATIO) {
      setIndex((i) => (dragX < 0 ? (i + 1) % count : (i - 1 + count) % count))
    }
    start.current = null
    horizontal.current = null
    setDragX(0)
    setTouching(false)
  }

  /** 밀고 난 손가락이 링크 탭으로 이어지지 않게. */
  const onClickCapture = useCallback((e: React.MouseEvent) => {
    if (moved.current) {
      e.preventDefault()
      e.stopPropagation()
      moved.current = false
    }
  }, [])

  if (count === 0) return null

  const dragging = dragX !== 0
  return (
    <section className="promo-carousel" aria-roledescription="carousel" aria-label="기획전 · 이벤트">
      <div
        ref={viewport}
        className="promo-viewport"
        onTouchStart={onTouchStart}
        onTouchMove={onTouchMove}
        onTouchEnd={onTouchEnd}
        onTouchCancel={onTouchEnd}
        onClickCapture={onClickCapture}
      >
        <div
          className="promo-track"
          style={{
            transform: `translate3d(calc(${-current * 100}% + ${dragX}px), 0, 0)`,
            transition: dragging || reduced ? 'none' : undefined,
          }}
        >
          {banners.map((b, i) => (
            <Link
              key={b.id}
              to={`/promotions/${b.id}`}
              className="promo-slide"
              aria-label={b.title}
              aria-hidden={i !== current}
              tabIndex={i === current ? undefined : -1}
              draggable={false}
            >
              <BannerArt banner={b} />
            </Link>
          ))}
        </div>
        {count > 1 && (
          <div className="promo-count" aria-live="off">
            {current + 1} / {count}
          </div>
        )}
      </div>
      {count > 1 && (
        <div className="promo-dots" aria-hidden="true">
          {banners.map((b, i) => (
            <i key={b.id} className={i === current ? 'on' : undefined} />
          ))}
        </div>
      )}
    </section>
  )
}
