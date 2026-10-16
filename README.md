# Second Lap — Backend

Backend for "Second Lap", a second-hand clothing store where every garment is a
unique piece (quantity = 1). It exposes a versioned REST API under `/api/v1`
that replaces the frontend's (Next.js) data boundary, so the responses match the
types the UI consumes.

## Stack

- Java 25 (LTS) + Spring Boot 3.5.x
- PostgreSQL 17
- Maven (`./mvnw` wrapper)
- Flyway (migrations) · JPA/Hibernate (explicit mapping)
- JUnit 5 + Testcontainers (real PostgreSQL) for integration

## Requirements

- JDK 25
- Docker (for PostgreSQL via docker-compose and for the integration tests)

## How to run

Configuration is fully externalized: `application.yml` has **no** fallback
defaults, so every variable listed below must be supplied by the environment.
This maps cleanly onto a secrets manager in production. `.env` is gitignored;
never commit real secrets (the repo is public).

### Option A — everything with docker-compose (Postgres + app)

```bash
cp .env.example .env   # fill in your dev values; .env is gitignored
docker compose up --build
```

The API is served at `http://localhost:8080`. Flyway applies the schema and seed
on startup. Compose loads the full `.env` and only overrides the values that must
differ inside its network (the DB host and the uploads directory).

### Option B — Postgres in Docker, app running locally

```bash
docker compose up -d postgres
```

The default profile is `dev` and points at `localhost:5432/secondlap`. Because
nothing has a default anymore, the app needs the **complete** set of variables
from the table below to start; if any is missing it fails fast at startup.

- **IntelliJ:** use the *EnvFile* plugin on the `SecondLapBackendApplication` run
  configuration and point it at your `.env` (single source of truth), or paste
  the variables into the run configuration's *Environment variables* field.
- **Shell / Maven:** load the `.env` into the shell before starting:

  ```bash
  set -a; source .env; set +a
  ./mvnw spring-boot:run
  ```

## Environment variables

Configuration is injected via environment variables. There are **no** defaults in
`application.yml`: all variables are required, so the app fails fast at startup if
any is missing. Never commit secrets (the repo is public).

| Variable | Required | Description |
|----------|----------|-------------|
| `SECONDLAP_DB_URL` | yes | JDBC URL (e.g. `jdbc:postgresql://localhost:5432/secondlap`); credentials are not embedded |
| `SECONDLAP_DB_USERNAME` | yes | Database user |
| `SECONDLAP_DB_PASSWORD` | yes | Database password (no default; public repo) |
| `SECONDLAP_ADMIN_USERNAME` | yes | Admin user created on startup |
| `SECONDLAP_ADMIN_PASSWORD` | yes | Admin password (no default; public repo) |
| `SERVER_PORT` | yes | HTTP port (e.g. `8080`) |
| `SECONDLAP_CORS_ALLOWED_ORIGINS` | yes | Allowed frontend origin (e.g. `http://localhost:3000`) |
| `SECONDLAP_UPLOADS_DIR` | yes | Local image directory (e.g. `./uploads`) |
| `SECONDLAP_UPLOADS_URL_PREFIX` | yes | Public prefix for serving images (e.g. `/uploads`) |
| `SECONDLAP_UPLOADS_MAX_BYTES` | yes | Max upload size in bytes (e.g. `5242880` = 5 MB) |
| `SECONDLAP_MAX_FILE_SIZE` | yes | Multipart max file size (e.g. `5MB`) |
| `SECONDLAP_MAX_REQUEST_SIZE` | yes | Multipart max request size (e.g. `15MB`) |
| `SECONDLAP_MAX_FORM_POST` | yes | Tomcat max form POST size (e.g. `2MB`) |
| `SECONDLAP_MAX_SWALLOW` | yes | Tomcat max swallow size (e.g. `2MB`) |
| `SECONDLAP_STORE_WHATSAPP` | yes | Store WhatsApp (52 + 10 digits) |
| `SECONDLAP_ORDER_MAX_ITEMS` | yes | Max items per order (e.g. `20`) |
| `SECONDLAP_RL_READS_CAPACITY` | yes | Rate limit: public reads — requests per window |
| `SECONDLAP_RL_READS_WINDOW` | yes | Rate limit: public reads — window in seconds |
| `SECONDLAP_RL_ORDERS_CAPACITY` | yes | Rate limit: orders by IP — requests per window |
| `SECONDLAP_RL_ORDERS_WINDOW` | yes | Rate limit: orders by IP — window in seconds |
| `SECONDLAP_RL_ORDERS_WA_CAPACITY` | yes | Rate limit: orders per WhatsApp — requests per window |
| `SECONDLAP_RL_ORDERS_WA_WINDOW` | yes | Rate limit: orders per WhatsApp — window in seconds |
| `SECONDLAP_RL_ADMIN_CAPACITY` | yes | Rate limit: admin login (`GET /admin/me`) — requests per window |
| `SECONDLAP_RL_ADMIN_WINDOW` | yes | Rate limit: admin login — window in seconds |
| `SECONDLAP_RL_ADMIN_API_CAPACITY` | yes | Rate limit: admin API (rest of `/admin/**`) — requests per window |
| `SECONDLAP_RL_ADMIN_API_WINDOW` | yes | Rate limit: admin API — window in seconds |

See `.env.example` for a complete, ready-to-copy template with sample dev values.

## Migrations (Flyway)

Migrations live in `src/main/resources/db/migration` and are applied
automatically on startup.

- `V1__initial_schema.sql` — schema (tables, CHECKs, indexes).
- `V2__seed_dev_data.sql` — development seed (no credentials).
- `V3__seed_real_data.sql` — real catalog data (categories, neighborhoods,
  products).

Convention: one migration per change, versioned and immutable once applied.
Schema and data (seed) live in separate files. `ddl-auto` is `validate`
(Hibernate validates the mapping against the Flyway schema; it never modifies the
database).

### Admin credentials (development)

The admin is created on startup from `SECONDLAP_ADMIN_USERNAME` /
`SECONDLAP_ADMIN_PASSWORD` (required; no defaults in the repo). No hash is
committed; rotate the password per environment. The admin panel uses HTTP Basic.

## API

Public (storefront):

- `GET /api/v1/products` — filters: `category` (UUID), `size`, `condition`,
  `status`, `maxPrice`; `sort` (e.g. `price,asc`).
- `GET /api/v1/products/{id}`
- `GET /api/v1/products/{id}/similar` — same category, available (default 8).
- `GET /api/v1/categories`
- `GET /api/v1/neighborhoods`
- `POST /api/v1/orders` — atomically reserves all items in a single transaction,
  computes totals server-side, and returns the order plus the store's WhatsApp.

Admin (HTTP Basic, ADMIN role):

- `POST/PUT/DELETE /api/v1/admin/products`, `PATCH /api/v1/admin/products/{id}/status`
- `POST /api/v1/admin/products/{id}/images` (multipart)
- `GET /api/v1/admin/orders` (newest first),
  `GET /api/v1/admin/orders/{id}` (single order with its status timeline)
- Order lifecycle transitions (each stamps a milestone timestamp and appends a
  status-change event to the order's timeline):
  - `PATCH /api/v1/admin/orders/{id}/confirm` — `pending → confirmed` (products become `vendido`)
  - `PATCH /api/v1/admin/orders/{id}/complete` — `confirmed → completed`
  - `PATCH /api/v1/admin/orders/{id}/deliver` — `completed → delivered`
  - `PATCH /api/v1/admin/orders/{id}/cancel` — `pending → cancelled` (releases items back to `disponible`)

  Only these transitions are allowed; any other returns `409 INVALID_ORDER_TRANSITION`.
  A confirmed/completed/delivered order cannot be cancelled (inventory is already sold).

## Tests

```bash
./mvnw clean verify
```

Runs unit tests (surefire) and `*IT` integration tests with Testcontainers
(failsafe). The integration suite spins up a real PostgreSQL 17.

### Testcontainers note on macOS (Docker Desktop)

If Testcontainers cannot find the daemon ("Could not find a valid Docker
environment"), export the active context's socket before running the tests:

```bash
export DOCKER_HOST=$(docker context inspect --format '{{.Endpoints.docker.Host}}')
./mvnw clean verify
```

On CI (`ubuntu-latest`) this is not needed: the runner ships Docker at the
standard path.

## CI

GitHub Actions (`.github/workflows/ci.yml`) runs `./mvnw clean verify` on JDK 25
(Temurin) with Maven caching, on every push to `main` and every pull request.

## Security

Application-level defenses:

- **Rate limiting** (Bucket4j, per instance): `POST /orders` is limited by IP and
  also by WhatsApp number (a single number cannot drain the inventory of unique
  pieces); public GETs have a generous limit. Admin throttling is split in two:
  the session check `GET /admin/me` uses an aggressive login limit (anti
  brute-force), while the rest of `/admin/**` uses a separate, generous admin-API
  limit so a server-side BFF — whose normal admin traffic all comes from a single
  IP — is not throttled during regular use. Exceeding any limit returns
  `429 Too Many Requests`. Configurable via the `SECONDLAP_RL_*` variables.
- **Per-order item cap** (`SECONDLAP_ORDER_MAX_ITEMS`) as an extra defense against
  draining the catalog.
- **Request size limits** (multipart and form POST) → `413 Payload Too Large`.
- **Security headers**: HSTS (over HTTPS), `X-Content-Type-Options`,
  `X-Frame-Options: DENY`, `Referrer-Policy`, and a restrictive CSP.
- **Strict CORS**: only the frontend origin, with narrowed methods and headers.
- **HTTPS-only in production**: the `prod` profile enables `require-https`
  (redirects HTTP→HTTPS). Dev stays on HTTP so the local flow is not broken.
- **No leaking errors**: the global handler never returns stack traces to the
  client.
- **Admin**: HTTP Basic + BCrypt against `admin_user`; `/api/v1/admin/**` requires
  the ADMIN role.

> **Volumetric DDoS:** not the application's responsibility and not solvable in
> Spring — by the time traffic reaches the process, the damage is done. When
> deploying, put the backend behind a CDN/WAF (e.g. Cloudflare) that absorbs the
> volume. The app's rate limiting covers abuse from an individual client and
> scripts, not a distributed attack.
