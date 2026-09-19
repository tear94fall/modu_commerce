import { useEffect } from 'react'

interface ToastProps {
  message: string | null
  onDone: () => void
}

/** 2초 뒤에 사라지는 안내. */
export default function Toast({ message, onDone }: ToastProps) {
  useEffect(() => {
    if (!message) return
    const t = setTimeout(onDone, 2000)
    return () => clearTimeout(t)
  }, [message, onDone])
  if (!message) return null
  return (
    <div className="toast" role="status">
      {message}
    </div>
  )
}
