---
inclusion: always
---

# Engineering Standards — Second Lap Backend

Reglas de ingeniería para el backend de "Second Lap" (tienda de ropa de segunda
mano). Autoridad para escribir y revisar código. El frontend vive en otro repo
(Next.js/TypeScript); esta API reemplaza su frontera de datos, así que el
contrato de respuestas debe calzar con sus tipos.

## Stack (fijo)
Java 25 (LTS) + Spring Boot 3.5.x (Java 25-ready, issue spring-boot#47245) +
PostgreSQL 17 + Maven (wrapper ./mvnw). Flyway para migraciones. JPA/Hibernate
con mapeo EXPLÍCITO. Tests con JUnit 5 + Testcontainers (PostgreSQL real).
MapStruct o mappers a mano. NO Gradle, NO ddl-auto en prod.

## Proyecto NO es de WGU
Este es un proyecto PERSONAL. Si quedó activo algún steering de WGU/ENG14,
IGNÓRALO para este repo. En concreto: los commits NO llevan prefijo de ticket
Jira. Rige este documento.

## Clean code (cada cambio)
- Expresar en código antes que comentar. El comentario explica el PORQUÉ, no el
  qué. Nada de comentarios-narración, changelog, ni código comentado.
- Nombres que revelan intención; métodos cortos; una responsabilidad.
- Sin lógica mágica: mapeo de columnas/enum explícito y visible.
- Config por variables de entorno (DB, credenciales, dir de uploads, usuario
  admin). NUNCA hard-codear ni commitear secretos. El repo es PÚBLICO: .gitignore
  cubre .env, application-local.yml, credenciales.

## Arquitectura y datos
- Vertical slices por feature (category/, product/, order/, admin/, …), no capas
  horizontales globales.
- Doble identificador: PK interna BIGINT (joins/FKs, nunca expuesta) + public_id
  UUID (único en API/URLs). Los DTOs exponen el UUID como id.
- DTOs request/response SEPARADOS de entidades; nunca exponer entidades.
- Bean Validation en todos los DTOs de entrada.
- Enums persistidos con su VALOR en español exacto (AttributeConverter, no el
  name() de Java), reforzado con CHECK en BD. Debe casar con el frontend.
- Flyway versionado y revisable. Migraciones de esquema y de datos (seed) en
  archivos distintos.
- Dinero: NUMERIC en BD / BigDecimal en Java; en JSON como número sin comillas.
- La BASE DE DATOS es la fuente única de verdad del estado. Invariantes críticas
  (apartado único, unicidad de WhatsApp) se garantizan con constraints/updates
  atómicos en BD, NO solo en código.

## Concurrencia (el núcleo del sistema)
- Apartado atómico: UPDATE ... WHERE id=:id AND status='disponible'; 1 fila =
  ganador, 0 filas = 409. Nunca read-then-write (TOCTOU).
- Pedido con varios items: una sola transacción; si un item falla, rollback que
  libera lo ya apartado. Lockear items en orden determinista (por id)
  anti-deadlock.
- get-or-create de customer: insert-or-select capturando la violación de UNIQUE.
- Todo esto DEBE tener un test de concurrencia (varios hilos, CountDownLatch)
  sobre PostgreSQL real vía Testcontainers.

## Seguridad
- Sanitizar/validar toda entrada. Nunca confiar en montos/precios del cliente:
  totales server-side desde snapshots.
- Admin: HTTP Basic + BCrypt contra admin_user. /api/v1/admin/** requiere rol
  ADMIN; GET públicos y POST /orders abiertos.
- Subida de archivos: validar content-type y tamaño; nombre único (UUID);
  proteger contra path traversal. Almacenamiento LOCAL (./uploads) — SIN AWS/nube
  (política de entorno). Mantener StorageService como abstracción.
- CORS solo para el origen del frontend. Manejo global de excepciones con forma
  consistente (code, message, context) y status correcto (400/401/404/409).

## Testing
- Lógica de negocio y endpoints cubiertos; integración con Testcontainers
  (PostgreSQL real), no H2. Correr y CITAR el resultado antes de afirmar que
  funciona.
- Aserciones robustas (comparar dinero por valor, no por escala/tipo JSON).
  Aislar tests que mutan estado (reset de BD entre ellos).
- Nota local: en esta máquina los *IT necesitan DOCKER_HOST al socket del
  contexto desktop-linux; en CI (ubuntu-latest) no hace falta.

## Puerta de calidad (correr y citar)
Antes de decir que algo funciona: ./mvnw clean verify (compila + unit + *IT con
failsafe). Citar el resultado. Si no se puede verificar, decirlo. Verificar antes
de afirmar; una afirmación sin evidencia es peor que ninguna.

## Git y PRs
- Trunk-based. Rama tipo/descripcion-kebab → PR → merge bottom-up en pilas.
  Commits en inglés, conventional (type(scope): desc), imperativo, atómicos, SIN
  prefijo de ticket (proyecto personal). Solo commitear cuando está completo y
  verificado.
- PRs pequeños de un solo concern; pila de PRs dependientes para trabajo grande,
  mergeada de abajo hacia arriba, rebasando sobre main al avanzar.
- No pushear directo a main. No --amend/force-push sobre historia publicada
  (salvo rebase con --force-with-lease en rama propia). Operaciones destructivas
  solo con permiso explícito.
- Fixes de review = commits nuevos, no amend sobre lo ya revisado.

## CI (GitHub Actions)
- actions/setup-java con Java 25 (verificar que la distribución elegida ya
  publique 25; no asumir), cache de Maven, ./mvnw clean verify.
- Dispara en push a main y en pull_request. Los runners ubuntu-latest traen
  Docker, así que Testcontainers corre sin config extra.

## Procedimiento de code-review
Alcance (diff/rama/archivo) → correr la puerta de calidad y CITARLA (roja = al
menos HIGH; build roto = BLOCKER) → barrer por áreas contra este doc
(arquitectura/slices, datos/mapeo, concurrencia, seguridad, testing, comentarios,
clean code) → clasificar BLOCKER/HIGH/MEDIUM/LOW/NOTE → verificar antes de
clasificar (leer la definición real, no asumir) → reconocer fortalezas, no solo
defectos → fix menos disruptivo consistente con los patrones existentes; no
marcar lo que ya cubre el linter/formatter.
