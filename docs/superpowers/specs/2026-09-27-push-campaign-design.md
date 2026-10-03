# Commerce push campaigns (푸시 캠페인) — design + contract (2026-09-27)

Goal: admins send marketing pushes ("특가 아이템 보러가기") to the commerce Android app; tapping opens the product / promotion / coupon screen. Scope: admin campaigns only (no automatic pushes yet).

Decisions: commerce-service owns device tokens + sends with Firebase Admin SDK itself (same Firebase project `moduchat-346913`, new Android app `com.example.moducommerce`). Messenger push-service is untouched.

## Law rules (정보통신망법) — enforced by the server, not the admin
- Send only to users with `marketing` consent (default off).
- Title gets prefix `(광고) `; body gets suffix `\n수신거부: 마이페이지 > 알림 설정`.
- If the actual send moment is 21:00–08:00 KST, recipients must also have `night` consent.
- Consent changes return the timestamp so the app shows "모두의 커머스 · 2026.09.27 … 동의했습니다".

## Data (JPA, ddl-auto update)
- `push_device`: id, user_id, token (unique, ≤ 4096… use 512), platform ('ANDROID'), created_at, last_seen_at. Re-registering a token owned by another user moves it.
- `push_consent`: user_id PK, marketing bool, marketing_updated_at, night bool, night_updated_at (UTC). night is forced false when marketing is false.
- `push_campaign`: id, title, body, image_url, target_type (PRODUCT|PROMOTION|COUPONS|HOME), target_id, target_label (snapshot, e.g. product name), scheduled_at (UTC), status (SCHEDULED|SENDING|SENT|CANCELED|FAILED), night_applied bool, target_users, target_devices, success_count, failure_count, removed_tokens, opened_count, created_by (staff user id / sub), created_at, sent_at, canceled_at, failure_message.
- `push_campaign_open`: campaign_id + user_id unique (opened counted once per user).

Path from target: PRODUCT → `/products/{id}`, PROMOTION → `/promotions/{id}`, COUPONS → `/coupons`, HOME → `/`.

## Sending
- `PushSender` port (application module): `send(tokens: List<String>, message: PushMessage): PushSendResult{success, failure, invalidTokens}`. `FirebasePushSender` (api module) uses FirebaseMessaging.sendEachForMulticast in chunks of 500; UNREGISTERED / INVALID_ARGUMENT / SENDER_ID_MISMATCH tokens are reported invalid and deleted. When `modu.push.firebase-credentials` is blank or the file is missing → `LoggingPushSender` (logs, counts every token as success). Tests use a recording stub.
- Message: notification {title, body, image?}, android channel id `promotions`, priority high; data {type:"campaign", campaignId, path}. Test sends: title `[테스트] (광고) …`, data has no campaignId.
- Scheduler every 60s (`@Scheduled`, disabled in tests via property `modu.push.scheduler-enabled`) claims due campaigns with `UPDATE … SET status='SENDING' WHERE id=? AND status='SCHEDULED'` (only 1 row updated wins). "지금 보내기" = scheduledAt now, and the create use case triggers the send asynchronously after commit (same claim, so no double send).
- Night check uses the injected Clock at send time.
- Any exception during send → FAILED with failure_message; partial counts kept.

## App API (/api/v1, commerce token aud=modu-commerce; errors `{message}`)
- `PUT /api/v1/me/push/devices` body `{ token: string, platform?: 'ANDROID' }` → 204
- `DELETE /api/v1/me/push/devices?token=…` → 204 (only if it's mine; otherwise still 204)
- `GET /api/v1/me/push/consent` → `PushConsent { marketing: boolean, marketingUpdatedAt: string|null, night: boolean, nightUpdatedAt: string|null }` (UTC LocalDateTime strings, no zone, like other timestamps)
- `PUT /api/v1/me/push/consent` body `{ marketing: boolean, night: boolean }` → PushConsent (updatedAt changes only for the flag that changed)
- `POST /api/v1/push/campaigns/{id}/opened` → 204 (idempotent per user; unknown id → 204 too)

## Admin API (/api-admin/v1/push-campaigns, ROLE_ADMIN aud modu-admin, via gateway `/commerce-service/api-admin/v1/...`)
- `GET ?status=&q=&page=0&size=15` → Page of `AdminPushCampaign` (sorted: scheduledAt desc, id desc)
  `AdminPushCampaign { id, title, body, imageUrl, targetType, targetId, targetLabel, path, status, scheduledAt, sentAt, canceledAt, nightApplied, targetUsers, targetDevices, successCount, failureCount, removedTokens, openedCount, createdBy, createdAt, failureMessage }` (times: UTC LocalDateTime strings)
- `GET /{id}` → AdminPushCampaign
- `POST` body `PushCampaignRequest { title (1..40), body (1..120), imageUrl?: string|null (http(s) URL), targetType, targetId?: number|null (required for PRODUCT/PROMOTION; product must exist and be SELLING, promotion must exist and be visible), scheduledAt?: string|null }` → 201 AdminPushCampaign. scheduledAt is ISO-8601 WITH offset, e.g. "2026-09-27T21:30:00+09:00"; null = send now; must not be in the past (1 min grace) nor > 30 days ahead.
- `POST /{id}/cancel` → AdminPushCampaign (409 unless SCHEDULED)
- `GET /audience?at=<ISO offset, optional>` → `{ night: boolean, users: number, devices: number, consentedUsers: number, nightUsers: number }` — users/devices = who would receive at that moment (night rule applied when `night`).
- `POST /test` body `{ title, body, imageUrl?, targetType, targetId?, userIds: string[] (1..5) }` → `{ users: number, devices: number, success: number, failure: number, noDeviceUserIds: string[] }` (ignores consent, not stored as a campaign).
- `GET /preview-text?title=&body=` not needed — the admin builds the preview itself with the same fixed prefix/suffix: prefix `(광고) `, suffix `\n수신거부: 마이페이지 > 알림 설정`.

## Android (modu_commerce app)
- google-services plugin + firebase-messaging (BOM). `app/google-services.json` gitignored; the build must still work WITHOUT it (apply the google-services plugin only if the file exists; Firebase init skipped → push features no-op).
- POST_NOTIFICATIONS permission, requested only when the web asks (toggle on). Channel `promotions` "혜택·이벤트 알림".
- Token: after login and in onNewToken → PUT devices with the stored commerce access token; logout → DELETE then clear session.
- CommerceMessagingService.onMessageReceived (foreground) builds the same notification; tap PendingIntent → MainActivity with extras `path`, `campaignId`. Background notification taps deliver data extras to the launcher activity too.
- MainActivity (singleTop) reads extras in onCreate/onNewIntent → pending deep link. WebScreen: if loaded, `evaluateJavascript("window.ModuWeb && window.ModuWeb.navigate('<path>')")`, else load `WEB_URL + path`. Not logged in → hold until after login. Report opened via POST … /opened natively.
- Bridge `window.ModuApp` additions: `getNotificationPermission(): 'granted'|'denied'|'default'` ('default' = never asked), `requestNotificationPermission()` → result later via `window.ModuWeb.onNotificationPermission(result)`, `openNotificationSettings()`.
- Path allow-list both sides: `/`, `/coupons`, `/products/<digits>`, `/promotions/<digits>`.

## Web (web/)
- `/settings/notifications` 알림 설정 from 마이페이지 menu: toggles 혜택·이벤트 알림 (marketing), 야간 알림 21시~08시 (night; disabled while marketing off). Under each: "2026.09.27 수신 동의" / "2026.09.27 수신 거부" / "설정 안 함". Toast on change: "모두의 커머스 · 2026.09.27 혜택·이벤트 알림 수신에 동의했습니다" (or 거부). Turning marketing on in the app asks OS permission first (if denied: stays saved on server but shows the banner). Banner when permission denied: "휴대폰 설정에서 알림이 꺼져 있어요" + 설정 열기. Outside the app: "알림은 모두의 커머스 앱에서 받을 수 있어요".
- `window.ModuWeb.navigate(path)` (allow-listed) via react-router; `window.ModuWeb.onNotificationPermission(result)`.

## Admin (modu-admin)
- Sidebar 커머스 group: "푸시 캠페인" `/push-campaigns` (chat 푸시 `/push` untouched).
- List: title, target ("상품 · 가을 다이어리"), send time (KST), status chip (SCHEDULED blue, SENDING amber, SENT green, CANCELED gray, FAILED red), 보냄/성공/열어 봄 + open rate; status filter + title search; cancel on scheduled rows.
- Form: title (one line, counter 40), body (textarea, counter 120), image URL, target chips 상품/기획전·이벤트/쿠폰함/홈 with product search (existing admin products API) / promotion picker (existing admin promotions API); send now / 예약 (DateField + hour/minute selects, 10-min steps, KST); audience line from /audience (night warning amber); phone notification preview with the (광고)/수신거부 text; test send with member search (existing member admin API), up to 5; in-page confirm for send now.
- Detail: content, target, times, result numbers; "복제해서 새로 만들기".

## Deploy
- `backend/secrets/` holds only a committed `.gitkeep` (everything else gitignored); compose mounts the DIRECTORY `./secrets:/secrets:ro` (mounting a missing file would create a directory) and sets `MODU_PUSH_FIREBASE_CREDENTIALS=/secrets/firebase-service-key.json`. application.yml: `modu.push.firebase-credentials: ${MODU_PUSH_FIREBASE_CREDENTIALS:}`. No platform config change needed.
