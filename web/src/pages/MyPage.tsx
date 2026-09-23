import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { getProfile, type Profile } from '../api/me'
import { formatPoints, getMyPoints } from '../api/points'
import { logoutSession } from '../auth/session'
import { bridge } from '../bridge/app'
import { Screen, TopBar } from '../components/Layout'

export default function MyPage() {
  const [profile, setProfile] = useState<Profile | null>(null)
  /** null = 아직/못 불러옴(줄은 그대로, 값만 비운다). 포인트 서비스가 죽어도 마이 탭은 떠야 한다. */
  const [points, setPoints] = useState<number | null>(null)
  const [confirm, setConfirm] = useState(false)
  const navigate = useNavigate()

  useEffect(() => {
    getProfile().then(setProfile).catch(() => setProfile({ name: '', email: '', picture: '' }))
    getMyPoints().then(setPoints).catch(() => setPoints(null))
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

  return (
    <Screen tabs>
      <TopBar title="마이" />
      <div className="me">
        {profile?.picture ? <img className="avatar" src={profile.picture} alt="" /> : <div className="avatar" />}
        <div>
          <div className="name">{profile?.name || '모두 회원'}</div>
          <div className="email">{profile?.email}</div>
        </div>
      </div>
      <div className="menu">
        <Link to="/points" className="menu-row">
          포인트
          <span className="value point-value">{points === null ? '' : formatPoints(points)}</span>
        </Link>
        <Link to="/orders" className="menu-row">
          주문 내역
        </Link>
        <Link to="/addresses" className="menu-row">
          배송지 관리
        </Link>
        <button type="button" className="menu-row danger" onClick={() => setConfirm(true)}>
          로그아웃
        </button>
      </div>
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
