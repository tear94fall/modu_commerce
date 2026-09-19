import { useState, type FormEvent } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { getToken, setToken } from '../auth/token'
import { hasBridge } from '../bridge/app'
import { Screen, TopBar } from '../components/Layout'

/**
 * 브라우저에서 열었을 때의 임시 로그인: 액세스 토큰을 붙여넣는다(개발용).
 * 앱 안에서는 앱이 로그인을 맡으므로 이 화면에 올 일이 없다. 모두 계정 웹 로그인은 다음 단계.
 */
export default function LoginPage() {
  const [value, setValue] = useState('')
  const navigate = useNavigate()
  if (hasBridge() || getToken()) return <Navigate to="/" replace />

  const submit = (e: FormEvent) => {
    e.preventDefault()
    const t = value.trim()
    if (!t) return
    setToken(t)
    navigate('/', { replace: true })
  }

  return (
    <Screen>
      <TopBar title="모두의 커머스" />
      <form className="login" onSubmit={submit}>
        <p className="hint">
          브라우저 개발용 로그인입니다. 모두 계정 액세스 토큰(aud=modu-commerce)을 붙여넣으세요. 앱 안에서는 앱이 로그인한 토큰을 그대로 씁니다.
        </p>
        <textarea value={value} onChange={(e) => setValue(e.target.value)} placeholder="eyJ…" aria-label="액세스 토큰" />
        <button type="submit" className="btn primary block" disabled={!value.trim()}>
          시작하기
        </button>
      </form>
    </Screen>
  )
}
