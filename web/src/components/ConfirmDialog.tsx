import type { ReactNode } from 'react'

interface ConfirmDialogProps {
  open: boolean
  title: string
  message: string
  /** 문구 아래 추가 내용(예: 사유 고르기). */
  children?: ReactNode
  confirmLabel: string
  onConfirm: () => void
  onClose: () => void
}

export default function ConfirmDialog({ open, title, message, children, confirmLabel, onConfirm, onClose }: ConfirmDialogProps) {
  if (!open) return null
  return (
    <div className="dialog-scrim" onClick={onClose}>
      <div className="dialog" role="dialog" aria-label={title} onClick={(e) => e.stopPropagation()}>
        <h3>{title}</h3>
        <p>{message}</p>
        {children}
        <div className="actions">
          <button type="button" onClick={onClose}>
            취소
          </button>
          <button type="button" style={{ color: 'var(--brand)' }} onClick={onConfirm}>
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}
