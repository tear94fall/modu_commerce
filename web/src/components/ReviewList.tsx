import { Link } from 'react-router-dom'
import type { Review } from '../api/reviews'
import { formatDateTime } from '../util/format'
import { Stars } from './Stars'

/** 상품 리뷰 줄. 내 리뷰에는 수정 링크가 붙는다. */
export default function ReviewList({ reviews }: { reviews: Review[] }) {
  return (
    <ul className="review-list">
      {reviews.map((r) => (
        <li key={r.id} className="review-row">
          <div className="head">
            <Stars rating={r.rating} />
            <span className="who">{r.authorName}</span>
            <span className="when">{formatDateTime(r.createdAt)}</span>
            {r.mine && (
              <Link to={`/reviews/${r.id}/edit`} className="edit">
                수정
              </Link>
            )}
          </div>
          {r.optionLabel && <div className="review-opt">{r.optionLabel}</div>}
          <p className="body">{r.content}</p>
        </li>
      ))}
    </ul>
  )
}
