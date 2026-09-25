import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { categoryLink, categoryPath, getCategories, getProducts, SORT_LABELS, type Category, type ProductSort } from '../api/catalog'
import { EmptyBox, ErrorBox, Loading } from '../components/Boxes'
import CategoryTile from '../components/CategoryTile'
import { Screen, TopBar } from '../components/Layout'
import { ProductGrid } from '../components/ProductCard'
import SortChips from '../components/SortChips'
import Toast from '../components/Toast'
import { useProductPager } from '../hooks/useProductPager'

const isSort = (s: string | null): s is ProductSort => s !== null && s in SORT_LABELS

/**
 * `/products?categoryId=&title=&sort=`. 정렬을 바꾸면 첫 페이지부터 다시 받는다(정렬도 주소에 둔다, replace).
 * categoryId 가 있으면 위에 대분류 알약 줄과 소분류 칩 줄을 붙여 그 자리에서 카테고리를 옮겨 다닌다.
 */
export default function ProductListPage() {
  const [params, setParams] = useSearchParams()
  const navigate = useNavigate()
  const categoryId = params.get('categoryId') ? Number(params.get('categoryId')) : undefined
  const sortParam = params.get('sort')
  const sort: ProductSort = isSort(sortParam) ? sortParam : 'latest'
  const [roots, setRoots] = useState<Category[]>([])
  const inCategory = categoryId !== undefined

  useEffect(() => {
    if (!inCategory) return
    // 카테고리 줄은 덤이다. 못 받으면 주소의 title 로 예전 모양 그대로 보인다.
    getCategories()
      .then(setRoots)
      .catch(() => setRoots([]))
  }, [inCategory])

  const path = categoryId === undefined ? [] : categoryPath(roots, categoryId)
  const title = path.length > 0 ? path.map((c) => c.name).join(' › ') : (params.get('title') ?? '상품')

  const load = useCallback((page: number) => getProducts({ categoryId, sort, page }), [categoryId, sort])
  const pager = useProductPager(load)

  const changeSort = (next: ProductSort) => {
    const p = new URLSearchParams(params)
    p.set('sort', next)
    setParams(p, { replace: true })
  }

  /** 카테고리 줄에서 옮길 때: 뒤로 가기가 목록마다 쌓이지 않게 replace, 정렬은 그대로. */
  const goCategory = (c: Category) => {
    if (c.id === categoryId) return
    navigate(categoryLink(c, sortParam && isSort(sortParam) ? sortParam : null), { replace: true })
    if (window.scrollY > 0) window.scrollTo(0, 0)
  }

  const root = path[0]
  const sub = path[1]

  return (
    <Screen>
      <TopBar title={title} back />
      {root && <CategoryRows roots={roots} root={root} current={sub ?? root} onSelect={goCategory} />}
      <SortChips value={sort} onChange={changeSort} />
      {pager.error ? (
        <ErrorBox message="상품을 불러오지 못했습니다." onRetry={pager.retry} />
      ) : pager.loading && pager.items.length === 0 ? (
        <Loading />
      ) : pager.items.length === 0 ? (
        root ? (
          <div className="empty-cat">
            <div className="art" aria-hidden="true">
              🛍️
            </div>
            <p className="title">아직 상품이 없어요</p>
            <p className="sub">곧 새로운 상품으로 채워질 거예요.</p>
            {sub ? (
              <button type="button" className="btn outline small" onClick={() => goCategory(root)}>
                {root.name} 전체 보기
              </button>
            ) : (
              <Link to={`/categories?root=${root.id}`} className="btn outline small">
                다른 카테고리 둘러보기
              </Link>
            )}
          </div>
        ) : (
          <EmptyBox message="보여 줄 상품이 없습니다." />
        )
      ) : (
        <>
          <ProductGrid products={pager.items} onToggleWish={pager.toggleWish} />
          {pager.loading && <Loading />}
          {!pager.loading && pager.hasNext && (
            <button type="button" className="btn outline small load-more" onClick={pager.loadMore}>
              더 보기
            </button>
          )}
        </>
      )}
      <Toast message={pager.message} onDone={pager.clearMessage} />
    </Screen>
  )
}

interface CategoryRowsProps {
  roots: Category[]
  root: Category
  /** 지금 보는 카테고리(대분류 자체면 "전체"). */
  current: Category
  onSelect: (c: Category) => void
}

/** 상단에 붙는 대분류 알약 줄 + 소분류 칩 줄. 고른 것은 가운데로 스크롤해 둔다. */
function CategoryRows({ roots, root, current, onSelect }: CategoryRowsProps) {
  const rootRow = useRef<HTMLDivElement>(null)
  const subRow = useRef<HTMLDivElement>(null)

  useLayoutEffect(() => {
    centerActive(rootRow.current)
    centerActive(subRow.current)
  }, [root.id, current.id])

  return (
    <div className="cat-nav">
      <div className="cat-pills" ref={rootRow} role="tablist" aria-label="대분류">
        {roots.map((r) => (
          <button key={r.id} type="button" role="tab" aria-selected={r.id === root.id} className={`cat-pill ${r.id === root.id ? 'active' : ''}`.trim()} onClick={() => onSelect(r)}>
            <CategoryTile category={r} size="xs" />
            {r.name}
          </button>
        ))}
      </div>
      <div className="chips cat-subs" ref={subRow} role="tablist" aria-label={`${root.name} 소분류`}>
        <button type="button" role="tab" aria-selected={current.id === root.id} className={`chip ${current.id === root.id ? 'active' : ''}`.trim()} onClick={() => onSelect(root)}>
          전체
        </button>
        {(root.children ?? []).map((c) => (
          <button key={c.id} type="button" role="tab" aria-selected={current.id === c.id} className={`chip ${current.id === c.id ? 'active' : ''}`.trim()} onClick={() => onSelect(c)}>
            {c.name}
          </button>
        ))}
      </div>
    </div>
  )
}

/** 줄 안에서 고른 칸(aria-selected)을 가운데로. 페이지 세로 스크롤은 건드리지 않는다. */
function centerActive(row: HTMLElement | null) {
  const el = row?.querySelector<HTMLElement>('[aria-selected="true"]')
  if (!row || !el) return
  row.scrollLeft = Math.max(0, el.offsetLeft - (row.clientWidth - el.offsetWidth) / 2)
}
