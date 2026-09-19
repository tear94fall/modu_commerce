import { BrowserRouter, Route, Routes } from 'react-router-dom'
import RequireAuth from './auth/RequireAuth'
import CategoryPage from './pages/CategoryPage'
import HomePage from './pages/HomePage'
import LoginPage from './pages/LoginPage'
import MyPage from './pages/MyPage'
import ProductDetailPage from './pages/ProductDetailPage'
import ProductListPage from './pages/ProductListPage'
import SearchPage from './pages/SearchPage'
import WishlistPage from './pages/WishlistPage'

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route element={<RequireAuth />}>
          <Route path="/" element={<HomePage />} />
          <Route path="/categories" element={<CategoryPage />} />
          <Route path="/products" element={<ProductListPage />} />
          <Route path="/products/:id" element={<ProductDetailPage />} />
          <Route path="/search" element={<SearchPage />} />
          <Route path="/wishlist" element={<WishlistPage />} />
          <Route path="/my" element={<MyPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}
