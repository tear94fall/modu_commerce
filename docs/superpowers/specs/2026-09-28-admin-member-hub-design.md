# Admin member hub (회원 통합) — design + contract (2026-09-28)

Goal: one member screen in modu-admin. Member detail = common info + conditional tabs [채팅] [커머스]; member list filters by service; the separate 커머스 > 고객 menu goes away (its filters move into the member list). "Chat member" becomes identifiable via per-service usage records written when auth-service issues tokens.

## A. modu_chat — member-service
- New table `member_service_usage`: id, user_id (varchar, matches member.user_id), service ENUM-string CHAT|COMMERCE, first_used_at, last_used_at (UTC). Unique (user_id, service). Index (service, user_id).
- `POST /api-internal/member/usage` body `{ userId, clientId }` → 204. Maps OAuth client id → service via property `modu.member.usage-clients` (map clientId → CHAT|COMMERCE; set it to the REAL registered client ids of the chat apps (Android/iOS) and the commerce app — find them in auth-service client registration config/config-repo). Unknown/console clients → ignored (204). Upsert; update last_used_at only if older than 1 hour (avoid a write per refresh). Unknown userId → 204 (ignore).
- `POST /api-internal/member/usage/bulk` body `{ service, userIds: string[] (≤1000), usedAt?: ISO string }` → `{ inserted }` — insert missing rows only (first=last=usedAt or now). Used once for backfilling COMMERCE from commerce customers.
- Startup backfill (idempotent): CHAT rows for every ACTIVE member with any chat room membership (member chatRoomMembers collection) or any member_friend relation; first_used_at = last_used_at = member.created.
- Admin member list (existing endpoint used by modu-admin members page, e.g. `GET /api-admin/member?keyword=...&page&size&sort`): new optional param `service=CHAT|COMMERCE|BOTH|NONE` (NONE = no usage rows). Every list item gains `services: ("CHAT"|"COMMERCE")[]` (sorted CHAT, COMMERCE).
- Admin member detail (existing endpoint used by the member detail page): gains `services: [{ service, firstUsedAt, lastUsedAt }]` (UTC LocalDateTime strings, no zone).
- Withdrawal: keep usage rows (history), nothing to change.

## B. modu_chat — auth-service
- After a successful token response for grants `urn:modu:params:oauth:grant-type:google_id_token`, `urn:modu:params:oauth:grant-type:sso_code` and `refresh_token`, call member-service `POST /api-internal/member/usage { userId: <token subject>, clientId: <registered client id> }` asynchronously, best-effort (timeout ~2s, log on failure, never affects the token response). Use the existing way auth-service calls member-service internally (Feign/RestClient + X-Internal-Token).

## C. modu_commerce — commerce-service
- `GET /api-admin/v1/customers/{userId}/summary` (ROLE_ADMIN aud modu-admin) → 200 always (zeros for unknown users):
  `CustomerSummary { customer: AdminCustomer | null /* existing shape from GET /customers/{userId}, without tierHistory */, orderCounts: { PAID, SHIPPING, DELIVERED, CANCELLED }, deliveredAmountTotal: number /* all-time Σ paymentAmount of DELIVERED */, lastOrderAt: string|null, recentOrders: [{ id, orderNo, status, paymentAmount, createdAt, itemSummary /* "모두 다이어리 2027 외 1건" */ }] /* newest 5 */, coupons: { available: number, used: number, expired: number }, wishlistCount: number, reviewCount: number, points: null /* not commerce's; admin reads point-service */ }`
- Keep existing `/api-admin/v1/customers` list/lookup/detail endpoints (the admin member list uses list/lookup for the commerce filter).

## D. modu_admin — modu-admin
- Sidebar: remove 커머스 > 고객. Routes `/customers` → redirect to `/members?service=COMMERCE`; `/customers/:userId` → resolve member by userId (member admin search) and redirect to `/members/:id?tab=commerce` (fallback: members list with keyword).
- Members list:
  - Filter chips `전체 / 채팅 / 커머스 / 둘 다` (URL param `service`). When `커머스` is selected, extra chips: tier (from GET /tiers) and 동의(전체/동의/동의 전). If a tier/agreed filter is active, the list is driven by commerce `GET /api-admin/v1/customers` (paged there) enriched with member info (batch member search by userIds, or show commerce's name/email which it already returns). Otherwise member-service list with `service=`.
  - Column "이용 서비스": `채팅` badge (neutral) + `커머스 · 골드` (tier color; 동의 전 → grey) using existing lookup batch.
- Member detail:
  - Header: name, badges (채팅 / 커머스 · 등급), common info (email, userId, 가입일, 상태, 직원 권한, 서비스별 처음·마지막 이용일), point balance (point-service admin API already used by the 포인트 pages; failure → "-").
  - Tabs `[채팅] [커머스]`, each only if the member uses it (services contains it, or for commerce: a customer exists). No tabs at all → a muted line "아직 이용한 서비스가 없어요". `?tab=chat|commerce` in URL; default = first visible. Lazy-load per tab; a failing service shows an error only inside its tab.
  - 채팅 tab: everything chat-specific currently on the member detail page (friends etc.) moves here unchanged.
  - 커머스 tab: tier card (badge, 기준/최근 6개월 금액, 적립률), 약관·개인정보 동의일 or "약관 동의 전", order counts by status + delivered total + last order, recent 5 orders (link to order admin page if one exists for an order id), coupons available/used/expired, wishlist/review counts, tier history (existing component).
- Remove now-unused CustomersPage/CustomerDetailPage (keep shared components).

## PRs (merge order)
1. modu_chat (member-service usage + auth-service hook)
2. modu_commerce (summary endpoint)
3. modu_admin (member hub)
Dev one-off after deploy: bulk COMMERCE usage from commerce_customers user ids.
