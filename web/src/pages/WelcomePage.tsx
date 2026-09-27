import { useCallback, useEffect, useState } from 'react'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom'
import { errorMessage } from '../api/client'
import { joinCustomer } from '../api/customer'
import { requestNotificationPermissionIfDefault } from '../bridge/web'
import { useCustomer } from '../customer/context'
import { safeNext } from '../customer/paths'
import { ChevronRightIcon } from '../components/Icons'
import { Screen, TopBar } from '../components/Layout'
import Toast from '../components/Toast'

interface Agreement {
  terms: boolean
  privacy: boolean
  marketing: boolean
}

const NONE: Agreement = { terms: false, privacy: false, marketing: false }

/** 약관 보기로 다녀와도 체크가 남게 탭 세션에 잠깐 둔다. */
const DRAFT_KEY = 'modu.welcome.agreement'

function loadDraft(): Agreement {
  try {
    const raw = sessionStorage.getItem(DRAFT_KEY)
    if (!raw) return NONE
    const v = JSON.parse(raw) as Partial<Agreement>
    return { terms: v.terms === true, privacy: v.privacy === true, marketing: v.marketing === true }
  } catch {
    return NONE
  }
}

function saveDraft(a: Agreement | null) {
  try {
    if (a) sessionStorage.setItem(DRAFT_KEY, JSON.stringify(a))
    else sessionStorage.removeItem(DRAFT_KEY)
  } catch {
    // 저장소를 못 쓰면 체크를 기억하지 않을 뿐이다.
  }
}

/**
 * 모두의 커머스 가입(약관 동의). 필수 두 가지에 동의해야 장바구니·주문·쿠폰·등급 혜택을 쓸 수 있다.
 * 가입하면 `?next=`(허용된 앱 안 경로) 또는 홈으로 돌아간다.
 */
export default function WelcomePage() {
  const [params] = useSearchParams()
  const next = safeNext(params.get('next'))
  const navigate = useNavigate()
  const { status, setCustomer } = useCustomer()
  const [agree, setAgree] = useState<Agreement>(loadDraft)
  const [joining, setJoining] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const clearMessage = useCallback(() => setMessage(null), [])

  useEffect(() => saveDraft(agree), [agree])

  const all = agree.terms && agree.privacy && agree.marketing
  const canJoin = agree.terms && agree.privacy && !joining

  const join = async () => {
    if (!canJoin) return
    setJoining(true)
    try {
      // 알림을 받겠다고 했으면 앱에서 OS 권한부터 묻는다. 거부해도 가입은 한다(설정에서 다시 켤 수 있다).
      if (agree.marketing) await requestNotificationPermissionIfDefault()
      const customer = await joinCustomer({ agreeTerms: true, agreePrivacy: true, marketing: agree.marketing })
      saveDraft(null)
      setCustomer(customer)
      navigate(next, { replace: true })
    } catch (e) {
      setMessage(errorMessage(e, '가입하지 못했습니다. 잠시 뒤 다시 시도해 주세요.'))
      setJoining(false)
    }
  }

  // 이미 가입한 사람이 들어오면 가려던 곳으로.
  if (status === 'customer' && !joining) return <Navigate to={next} replace />

  return (
    <Screen className="welcome with-bottom-bar">
      <TopBar title="모두의 커머스 시작하기" back />
      <div className="welcome-hero">
        <h1>모두의 커머스에 오신 걸 환영해요</h1>
        <p>약관에 동의하면 장바구니·주문·쿠폰과 회원 등급 적립 혜택을 쓸 수 있어요.</p>
      </div>
      <section className="my-card agree-card" aria-label="약관 동의">
        <label className="agree-row all">
          <input type="checkbox" checked={all} onChange={(e) => setAgree(e.target.checked ? { terms: true, privacy: true, marketing: true } : NONE)} />
          <span className="lbl">전체 동의</span>
        </label>
        <AgreeRow label="이용약관 동의" required checked={agree.terms} onChange={(v) => setAgree((a) => ({ ...a, terms: v }))} to="/terms" />
        <AgreeRow label="개인정보 수집·이용 동의" required checked={agree.privacy} onChange={(v) => setAgree((a) => ({ ...a, privacy: v }))} to="/privacy" />
        <AgreeRow label="혜택·이벤트 알림 수신 동의" checked={agree.marketing} onChange={(v) => setAgree((a) => ({ ...a, marketing: v }))} desc="특가·쿠폰·기획전 소식을 알림으로 받아요. 나중에 알림 설정에서 바꿀 수 있어요." />
      </section>
      <div className="bottom-bar">
        <button type="button" className="btn primary block" disabled={!canJoin} onClick={join}>
          {joining ? '가입하는 중…' : '동의하고 시작하기'}
        </button>
      </div>
      <Toast message={message} onDone={clearMessage} />
    </Screen>
  )
}

interface AgreeRowProps {
  label: string
  required?: boolean
  checked: boolean
  onChange: (checked: boolean) => void
  to?: string
  desc?: string
}

function AgreeRow({ label, required = false, checked, onChange, to, desc }: AgreeRowProps) {
  const full = `${label} (${required ? '필수' : '선택'})`
  return (
    <div className="agree-row">
      <label className="check">
        <input type="checkbox" checked={checked} aria-label={full} onChange={(e) => onChange(e.target.checked)} />
        <span className="lbl">
          <span className={required ? 'agree-req' : 'agree-opt'}>[{required ? '필수' : '선택'}]</span> {label}
          {desc && <span className="desc">{desc}</span>}
        </span>
      </label>
      {to && (
        <Link to={to} className="view" aria-label={`${label} 보기`}>
          보기 <ChevronRightIcon />
        </Link>
      )}
    </div>
  )
}
