# Commerce customers, terms, membership tiers — design + contract (2026-09-27)

Goals: (1) commerce owns a "customer" record (joined via terms agreement) so admin can tell commerce customers from chat-only members; (2) membership tiers by last-6-months spend with purchase point earn + monthly tier coupons; tier shown in the app.

Money: KRW Long. Times: server stores/returns UTC LocalDateTime strings without zone like other commerce APIs; periods are KST calendar months. Clock injected everywhere.

## 1. Customers (commerce-service)
Table `commerce_customers`: user_id (PK, varchar 64), status ACTIVE|WITHDRAWN, joined_at, terms_agreed_at (nullable), privacy_agreed_at (nullable), terms_version (nullable, e.g. "2026-10"), tier_code (FK-ish to commerce_tiers.code), tier_since, tier_basis_amount (Long), withdrawn_at, migrated (bool: created by backfill).
- Current terms version = property `modu.customer.terms-version` (default "2026-10").
- "Agreed" = terms_agreed_at != null && privacy_agreed_at != null (version re-consent is out of scope).
- App API (commerce token):
  - `GET /api/v1/me/customer` → 200 `CustomerMe` | 404 `{message, code:"CUSTOMER_REQUIRED"}` when no row, WITHDRAWN, or not agreed.
    `CustomerMe { userId, joinedAt, termsAgreedAt, privacyAgreedAt, termsVersion, tier: TierSummary, basisAmount /*current tier basis*/, rolling: { amount: number /*delivered net spend in the rolling window: from the 1st of the month 5 months ago (KST) to now*/, expectedTier: TierSummary, nextTier: TierSummary|null, amountToNext: number|null }, periodLabel: string /*"2026.04 ~ 2026.09" of the current tier basis*/ }`
    `TierSummary { code, name, color /*#RRGGBB*/, earnRate /*percent, e.g. 3 means 3%*/, minAmount }`
  - `POST /api/v1/me/customer` body `{ agreeTerms: true, agreePrivacy: true, marketing?: boolean }` → 201 CustomerMe. 400 unless both true. Existing (migrated or withdrawn) row: sets agreements, ACTIVE, keeps data. marketing=true → same effect as PUT /me/push/consent {marketing:true, night:false} (don't touch if false/absent).
  - Guard: endpoints that need a customer return 403 `{message:"모두의 커머스 가입이 필요합니다", code:"CUSTOMER_REQUIRED"}` for non-customers: cart (all), orders (create/list/detail/cancel), wishlist (add/remove/list), reviews (write/edit/delete, /me/reviews), coupon download/redeem/applicable/me coupons, promotion attendance + coupon event claim, addresses, push devices PUT (device DELETE stays open), push consent PUT, notifications. Browsing (products, categories, promotions GET, banners, product reviews list) stays open. Implement as one interceptor/annotation, not per-controller copies.
- Backfill on startup (idempotent, runs once via a marker row or "insert … select distinct user_id … where not exists"): every user_id found in orders, wishlists, addresses, cart_items, reviews, push_consents, push_devices, user_coupons, attendance_checks → customer row with migrated=true, terms/privacy null, tier = lowest, joined_at = earliest created_at found (or now).
- Internal API for member withdrawal: `DELETE /api-internal/v1/customers/{userId}` (X-Internal-Token, same token property as elsewhere: modu.internal-api.token) → 204; marks WITHDRAWN (idempotent, 204 when missing), deletes push_devices and push_consents of that user. Add an internal-token filter for `/api-internal/**` in commerce if none exists (constant-time compare, like other services).
- Admin API (ROLE_ADMIN aud modu-admin):
  - `GET /api-admin/v1/customers?q=&tier=&agreed=&page=0&size=20` → Page of `AdminCustomer { userId, name, email /*from member lookup, may be null*/, status, tier: TierSummary, basisAmount, rollingAmount, joinedAt, termsAgreedAt, privacyAgreedAt, migrated }` (q matches userId exactly or name/email via member lookup is NOT required — q matches userId prefix only; name/email columns are filled by batch member lookup of the page). Sorted joinedAt desc.
  - `GET /api-admin/v1/customers/lookup?userIds=a,b,c` (≤100) → `[{ userId, status, tier: TierSummary, agreed: boolean }]` only for existing customers (used by the admin members page badges).
  - `GET /api-admin/v1/customers/{userId}` → AdminCustomer + `tierHistory: [{ fromCode, toCode, basisAmount, periodLabel, changedAt, reason: 'MONTHLY'|'MANUAL'|'JOIN' }]` (newest first, ≤24).

## 2. Tiers
Table `commerce_tiers`: code (PK, e.g. WELCOME/SILVER/GOLD/VIP), name, color, min_amount, earn_rate (int percent 0..20), sort_order, coupon_ids (element collection or join table `commerce_tier_coupons`). Seed if empty: WELCOME 웰컴 #64748B 0 1%, SILVER 실버 #94A3B8 100000 2%, GOLD 골드 #D97706 300000 3%, VIP VIP #7C3AED 700000 5%.
Table `commerce_tier_histories`: id, user_id, from_code, to_code, basis_amount, period_label, reason, changed_at, run_id.
Table `commerce_tier_runs`: id, period_label, started_at, finished_at, reason MONTHLY|MANUAL, customers, changed, counts_by_tier (json string), coupons_issued, coupons_skipped, status RUNNING|DONE|FAILED, message.
- Rule: tier = highest tier whose min_amount ≤ basis. basis = Σ order.paymentAmount (cash paid after coupon+points) of DELIVERED orders of the user with delivered_at in [first day of month-6, first day of this month) KST. Cancelled never counts.
- `orders.delivered_at` new nullable column, set when status becomes DELIVERED; backfill existing DELIVERED orders with updated_at.
- Monthly job: `@Scheduled(cron="0 10 0 1 * *", zone="Asia/Seoul")` (property to disable in tests) → run(MONTHLY). Manual: `POST /api-admin/v1/tiers/runs` → 202 run (async; returns the run row). Only one RUNNING at a time (409). A run recalculates all ACTIVE customers (agreed or migrated), writes histories for changes, then issues monthly coupons.
- Monthly tier coupons: for each tier's coupons, issue to each ACTIVE+agreed customer in that tier with source TIER and issue_key `tier:<YYYY-MM>` (period of the run month). MANUAL runs issue only if that month's key was not issued yet (unique key makes it naturally idempotent). Respect coupon active + totalQuantity; skip (count) otherwise; ignore downloadable/issue period.
- `user_coupons.issue_key` new column NOT NULL default 'once'; unique constraint becomes (coupon_id, user_id, issue_key). All existing issuing paths use 'once' (behaviour unchanged). "I already have this coupon" checks keep using issue_key='once'. Dev migration: add column with default, drop old unique `uk_user_coupons`, add new unique (do it in code at startup only if needed, or document SQL in the PR — ddl-auto won't drop the old constraint).
- Admin tier API: `GET /api-admin/v1/tiers` → `[AdminTier { code, name, color, minAmount, earnRate, sortOrder, coupons: [{id,name,discountLabel?}] , customerCount }]`; `PUT /api-admin/v1/tiers` body full list `[{code,name,color,minAmount,earnRate,couponIds}]` (same codes as existing — codes fixed, 4 tiers; lowest must have minAmount 0; strictly increasing minAmount; earnRate 0..20; couponIds must exist) → list. `GET /api-admin/v1/tiers/runs?page=` → Page of runs.
- App: `GET /api/v1/tiers` (public) → `[TierSummary & { monthlyCoupons: [{ name, discountLabel }] }]` ordered.

## 3. Purchase point earn
- When an order becomes DELIVERED (admin PATCH status), after commit: earn = floor(paymentAmount × tier.earnRate / 100) using the customer's CURRENT tier at that moment; if > 0 call point-service `POST /api-internal/point/earn-amount` { userId, amount, reason:"PURCHASE", refId:"purchase:order:<orderId>", memo:"구매 적립 · 주문 <orderNo> (<tierName> <rate>%)" }.
- Persist on the order: `earn_points` (Long, nullable = not decided), `earn_rate`, `earn_status` NONE|PENDING|DONE|FAILED, `earned_at`. Point-service failure → PENDING; a retry scheduler every 10 min (disable in tests) retries PENDING (idempotent refId). Non-customer or 0 points → NONE.
- Order responses (app list/detail) gain `earn: { status, points, rate } | null`, plus `expectedEarn: { points, rate } | null` for PAID/SHIPPING orders (current tier). Checkout: `GET /api/v1/me/customer` gives tier.earnRate, web computes the preview.
- PointGateway port gets `earnAmount(userId, amount, refId, memo): EarnResult`.

## 4. point-service (modu_chat backend/point-service)
- `POST /api-internal/point/earn-amount` body `{ userId (NotBlank), amount (1..1_000_000), reason (NotBlank, ≤30, e.g. "PURCHASE"), refId (NotBlank ≤128), memo? ≤200 }` → `EarnResultDto { applied, amount, balance, reason? }`. Idempotent per (userId, refId) exactly like rule earn (duplicate → applied=false, reason DUPLICATE, amount 0). No rule, no daily/total limits. Ledger entry type EARN with description memo; ledger shows it like other earns (admin point screens should label source from reason; keep existing columns — add `source`/`reason` column only if needed).
- Tests in point-service.

## 5. Member withdrawal (modu_chat member-service)
- Withdrawal flow additionally calls commerce `DELETE {modu.commerce.url}/api-internal/v1/customers/{userId}` with X-Internal-Token; best-effort (log and continue on failure/timeout 3s). Property `modu.commerce.url` default `http://commerce-service:8200`. Test with a mock server or stub bean.

## 6. Web (commerce web/)
- `/welcome` onboarding (see sections 1): 전체 동의 + 이용약관(필수) + 개인정보 수집·이용(필수) + 혜택·이벤트 알림(선택), each "보기 ›" to `/terms` / `/privacy` (static draft pages, clearly marked 개발용 초안), sticky "동의하고 시작하기" disabled until both required. After join → return to `?next=` path (allow-listed internal path) or `/`. In-app: if marketing checked, request OS notification permission like the settings page does.
- Gate: after login, app start calls GET /me/customer; 404 CUSTOMER_REQUIRED → redirect to /welcome?next=<current>. Also any API 403 with code CUSTOMER_REQUIRED → redirect to /welcome (central API client handling). Browsing pages must work before joining (so only redirect proactively on protected routes + on 403).
- My page top card: tier badge (tier color pill) next to name; line "최근 6개월 구매 184,000원 · VIP까지 516,000원" + progress bar to next tier (full + "최고 등급이에요" at top). Tap → `/membership`.
- `/membership`: tier cards (name, 기준 금액, 적립률, 매월 쿠폰), current highlighted, 다음 달 예상 등급 (rolling.expectedTier), rule text ("매월 1일, 지난 6개월 배송 완료 금액으로 정해져요. 포인트로 낸 금액은 빠져요."), recent tier history? (not in app API — skip).
- Order detail: "배송 완료 시 3% 적립 예정 · 1,200P" (expectedEarn) / "1,200P 적립 완료" (earn DONE) / "적립 처리 중" (PENDING). Checkout summary line "골드 3% 적립 예정 · N P" computed from payment amount.

## 7. Admin (modu-admin)
- Members list + detail: badge `커머스 · 골드` (tier color) for customers via `/customers/lookup` batch per page; detail shows commerce joined/agreed dates, 6-month amount, tier history, link to customer page. "동의 전" muted badge for migrated/not agreed.
- 커머스 > 고객 `/customers`: list (name/email/userId, tier badge, basis & rolling amount, joined, 약관 동의 여부), filters tier/agreed, q; detail `/customers/:userId` with tier history.
- 커머스 > 회원 등급 `/tiers`: editable table of 4 tiers (name, color picker, min amount, earn rate, monthly coupons via coupon search multi-select), customer counts; save with validation messages from server; runs history table; "지금 다시 산정" with in-page confirm → POST runs, poll until DONE.

## PRs (merge order)
1. modu_chat: point-service earn-amount + member-service withdrawal → commerce.
2. modu_commerce: everything commerce (backend + web).
3. modu_admin: screens.
