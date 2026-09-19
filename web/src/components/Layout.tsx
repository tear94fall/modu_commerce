import type { ReactNode } from 'react'
import { NavLink, useNavigate } from 'react-router-dom'
import { BackIcon, CategoryIcon, HeartOutlineIcon, HomeIcon, PersonIcon } from './Icons'

interface TopBarProps {
  title: string
  back?: boolean
  actions?: ReactNode
  /** 제목 자리에 다른 것(검색창)을 넣을 때. */
  center?: ReactNode
}

export function TopBar({ title, back, actions, center }: TopBarProps) {
  const navigate = useNavigate()
  return (
    <header className="top-bar">
      {back && (
        <button type="button" className="icon-btn" aria-label="뒤로" onClick={() => navigate(-1)}>
          <BackIcon />
        </button>
      )}
      {center ?? <div className="title">{title}</div>}
      {actions}
    </header>
  )
}

const TABS = [
  { to: '/', label: '홈', icon: <HomeIcon /> },
  { to: '/categories', label: '카테고리', icon: <CategoryIcon /> },
  { to: '/wishlist', label: '찜', icon: <HeartOutlineIcon /> },
  { to: '/my', label: '마이', icon: <PersonIcon /> },
]

export function TabBar() {
  return (
    <nav className="tab-bar" aria-label="탭">
      {TABS.map((tab) => (
        <NavLink key={tab.to} to={tab.to} end={tab.to === '/'} className={({ isActive }) => (isActive ? 'active' : undefined)}>
          {tab.icon}
          <span>{tab.label}</span>
        </NavLink>
      ))}
    </nav>
  )
}

interface ScreenProps {
  children: ReactNode
  tabs?: boolean
  className?: string
}

/** 탭 화면은 하단 탭 높이만큼, 그 외는 여백만 바닥에 둔다. */
export function Screen({ children, tabs = false, className = '' }: ScreenProps) {
  return (
    <div className="screen">
      <main className={`content ${tabs ? '' : 'no-tabs'} ${className}`.trim()}>{children}</main>
      {tabs && <TabBar />}
    </div>
  )
}
