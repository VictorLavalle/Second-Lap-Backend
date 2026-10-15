# Second Lap — Backend

Backend de "Second Lap", una tienda de ropa de segunda mano donde cada prenda es
una pieza única (cantidad = 1). Expone una API REST versionada en `/api/v1` que
reemplaza la frontera de datos del frontend (Next.js), por lo que las respuestas
calzan con los tipos que consume la UI.

## Stack

- Java 25 (LTS) + Spring Boot 3.5.x
- PostgreSQL 17
- Maven (wrapper `./mvnw`)
- Flyway (migraciones) · JPA/Hibernate (mapeo explícito)
- JUnit 5 + Testcontainers (PostgreSQL real) para integración

## Requisitos

- JDK 25
- Docker (para PostgreSQL vía docker-compose y para los tests de integración)

## Cómo correr

### Opción A — todo con docker-compose (Postgres + app)

```bash
cp .env.example .env   # ajusta los valores de dev; .env está gitignored
docker compose up --build
```

La API queda en `http://localhost:8080`. Flyway aplica el esquema y el seed al
arrancar.

### Opción B — Postgres en Docker, app local

```bash
docker compose up -d postgres

# las credenciales no tienen default (repo público): expórtalas antes de arrancar
export SECONDLAP_DB_PASSWORD=your-dev-password
export SECONDLAP_ADMIN_USERNAME=admin
export SECONDLAP_ADMIN_PASSWORD=your-dev-password
./mvnw spring-boot:run
```

El perfil por defecto es `dev` y apunta a `localhost:5432/secondlap`. Si no
exportas las credenciales, la app falla al arrancar (fail-fast intencional: no
hay contraseñas por defecto en el repo).

## Variables de entorno

La configuración se inyecta por variables de entorno; los valores por defecto
sirven para desarrollo local. **Nunca** se commiten secretos (el repo es
público).

| Variable | Default | Descripción |
|----------|---------|-------------|
| `SECONDLAP_DB_URL` | `jdbc:postgresql://localhost:5432/secondlap` | JDBC URL |
| `SECONDLAP_DB_USERNAME` | `secondlap` | Usuario de la BD |
| `SECONDLAP_DB_PASSWORD` | _(requerida)_ | Password de la BD (sin default; repo público) |
| `SERVER_PORT` | `8080` | Puerto HTTP |
| `SECONDLAP_CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Origen del frontend permitido |
| `SECONDLAP_UPLOADS_DIR` | `./uploads` | Directorio local de imágenes |
| `SECONDLAP_UPLOADS_URL_PREFIX` | `/uploads` | Prefijo público para servir imágenes |
| `SECONDLAP_UPLOADS_MAX_BYTES` | `5242880` | Tamaño máximo de subida (5 MB) |
| `SECONDLAP_STORE_WHATSAPP` | `529996384026` | WhatsApp de la tienda (52 + 10 dígitos) |
| `SECONDLAP_ADMIN_USERNAME` | `admin` | Usuario admin creado al arranque |
| `SECONDLAP_ADMIN_PASSWORD` | _(requerida)_ | Password admin (sin default; repo público) |

## Migraciones (Flyway)

Las migraciones viven en `src/main/resources/db/migration` y se aplican
automáticamente al arrancar la app.

- `V1__initial_schema.sql` — esquema (tablas, CHECKs, índices).
- `V2__seed_dev_data.sql` — seed de desarrollo (sin credenciales).
- `V3__seed_real_data.sql` — datos reales del catálogo (categorías, colonias,
  productos).

Convención: una migración por cambio, versionada e inmutable una vez aplicada.
Esquema y datos (seed) van en archivos separados. `ddl-auto` es `validate`
(Hibernate valida el mapeo contra el esquema de Flyway; nunca modifica la BD).

### Credenciales de admin (desarrollo)

El admin se crea al arranque desde `SECONDLAP_ADMIN_USERNAME` /
`SECONDLAP_ADMIN_PASSWORD` (requeridas; sin valores por defecto en el repo). No se
commite ningún hash; rota la contraseña por entorno. El panel admin usa HTTP
Basic.

## API

Pública (storefront):

- `GET /api/v1/products` — filtros: `category` (UUID), `size`, `condition`,
  `status`, `maxPrice`; `sort` (p. ej. `price,asc`).
- `GET /api/v1/products/{id}`
- `GET /api/v1/products/{id}/similar` — misma categoría, disponibles (default 8).
- `GET /api/v1/categories`
- `GET /api/v1/neighborhoods`
- `POST /api/v1/orders` — aparta atómicamente todos los items en una transacción,
  calcula totales server-side y devuelve la orden + el WhatsApp de la tienda.

Admin (HTTP Basic, rol ADMIN):

- `POST/PUT/DELETE /api/v1/admin/products`, `PATCH /api/v1/admin/products/{id}/status`
- `POST /api/v1/admin/products/{id}/images` (multipart)
- `GET /api/v1/admin/orders`, `PATCH /api/v1/admin/orders/{id}/confirm`,
  `PATCH /api/v1/admin/orders/{id}/cancel` (cancelar libera los items a disponible)

## Tests

```bash
./mvnw clean verify
```

Corre tests unitarios (surefire) y de integración `*IT` con Testcontainers
(failsafe). La integración levanta un PostgreSQL 17 real.

### Nota de Testcontainers en macOS (Docker Desktop)

Si Testcontainers no encuentra el daemon ("Could not find a valid Docker
environment"), exporta el socket del contexto activo antes de correr los tests:

```bash
export DOCKER_HOST=$(docker context inspect --format '{{.Endpoints.docker.Host}}')
./mvnw clean verify
```

En CI (`ubuntu-latest`) no hace falta: el runner trae Docker en la ruta estándar.

## CI

GitHub Actions (`.github/workflows/ci.yml`) corre `./mvnw clean verify` sobre
JDK 25 (Temurin) con cache de Maven, en cada push a `main` y en cada pull
request.

## Seguridad

Defensas aplicadas a nivel de aplicación:

- **Rate limiting** (Bucket4j, por instancia): `POST /orders` limitado por IP y
  además por número de WhatsApp (un mismo número no puede agotar el inventario de
  piezas únicas); GET públicos con un límite holgado; login admin con límite
  agresivo (anti fuerza bruta). Al exceder se devuelve `429 Too Many Requests`.
  Configurable por variables `SECONDLAP_RL_*`.
- **Límite de items por pedido** (`SECONDLAP_ORDER_MAX_ITEMS`, default 20) como
  defensa extra contra el agotamiento del catálogo.
- **Límites de tamaño de request** (multipart y form POST) → `413 Payload Too Large`.
- **Security headers**: HSTS (en HTTPS), `X-Content-Type-Options`, `X-Frame-Options: DENY`,
  `Referrer-Policy`, y una CSP restrictiva.
- **CORS estricto**: solo el origen del frontend, métodos y headers acotados.
- **HTTPS-only en producción**: el perfil `prod` activa `require-https` (redirige
  HTTP→HTTPS). En dev se mantiene HTTP para no romper el flujo local.
- **Errores sin fugas**: el manejo global nunca devuelve stack traces al cliente.
- **Admin**: HTTP Basic + BCrypt contra `admin_user`; `/api/v1/admin/**` requiere rol ADMIN.

> **DDoS volumétrico:** no es responsabilidad de la aplicación y no se puede
> resolver en Spring — para cuando el tráfico llega al proceso, el daño ya está
> hecho. Al desplegar, poné el backend detrás de un CDN/WAF (p. ej. Cloudflare)
> que absorba el volumen. El rate limiting de la app cubre el abuso de un cliente
> individual y los scripts, no un ataque distribuido.
