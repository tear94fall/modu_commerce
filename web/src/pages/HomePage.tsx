import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getCategories, getProducts, setWish, type Category, type ProductSummary } from '../api/catalog'
import { ErrorBox } from '../components/Boxes'
import { SearchIcon } from '../components/Icons'
import { Screen, TopBar } from '../components/Layout'
import { ProductGrid } from '../components/ProductCard'
import Toast from '../components/Toast'
import { useProductPager } from '../hooks/useProductPager'

const ROW_SIZE = 10

const loadLatest = (page: number) => getProducts({ sort: 'latest', page })

/** 홈: 카테고리 칩, 새 상품·인기 상품 가로줄, 그 아래 전체 상품 격자(최신순 페이징). */
export default function HomePage() {
  const [categories, setCategories] = useState<Category[]>([])
  const [newest, setNewest] = useState<ProductSummary[]>([])
  const [popular, setPopular] = useState<ProductSummary[]>([])
  const pager = useProductPager(loadLatest)
  const [message, setMessage] = useState<string | null>(null)

  const loadSections = useCallback(() => {
    getCategories().then(setCategories).catch(() => {})
    getProducts({ sort: 'latest', size: ROW_SIZE }).then((p) => setNewest(p.content)).catch(() => {})
    getProducts({ sort: 'popular', size: ROW_SIZE }).then((p) => setPopular(p.content)).catch(() => {})
  }, [])

  useEffect(loadSections, [loadSections])

  /** 가로줄과 격자 어디서 눌러도 한 번에 반영한다. */
  const toggleWish = (productId: number) => {
    const inGrid = pager.items.some((p) => p.id === productId)
    const current = [...newest, ...popular, ...pager.items].find((p) => p.id === productId)
    if (!current) return
    const next = !current.wished
    const patch = (wished: boolean) => {
      const f = (list: ProductSummary[]) => list.map((p) => (p.id === productId ? { ...p, wished } : p))
      setNewest(f)
      setPopular(f)
    }
    patch(next)
    if (inGrid) {
      pager.toggleWish(productId).then(() => {})
      return
    }
    setWish(productId, next).catch(() => {
      patch(!next)
      setMessage('찜을 바꾸지 못했습니다.')
    })
  }

  const clearMessage = useCallback(() => {
    setMessage(null)
    pager.clearMessage()
  }, [pager])

  return (
    <Screen tabs>
      <TopBar
        title="모두의 커머스"
        actions={
          <Link to="/search" className="icon-btn" aria-label="검색">
            <SearchIcon />
          </Link>
        }
      />
      {categories.length > 0 && (
        <div className="chips">
          {categories.map((c) => (
            <Link key={c.id} to={`/products?categoryId=${c.id}&title=${encodeURIComponent(c.name)}`} className="chip">
              {c.name}
            </Link>
          ))}
        </div>
      )}
      {newest.length > 0 && (
        <section>
          <div className="section section-head">
            <h2>새로 들어온 상품</h2>
            <Link to="/products?sort=latest&title=%EC%83%88%EB%A1%9C%20%EB%93%A4%EC%96%B4%EC%98%A8%20%EC%83%81%ED%92%88">더보기</Link>
          </div>
          <ProductGrid products={newest} onToggleWish={toggleWish} row />
        </section>
      )}
      {popular.length > 0 && (
        <section>
          <div className="section section-head">
            <h2>인기 상품</h2>
            <Link to="/products?sort=popular&title=%EC%9D%B8%EA%B8%B0%20%EC%83%81%ED%92%88">더보기</Link>
          </div>
          <ProductGrid products={popular} onToggleWish={toggleWish} row />
        </section>
      )}
      <section>
        <div className="section section-head">
          <h2>전체 상품</h2>
        </div>
        {pager.error ? (
          <ErrorBox message="상품을 불러오지 못했습니다." onRetry={pager.retry} />
        ) : (
          <>
            <ProductGrid products={pager.items} onToggleWish={toggleWish} />
            {pager.loading && <div className="spinner" role="status" aria-label="불러오는 중" />}
            {!pager.loading && pager.hasNext && (
              <button type="button" className="btn outline small load-more" onClick={pager.loadMore}>
                더 보기
              </button>
            )}
          </>
        )}
      </section>
      <Toast message={message ?? pager.message} onDone={clearMessage} />
    </Screen>
  )
}
