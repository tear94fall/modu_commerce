import { BrowserRouter, Route, Routes } from 'react-router-dom'
import RequireAuth from './auth/RequireAuth'
import { ModuWebBridge } from './bridge/web'
import CustomerLayout from './customer/CustomerProvider'
import RequireCustomer from './customer/RequireCustomer'
import AddressesPage from './pages/AddressesPage'
import CartPage from './pages/CartPage'
import CategoryPage from './pages/CategoryPage'
import CheckoutPage from './pages/CheckoutPage'
import CouponsPage from './pages/CouponsPage'
import CouponZonePage from './pages/CouponZonePage'
import HomePage from './pages/HomePage'
import { PrivacyPage, TermsPage } from './pages/LegalPage'
import LoginPage from './pages/LoginPage'
import MembershipPage from './pages/MembershipPage'
import MyReviewsPage from './pages/MyReviewsPage'
import MyPage from './pages/MyPage'
import NotificationsPage from './pages/NotificationsPage'
import NotificationSettingsPage from './pages/NotificationSettingsPage'
import OrderDetailPage from './pages/OrderDetailPage'
import OrdersPage from './pages/OrdersPage'
import PointsPage from './pages/PointsPage'
import ProductDetailPage from './pages/ProductDetailPage'
import ProductListPage from './pages/ProductListPage'
import PromotionPage from './pages/PromotionPage'
import ProductReviewsPage from './pages/ProductReviewsPage'
import ReviewFormPage from './pages/ReviewFormPage'
import SearchPage from './pages/SearchPage'
import WelcomePage from './pages/WelcomePage'
import WishlistPage from './pages/WishlistPage'

export default function App() {
  return (
    <BrowserRouter>
      <ModuWebBridge />
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        {/* 약관은 로그인 전에도 볼 수 있다. */}
        <Route path="/terms" element={<TermsPage />} />
        <Route path="/privacy" element={<PrivacyPage />} />
        <Route element={<RequireAuth />}>
          <Route element={<CustomerLayout />}>
            <Route path="/welcome" element={<WelcomePage />} />
            {/* 둘러보기: 가입(약관 동의) 전에도 열린다. */}
            <Route path="/" element={<HomePage />} />
            <Route path="/categories" element={<CategoryPage />} />
            <Route path="/products" element={<ProductListPage />} />
            <Route path="/products/:id" element={<ProductDetailPage />} />
            <Route path="/products/:id/reviews" element={<ProductReviewsPage />} />
            <Route path="/promotions/:id" element={<PromotionPage />} />
            <Route path="/search" element={<SearchPage />} />
            <Route path="/coupons" element={<CouponZonePage />} />
            <Route path="/my" element={<MyPage />} />
            <Route path="/points" element={<PointsPage />} />
            {/* 가입해야 쓰는 화면. customer/paths.ts 의 PROTECTED 와 같게 둔다. */}
            <Route element={<RequireCustomer />}>
              <Route path="/reviews/new" element={<ReviewFormPage />} />
              <Route path="/reviews/:id/edit" element={<ReviewFormPage />} />
              <Route path="/wishlist" element={<WishlistPage />} />
              <Route path="/my/reviews" element={<MyReviewsPage />} />
              <Route path="/my/coupons" element={<CouponsPage />} />
              <Route path="/membership" element={<MembershipPage />} />
              <Route path="/cart" element={<CartPage />} />
              <Route path="/checkout" element={<CheckoutPage />} />
              <Route path="/orders" element={<OrdersPage />} />
              <Route path="/orders/:id" element={<OrderDetailPage />} />
              <Route path="/addresses" element={<AddressesPage />} />
              <Route path="/notifications" element={<NotificationsPage />} />
              <Route path="/settings/notifications" element={<NotificationSettingsPage />} />
            </Route>
          </Route>
        </Route>
      </Routes>
    </BrowserRouter>
  )
}
