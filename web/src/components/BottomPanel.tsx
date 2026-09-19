import { useEffect, type ReactNode } from 'react'

interface BottomPanelProps {
  open: boolean
  onClose: () => void
  children: ReactNode
}

/** 아래에서 올라오는 패널. 스크림을 누르거나 뒤로 가기(popstate)를 하면 닫힌다. */
export default function BottomPanel({ open, onClose, children }: BottomPanelProps) {
  useEffect(() => {
    if (!open) return
    // 열릴 때 히스토리 한 칸을 넣어 두면 뒤로 가기가 화면이 아니라 패널을 닫는다.
    history.pushState({ panel: true }, '')
    const onPop = () => onClose()
    window.addEventListener('popstate', onPop)
    return () => {
      window.removeEventListener('popstate', onPop)
      if (history.state?.panel) history.back()
    }
  }, [open, onClose])

  if (!open) return null
  return (
    <>
      <div className="panel-scrim" onClick={onClose} data-testid="panel-scrim" />
      <div className="panel" role="dialog">
        <div className="handle" />
        {children}
      </div>
    </>
  )
}
