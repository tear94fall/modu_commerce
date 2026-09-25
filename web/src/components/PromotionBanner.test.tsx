import { act, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { PromotionBanner } from '../api/promotions'
import PromotionCarousel, { AUTO_ADVANCE_MS } from './PromotionBanner'

const banner = (over: Partial<PromotionBanner> = {}): PromotionBanner => ({
  id: 1,
  type: 'EXHIBITION',
  title: '가을 신상 기획전',
  subtitle: '최대 30% 할인',
  bannerImageUrl: null,
  bannerColor: '#E11D48',
  startDate: '2026-09-01',
  endDate: '2026-09-30',
  ...over,
})

const three = [banner(), banner({ id: 2, type: 'EVENT', title: '매일 출석 체크', subtitle: null }), banner({ id: 3, title: '홈카페 모음', subtitle: '원두 · 드리퍼', bannerImageUrl: 'https://img.example/cafe.jpg' })]

const renderCarousel = (banners: PromotionBanner[]) =>
  render(
    <MemoryRouter initialEntries={['/']}>
      <Routes>
        <Route path="/" element={<PromotionCarousel banners={banners} />} />
        <Route path="/promotions/:id" element={<p>기획전 화면</p>} />
      </Routes>
    </MemoryRouter>,
  )

const counter = () => screen.getByText(/^\d+ \/ \d+$/).textContent
const track = () => document.querySelector('.promo-track') as HTMLElement

describe('PromotionCarousel', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => vi.useRealTimers())

  it('renders every slide with its type chip, colour and image', () => {
    renderCarousel(three)

    expect(screen.getByText('가을 신상 기획전')).toBeInTheDocument()
    expect(screen.getByText('최대 30% 할인')).toBeInTheDocument()
    expect(screen.getByText('매일 출석 체크')).toBeInTheDocument()
    expect(screen.getAllByText('기획전').length).toBe(2)
    expect(screen.getByText('이벤트')).toBeInTheDocument()
    expect(document.querySelector('img')).toHaveAttribute('src', 'https://img.example/cafe.jpg')
    expect(document.querySelectorAll('.promo-dots i').length).toBe(3)
    expect(counter()).toBe('1 / 3')
  })

  it('advances every 4 seconds and loops back to the first slide', () => {
    renderCarousel(three)

    act(() => vi.advanceTimersByTime(AUTO_ADVANCE_MS - 1))
    expect(counter()).toBe('1 / 3')
    act(() => vi.advanceTimersByTime(1))
    expect(counter()).toBe('2 / 3')
    expect(track().style.transform).toContain('-100%')
    act(() => vi.advanceTimersByTime(AUTO_ADVANCE_MS))
    expect(counter()).toBe('3 / 3')
    act(() => vi.advanceTimersByTime(AUTO_ADVANCE_MS))
    expect(counter()).toBe('1 / 3')
    expect(document.querySelectorAll('.promo-dots i')[0]).toHaveClass('on')
  })

  it('pauses while touched, swipes to the next slide and resumes afterwards', () => {
    renderCarousel(three)
    const viewport = document.querySelector('.promo-viewport') as HTMLElement

    fireEvent.touchStart(viewport, { touches: [{ clientX: 200, clientY: 50 }] })
    act(() => vi.advanceTimersByTime(AUTO_ADVANCE_MS * 2))
    expect(counter()).toBe('1 / 3')

    // jsdom 은 너비가 0 이라 1px 기준으로 본다 → 조금만 밀어도 넘어간다.
    fireEvent.touchMove(viewport, { touches: [{ clientX: 100, clientY: 52 }] })
    fireEvent.touchEnd(viewport)
    expect(counter()).toBe('2 / 3')

    act(() => vi.advanceTimersByTime(AUTO_ADVANCE_MS))
    expect(counter()).toBe('3 / 3')
  })

  it('opens the promotion on tap', () => {
    renderCarousel(three)

    fireEvent.click(screen.getByRole('link', { name: '가을 신상 기획전' }))
    expect(screen.getByText('기획전 화면')).toBeInTheDocument()
  })

  it('shows a single banner without dots or auto-advance', () => {
    renderCarousel([banner()])

    expect(document.querySelector('.promo-dots')).toBeNull()
    expect(screen.queryByText(/^\d+ \/ \d+$/)).toBeNull()
    act(() => vi.advanceTimersByTime(AUTO_ADVANCE_MS * 3))
    expect(track().style.transform).toContain('0%')
  })

  it('does not auto-advance when the user prefers reduced motion', () => {
    vi.stubGlobal('matchMedia', (q: string) => ({ matches: q.includes('reduce'), media: q, addEventListener() {}, removeEventListener() {} }))
    try {
      renderCarousel(three)
      act(() => vi.advanceTimersByTime(AUTO_ADVANCE_MS * 3))
      expect(counter()).toBe('1 / 3')
    } finally {
      vi.unstubAllGlobals()
    }
  })

  it('renders nothing without banners', () => {
    const { container } = renderCarousel([])
    expect(container).toBeEmptyDOMElement()
  })
})
