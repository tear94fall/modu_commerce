export const Loading = () => <div className="spinner" role="status" aria-label="불러오는 중" />

export const EmptyBox = ({ message }: { message: string }) => <div className="box">{message}</div>

export function ErrorBox({ message = '불러오지 못했습니다.', onRetry }: { message?: string; onRetry?: () => void }) {
  return (
    <div className="box">
      <div>{message}</div>
      {onRetry && (
        <button type="button" className="btn outline small retry" onClick={onRetry}>
          다시 시도
        </button>
      )}
    </div>
  )
}
