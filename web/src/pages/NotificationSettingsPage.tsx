import { useCallback, useEffect, useState } from 'react'
import { errorMessage } from '../api/client'
import { getPushConsent, updatePushConsent, type PushConsent } from '../api/push'
import { bridge, type NotificationPermissionResult } from '../bridge/app'
import { readNotificationPermission as readPermission, requestNotificationPermissionIfDefault } from '../bridge/web'
import { ErrorBox, Loading } from '../components/Boxes'
import { Screen, TopBar } from '../components/Layout'
import Toast from '../components/Toast'
import { formatDate } from '../util/format'

const today = () => formatDate(new Date().toISOString())

/** "2026.09.27 수신 동의" / "2026.09.27 수신 거부" / "설정 안 함" */
function consentStatus(on: boolean, updatedAt: string | null): string {
  if (!updatedAt) return '설정 안 함'
  return `${formatDate(updatedAt)} ${on ? '수신 동의' : '수신 거부'}`
}

/** 정보통신망법: 수신 동의·거부 결과를 처리 일자와 함께 알린다. */
const consentToast = (label: string, on: boolean, updatedAt: string | null) =>
  `모두의 커머스 · ${updatedAt ? formatDate(updatedAt) : today()} ${label} 수신에 ${on ? '동의' : '거부'}했습니다`

/** 마이 페이지 > 알림 설정. 혜택·이벤트(마케팅) 푸시와 야간 푸시 수신 동의. */
export default function NotificationSettingsPage() {
  const inApp = bridge() !== undefined
  const [consent, setConsent] = useState<PushConsent | null>(null)
  const [error, setError] = useState(false)
  const [saving, setSaving] = useState(false)
  const [permission, setPermission] = useState<NotificationPermissionResult | null>(readPermission)
  const [toast, setToast] = useState<string | null>(null)
  const clearToast = useCallback(() => setToast(null), [])

  const load = useCallback(() => {
    setError(false)
    getPushConsent()
      .then(setConsent)
      .catch(() => setError(true))
  }, [])
  useEffect(load, [load])

  // 설정 앱에서 권한을 바꾸고 돌아오면 배너를 다시 판단한다.
  useEffect(() => {
    const refresh = () => {
      if (document.visibilityState === 'visible') setPermission(readPermission())
    }
    document.addEventListener('visibilitychange', refresh)
    window.addEventListener('focus', refresh)
    return () => {
      document.removeEventListener('visibilitychange', refresh)
      window.removeEventListener('focus', refresh)
    }
  }, [])

  const save = async (next: { marketing: boolean; night: boolean }, toastOf: (saved: PushConsent) => string) => {
    setSaving(true)
    try {
      const saved = await updatePushConsent(next)
      setConsent(saved)
      setToast(toastOf(saved))
    } catch (e) {
      setToast(errorMessage(e, '알림 설정을 저장하지 못했습니다.'))
    } finally {
      setSaving(false)
    }
  }

  const toggleMarketing = async () => {
    if (!consent || saving) return
    const on = !consent.marketing
    if (on) {
      // 앱에서 켤 때는 OS 권한부터 묻는다. 거부해도 동의는 서버에 저장하고 배너로 안내한다.
      setSaving(true)
      const asked = await requestNotificationPermissionIfDefault()
      if (asked) setPermission(asked)
    }
    await save({ marketing: on, night: on ? consent.night : false }, (s) => consentToast('혜택·이벤트 알림', s.marketing, s.marketingUpdatedAt))
  }

  const toggleNight = () => {
    if (!consent || saving || !consent.marketing) return
    save({ marketing: true, night: !consent.night }, (s) => consentToast('야간 알림', s.night, s.nightUpdatedAt))
  }

  return (
    <Screen className="my notify-settings">
      <TopBar title="알림 설정" back />
      {error && consent === null ? (
        <ErrorBox message="알림 설정을 불러오지 못했습니다." onRetry={load} />
      ) : consent === null ? (
        <Loading />
      ) : (
        <>
          {!inApp && <p className="notify-note">알림은 모두의 커머스 앱에서 받을 수 있어요</p>}
          {inApp && permission === 'denied' && (
            <div className="notify-banner" role="alert">
              <span>휴대폰 설정에서 알림이 꺼져 있어요</span>
              <button type="button" onClick={() => bridge()?.openNotificationSettings?.()}>
                설정 열기
              </button>
            </div>
          )}
          <section className="my-card notify-card" aria-label="알림 수신 동의">
            <ToggleRow
              label="혜택·이벤트 알림"
              desc="특가·쿠폰·기획전 소식을 알려 드려요"
              status={consentStatus(consent.marketing, consent.marketingUpdatedAt)}
              checked={consent.marketing}
              disabled={saving}
              onToggle={toggleMarketing}
            />
            <ToggleRow
              label="야간 알림 (21시~08시)"
              desc={consent.marketing ? '밤 9시~아침 8시에도 혜택 알림을 받아요' : '혜택·이벤트 알림을 켜야 설정할 수 있어요'}
              status={consentStatus(consent.night, consent.nightUpdatedAt)}
              checked={consent.night}
              disabled={saving || !consent.marketing}
              onToggle={toggleNight}
            />
          </section>
        </>
      )}
      <Toast message={toast} onDone={clearToast} />
    </Screen>
  )
}

interface ToggleRowProps {
  label: string
  desc: string
  status: string
  checked: boolean
  disabled: boolean
  onToggle: () => void
}

function ToggleRow({ label, desc, status, checked, disabled, onToggle }: ToggleRowProps) {
  return (
    <div className={`notify-row${disabled ? ' disabled' : ''}`}>
      <div className="body">
        <div className="lbl">{label}</div>
        <div className="desc">{desc}</div>
        <div className="status">{status}</div>
      </div>
      <button type="button" role="switch" aria-checked={checked} aria-label={label} className={`switch${checked ? ' on' : ''}`} disabled={disabled} onClick={onToggle}>
        <span className="knob" />
      </button>
    </div>
  )
}
