import { Link } from 'react-router-dom'
import type { ProductSummary } from '../api/catalog'
import { HeartIcon, HeartOutlineIcon } from './Icons'
import Price from './Price'
import { formatRating } from '../api/reviews'
import { StarIcon } from './Icons'

interface ProductCardProps {
  product: ProductSummary
  onToggleWish: (id: number) => void
}

export default function ProductCard({ product, onToggleWish }: ProductCardProps) {
  return (
    <div className="card">
      <Link to={`/products/${product.id}`} aria-label={product.name}>
        <div className="thumb">
          {product.imageUrl ? <img src={product.imageUrl} alt="" loading="lazy" /> : null}
          {product.soldOut && <div className="sold">품절</div>}
        </div>
        <div className="name">{product.name}</div>
        <Price price={product.price} listPrice={product.listPrice} discountRate={product.discountRate} />
        {product.reviewCount > 0 && (
          <div className="rating-line">
            <StarIcon className="on" /> {formatRating(product.ratingAverage)} <span className="cnt">({product.reviewCount.toLocaleString('ko-KR')})</span>
          </div>
        )}
      </Link>
      <button
        type="button"
        className={`wish-btn ${product.wished ? 'on' : ''}`}
        aria-label={product.wished ? '찜 해제' : '찜'}
        aria-pressed={product.wished}
        onClick={() => onToggleWish(product.id)}
      >
        {product.wished ? <HeartIcon /> : <HeartOutlineIcon />}
      </button>
    </div>
  )
}

interface ProductGridProps {
  products: ProductSummary[]
  onToggleWish: (id: number) => void
  /** true 면 가로 한 줄, 아니면 두 열 격자. */
  row?: boolean
}

export function ProductGrid({ products, onToggleWish, row = false }: ProductGridProps) {
  return (
    <div className={row ? 'row' : 'grid'}>
      {products.map((p) => (
        <ProductCard key={p.id} product={p} onToggleWish={onToggleWish} />
      ))}
    </div>
  )
}
