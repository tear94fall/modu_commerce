import { useCallback, useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router-dom'
import { getProducts, type Page, type ProductSort, type ProductSummary } from '../api/catalog'
import { EmptyBox, ErrorBox, Loading } from '../components/Boxes'
import { Screen, TopBar } from '../components/Layout'
import { ProductGrid } from '../components/ProductCard'
import SortChips from '../components/SortChips'
import Toast from '../components/Toast'
import { useProductPager } from '../hooks/useProductPager'

const EMPTY: Page<ProductSummary> = { content: [], totalElements: 0, totalPages: 0, number: 0 }

/** 검색. 검색어를 제출해야 서버를 부른다(입력마다 부르지 않는다). 제출한 검색어는 `?q=` 에 남는다. */
export default function SearchPage() {
  const [params, setParams] = useSearchParams()
  const submitted = params.get('q') ?? ''
  const [query, setQuery] = useState(submitted)
  const [sort, setSort] = useState<ProductSort>('latest')

  const load = useCallback((page: number) => (submitted ? getProducts({ q: submitted, sort, page }) : Promise.resolve(EMPTY)), [submitted, sort])
  const pager = useProductPager(load)

  const submit = (e: FormEvent) => {
    e.preventDefault()
    const q = query.trim()
    if (q) setParams({ q }, { replace: submitted !== '' })
  }

  return (
    <Screen>
      <TopBar
        title="검색"
        back
        center={
          <form className="search-bar" onSubmit={submit} role="search">
            <input type="search" value={query} onChange={(e) => setQuery(e.target.value)} placeholder="상품 이름·설명 검색" aria-label="검색어" autoFocus enterKeyHint="search" />
          </form>
        }
      />
      {submitted && <SortChips value={sort} onChange={setSort} />}
      {!submitted ? (
        <EmptyBox message="찾는 상품을 입력해 주세요." />
      ) : pager.error ? (
        <ErrorBox message="상품을 불러오지 못했습니다." onRetry={pager.retry} />
      ) : pager.loading && pager.items.length === 0 ? (
        <Loading />
      ) : pager.items.length === 0 ? (
        <EmptyBox message={`'${submitted}' 검색 결과가 없습니다`} />
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
