import { useCallback, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { getProducts, type ProductSort } from '../api/catalog'
import { EmptyBox, ErrorBox, Loading } from '../components/Boxes'
import { Screen, TopBar } from '../components/Layout'
import { ProductGrid } from '../components/ProductCard'
import SortChips from '../components/SortChips'
import Toast from '../components/Toast'
import { useProductPager } from '../hooks/useProductPager'

/** `/products?categoryId=&title=&sort=`. 정렬을 바꾸면 첫 페이지부터 다시 받는다. */
export default function ProductListPage() {
  const [params] = useSearchParams()
  const categoryId = params.get('categoryId') ? Number(params.get('categoryId')) : undefined
  const title = params.get('title') ?? '상품'
  const [sort, setSort] = useState<ProductSort>((params.get('sort') as ProductSort) ?? 'latest')

  const load = useCallback((page: number) => getProducts({ categoryId, sort, page }), [categoryId, sort])
  const pager = useProductPager(load)

  return (
    <Screen>
      <TopBar title={title} back />
      <SortChips value={sort} onChange={setSort} />
      {pager.error ? (
        <ErrorBox message="상품을 불러오지 못했습니다." onRetry={pager.retry} />
      ) : pager.loading && pager.items.length === 0 ? (
        <Loading />
      ) : pager.items.length === 0 ? (
        <EmptyBox message="보여 줄 상품이 없습니다." />
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
