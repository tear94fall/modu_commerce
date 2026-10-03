# 커머스 앱 웹뷰 전환 1단계 (web 스캐폴드 + 카탈로그 + Android 껍데기)

**Goal:** 커머스 앱의 화면을 React 웹(`web/`)으로 옮기고 Android 앱은 로그인 + WebView 껍데기만 남긴다. 1단계는 홈·카테고리·검색·상품 상세·찜·마이.

**Architecture:** Vite dev 서버(LAN, HMR)를 WebView 가 열어 저장 즉시 반영. 웹은 같은 출처 `/api/v1/**` 를 부르고 Vite/nginx 가 commerce-service(:8200)로 프록시(CORS 없음). 토큰은 `window.ModuApp` 브리지(getAccessToken / refreshAccessToken / onSessionExpired / logout / getProfile)로 받고, 브리지가 없으면 localStorage(개발용 토큰 붙여넣기).

**Tasks**
1. web 스캐폴드(package/vite/tsconfig/oxlint/test-setup) + styles 토큰
2. bridge/token/client (+tests: bridge 우선, 401 → refresh 1회 → 재시도, 실패 시 onSessionExpired)
3. api/catalog, api/cart, api/me + util/format, util/sku (+tests: 포팅한 selectSku/isValueAvailable)
4. components: Layout(top bar, tabs), ProductCard/Grid, BottomPanel, boxes, SortChips; hooks/useProductPager
5. pages: Home, Category, ProductList, Search, ProductDetail(옵션→SKU, 수량, 찜, 장바구니 담기), Wishlist, My, Login(dev) (+tests: Home, ProductDetail)
6. Android: SessionRefresher 추출, SessionEvents(expired 플래그), ModuAppBridge, WebScreen, Routes/NavHost 축소, Compose 화면·카탈로그/주문 코드·coil 제거, WEB_URL buildConfig, 테스트 정리
7. 실기기 검증(Flip3, Vite dev 서버) + README + PR
