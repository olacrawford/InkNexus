# Repository Guidelines

## Project Structure & Module Organization

The Git root is this directory. The project is named 墨枢 InkNexus; Maven modules, Java packages (`com.inknexus`), SQL, and frontend storage keys all use the `inknexus` naming.

- `InkNexus/`: Spring Cloud Alibaba backend:
  - `inknexus-common`: shared `Result`, `PageResult`, errors, and exceptions.
  - `inknexus-auth` (8060): registration, login, JWT, and user address management.
  - `inknexus-book` (8070): books, categories, pagination, Redis, and Sentinel. Cache protections live in `BookDetailCache` (mutex rebuild via SETNX + Lua unlock, null-mark for penetration) and `JitterRedisCacheWriter` (±10% TTL jitter for avalanche).
  - `inknexus-cart` (8083): cart items with quantity/selection and Feign validation against the book service.
  - `inknexus-stock` (8090): book stock queries, order-time reservation, payment confirmation, and cancellation release.
  - `inknexus-order` (8050): direct/cart orders, paid-stock confirmation, timeout close, and OpenFeign calls to book, cart, and stock services.
  - `inknexus-payment` (8051): mock payment records and order paid-state updates via OpenFeign.
  - `inknexus-gateway` (8080): routing, JWT validation, and `X-User-Id` / `X-User-Role` passthrough (book write endpoints require the ADMIN role from the JWT `role` claim); generates/passes `X-Trace-Id` for cross-service log correlation (services write it to MDC via `com.inknexus.common.trace.TraceIdFilter`).
  - `inknexus-ai` (8071): read-only chatbot using LangChain4j + DashScope (Qwen); SSE streaming via `POST /ai/chat/stream` (`TokenStream` + `SseEmitter`, `/ai/chat` is the fallback); session memory in Redis; calls book/order via OpenFeign.
  - Async flow via RabbitMQ: `inknexus-payment` publishes pay-success events; `inknexus-order` consumes them to mark orders paid; `inknexus-order` and `inknexus-stock` exchange stock confirm/release events; order-timeout close runs on a RabbitMQ TTL+dead-letter delay queue declared in `inknexus-order`'s `RabbitMqConfig` (queue-level TTL = `expire-minutes`; `OrderTimeoutTask` is only a backstop sweep). Producers/consumers live in each module's `mq` package. Stock confirm/release consumption is idempotent per message `eventId` via `t_mq_consumed_log` (dedup insert and stock update share one transaction). All business queues carry dead-letter args into `inknexus.dlx.queue` after listener retry exhaustion (`default-requeue-rejected: false`), and publishers register confirm/returns callbacks in each module's `RabbitReliabilityConfig`.
- REST APIs are documented with Knife4j (springdoc) in each service.
- `front/`: Vue 3 + Vite app under `front/src/` — axios wrapper and endpoints in `src/api/` (`http.js`, `inknexus.js`, `result.js` for unwrapping `Result`), session helpers in `src/utils/`, views in `src/views/` (Home, Books, Cart, Orders, Address, Login, AiChat).
- `sql/sql.txt`: complete MySQL schema and seed data, including all 10 tables.
- `sql/updates/`: numbered incremental SQL scripts (`001_*.sql` …) for existing environments.
- `nacos-config/`: per-service config and `publish.sh`.
- `docker-compose.infra.yml`: local MySQL, Nacos, Redis, and RabbitMQ for macOS / Docker Desktop.
- `docker-compose.nginx.yml`: builds `front/` via its Dockerfile and serves the SPA through nginx on port 80; see `说明文档/InkNexus-Nginx部署说明.md`. Note `front/package.json`'s `build:docker` uses Windows `copy` — on macOS run `npm run build` instead.
- `scripts/dev-macos.sh`: optional macOS bootstrap for infra + Nacos config publishing.
- `benchmark/`: JMeter plans (`book-list.jmx` cache cold/warm comparison, `order-create.jmx` write path) driven by `-J` properties; fill results into `说明文档/InkNexus-压测报告.md`.
- `说明文档/`: detailed module and deployment docs.

## Build, Test, and Development Commands

Use `mvn -f InkNexus/pom.xml -q clean package` to compile all backend modules, and `mvn -f InkNexus/pom.xml -q test` to run backend tests. Run `mvn -f InkNexus/pom.xml -DskipTests install` once, then start one service with `mvn -f InkNexus/pom.xml -pl inknexus-auth spring-boot:run`; replace the module name as needed. Do not use `-am` with `spring-boot:run`, because it also runs the parent POM, which has no main class. Start auth, book, cart, stock, order, and payment before the gateway; `inknexus-ai` is independent and can run after the gateway.

`inknexus-ai` needs the DashScope API key as `DASHSCOPE_API_KEY` env var (never commit it); its Spring config lives in `nacos-config/ai-assistant.yaml`.

On Apple Silicon, the gateway gets the correct Netty native library through the `macos-arm64` Maven profile in `inknexus-gateway/pom.xml`.

When starting `inknexus-book`, if Sentinel cannot write its default log directory, point it into the workspace:

```bash
mkdir -p logs/sentinel
mvn -f InkNexus/pom.xml -pl inknexus-book spring-boot:run "-Dspring-boot.run.jvmArguments=-Dcsp.sentinel.log.dir=${PWD}/logs/sentinel"
```

For frontend, run `cd front && npm install && npm run dev` to start Vue at `http://localhost:5173`, or `npm run build` to produce the build. Apply database changes by running `sql/sql.txt` and `sql/updates/*.sql` against MySQL. Publish config changes with `cd nacos-config && bash publish.sh`.

## Coding Style & Naming Conventions

Backend: use Java 17, UTF-8, and 4-space indentation. Follow existing packages: `controller`, `service`, `service.impl`, `mapper`, `entity`, `dto`, `vo`, `config`, `client`, `filter`, `util`. Name classes by role, like `BookCreateRequest` or `AuthGlobalFilter`. Keep controllers thin, validate DTOs, return `Result<T>` / `PageResult<T>`, and throw `BusinessException` with `ErrorCode`. No linter is configured; match the surrounding code.

Frontend: keep requests in `front/src/api/`, views in `front/src/views/`, and use the existing Vue 3 SFC style. New database changes go in `sql/updates/` and align with backend entities and mappers. For new environments, `sql/sql.txt` is the complete initialization script.

## Testing Guidelines

All backend modules have JUnit 5 + Mockito unit tests under `InkNexus/<module>/src/test/java/com/inknexus/<module>`; they are plain unit tests (MockitoExtension, mocked mappers/Feign clients/MQ publishers) and do not start a Spring context or require infra. Add new tests there, name them `method_expectedBehavior_whenCondition`, e.g. `createOrder_snapshotPrice_whenBookExists`. Run targeted tests with `mvn -f InkNexus/pom.xml -pl <module> -am test`. No frontend test runner is configured; verify with `npm run dev`.

## Commit & Pull Request Guidelines

Use scoped Conventional Commits like `feat(auth)`, `docs`, or `chore`. Keep subjects lowercase and focused. In PRs, describe changes, show verification steps, and reference related issues. Include related backend, frontend, SQL, and config changes for cross-layer features.

## Documentation Sync

Update `README.md`, `说明文档/`, `sql/sql.txt`, `nacos-config/*.yaml`, and this file in the same change as related code, schema, or config modifications. Keep implementation and documentation in one PR.

## Security & Configuration

Keep real credentials out of Git. Keep local ports and service names in `application.yml`; DB, Redis, and JWT values belong in `nacos-config/*.yaml`. Never let downstream services trust client-supplied `X-User-Id`; only the gateway filter should set it.

