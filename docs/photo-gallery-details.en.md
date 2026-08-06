# Photo Gallery — Detailed Guide

> Companion to the README: features, project structure, deployment and ops in full detail.
> For the quick start, architecture diagram and core info, see [README](../README.en.md).

## 1. Features

### Photo management

- **Upload** — drag & drop / paste / batch (max 50 per batch), magic-byte validation (JPEG/PNG/GIF/BMP/WebP), client-side Canvas compression for large images (max 1920px, JPEG quality 0.85), XHR with live progress bar, SHA-256 dedup (single uploads return 409 with the existing photo; batches skip silently)
- **Browse** — virtual scrolling (constant DOM node count, smooth with tens of thousands of photos), 3D tilt cards, skeleton loading
- **Edit** — rename/description, category/tag/album assignment. `PUT /photos/{id}` optional fields follow "null = no change"; explicit clearing uses the sentinel `0` (`categoryId: 0` = clear category, consistent with the `albumId=0` "unassigned" convention; 404 if the target doesn't exist)
- **Batch operations** — multi-select, select all, batch delete, batch edit (`categoryOp` enum NONE/SET/CLEAR), batch share-link generation
- **Search** — MySQL FULLTEXT index + ngram Chinese tokenizer (bigram tokens), `MATCH ... AGAINST` boolean mode over name and description; single-character queries or non-MySQL databases (H2 etc.) automatically fall back to LIKE substring matching
- **Sorting** — by date/name/size, ascending or descending

### Image processing

- **Async processing pipeline** — upload returns immediately; EXIF extraction → auto-rotate → watermark → thumbnails (200px + 400px) → WebP run in the background. The dev environment uses the `@Async` thread pool (queue overflow drops, never synchronous in the request thread); prod switches to RabbitMQ (durable queue + 3 retries + dead-letter queue). Photo cards poll for status automatically — no manual refresh needed. Failed photos can be retried with one click (`POST /photos/{id}/retry-processing`); a recovery pass runs once at startup and every 5 minutes for photos stuck in `PROCESSING`
- **Responsive thumbnails** — upload generates 200px + 400px JPEG variants; the frontend `srcset` + `sizes` pick per viewport and DPR
- **Editor** — full-resolution Canvas rotate (any angle) / flip (horizontal/vertical) / crop
- **Watermark** — semi-transparent white text at bottom-right, font size scales with image width; font/size ratio/opacity configurable (`photo.watermark.*`)
- **WebP** — a lossy WebP copy is generated on upload; browser support detected at runtime
- **EXIF auto-rotation** — orientation corrected from the EXIF Orientation tag

### Taxonomy

- **Categories** — many-to-one, mutually exclusive
- **Tags** — many-to-many cross-marking with custom colors
- **Albums** — many-to-many grouping, cover-card grid, sort by date/name, "unassigned" auto-aggregated

### EXIF & browsing

- **EXIF extraction** — capture time, camera model, lens model, focal length, aperture, shutter speed, ISO, GPS
- **EXIF detail panel** — collapsible panel in the photo viewer, grouped into camera info / shooting parameters / time & location, bilingual
- **Timeline** — grouped by year-month, ascending/descending
- **Map** — Leaflet clustered markers, WGS-84 → GCJ-02 conversion (server-side, non-destructive), AMap satellite tiles + road overlay

### Security

- **Konami gate** — challenge-response: the frontend records keys only and never validates → `GET /api/v1/auth/challenge` issues a one-time nonce (60s TTL) → `POST /api/v1/auth/unlock` submits nonce + sequence; the sequence lives only in backend config. Keyboard + touch input
- **Brute-force protection** — per-IP rate limit on auth endpoints (10 req/s) + failure counter (Caffeine); 5 failures = 15-minute ban
- **Dual-role JWT** — admin (24h, management); share links no longer issue viewer JWTs (see below)
- **Revocable share links** — generate share links for selected photos; friends view without a password. The credential is a high-entropy random DB token (`share_tokens` table): same content reuses the same link (idempotent), one-click revoke in the dialog (`POST /api/v1/share/{token}/revoke` — the old link stops working immediately); photo whitelist + `permission` (only `view`/`download`, invalid → 400) are stored in the token record, checked per request by `JwtAuthFilter` (no caching → instant revocation). The legacy viewer JWT branch exists only for transition (old links expire naturally within 7 days, not revocable)
- **Short-lived signed image URLs** — `<img>` tags can't carry Authorization headers, so image URLs use HMAC short-lived signatures (`photoId.bucket.hmac`, 300s buckets ±1 → ~10min sliding window, key derived one-way from `JWT_SECRET`). The session JWT never appears in URL query strings (no leaks via logs/history/Referer); share responses strip signatures so they can't be borrowed to download
- **Share permission enforcement** — `/api/v1/photos/{id}/file` requires `permission=download`: `view` permission or a missing signature → 403; when a thumbnail/WebP is missing, fallback depends on role (admin falls back to the original, viewers always get 404, `w` whitelist 200/400 — blocking view-only shares from downloading originals via the fallback)
- **SHA-256 dedup** — file hash computed outside the transaction (no DB connection held); duplicates return 409 with existing photo data (single) or are skipped silently (batch)
- **Unified validation** — missing required params / type errors / invalid enums → 400 with the param name (`GlobalExceptionHandler` catch-all, never 500)
- **Caching** — Caffeine local cache in dev (30s TTL), Redis distributed cache in prod (JSON serialization + PageImpl mixin); all write operations evict affected caches
- **Soft delete + trash** — deletion sets `deleted_at` (Hibernate `@SQLRestriction` global filter) and clears the hash to allow re-upload. 5s toast undo + trash restore/permanent delete; nightly 3 AM purge of 30-day-old records
- **Backup export** — one-click download of `photo-gallery-backup-YYYY-MM-DD.zip` from the header bar: all originals + DB metadata (photos/exif/tags/categories/albums JSON with relations inlined), streamed via `java.util.zip` without holding memory (zero third-party deps), pre-generated cache + data fingerprint check (instant response when unchanged), optional filters by album/category/date range, response never cached (admin only)
- **Frontend error boundary** — component errors are caught automatically with a degraded page (retry / refresh / copy error info); auto-recovers on route change

### PWA (Progressive Web App)

- Installable to desktop / home screen (Android Chrome + iOS Safari + desktop Edge/Chrome)
- Service Worker offline caching (Workbox runtime caching):
  - Static assets: precached at build time (`globPatterns` incl. fonts)
  - Thumbnails `/api/photos/{id}/thumbnail`: CacheFirst, 7 days, max 500 entries
  - Full-size/WebP `/api/photos/{id}/file|webp`: NetworkFirst, 1 day, max 100 entries
  - API responses (photos/tags/categories/albums/timeline/map): NetworkFirst, 5 min, max 50 entries
- Web App Manifest + iOS `apple-mobile-web-app-capable` standalone window

### i18n

- Frontend `vue-i18n`, zh-CN / en-US, automatic browser-language detection, persisted in localStorage, instant switch from the header (antd strings follow)

### Misc

- Design tokens single-sourced in `theme.ts` (drives the antd ConfigProvider theme + CSS variables at runtime, light/dark follows the system); `styles/tokens.css` is the no-JS first-paint fallback
- `/actuator/health` (public; dev: DB + diskSpace, Rabbit/Redis indicators disabled to avoid false DOWN; prod: incl. RabbitMQ/Redis) + `/actuator/prometheus` (**Basic Auth**: `MONITORING_USER/MONITORING_PASSWORD`)
- Micrometer custom metrics: `photo.upload.total`, `photo.upload.bytes`, `photo.processing.total`, `photo.processing.failures`, `@Timed("photo.processing.time")` for processing duration
- Toast notifications, error boundaries (component-level ErrorBoundary + global errorHandler fallback page)
- Mobile responsive (bottom nav, centered upload button, hover degradation, map adaptation)
- Security headers: CSP (frame-ancestors 'none') / HSTS / nosniff / frame-deny
- SpringDoc `/swagger-ui.html` interactive API docs (dev only, disabled in prod)
- Client IP resolution trusts only a trusted header (prod default `Cf-Connecting-Ip`, for cloudflared tunnels; X-Forwarded-For never trusted)

## 2. Tech Stack

| Layer | Technology | Version |
|------|------|------|
| Runtime | Java | 17 |
| Backend framework | Spring Boot | 3.3.13 |
| Security | Spring Security + JWT (jjwt) | 0.12.6 |
| ORM | Spring Data JPA + Hibernate | — |
| Database | MySQL + Flyway migrations (incl. FULLTEXT index) | 8.0 |
| Caching | Spring Cache + Caffeine (dev) / Redis (prod, 7-alpine) | — |
| Message queue | RabbitMQ (prod, durable queue + 3 retries + DLQ) | 3.13 |
| Search | MySQL FULLTEXT + ngram Chinese tokenizer (token size 2) | — |
| EXIF | metadata-extractor | 2.19.0 |
| Image encoding | webp-imageio (JDK ImageIO plugin) | 0.1.6 |
| Monitoring | Micrometer + Prometheus (prom/prometheus image) | v3.2.0 |
| Health checks | Spring Boot Actuator (health + prometheus only) | — |
| API docs | SpringDoc OpenAPI (dev only, disabled in prod) | 2.5.0 |
| Frontend framework | Vue 3 (Composition API) | 3.5 |
| State management | Pinia | 3.x |
| Routing | vue-router | 4.x |
| Language | TypeScript | 6.x |
| Build | Vite | 5.4 |
| Virtual scrolling | @tanstack/vue-virtual | 3.13.31 |
| Maps | Leaflet + leaflet.markercluster | 1.9.4 / 1.5.3 |
| UI | ant-design-vue (theme tokens driven by `theme.ts`) | 4.2.6 |
| Charts | uPlot | 1.6.32 |
| i18n | vue-i18n (frontend, zh-CN / en-US) | 11.x |
| PWA | vite-plugin-pwa + Workbox | 1.3 |
| Code quality | ESLint 10 + Prettier 3 + SpotBugs + JaCoCo (coverage ≥35%) | — |
| Deployment | Docker Compose / Nginx reverse proxy | — |

## 3. Project Structure

```
photo-gallery/
├── backend/
│   ├── src/main/java/com/hape/photogallery/
│   │   ├── PhotoGalleryApplication.java        # @SpringBootApplication + @EnableCaching
│   │   ├── ApiResponse.java                    # Unified response body {code, message, data}
│   │   ├── controller/
│   │   │   ├── AuthController.java             # challenge/unlock + share generate/revoke
│   │   │   ├── ShareController.java            # Share landing page + view API
│   │   │   ├── PhotoController.java            # Photo REST API (upload/edit/search/retry/migration endpoints)
│   │   │   ├── TagController.java              # Tag CRUD
│   │   │   ├── CategoryController.java         # Category CRUD
│   │   │   ├── AlbumController.java            # Album CRUD
│   │   │   ├── TrashController.java            # Trash API
│   │   │   ├── BackupController.java           # Backup export (POST /api/v1/backup/export, zip + pre-generated cache)
│   │   │   └── StatsController.java            # Stats panel (GET /api/v1/stats)
│   │   ├── service/
│   │   │   ├── PhotoService.java               # Write-path core (upload/edit/soft-delete/batch/dedup/processing recovery)
│   │   │   ├── PhotoQueryService.java          # Queries/search/timeline/map/DTO mapping (split in P2-#15)
│   │   │   ├── TrashService.java               # Trash (restore/permanent delete/daily 3 AM purge of 30-day-old records)
│   │   │   ├── FullTextProbe.java              # FULLTEXT availability probe (falls back to LIKE when unavailable)
│   │   │   ├── PhotoProcessor.java             # Image pipeline (EXIF→rotate→watermark→thumbnails→WebP), plain JDK image ops
│   │   │   ├── PhotoTransformService.java      # Image transforms (transaction boundary + failure compensation)
│   │   │   ├── FilePathResolver.java           # Path resolution / artifact paths / physical delete
│   │   │   ├── MigrationService.java           # Data migration (thumbnail/WebP backfill, EXIF backfill; paged cursor + idempotent)
│   │   │   ├── AuthService.java                # Unlock validation / share issuance (thin controllers)
│   │   │   ├── StatsService.java               # Stats aggregation (4 queries + 30s cache)
│   │   │   ├── BackupScheduler.java            # Pre-generated backup scheduler (default daily 3:05)
│   │   │   ├── TagService.java                 # Tag service
│   │   │   ├── CategoryService.java            # Category service
│   │   │   ├── AlbumService.java               # Album service
│   │   │   ├── AsyncImageProcessor.java        # dev: @Async image-processing sender (delegates to PhotoProcessor)
│   │   │   ├── ImageProcessingService.java     # Thumbnails (multi-size)/WebP/watermark/rotate/flip/magic-byte validation
│   │   │   ├── ExifService.java                # metadata-extractor integration
│   │   │   ├── StorageService.java             # Storage interface (extensible backends)
│   │   │   ├── LocalStorageService.java        # Local filesystem storage (path-traversal protection)
│   │   │   └── BackupService.java              # Backup export (metadata collection in transaction + streamed zip + cache/fingerprint)
│   │   ├── messaging/
│   │   │   ├── ProcessingMessageSender.java    # Processing-message sender interface
│   │   │   ├── ProcessingMessage.java          # Message POJO (photoId/path/watermark)
│   │   │   ├── RabbitProcessingSender.java     # prod: RabbitMQ sender implementation
│   │   │   └── PhotoProcessingConsumer.java    # RabbitMQ consumer (3 retries → DLQ)
│   │   ├── repository/
│   │   │   ├── PhotoRepository.java            # JPQL paging + filters + FULLTEXT search
│   │   │   ├── ShareTokenRepository.java       # Share-token lookup (per-request DB query → instant revocation)
│   │   │   ├── TagRepository.java
│   │   │   ├── CategoryRepository.java
│   │   │   ├── AlbumRepository.java
│   │   │   └── ExifDataRepository.java
│   │   ├── entity/
│   │   │   ├── Photo.java                      # Photo entity (soft-deleted, LAZY associations + @BatchSize)
│   │   │   ├── ProcessingStatus.java           # Processing status enum (PROCESSING/DONE/FAILED)
│   │   │   ├── Tag.java                        # Tag entity
│   │   │   ├── Category.java                   # Category entity
│   │   │   ├── Album.java                      # Album entity (soft-deleted)
│   │   │   ├── ExifData.java                   # EXIF metadata entity (OneToOne)
│   │   │   └── ShareToken.java                 # Share credential (unique token + photoIds whitelist + permission + expiry/revoke)
│   │   ├── dto/
│   │   │   ├── PhotoResponse.java              # Photo response DTO
│   │   │   ├── PhotoUpdateRequest.java         # Update request body (null = no change / 0 = clear)
│   │   │   ├── TimelineItem.java               # Timeline item
│   │   │   ├── MapItem.java                    # Map item (incl. GCJ-02 coordinates)
│   │   │   ├── ShareGenerateRequest.java       # Share-link generation request (permission only view/download)
│   │   │   ├── TransformRequest.java           # Image transform parameters
│   │   │   ├── BackupExportRequest.java        # Backup export filter parameters
│   │   │   ├── AlbumRequest.java               # Album create/update request
│   │   │   ├── AlbumResponse.java              # Album response DTO (incl. photoCount/mediaToken)
│   │   │   ├── BatchPhotoUpdateRequest.java    # Batch edit request (add/remove + categoryOp tri-state)
│   │   │   ├── StatsResponse.java              # Stats panel response
│   │   │   └── UploadParams.java               # Upload parameter wrapper (record)
│   │   ├── config/
│   │   │   ├── AsyncConfig.java                # @EnableAsync + image-processing thread pool (DiscardPolicy) + MDC propagation
│   │   │   ├── RedisConfig.java                # Redis cache config (JSON serialization + PageImpl mixin)
│   │   │   ├── RabbitMQConfig.java             # Queue/Exchange/DLQ topology + MANUAL ack
│   │   │   ├── SecurityConfig.java             # SecurityFilterChain + CORS whitelist + CSP
│   │   │   ├── JwtService.java                 # HS256 JWT issuance (admin) & validation (≥32-byte key check)
│   │   │   ├── MediaSignatureService.java      # Short-lived image signatures (HMAC time buckets, JWT never in URL)
│   │   │   ├── ClientIpResolver.java           # Trusted-header IP resolution (Cf-Connecting-Ip; XFF never trusted)
│   │   │   ├── ProdSecurityValidator.java      # prod startup hard check (Redis/Rabbit passwords non-blank)
│   │   │   ├── JwtAuthFilter.java              # OncePerRequestFilter + image signature first / JWT whitelist fallback
│   │   │   ├── RateLimitFilter.java            # Auth-endpoint rate limit (Caffeine, 10 req/s/IP)
│   │   │   ├── NonceStore.java                 # One-time nonce (Caffeine 60s TTL)
│   │   │   ├── FailedAttemptStore.java         # Per-IP failure counter (5 failures → 15-min ban)
│   │   │   ├── TraceIdFilter.java              # End-to-end traceId (MDC + response header)
│   │   │   └── CacheControlFilter.java         # Global Cache-Control headers
│   │   ├── exception/
│   │   │   ├── BusinessException.java          # Business exception
│   │   │   ├── DuplicateException.java         # Duplicate-file exception (409)
│   │   │   ├── FileSizeExceededException.java  # File-size exception
│   │   │   ├── InvalidFileTypeException.java   # File-format exception
│   │   │   └── GlobalExceptionHandler.java     # @RestControllerAdvice
│   │   └── util/
│   │       └── CoordUtil.java                  # WGS-84 → GCJ-02 conversion
│   ├── src/main/resources/
│   │   ├── application.properties              # Shared config (port/upload dir/watermark/Konami sequence; default dev profile)
│   │   ├── application-dev.yml                 # Dev (ddl-auto: update; Rabbit/Redis health indicators disabled)
│   │   ├── application-prod.yml                # Prod (Redis/RabbitMQ/rate-limit trusted header; ddl-auto: validate)
│   │   ├── application-local.yml.example       # Local secrets template (password/JWT secret, gitignored)
│   │   ├── logback-spring.xml                  # Console + daily rolling files (30/90-day retention)
│   │   ├── db/migration/                       # Flyway migrations V1–V10 (incl. file_hash dedup + FULLTEXT/ngram index + share_tokens)
│   │   └── static/                             # Frontend build output (SPA, copied by npm run build, not committed)
│   ├── Dockerfile                              # JRE 17 Alpine + WenQuanYi font + curl
│   └── pom.xml                                 # Maven config (JaCoCo ≥35% + SpotBugs gate)
│
├── frontend/
│   ├── vite.config.js                          # Vite + PWA(Workbox) + manual chunks (dist copy done by scripts/copy-dist.mjs at the end of build)
│   ├── vitest.config.ts                        # Vitest (happy-dom + coverage)
│   ├── playwright.config.ts                    # Playwright E2E
│   ├── tsconfig.json                           # TypeScript strict mode
│   ├── index.html                              # Entry + iOS PWA meta tags
│   ├── public/
│   │   └── pwa-icon.svg                        # PWA icon
│   ├── scripts/
│   │   ├── copy-dist.mjs                       # Copies build output (dist → backend static; runs automatically in npm run build)
│   │   ├── verify.mjs                          # Objective design verification (tokens/contrast/responsive, Playwright-driven)
│   │   └── capture.mjs                         # Screenshot capture (pages/light-dark themes, output in frontend/.shots)
│   ├── e2e/                                    # Playwright E2E specs (gate/core flows/albums/sort/search-trash, 5 specs)
│   └── src/
│       ├── main.ts                             # Entry (Pinia + Router + i18n + global error handling)
│       ├── App.vue                             # Root component (error boundary + RouterView + language switch)
│       ├── theme.ts                            # Design-token single source (antd theme + CSS variables)
│       ├── api.ts                              # fetch wrapper + JWT injection + short-lived signature joining
│       ├── i18n.ts                             # vue-i18n config (browser-language detection + localStorage)
│       ├── upload.ts                           # Client-side compression + XHR progress upload
│       ├── router/index.ts                     # Vue Router (AppShell child routes + 404 fallback redirect)
│       ├── styles/
│       │   ├── tokens.css                      # Design-token static fallback (overridden at runtime by theme.ts)
│       │   └── base.css                        # Reset / typography / focus / a11y / scrollbars
│       ├── stores/                             # photo / ui / toast / data (Pinia)
│       ├── types/                              # TypeScript type definitions
│       ├── locales/                            # zh-CN / en-US language files
│       ├── utils/                              # token / format / error / escape / clipboard / logger / webp
│       ├── composables/                        # usePhotoActions / useUrlState / useInfinitePagination / useProcessingPolling / useViewerControls / useKeyboardShortcuts / useImageEditorCanvas
│       ├── layouts/
│       │   └── AppShell.vue                    # Main layout (Konami gate / topbar / router outlet)
│       └── components/
│           ├── auth/                           # KonamiGate (arcade unlock screen) + ArcadePanel
│           ├── gallery/                        # PhotosView + PhotoGrid (virtual scroll) + PhotoTile + GridSkeleton + SelectionBar
│           ├── viewer/                         # PhotoViewer + ViewerStage + ViewerBottom + ExifPanel
│           ├── upload/                         # UploadDrawer (drag & drop / paste / compress / dedup)
│           ├── editor/                         # ImageEditor + EditorToolbar + PhotoEditDrawer + BatchEditDrawer
│           ├── albums/                         # AlbumsView + AlbumDetail + AlbumEditDrawer
│           ├── timeline/                       # TimelineView
│           ├── map/                            # MapView (Leaflet clusters + AMap tiles)
│           ├── stats/                          # StatsView + useTrendChart (uPlot)
│           ├── trash/                          # TrashView
│           ├── share/                          # ShareViewer (public landing page)
│           ├── topbar/                         # TopBar + ModeTabs + MobileTabBar + FilterPanel + FilterPanelContent + CornerMenu + HelpModal
│           └── common/                         # ErrorBoundary + ToastStack + ShareDialog + EmptyState
│
├── prometheus/
│   ├── prometheus.yml                          # Prometheus scrape config (15s, target app:8080 + rabbitmq:15692)
│   └── grafana-provisioning/                   # Grafana provisioning (datasource/dashboard/alert auto-loaded)
│       ├── datasources/                        # Prometheus datasource (fixed uid prometheus-main)
│       ├── dashboards/                         # photo-gallery.json dashboard (JVM/HTTP/upload/queue)
│       └── alerting/                           # Alert rules (App down / 5xx rate, single-track)
├── scripts/                                    # Build/dev scripts (runnable from anywhere)
│   ├── build-docker.sh / .ps1                  # One-command Docker build + start
│   ├── build-traditional.sh / .ps1             # One-command traditional JAR build (frontend embedded)
│   ├── dev-start.sh / .ps1                     # One-command dev environment (backend + frontend)
│   └── k6/                                     # k6 load-test scripts (smoke/upload/photo-list/share, 4 scenarios)
├── .env.example                                # Docker Compose environment-variable template
├── .github/workflows/ci.yml                    # CI: backend / frontend / docker / e2e, four parallel jobs
└── docker-compose.yml                          # App + MySQL + Redis + RabbitMQ + Prometheus + Grafana
```

## 4. Local Quick Start

### Prerequisites

Java 17+ / Maven 3.6+ / Node.js 18+ / MySQL 8.0+

### 1. Create the database

```sql
CREATE DATABASE IF NOT EXISTS photodb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 2. Configure local secrets

Sensitive config (DB password, JWT secret) lives in `application-local.yml`, which is gitignored:

```bash
cd backend/src/main/resources

# Copy the template (only once)
cp application-local.yml.example application-local.yml

# Edit application-local.yml with your local MySQL password
# If the password contains special characters (@ ` ? { } etc.), wrap it in quotes, e.g. DB_PASSWORD: "@my-p@ss"
```

Template defaults:

```yaml
DB_PASSWORD: "your-password-here"
JWT_SECRET: dev-secret-do-not-use-in-production
```

`application-dev.yml` loads this file automatically via `spring.config.import: optional:classpath:application-local.yml` (skipped if missing, falls back to environment variables).

> Environment variables still work for CI/CD and take precedence over config files.

### 3. Start the backend

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

The backend runs at `http://localhost:8080`; Flyway creates/migrates the schema on first startup. The dev profile uses the Caffeine local cache + `@Async` thread pool for image processing — no Redis/RabbitMQ required (to test them locally, uncomment the corresponding sections in `application-local.yml` to override the defaults).

### 4. Start the frontend

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173` in your browser.

### 5. First use

1. Open the page → Konami arcade gate
2. Enter **`↑ ↑ ↓ ↓ ← → ← → B A B A`** (keyboard arrows + letters, or tap the virtual buttons)
3. After the backend challenge-response validation succeeds, a 24h admin JWT is issued
4. You're in — start uploading photos

## 5. Building

### One-command builds

```bash
./scripts/build-traditional.sh     # Traditional JAR (frontend embedded, for Nginx + JAR deploys)
./scripts/build-docker.sh          # Docker image + docker compose start
```

The build scripts automatically: install dependencies (only if `node_modules` is missing, via `npm ci`) → `npm run build` (vue-tsc type-check + build + auto-copy to `backend/static`) → `mvn clean package -DskipTests` → (Docker version) `docker compose up -d --build`. Windows users can use the `.ps1` counterparts.

One-command dev environment (backend :8080 + frontend :5173, Ctrl+C stops everything and cleans up any leftover process on port 8080):

```bash
./scripts/dev-start.sh     # or Windows: ./scripts/dev-start.ps1
```

> All scripts locate the project root by walking up until `frontend/package.json` is found, so they can be invoked from anywhere.

### Manual build

```bash
# Frontend (vue-tsc type-check + build + auto-copy to backend/src/main/resources/static/)
cd frontend
npm ci && npm run build

# Backend
cd ../backend
mvn clean package -DskipTests
```

> The dist copy is handled by `frontend/scripts/copy-dist.mjs` (clears the target first, then copies, so stale hashed files never linger) — no manual `cp` needed.

## 6. Deployment

### Docker Compose

#### 1. Create .env

```bash
cp .env.example .env   # then edit in your passwords
```

Variables in `.env.example`:

```bash
MYSQL_ROOT_PASSWORD=your-mysql-password      # MySQL container root password
MYSQL_DATABASE=photodb
DB_HOST=mysql
DB_USERNAME=root
DB_PASSWORD=${MYSQL_ROOT_PASSWORD}
JWT_SECRET=$(openssl rand -base64 32)        # must be replaced in production
REDIS_PASSWORD=your-redis-password           # Redis password (prod startup requires non-blank)
RABBIT_USER=your-rabbit-user                 # RabbitMQ username (must NOT be the default "admin")
RABBIT_PASS=your-rabbit-pass                 # RabbitMQ password (prod startup requires Redis/Rabbit passwords non-blank)
MONITORING_USER=your-monitor-user            # /actuator/prometheus Basic Auth username (prod requires non-blank)
MONITORING_PASSWORD=your-monitor-pass        # Same, password (referenced via ${} in prometheus.yml basic_auth; keep in sync)
```

#### 2. Build and start

```bash
# From the project root
docker compose up -d --build
```

This starts 6 services: `app` (Spring Boot, prod profile), `mysql`, `redis`, `rabbitmq`, `prometheus`, `grafana` (dashboards/alerts provisioning auto-loaded). Inside Compose, `app` always runs the prod profile (Redis cache + RabbitMQ processing); local development (outside Docker) needs only MySQL — the dev profile uses Caffeine + the `@Async` thread pool.

Access `http://localhost:8080` (bound to 127.0.0.1 only; expose it via Nginx or cloudflared).

#### 3. Container resources

| Container | Memory limit | Notes |
|------|---------|------|
| App | 768M | JVM heap 448M + Metaspace 128M (G1GC) |
| MySQL | 512M | 8.0, InnoDB buffer pool 128M |
| Redis | 256M | 7-alpine, maxmemory 128M + allkeys-lru + AOF |
| RabbitMQ | 256M | 3.13 management-alpine (built-in Prometheus plugin, 15692 accessible only inside the container network) |
| Prometheus | 128M | v3.2.0, 15-day retention, scrape every 15s |
| Grafana | 256M | 13.1.0, bound to 127.0.0.1:3000 only |

All containers have Docker healthchecks (`restart: always` restarts containers that exit abnormally). Total memory limits ≈ 1.9GB — on a 2GB server, consider stopping other local services.

#### 4. Common commands

```bash
docker compose ps              # Status
docker compose logs -f app     # Follow app logs
docker compose restart app     # Restart the app
docker compose down            # Stop
```

### Nginx reverse proxy (HTTPS recommended)

```nginx
server {
    listen 80;
    server_name your-domain;
    client_max_body_size 20m;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

```bash
nginx -t && systemctl reload nginx
certbot --nginx -d your-domain   # Free SSL
```

> Client IP resolution (rate limit/ban) trusts `Cf-Connecting-Ip` by default (cloudflared tunnels). If you switch to Nginx, change `security.trusted-proxy-header` to `X-Real-IP` in `application-prod.yml`, otherwise the real client IP won't be seen.

## 7. PWA Installation

Once deployed to HTTPS, the browser shows an install button:

| Platform | How to install |
|------|---------|
| Android Chrome | "Install" banner in the address bar |
| iOS Safari | Share button → "Add to Home Screen" |
| Desktop Chrome/Edge | Install icon at the right of the address bar |

Icon: edit `frontend/public/pwa-icon.svg` and rebuild.

## 8. Language Switching

Browser-language auto-detection (`navigator.language`):

| Browser language | UI language |
|-----------|---------|
| `zh-*` | 简体中文 |
| Other | English |

Manual switch: `localStorage.setItem('locale', 'en-US')` or `'zh-CN'`, then refresh.

## 9. Authentication

```
┌──────────────────────────────────────────────────────────┐
│  Konami unlock (challenge-response)                       │
│    → frontend records keys only (sequence never in client)│
│    → GET /api/v1/auth/challenge → one-time nonce (60s)   │
│    → POST /api/v1/auth/unlock {nonce, keys}               │
│    → backend validates sequence + nonce (single-use)      │
│    → auth endpoints rate-limited (challenge+unlock,       │
│      10/s/IP) + failure counter                           │
│    → issues 24h admin JWT (role: admin)                   │
│                                                           │
│  Share links                                              │
│    → POST /api/v1/share/generate {photoIds, permission}  │
│    → high-entropy token persisted (share_tokens,          │
│      idempotent reuse)                                    │
│    → /share/{token} → ShareViewer landing page            │
│    → JwtAuthFilter validates per request (instant revoke) │
└──────────────────────────────────────────────────────────┘
```

| Entry | Credential | Permissions |
|------|-----------|------|
| Konami unlock | `role: admin` JWT | Upload, edit, delete, generate share links, manage categories/tags/albums |
| Share link | DB token (`view`/`download`) | View only the photos in the token's whitelist |

| Request | Permissions |
|------|------|
| `GET /api/v1/share/view` | `ROLE_admin` or share token (returns only whitelisted photos) |
| `GET /api/v1/photos/{id}/thumbnail\|webp\|file` | Short-lived image signature takes precedence (403 if invalid/photoId mismatch); without a signature, falls back to `ROLE_admin` or viewer (viewer requires the ID in the whitelist; `/file` additionally requires `permission=download`) |
| `GET /api/v1/**` (other) | `ROLE_admin` (viewers can't access lists, timeline, map, etc.) |
| `POST /api/v1/backup/export` | `ROLE_admin` (streamed zip download, never cached) |
| `POST/PUT/DELETE /api/v1/**` (other) | `ROLE_admin` |
| `GET /api/v1/auth/challenge`, `POST /api/v1/auth/unlock` | Public (auth endpoints rate-limited 10/s/IP; unlock additionally bans 15 min after 5 failures) |
| `GET /share/**` | Public (forwards to the SPA landing page) |
| `/actuator/health` | Public |
| `/actuator/prometheus` | Basic Auth (`MONITORING_USER/MONITORING_PASSWORD`, MONITOR role) |
| `/swagger-ui/**`, `/v3/api-docs/**` | Public (dev only; springdoc disabled in prod) |
| Static assets | Public |

## 10. Health Checks & Monitoring

```
GET /actuator/health
→ {"status":"UP","components":{"db":{"status":"UP"},"diskSpace":{"status":"UP"}}}   # dev; prod also includes redis/rabbitmq

GET /actuator/prometheus   # Basic Auth required (MONITORING_USER/MONITORING_PASSWORD)
→ # HELP photo_upload_total ...
→ # HELP photo_processing_time_seconds ...
```

In Docker, the `app` container's healthcheck curls `/actuator/health` every 15 seconds (3 retries, 45s startup grace); failures mark it unhealthy. `restart: always` restarts the container if the process exits/crashes.

**Monitoring dashboards**: Prometheus scrapes `/actuator/prometheus` every 15s (`prometheus/prometheus.yml`, Basic Auth) and `rabbitmq:15692` (RabbitMQ built-in Prometheus plugin, enabled by default since 3.8, port not exposed to the host). After `docker compose up -d grafana`, visit `http://127.0.0.1:3000` (admin password = `GF_SECURITY_ADMIN_PASSWORD` in `.env`, anonymous login disabled) — the datasource, the `Photo Gallery` dashboard (JVM memory / HTTP request rate / upload total / RabbitMQ queue depth) and alert rules (App down, 5xx rate) are auto-loaded from `prometheus/grafana-provisioning/`, no manual setup needed. Alert notification channels are not connected (single-user self-host; rules are in place, wire a webhook in the Grafana UI as needed); if memory is tight, `docker compose stop grafana` anytime.

## 11. Backup & Restore

### In-app backup export (recommended)

After unlocking, click the **⤓** button on the right of the header bar to download `photo-gallery-backup-YYYY-MM-DD.zip`, containing all original photos and DB metadata:

```
photo-gallery-backup-2026-08-02.zip
├── database/                 # DB metadata (JSON, viewable/restorable offline)
│   ├── metadata.json         #   export version, time, photo count, filter params
│   ├── photos.json           #   photos + category/tag/album relations
│   ├── exif.json             #   EXIF shooting info
│   ├── tags.json / categories.json / albums.json
└── photos/                   # original photo files, preserving the server directory layout
    └── 2024/01/uuid_xxx.jpg
```

API: `POST /api/v1/backup/export` (admin only), optional JSON body to filter:

```bash
curl -X POST http://localhost:8080/api/v1/backup/export \
  -H "Authorization: Bearer $JWT" -H "Content-Type: application/json" \
  -d '{"albumId":3,"categoryId":5,"dateFrom":"2026-01-01","dateTo":"2026-07-31"}'
```

- `albumId=0` means "photos not in any album"; omitted fields are unlimited
- Streamed zip — no server memory spike; `Cache-Control: no-store` forbids caching
- A full backup zip is pre-generated daily at 3:05 (`backup.auto-cron`); on export, the data fingerprint (photo add/delete, tag/album/category counts) is compared — if unchanged, the cached zip is served instantly instead of packing on the fly

### Manual backup script

> The app does **not** bundle a mysqldump scheduler (the app-level BackupScheduler already covers the daily pre-generation + fingerprint cache). This section is a manual example for reference. For the database layer, schedule periodic `mysqldump` (the app export doesn't include the raw table schema):

```bash
#!/bin/bash
# Manual backup (Docker Compose deploy; the MySQL container is named photodb)
BACKUP_DIR="/tmp/photo-backup-$(date +%Y%m%d)"
mkdir -p "$BACKUP_DIR"
docker exec photodb mysqldump -u root -p"$MYSQL_ROOT_PASSWORD" photodb \
  | gzip > "$BACKUP_DIR/database.sql.gz"
cp -r /data/photo-uploads "$BACKUP_DIR/photos"
tar -czf "photo-backup-$(date +%Y%m%d).tar.gz" -C "$BACKUP_DIR" .
rm -rf "$BACKUP_DIR"
echo "Backup complete: photo-backup-$(date +%Y%m%d).tar.gz"
```

To restore: unpack, restore the photo files to `photo.upload-dir` (inside the prod container: `/data/photo-uploads`), then import the DB dump:

```bash
docker exec -i photodb mysql -u root -p"$MYSQL_ROOT_PASSWORD" photodb < database.sql
```

## 12. Testing

### Backend (JUnit + Spring Boot Test, H2 in-memory)

```bash
cd backend
mvn test                       # All unit tests
mvn test -Dtest=PhotoServiceTest   # Single test class
mvn spotbugs:check             # SpotBugs static analysis (Max effort / Low threshold)
```

- `maven-surefire-plugin` injects a test `JWT_SECRET` environment variable automatically
- JaCoCo gate: minimum 35% instruction coverage (report at `backend/target/site/jacoco/index.html`)

### Frontend (Vitest)

```bash
cd frontend
npm run type-check             # vue-tsc --noEmit
npm test                       # Vitest unit tests (happy-dom + @pinia/testing)
npm run lint                   # ESLint
npm run format:check           # Prettier check
```

### E2E (Playwright, requires the backend running)

```bash
cd frontend
npm run test:e2e               # 5 specs: unlock gate / core flows / albums / sort / search-filter-trash
```

`frontend/scripts/verify.mjs` additionally provides objective design verification (design tokens / WCAG AA contrast / responsive breakpoints) as a regression substitute when there's no visual review channel.

### CI (`.github/workflows/ci.yml`)

A four-job pipeline on push/PR to main (`frontend` → `backend` → `docker`/`e2e`; the first two are serial, `docker` and `e2e` run in parallel):

1. **frontend** — `npm install --no-audit` + `npm run test:coverage` (so the vitest thresholds take effect) + `npm run lint` + `npm run build`, uploads the `frontend-dist` artifact
2. **backend** — downloads `frontend-dist` and runs `node frontend/scripts/copy-dist.mjs` (reusing the frontend build for the final JAR) → `mvn test` + `spotbugs:check` + `mvn package -DskipTests`, uploads the `backend-jar` artifact
3. **docker** — downloads the JAR and `docker build`s directly (no rebuild)
4. **e2e** — downloads the JAR → starts the backend on H2 (Flyway off, Rabbit/Redis autoconfig excluded) → full Playwright E2E suite

### k6 load testing

`scripts/k6/` provides 4 scenarios (smoke / photo-list / upload / share); real results are recorded in [BENCHMARK.md](../BENCHMARK.md):

```bash
k6 run scripts/k6/photo-list.js                    # default MODE=hit (cache-hit state); MODE variants below
k6 run --env MODE=miss scripts/k6/photo-list.js    # random page 0..499 (mixed state within 30s TTL)
k6 run --env MODE=deep scripts/k6/photo-list.js    # random deep page 400..499 (OFFSET 8000~10000)
k6 run --env MODE=search scripts/k6/photo-list.js  # q=风景 FULLTEXT (uncached endpoint, always cache-miss)
k6 run scripts/k6/upload.js        # 5 VU × 100 iterations (seeded real photos, write throughput, no preseed needed)
k6 run scripts/k6/smoke.js
k6 run scripts/k6/share.js
```

**Seeding & cache-miss methodology** (measured 2026-08-06):

- **Seeding**: `scripts/k6/seed-10000.sql` — SQL-direct insert of 10k photos (+EXIF/tag/album/category relations, `k6-` prefix for cleanup). The list endpoint never touches files, so SQL seeding is sufficient for query/cache-path testing; real upload throughput is covered by the upload scenario
- **Measure cache-miss separately**: the list `@Cacheable` key space is bounded (10k ÷ 20 = 500 keys), so random pages fill every key in the first second and cache-miss share is <1% over 60s — measure cold-cache latency with `redis-cli FLUSHDB` + single-concurrency first-request (measured: 10k-row cold cache-miss p95 ≈ 32ms)
- **Measured**: cache-hit p95 7.96ms (20 VU × 60s, 192k requests) / cold cache-miss 32ms / deep pagination identical to shallow / search 20-concurrency 297.5ms near the 300ms threshold; the benchmark run also surfaced and fixed a prod-only Redis serialization 500 (PhotoResponse entity leakage → DTOs; see doc/photo-gallery-改进方案问题记录.md §2.8/Appendix O)

## 13. Engineering Conventions

- **Git hooks (Husky)**: `pre-commit` runs lint-staged (`*.{ts,vue}` → Prettier + ESLint --fix; `*.css` / `*.{json,md}` → Prettier only); `commit-msg` runs commitlint — commit messages must follow Conventional Commits (`feat`/`fix`/`docs`/`style`/`refactor`/`perf`/`test`/`chore`/`ci`/`build`)
- **Script conventions**: all build/dev scripts live in `scripts/` (bash + PowerShell pairs), auto-locate the project root, runnable from anywhere; the frontend dist copy is handled by `frontend/scripts/copy-dist.mjs`
- **Build artifacts never committed**: `backend/src/main/resources/static/`, `frontend/dist/`, `backend/target/`, `node_modules/` are all gitignored; CI and local builds always produce them from source
