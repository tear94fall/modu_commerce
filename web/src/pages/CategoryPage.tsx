import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getCategories, type Category } from '../api/catalog'
import { EmptyBox, ErrorBox, Loading } from '../components/Boxes'
import { Screen, TopBar } from '../components/Layout'

const link = (id: number, title: string) => `/products?categoryId=${id}&title=${encodeURIComponent(title)}`

/** 카테고리 트리. 대분류 아래에 "전체" 와 소분류 줄이 있다. */
export default function CategoryPage() {
  const [roots, setRoots] = useState<Category[] | null>(null)
  const [error, setError] = useState(false)

  const load = () => {
    setError(false)
    getCategories()
      .then(setRoots)
      .catch(() => setError(true))
  }
  useEffect(load, [])

  return (
    <Screen tabs>
      <TopBar title="카테고리" />
      {error ? (
        <ErrorBox message="카테고리를 불러오지 못했습니다." onRetry={load} />
      ) : roots === null ? (
        <Loading />
      ) : roots.length === 0 ? (
        <EmptyBox message="카테고리가 없습니다." />
      ) : (
        roots.map((root) => (
          <section key={root.id}>
            <div className="cat-root">{root.name}</div>
            <Link to={link(root.id, root.name)} className="cat-child">
              전체
            </Link>
            {root.children.map((child) => (
              <Link key={child.id} to={link(child.id, child.name)} className="cat-child">
                {child.name}
              </Link>
            ))}
          </section>
        ))
      )}
    </Screen>
  )
}
