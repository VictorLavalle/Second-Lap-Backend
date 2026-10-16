# Second Lap — Backend Architecture

Technical guide to how the backend works: structure, request flow, data model,
order lifecycle, security, observability and the design decisions behind them.
Companion to the [README](../README.md) (how to run) and
[openapi.yaml](./openapi.yaml) (the HTTP contract).

> Scope: this is the backend for "Second Lap", a second-hand clothing store where
> every garment is a **unique piece** (quantity = 1). It exposes a versioned REST
> API under `/api/v1`. The storefront and admin panel are separate frontends; the
> admin panel is a server-side BFF (Next.js) that calls `/api/v1/admin/**`
> server-to-server.

---

## 1. Stack

| Concern | Choice |
|---|---|
| Language / runtime | Java 25 (LTS) |
| Framework | Spring Boot 3.5.x (Web MVC, Data JPA, Security, Actuator) |
| Database | PostgreSQL 17 |
| Migrations | Flyway (versioned, immutable) |
| ORM | JPA / Hibernate, **explicit mapping** (`ddl-auto=validate`) |
| Build | Maven (`./mvnw` wrapper) |
| Rate limiting | Bucket4j (in-memory, per instance) |
| Logging | SLF4J + Logback; JSON in prod via `logstash-logback-encoder` |
| Tests | JUnit 5 + Testcontainers (real PostgreSQL) |

Configuration is **fully externalized**: `application.yml` has no fallback
defaults, so every variable must be supplied by the environment (fail-fast at
startup). This maps cleanly onto a secrets manager in production. See the README
for the full variable table.

---

## 2. Architectural principles

- **Vertical slices by feature**, not horizontal layers. Each feature package
  (`product/`, `order/`, `category/`, …) holds its own controller, service,
  entity, repository, DTOs, mappers and converters.
- **Dual identifier**: every entity has an internal `BIGINT` PK (`id`, used for
  FKs/joins, never exposed) plus a public `UUID` (`public_id`, unique, the only id
  that appears in the API and URLs). DTOs expose the UUID as `id`.
- **DTOs are separate from entities**; entities are never serialized directly.
  Request and response DTOs are distinct.
- **The database is the source of truth** for state. Critical invariants (unique
  reservation, WhatsApp uniqueness) are enforced with DB constraints and atomic
  conditional updates, not just application code.
- **Enums are stored as their Spanish string value** (via `AttributeConverter`),
  reinforced by `CHECK` constraints, so the DB and the frontend contract agree.
- **Money** is `NUMERIC` in the DB / `BigDecimal` in Java, serialized as a JSON
  number (never a quoted string).

---

## 3. Package map

```
com.secondlap.backend
├── SecondLapBackendApplication        # Spring Boot entry point
├── common/                            # cross-cutting: errors, request-id filter
│   ├── GlobalExceptionHandler         # @RestControllerAdvice → ApiError
│   ├── ApiError                       # { code, message, context? }
│   ├── RequestIdFilter                # @Order(0) request-id → MDC + header
│   ├── ResourceNotFoundException      # → 404
│   ├── ProductNotAvailableException   # → 409
│   ├── InvalidOrderTransitionException# → 409
│   └── RateLimitExceededException     # → 429
├── config/
│   ├── SecurityConfig                 # filter chain, CORS, auth rules, BCrypt
│   ├── StaticResourceConfig           # serves /uploads/** from local disk
│   ├── CorsProperties / StoreProperties
├── ratelimit/
│   ├── RateLimitFilter                # @Order(1) per-IP throttling
│   ├── RateLimitService               # Bucket4j buckets per (tier, key)
│   └── RateLimitProperties            # 5 tiers (reads, orders, orders-wa, admin-login, admin-api)
├── admin/
│   ├── AdminController                # GET /admin/me (session check)
│   ├── CurrentAdminProvider           # single reader of the authenticated username
│   ├── AdminUser / AdminUserRepository / AdminUserDetailsService
│   └── AdminUserInitializer           # creates the admin on startup
├── category/        # public catalog: categories
├── neighborhood/    # public catalog: delivery neighborhoods + fees
├── product/         # catalog + admin product management, image upload
├── customer/        # get-or-create customer keyed by WhatsApp
├── order/           # checkout, order lifecycle, status timeline  (the core)
└── storage/         # StorageService abstraction + LocalFileStorageService
```

Each `product/`, `order/`, etc. also contains a `dto/` subpackage.

---

## 4. Layering & request flow

A request flows through these stages:

```
HTTP request
  → RequestIdFilter (@Order 0)      assigns requestId → MDC + X-Request-Id header
  → RateLimitFilter (@Order 1)      per-IP throttle by route tier → 429 if exceeded
  → Spring Security filter chain    HTTP Basic auth for /admin/**; headers; (HTTPS redirect in prod)
  → Controller                      maps HTTP ↔ DTO, no business logic
  → Service (@Transactional)        business rules, orchestration
  → Repository (Spring Data JPA)    persistence
  → Mapper                          entity → response DTO
HTTP response                       (errors shaped by GlobalExceptionHandler)
```

- **Controllers** are thin: parse path/query/body into DTOs, call a service,
  return a response DTO. No business logic.
- **Services** own the business rules and transaction boundaries
  (`@Transactional`). Read paths use `@Transactional(readOnly = true)`.
- **Mappers** (hand-written, e.g. `OrderMapper`, `ProductMapper`) translate
  entities to response DTOs so entities never leak.
- **Converters** (`AttributeConverter`) map enum ↔ Spanish DB string.

---

## 5. Data model

Internal PK (`BIGINT`) + public `UUID` on every exposed entity. Enum columns are
`VARCHAR` guarded by `CHECK`. Timestamps are `TIMESTAMPTZ` stored in UTC
(`hibernate.jdbc.time_zone=UTC`).

```
category (id, public_id, name, sort_order)
   └──< product (id, public_id, name, price, category_id→category,
                 size, condition, status, brand, color, measurements)
          └──< product_image (id, product_id→product, url, position)

neighborhood (id, public_id, name, fee)        # delivery fee lookup by name

customer (id, public_id, name, whatsapp UNIQUE, comments)
   └──< orders (id, public_id, customer_id→customer, address_id→address?,
                delivery_method, payment_method, subtotal, shipping_fee, total,
                status, created_at,
                confirmed_at?, completed_at?, delivered_at?, cancelled_at?)
          ├──< order_item (id, public_id, order_id→orders, product_id→product,
          │                price_snapshot)         # UNIQUE(order_id, product_id)
          └──< order_event (id, public_id, order_id→orders,
                            from_status?, to_status, actor?, occurred_at)

address (id, public_id, street, neighborhood, city, postal_code, …, shipping_fee)
admin_user (id, public_id, username UNIQUE, password_hash)
```

Key points:
- **`order_item.price_snapshot`** freezes the price at order time, so later price
  changes never alter a placed order's totals. Totals are computed server-side.
- **`address`** belongs to the order (orders are account-less, one-off shipments).
  A `CHECK` enforces that `recoger` has no address and `domicilio`/`paqueteria` do.
- **`order_event`** is an append-only status timeline (see §7).
- **Migrations** live in `src/main/resources/db/migration`, applied on startup:
  - `V1` schema · `V2` dev seed · `V3` real catalog seed
  - `V4` order tracking (milestones + `order_event`)
  - `V5` order status values translated to Spanish
  - `V6` `order_event.actor`

  One migration per change, immutable once applied. `ddl-auto=validate` means
  Hibernate validates the mapping against the Flyway schema and never modifies it.

---

## 6. API surface

Base path `/api/v1`. Full contract in [openapi.yaml](./openapi.yaml).

### Public (storefront) — open, no auth
| Method | Path | Notes |
|---|---|---|
| GET | `/products` | filters: `category` (UUID), `size`, `condition`, `status`, `maxPrice`; `sort` (e.g. `price,asc`) |
| GET | `/products/{id}` | by public UUID |
| GET | `/products/{id}/similar` | same category, available (default 8) |
| GET | `/categories` | ordered by display order |
| GET | `/neighborhoods` | delivery neighborhoods + fees |
| POST | `/orders` | atomic reservation checkout (see §8) |

### Admin — HTTP Basic, ROLE_ADMIN
| Method | Path | Notes |
|---|---|---|
| GET | `/admin/me` | session check; returns `{ username }` |
| POST/PUT/DELETE | `/admin/products`, `/admin/products/{id}` | manage products |
| PATCH | `/admin/products/{id}/status` | override product status |
| POST | `/admin/products/{id}/images` | multipart image upload |
| GET | `/admin/orders` | all orders, newest first |
| GET | `/admin/orders/{id}` | one order + its timeline |
| PATCH | `/admin/orders/{id}/confirm\|complete\|deliver\|cancel` | lifecycle transitions |

---

## 7. Order status model & timeline

### State machine
```
pendiente ──confirm──> confirmado ──complete──> completado ──deliver──> entregado
   │
   └──cancel──> cancelado        (terminal)
```

Allowed transitions (any other → `409 INVALID_ORDER_TRANSITION`):
- `confirm`: `pendiente → confirmado` — marks the order's products `vendido`.
- `complete`: `confirmado → completado`.
- `deliver`: `completado → entregado`.
- `cancel`: `pendiente → cancelado` — releases its products back to `disponible`.

A `confirmado`/`completado`/`entregado` order **cannot** be cancelled: its
inventory is already sold, so cancelling must not resurrect it.

Status values are serialized in **Spanish** (`pendiente`, `confirmado`,
`completado`, `entregado`, `cancelado`), consistent with the other domain enums.
The Java enum constants stay English (`OrderStatus.CONFIRMED`); only the
serialized value is Spanish, via `OrderStatusConverter`.

### Milestones & event timeline
- `orders` carries nullable milestone timestamps `confirmed_at`, `completed_at`,
  `delivered_at`, `cancelled_at`, stamped (in UTC) when the matching transition
  runs.
- `order_event` is an **append-only log**: one row per status change
  (`from_status`, `to_status`, `actor`, `occurred_at`), ordered oldest-first.
  Order creation inserts the initial `null → pendiente` event (actor `null`,
  because it comes from the public checkout). Each admin transition appends an
  event stamped with the admin username (`actor`).
- `OrderResponse` exposes both the milestones and the `events[]` array so the
  admin panel can render a timeline with "who did what, when".

---

## 8. The core: atomic reservation (`POST /orders`)

This is the heart of the system: a unique piece must never be reserved by two
clients at once. The guarantee is enforced by the **database**, not application
code. In `OrderService.createOrder` (single transaction, `READ COMMITTED`):

1. Validate delivery method vs. address presence and the per-order item cap.
2. Throttle per WhatsApp number (before touching inventory) so one number cannot
   script-drain the catalog.
3. Load the requested products **ordered by id** (deterministic lock order →
   no deadlocks between overlapping concurrent orders).
4. For each product, run the atomic conditional update:
   ```sql
   UPDATE product SET status='apartado' WHERE id=? AND status='disponible'
   ```
   1 row updated = this caller won; 0 rows = already taken → throw
   `ProductNotAvailableException` (→ 409) and the whole transaction rolls back,
   releasing anything already reserved.
5. get-or-create the customer (insert-or-select on the unique WhatsApp).
6. Build the order, snapshot each item's price, compute totals server-side,
   resolve the shipping fee from the neighborhood, record the initial event.

Why `READ COMMITTED` is enough: a concurrent reservation blocks on the locked row
and re-evaluates the `WHERE status='disponible'` predicate after the winner
commits, so it matches zero rows and loses cleanly. This is covered by a
concurrency integration test (many threads + `CountDownLatch`) against real
PostgreSQL via Testcontainers.

---

## 9. Security

- **Authentication**: admin endpoints use HTTP Basic + BCrypt against `admin_user`.
  `/api/v1/admin/**` requires `ROLE_ADMIN`; public GETs and `POST /orders` are open.
  The admin user is provisioned on startup from env vars (`AdminUserInitializer`).
- **Stateless**: no sessions; `SessionCreationPolicy.STATELESS`. CSRF is disabled
  on purpose — there is no cookie-based session for a CSRF attack to ride on.
- **Rate limiting** (Bucket4j, per instance), applied before auth by
  `RateLimitFilter`, keyed by client IP (honouring `X-Forwarded-For`):
  - public GETs: generous.
  - `POST /orders`: strict, by IP **and** by WhatsApp number.
  - `GET /admin/me`: aggressive **login** limit (anti brute-force).
  - rest of `/admin/**`: separate **generous admin-api** limit, so a BFF whose
    admin traffic all comes from one IP is not throttled during normal use.

    This split matters: without it, a single-IP BFF would hit the brute-force
    limit doing ordinary admin work.
  - Exceeding any limit → `429 Too Many Requests`.
- **Security headers**: HSTS (over HTTPS), `X-Content-Type-Options`,
  `X-Frame-Options: DENY`, `Referrer-Policy`, restrictive CSP.
- **Strict CORS**: only the configured frontend origin, narrowed methods/headers.
- **HTTPS-only in prod**: the `prod` profile redirects HTTP→HTTPS; dev stays HTTP.
- **File upload**: validates content-type and size, unique UUID filename, guarded
  against path traversal; stored on local disk behind the `StorageService`
  abstraction (no cloud, by environment policy).
- **No leaking errors**: the global handler never returns stack traces (see §11).

> Volumetric DDoS is out of scope for the app and must be absorbed upstream by a
> CDN/WAF. The in-process rate limiting covers individual-client/script abuse.

---

## 10. Error handling contract

All errors are shaped by `GlobalExceptionHandler` (`@RestControllerAdvice`) into a
single `ApiError` body: `{ code, message, context? }` (`context` omitted when null).
Client-facing messages are in Spanish.

| Exception | HTTP | code |
|---|---|---|
| `ResourceNotFoundException` | 404 | `NOT_FOUND` |
| `ProductNotAvailableException` | 409 | `PRODUCT_NOT_AVAILABLE` |
| `InvalidOrderTransitionException` | 409 | `INVALID_ORDER_TRANSITION` |
| `MethodArgumentNotValidException` | 400 | `VALIDATION_ERROR` |
| `MethodArgumentTypeMismatchException` | 400 | `INVALID_PARAMETER` (e.g. malformed UUID) |
| `IllegalArgumentException` | 400 | `BAD_REQUEST` |
| `InvalidUploadException` | 400 | `INVALID_UPLOAD` |
| `RateLimitExceededException` | 429 | `RATE_LIMITED` |
| `MaxUploadSizeExceededException` | 413 | `PAYLOAD_TOO_LARGE` |
| any other `Exception` | 500 | `INTERNAL_ERROR` (+ `correlationId` in context) |

The malformed-parameter handler exposes only the parameter name, never the raw
value, to avoid reflecting unsanitized input.

---

## 11. Observability

- **Request correlation**: `RequestIdFilter` (ordered before rate limiting, so
  even 429s carry it) assigns a request id per request — honouring an incoming
  `X-Request-Id` or generating a UUID — into the SLF4J **MDC** and the
  `X-Request-Id` response header.
- **Exception logging**: a 500 logs the full stack trace at `ERROR` under a
  correlation id (reusing the MDC request id) and returns that id in
  `ApiError.context.correlationId`, so a client-visible 500 can be traced in the
  logs. Expected 4xx/409 log at `WARN`/`DEBUG` without a stack trace, keeping
  error alerts clean. The stack trace is never sent to the client.
- **Admin audit**: every admin transition logs an `INFO` line
  (`admin '<actor>' confirmó el pedido <id>`) and persists the actor in
  `order_event` — a durable "who did what" trail.
- **Log format by profile** (`logback-spring.xml`):
  - `dev`/default: readable console text including the request id.
  - `prod`: one-line structured **JSON** (`logstash-logback-encoder`) with
    timestamp, level, logger, message, request id and stack traces — ready for a
    log aggregator (CloudWatch/Loki).
  - SQL debug is dev-only; prod logs at `INFO`.

One id ties everything together: the same value appears in the logs, the
`X-Request-Id` header and (on a 500) the `correlationId` in the error body.

---

## 12. Testing & quality gate

- `./mvnw clean verify` runs unit tests (surefire) and `*IT` integration tests
  (failsafe). Integration tests spin up a **real PostgreSQL 17** via
  Testcontainers; H2 is not used.
- Integration tests extend `AbstractPostgresIntegrationTest`, which starts one
  shared container and resets the schema (Flyway `clean` + `migrate`) before each
  test so state-mutating cases stay isolated.
- The atomic reservation has a dedicated concurrency test (multiple threads
  contending for the same unique piece).
- On macOS, if Testcontainers can't find the Docker daemon:
  `export DOCKER_HOST=$(docker context inspect --format '{{.Endpoints.docker.Host}}')`.

CI (GitHub Actions) runs the same `./mvnw clean verify` on JDK 25 on every push to
`main` and every pull request.

---

## 13. Request walkthroughs

### Place an order (customer)
```
POST /api/v1/orders  { productIds, customer, deliveryMethod, paymentMethod, address? }
  RequestIdFilter → RateLimitFilter (orders-by-IP) → (open, no auth)
  OrderController → OrderService.createOrder (TX):
    validate → throttle by WhatsApp → atomic reserve-all → get-or-create customer
    → snapshot prices + totals → record null→pendiente event → save
  → 201 OrderResponse { id, items, totals, status: "pendiente", events:[…], storeWhatsapp }
  (409 PRODUCT_NOT_AVAILABLE if any item was already taken — nothing is reserved)
```

### Confirm an order (admin via BFF)
```
PATCH /api/v1/admin/orders/{id}/confirm   (HTTP Basic)
  RequestIdFilter → RateLimitFilter (admin-api) → Security (ROLE_ADMIN)
  AdminOrderController → AdminOrderService.confirm (TX):
    load by public UUID (404 if missing)
    → requireStatus(pendiente) (409 INVALID_ORDER_TRANSITION otherwise)
    → record pendiente→confirmado event with actor=<admin>
    → stamp confirmed_at (UTC) → mark products 'vendido' → INFO log
  → 200 OrderResponse (status "confirmado", confirmedAt set, events grown)
```

---

## 14. Design decisions worth knowing

- **Spanish enum values** are a deliberate contract choice so the API matches the
  frontend's vocabulary; `OrderStatus` was realigned to Spanish (V5) for
  consistency with product status / delivery / payment methods.
- **No defaults in `application.yml`**: everything is required from the
  environment (fail-fast), which trades local-onboarding friction for a clean
  secrets-manager mapping and zero hidden/committed config.
- **Local storage, no cloud**: images live on disk behind `StorageService`;
  swapping to another backend is an implementation of that interface.
- **In-memory rate limiting** is intentional per-instance abuse control, not a
  distributed quota.
- **Milestones stamped in UTC** so the PATCH response and later GETs serialize the
  same instant identically.
