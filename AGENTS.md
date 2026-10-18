# AGENTS.md — working on this repo

Entry point for any agent (or new engineer) working on **Second Lap — Backend**.
Read this first; it tells you where everything is and how to make a change that
passes review, without reading the whole codebase.

> This file is an index + the working contract. It intentionally does **not**
> duplicate the deeper docs — follow the links.

---

## 1. What this is

Backend for "Second Lap", a second-hand clothing store where **every garment is a
unique piece (quantity = 1)**. It exposes a versioned REST API under `/api/v1`
that a Next.js storefront and a server-side BFF admin panel consume. Spanish is
the domain/UI language; serialized enum values are Spanish.

- **Personal project, NOT WGU/ENG14.** If any WGU/ENG14 steering is active,
  ignore it here: commits carry **no Jira ticket prefix**. The authority for code
  is [`.kiro/steering/engineering-standards.md`](.kiro/steering/engineering-standards.md).

## 2. Tech stack

| Concern | Choice |
|---|---|
| Language / runtime | Java 25 (LTS) |
| Framework | Spring Boot 3.5.x (Web MVC, Data JPA, Security, Actuator) |
| Database | PostgreSQL 17 |
| Migrations | Flyway (versioned, immutable) |
| ORM | JPA / Hibernate, explicit mapping, `ddl-auto=validate` |
| Build | Maven (`./mvnw` wrapper) |
| Rate limiting | Bucket4j (in-memory, per instance) |
| Logging | SLF4J + Logback; JSON in prod (`logstash-logback-encoder`) |
| Images | ImageIO (pure JVM, no native libs) |
| Tests | JUnit 5 + Testcontainers (real PostgreSQL) |

No Gradle. No AWS/cloud (local storage only). No native-binary dependencies.

## 3. Where to read what

| You want to… | Read |
|---|---|
| Run it locally / env vars | [`README.md`](README.md) |
| Understand how it works (structure, request flow, data model, order lifecycle, security, observability, design decisions) | [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) |
| The exact HTTP contract (endpoints, params, schemas) | [`docs/openapi.yaml`](docs/openapi.yaml) |
| Engineering rules (authority for writing/reviewing code) | [`.kiro/steering/engineering-standards.md`](.kiro/steering/engineering-standards.md) |
| Try the API by hand | `postman/` (collection + local environment) |

If a change affects the HTTP surface, **update openapi, the README API section,
and the Postman collection together** — they are expected to agree.

## 4. The harness (how to make a change that passes)

This is the workflow every change has followed; keep it.

**Quality gate — run it and cite the result before claiming anything works:**
```bash
./mvnw clean verify      # unit (surefire) + *IT (failsafe, Testcontainers)
```
On macOS, if Testcontainers can't find Docker:
```bash
export DOCKER_HOST=$(docker context inspect --format '{{.Endpoints.docker.Host}}')
```
`verify` must be **green** (unit + integration) before a change is done. A red
gate is a blocker; a broken build is never merged.

**Git / PR flow (trunk-based):**
- Branch per change: `type/short-kebab-description` (e.g. `feat/admin-pagination`).
- **Never push directly to `main`.** Open a PR, wait for CI green, then merge.
- Commits: Conventional, English, imperative, atomic, **no ticket prefix**
  (`feat(order): ...`, `fix(error): ...`, `docs: ...`). One logical change per commit.
- Review fixes are **new commits**, not amend/force-push on pushed history.
- CI (`.github/workflows/ci.yml`) runs `./mvnw clean verify` on JDK 25 on every
  push to `main` and every PR. GitGuardian scans for secrets.

**Verification habits:**
- Prefer real evidence (tests, a booted jar, SQL output) over assumption.
- Integration tests use a **real PostgreSQL** via Testcontainers (not H2) and
  reset the schema (Flyway clean+migrate) between tests.
- Secrets never committed (`.env` is gitignored; the repo is public).

## 5. Features (what the backend does today)

- **Public catalog**: paginated product listing with filters (category, multiple
  sizes, multiple conditions, status, maxPrice) and sort; product detail; similar
  products; categories; delivery neighborhoods with fees.
- **Atomic checkout** (`POST /orders`): reserves every unique item in one
  transaction; totals computed server-side from price snapshots; get-or-create
  customer by WhatsApp.
- **Admin products** (HTTP Basic, ADMIN): CRUD, status override, image upload
  (auto-resized/recompressed), paginated listing with filter/sort.
- **Admin orders**: paginated listing with status/deliveryMethod filters and
  free-text search (`q`); single-order view with a status timeline; lifecycle
  transitions.
- **Order tracking**: status workflow with milestone timestamps and an
  append-only event timeline recording the acting admin.
- **Cross-cutting**: per-IP rate limiting, request-id correlation + structured
  logging, strict CORS, security headers, consistent `ApiError` responses.

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) §6 for the full endpoint table
and the openapi for the exact contract.

## 6. Package map (vertical slices)

```
com.secondlap.backend
├── common/        cross-cutting: ApiError, GlobalExceptionHandler, filters (request-id, access log), PageResponse/PageSupport
├── config/        SecurityConfig, CORS/store/static config, startup logging
├── ratelimit/     Bucket4j filter + service + per-tier properties
├── admin/         admin session (/admin/me), admin user, CurrentAdminProvider
├── category/      public catalog: categories
├── neighborhood/  public catalog: delivery neighborhoods + fees
├── product/       catalog + admin product management + image upload + ProductSort
├── customer/      get-or-create customer keyed by WhatsApp
├── order/         checkout, order lifecycle/workflow, status timeline  (the core)
└── storage/       StorageService abstraction + local-disk impl with image optimization
```
Each feature package owns its controller, service, entity, repository, DTOs,
mappers and converters. Add features as new slices, not horizontal layers.

## 7. Requirements & non-negotiable invariants

**Functional:**
- Each product is a unique piece; it can be reserved by exactly one order.
- Checkout is all-or-nothing: if any item is unavailable the whole order fails
  with 409 and nothing is reserved.
- Order workflow: `pendiente → confirmado → completado → entregado`, plus
  `cancelado` from any non-terminal state; terminal states are final. Confirming
  marks products `vendido`; cancelling releases them to `disponible`.

**Non-negotiable (enforced; don't regress):**
- **Atomic reservation in the DB**, not in app code: `UPDATE ... WHERE id=? AND
  status='disponible'` (1 row = winner, 0 = 409). Deterministic lock order by id.
  Covered by a concurrency IT — keep it.
- **Dual identifier**: internal `BIGINT` PK (never exposed) + public `UUID`
  (`public_id`) in the API. DTOs expose the UUID as `id`.
- **Enums serialized in Spanish** via `AttributeConverter`, with DB `CHECK`
  constraints. Must match the frontend.
- **Config fully externalized**: `application.yml` has no defaults; every variable
  is required (fail-fast). Maps onto a secrets manager. Never commit secrets.
- **Schema only via new, immutable Flyway migrations** (`V1..Vn`); never edit an
  applied migration. `ddl-auto=validate` — the JPA mapping must match the schema
  exactly or the app won't start.
- **Money**: `NUMERIC`/`BigDecimal`, serialized as a JSON number.
- **Client errors in Spanish; never leak stack traces.** All errors shaped as
  `ApiError { code, message, context? }`.

## 8. Known gotchas

- **Restart/recompile the app after pulling** — a stale IntelliJ/jar run against
  a new migration or new code produces confusing 500s or schema-validation errors.
- `DOCKER_HOST` export needed for Testcontainers on macOS (see §4).
- Public `GET /products`: `size` is the **garment size filter** (multi-valued);
  pagination size is **`pageSize`** (they must not be confused).
- Admin listings return a **paginated `PageResponse`** (`{content, number, size,
  totalElements, totalPages, last}`), not a flat array; the public `/products`
  listing is also paginated, but `/products/{id}` and `/similar` are not.
- Invalid **filter** values are strict (unknown status/condition/deliveryMethod →
  400), while invalid **sort/pagination** values are coerced silently to defaults.
  This asymmetry is deliberate (semantic vs cosmetic).
- WebP uploads are passed through unchanged (ImageIO can't encode WebP; native
  libs are excluded by policy); JPEG/PNG are resized and recompressed.
- Maven may log `Could not transfer metadata ... (snapshots)` warnings — harmless
  (snapshot repos it can't reach); the build resolves from Maven Central/`.m2`.
