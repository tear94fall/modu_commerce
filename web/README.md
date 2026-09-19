# 모두의 커머스 웹 (web/)

커머스 앱의 화면 전부입니다. React 19 + Vite + TypeScript(백오피스 modu_admin 과 같은 스택). Android 앱(`android/modu_commerce`)은
로그인과 WebView 껍데기만 가지고 이 웹을 엽니다. 브라우저에서도 그대로 열립니다.

## 개발

```bash
cd web
npm install
npm run dev          # http://<Mac IP>:5174 (LAN 에 열림, HMR)
npm test             # vitest
npm run lint         # oxlint
npm run build        # tsc + vite build → dist/
```

- API 는 같은 출처의 `/api/v1/**`(commerce-service :8200)과 `/auth-service/**`(게이트웨이 :8000)를 부르고 Vite 가 프록시합니다
  (`vite.config.ts`, `COMMERCE_URL`/`GATEWAY_URL` 환경변수로 대상을 바꿈). 그래서 서버에 CORS 설정이 없어도 됩니다.
- **앱에서 열기**: Android 디버그 빌드의 `WEB_URL`(`app/build.gradle.kts`)이 `http://192.168.0.3:5174/` 입니다. Mac 에서 `npm run dev` 를
  띄워 두면 폰의 앱이 그 화면을 열고, 파일을 저장하면 폰 화면이 바로 바뀝니다. `chrome://inspect` 로 WebView 를 디버깅할 수 있습니다.
- **브라우저에서 열기**: `/login` 에서 모두 계정 액세스 토큰(aud=modu-commerce)을 붙여넣습니다(개발용 임시 로그인).
  모두 계정 웹 로그인(auth-service OAuth2)은 다음 단계입니다.

## 앱 브리지 (`src/bridge/app.ts` ↔ android `ModuAppBridge`)

WebView 안에서는 `window.ModuApp` 이 있습니다. `getAccessToken()` 으로 Bearer 토큰을 받고, 401 이면 `refreshAccessToken()` 으로 한 번
갱신해 재시도하며, 그래도 401 이면 `onSessionExpired()` 로 앱이 로그인 화면으로 갑니다. 마이 탭 로그아웃은 `logout()`, 프로필은 `getProfile()`.
브리지가 없으면(브라우저) localStorage 토큰을 씁니다.

## 구조

- `src/api/` 서버 호출(client, catalog, cart, me) · `src/util/` 가격 표기·옵션→SKU 계산 · `src/hooks/useProductPager` 페이징+찜 토글
- `src/components/` 상단바·탭·상품 카드·바닥 패널·토스트 · `src/pages/` 홈, 카테고리, 상품 목록, 검색, 상품 상세, 찜, 마이, (개발용) 로그인
