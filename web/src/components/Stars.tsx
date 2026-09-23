import { StarIcon } from './Icons'

/** 읽기용 별 다섯 개. 채워진 개수는 반올림(4.5 → 5, 4.4 → 4). */
export function Stars({ rating, className = '' }: { rating: number; className?: string }) {
  const filled = Math.round(rating)
  return (
    <span className={`stars ${className}`.trim()} role="img" aria-label={`별점 ${rating}점`}>
      {[1, 2, 3, 4, 5].map((n) => (
        <StarIcon key={n} className={n <= filled ? 'on' : undefined} />
      ))}
    </span>
  )
}

/** 리뷰 쓰기의 별점 고르기. 0 이면 아직 안 고른 것. */
export function StarPicker({ value, onChange }: { value: number; onChange: (n: number) => void }) {
  return (
    <div className="star-picker" role="radiogroup" aria-label="별점">
      {[1, 2, 3, 4, 5].map((n) => (
        <button key={n} type="button" role="radio" aria-checked={value === n} aria-label={`${n}점`} className={n <= value ? 'on' : undefined} onClick={() => onChange(n)}>
          <StarIcon />
        </button>
      ))}
    </div>
  )
}
