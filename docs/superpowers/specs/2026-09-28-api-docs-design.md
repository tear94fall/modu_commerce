# Unified API docs (API 문서) — design + contract (2026-09-28)

Goal: one screen in the system console (modu-system, :8084) where staff pick a service and see its API list (Swagger UI), instead of opening docs per service.

## 1. Services (modu_chat backend: auth, member, chat, chat-store, ws, push, storage, profile, point, schedule; modu_commerce commerce-service)
- Add `org.springdoc:springdoc-openapi-starter-webmvc-api` (2.8.x compatible with the service's Spring Boot; NO swagger-ui dependency). Exposes `GET /v3/api-docs` (JSON).
- Properties (application.yml of each service, not config-repo):
  ```yaml
  springdoc:
    api-docs:
      enabled: ${SPRINGDOC_API_DOCS_ENABLED:true}
      path: /v3/api-docs
    default-produces-media-type: application/json
    paths-to-exclude: /actuator/**, /error
  ```
- One `OpenAPI` bean per service: `info.title = "<service-name>"` (spring.application.name), `info.version = "v1"`, `info.description` = one Korean sentence on what the service does. Tags: leave springdoc defaults (controller names).
- If the service has Spring Security or a servlet filter guarding paths (auth-service security chain, commerce security chains, any InternalApiFilter), `/v3/api-docs` and `/v3/api-docs/**` must be reachable without auth ON THE SERVICE PORT (it is only exposed inside the docker network; the gateway protects external access).
- Test per service: MockMvc/SpringBootTest `GET /v3/api-docs` → 200, JSON with `openapi` and at least one path of the service's own tier (e.g. `/api-admin/...`). Tests that disable config client etc. keep working.

## 2. Gateway (modu_platform gateway-service, WebFlux)
- `GET /gateway-service/api-admin/api-docs` → `{ services: [{ name: "member-service", title: "member-service" }] }` — distinct `lb://<name>` targets of the configured routes (GatewayProperties), sorted by name, excluding gateway-service itself and discovery/config.
- `GET /gateway-service/api-admin/api-docs/{name}` → the service's OpenAPI JSON fetched via load-balanced WebClient `lb://{name}/v3/api-docs` (timeout 5s), with `servers` replaced by `[{ "url": "/{name}", "description": "게이트웨이 경유" }]`. Unknown name (not in routes) → 404 `{message}`; fetch failure/timeout → 502 `{message:"<name> 문서를 불러오지 못했습니다"}`.
- Auth: same mechanism as the existing gateway config API controller (JwtAccess.verify) but requiring role `ROLE_SYSTEM` (aud `modu-admin`); 401 otherwise.
- Tests like the existing config API tests (mock WebClient / stub service).

## 3. System console (modu_admin modu-system)
- Sidebar menu "API 문서" (`/api-docs`), visible to SYSTEM staff (same guard as other modu-system pages).
- Top bar: service `<select>` (from the list API; remember last choice in localStorage in try/catch) + section chips `전체 · 앱 · 어드민 · 내부` (filter the spec's `paths` client-side by prefix: 앱 = `/api-public/`, `/api/v1/`, `/api/`; 어드민 = `/api-admin/`, `/api-super/`, `/api-staff/`; 내부 = `/api-internal/`; 전체 = all). Show counts per chip. URL params `?service=&section=`.
- Swagger UI via npm `swagger-ui-react` (or `swagger-ui-dist` + a small React wrapper) rendering the filtered spec object; docExpansion "list", filter box on, deepLinking off.
  - `requestInterceptor`: for paths under 어드민 prefixes, add `Authorization: Bearer <console token>` automatically (the console's stored token) unless the user set one via Authorize.
  - 내부 section: `supportedSubmitMethods: []` (no Try it out) + a note "내부 API 는 서비스끼리만 호출돼 여기서 실행할 수 없어요".
  - Add a bearer security scheme to the spec client-side so Authorize shows a token box (for app APIs).
- Loading / 502 error per service shown inline with retry; the rest of the page keeps working.
- modu-system nginx: proxy `^/(auth-service|member-service|chat-service|chat-store-service|ws-service|push-service|storage-service|profile-service|point-service|schedule-service|commerce-service)/` to the gateway exactly like modu-admin's nginx does (so Try it out works from :8084), besides the existing gateway-service proxy.
- Swagger UI CSS: import it scoped; make sure it doesn't restyle the console (wrap in a container class; override font to the console font).
- Tests: service select + section filter counts, interceptor adds token for admin paths only, internal section disables submit, error state.

## Branches / PRs (merge order)
1. modu_chat `feature/api_docs` (10 services)  2. modu_commerce `feature/api_docs`  3. modu_platform `feature/api_docs` (gateway)  4. modu_admin `feature/api_docs` (modu-system).
