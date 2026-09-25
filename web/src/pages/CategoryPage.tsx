import { useCallback, useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { categoryLink, getCategories, getProducts, setWish, type Category, type ProductSummary } from '../api/catalog'
import { EmptyBox, ErrorBox, Loading } from '../components/Boxes'
import CategoryTile from '../components/CategoryTile'
import { SearchIcon } from '../components/Icons'
import { Screen, TopBar } from '../components/Layout'
import { ProductGrid } from '../components/ProductCard'
import Toast from '../components/Toast'

const POPULAR_SIZE = 8

/**
 * 카테고리 탭. 왼쪽 레일에 대분류, 오른쪽에 고른 대분류의 소분류 격자와 인기 상품 줄.
 * 고른 대분류는 `?root=` 에 둔다(replace). 상품 목록에서 뒤로 오면 같은 대분류가 열려 있다.
 */
export default function CategoryPage() {
  const [roots, setRoots] = useState<Category[] | null>(null)
  const [error, setError] = useState(false)
  const [params, setParams] = useSearchParams()

  const load = useCallback(() => {
    setError(false)
    getCategories()
      .then(setRoots)
      .catch(() => setError(true))
  }, [])
  useEffect(load, [load])

  const rootParam = Number(params.get('root'))
  const selected = roots?.find((r) => r.id === rootParam) ?? roots?.[0] ?? null
  const select = (id: number) => setParams({ root: String(id) }, { replace: true })

  return (
    <Screen tabs>
      <TopBar
        title="카테고리"
        actions={
          <Link to="/search" className="icon-btn" aria-label="검색">
            <SearchIcon />
          </Link>
        }
      />
      {error ? (
        <ErrorBox message="카테고리를 불러오지 못했습니다." onRetry={load} />
      ) : roots === null ? (
        <Loading />
      ) : roots.length === 0 || selected === null ? (
        <EmptyBox message="카테고리가 없습니다." />
      ) : (
        <div className="cat-split">
          <nav className="cat-rail" aria-label="대분류">
            {roots.map((root) => (
              <button
                key={root.id}
                type="button"
                className={`cat-rail-item ${root.id === selected.id ? 'on' : ''}`.trim()}
                aria-current={root.id === selected.id ? 'true' : undefined}
                onClick={() => select(root.id)}
              >
                <CategoryTile category={root} size="sm" />
                <span className="label">{root.name}</span>
              </button>
            ))}
          </nav>
          <RootPane key={selected.id} root={selected} />
        </div>
      )}
    </Screen>
  )
}

/** 고른 대분류 한 칸. 대분류가 바뀌면 key 로 새로 그려 인기 상품을 다시 받는다. */
function RootPane({ root }: { root: Category }) {
  const [popular, setPopular] = useState<ProductSummary[]>([])
  const [message, setMessage] = useState<string | null>(null)
  const clearMessage = useCallback(() => setMessage(null), [])

  useEffect(() => {
    let alive = true
    // 인기 상품 줄은 덤이다. 못 받으면 줄을 숨긴다.
    getProducts({ categoryId: root.id, sort: 'popular', size: POPULAR_SIZE })
      .then((p) => alive && setPopular(p.content.slice(0, POPULAR_SIZE)))
      .catch(() => alive && setPopular([]))
    return () => {
      alive = false
    }
  }, [root.id])

  const toggleWish = (productId: number) => {
    const current = popular.find((p) => p.id === productId)
    if (!current) return
    const patch = (wished: boolean) => setPopular((list) => list.map((p) => (p.id === productId ? { ...p, wished } : p)))
    patch(!current.wished)
    setWish(productId, !current.wished).catch(() => {
      patch(current.wished)
      setMessage('찜을 바꾸지 못했습니다.')
    })
  }

  const children = root.children ?? []

  return (
    <section className="cat-pane" aria-label={root.name}>
      <div className="cat-pane-head">
        <CategoryTile category={root} size="lg" />
        <h2>{root.name}</h2>
        <Link to={categoryLink(root)} className="cat-all">
          전체 보기 <span aria-hidden="true">›</span>
        </Link>
      </div>
      <div className="cat-sub-grid">
        {children.length === 0 ? (
          <Link to={categoryLink(root)} className="cat-sub">
            <CategoryTile category={root} />
            <span className="label">전체 상품 보기</span>
          </Link>
        ) : (
          children.map((child) => (
            <Link key={child.id} to={categoryLink(child)} className="cat-sub">
              <CategoryTile category={child} />
              <span className="label">{child.name}</span>
            </Link>
          ))
        )}
      </div>
      {popular.length > 0 && (
        <div className="cat-popular">
          <div className="cat-popular-head">
            <h3>인기 상품</h3>
            <Link to={categoryLink(root, 'popular')}>더보기</Link>
          </div>
          <ProductGrid products={popular} onToggleWish={toggleWish} row />
        </div>
      )}
      <Toast message={message} onDone={clearMessage} />
    </section>
  )
}
