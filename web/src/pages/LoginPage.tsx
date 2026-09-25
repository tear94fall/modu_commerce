import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { renderGoogleButton } from '../auth/google'
import { loginWithGoogle } from '../auth/session'
import { getToken, setToken } from '../auth/token'
import { hasBridge } from '../bridge/app'
import { Screen } from '../components/Layout'

/**
 * 브라우저 로그인. Google 로 로그인 → ID 토큰을 auth-service 에서 커머스 토큰으로 교환(앱의 Google 폴백과 같은 grant).
 * 앱 안에서는 앱이 로그인을 맡으므로 이 화면에 올 일이 없다. 아래 접힌 칸은 토큰을 직접 넣는 개발용 우회다.
 */
export default function LoginPage() {
  const navigate = useNavigate()
  const buttonRef = useRef<HTMLDivElement>(null)
  const [working, setWorking] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [raw, setRaw] = useState('')
  const loggedIn = hasBridge() || !!getToken()

  useEffect(() => {
    if (loggedIn || !buttonRef.current) return
    let cancelled = false
    renderGoogleButton(buttonRef.current, async (idToken) => {
      if (cancelled) return
      setWorking(true)
      setMessage('로그인하는 중…')
      try {
        await loginWithGoogle(idToken)
        navigate('/', { replace: true })
      } catch {
        setMessage('로그인에 실패했습니다. 잠시 후 다시 시도해 주세요.')
        setWorking(false)
      }
    }).catch(() => setMessage('Google 로그인을 불러오지 못했습니다.'))
    return () => {
      cancelled = true
    }
  }, [loggedIn, navigate])

  if (loggedIn) return <Navigate to="/" replace />

  const submitRaw = (e: FormEvent) => {
    e.preventDefault()
    const t = raw.trim()
    if (!t) return
    setToken(t)
    navigate('/', { replace: true })
  }

  return (
    <Screen>
      <div className="login">
        <div className="login-brand">
          <div className="logo" aria-hidden="true" />
          <h1>모두의 커머스</h1>
          <p>모두 계정으로 바로 쇼핑하세요</p>
        </div>
        <div ref={buttonRef} className="google-button" aria-busy={working} />
        {message && <p className="hint" role="status">{message}</p>}
        <details className="dev">
          <summary>개발용: 토큰 직접 입력</summary>
          <form onSubmit={submitRaw}>
            <p className="hint">모두 계정 액세스 토큰(aud=modu-commerce)을 붙여넣습니다. refresh 가 없어 만료되면 다시 넣어야 합니다.</p>
            <textarea value={raw} onChange={(e) => setRaw(e.target.value)} placeholder="eyJ…" aria-label="액세스 토큰" />
            <button type="submit" className="btn outline block" disabled={!raw.trim()}>
              시작하기
            </button>
          </form>
        </details>
      </div>
    </Screen>
  )
}
