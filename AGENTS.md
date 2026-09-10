# Repository Guidelines

## Project Structure & Module Organization

The Git root is this directory.

- `BookMall/`: Spring Cloud Alibaba backend:
  - `bookmall-common`: shared `Result`, `PageResult`, errors, and exceptions.
  - `bookmall-auth` (8060): registration, login, JWT, and user address management.
  - `bookmall-book` (8070): books, categories, pagination, Redis, and Sentinel.
  - `bookmall-cart` (8083): cart items with quantity/selection and Feign validation against the book service.
  - `bookmall-stock` (8090): book stock queries, order-time reservation, payment confirmation, and cancellation release.
  - `bookmall-order` (8050): direct/cart orders, paid-stock confirmation, timeout close, and OpenFeign calls to book, cart, and stock services.
  - `bookmall-payment` (8051): mock payment records and order paid-state updates via OpenFeign.
  - `bookmall-gateway` (8080): routing, JWT validation, and `X-User-Id`.
  - `bookmall-ai` (8071): read-only chatbot using LangChain4j + DashScope (Qwen); session memory in Redis; calls book/order via OpenFeign.
  - Async flow via RabbitMQ: `bookmall-payment` publishes pay-success events; `bookmall-order` consumes them to mark orders paid; `bookmall-order` and `bookmall-stock` exchange stock confirm/release events. Producers/consumers live in each module's `mq` package.
- REST APIs are documented with Knife4j (springdoc) in each service.
- `front/`: Vue 3 + Vite app under `front/src/` — axios wrapper and endpoints in `src/api/` (`http.js`, `bookmall.js`), views in `src/views/` (Home, Books, Cart, Orders, Address, Login, AiChat).
- `sql/sql.txt`: complete MySQL schema and seed data, including all 9 tables.
- `sql/updates/`: numbered incremental SQL scripts (`001_*.sql` …) for existing environments.
- `nacos-config/`: per-service config and `publish.sh`.
- `docker-compose.infra.yml`: local MySQL, Nacos, Redis, and RabbitMQ for macOS / Docker Desktop.
- `scripts/dev-macos.sh`: optional macOS bootstrap for infra + Nacos config publishing.
- `说明文档/`: detailed module and deployment docs.

## Build, Test, and Development Commands

Use `mvn -f BookMall/pom.xml -q clean package` to compile all backend modules, and `mvn -f BookMall/pom.xml -q test` to run backend tests. Run `mvn -f BookMall/pom.xml -DskipTests install` once, then start one service with `mvn -f BookMall/pom.xml -pl bookmall-auth spring-boot:run`; replace the module name as needed. Do not use `-am` with `spring-boot:run`, because it also runs the parent POM, which has no main class. Start auth, book, cart, stock, order, and payment before the gateway; `bookmall-ai` is independent and can run after the gateway.

`bookmall-ai` needs the DashScope API key as `DASHSCOPE_API_KEY` env var (never commit it); its Spring config lives in `nacos-config/ai-assistant.yaml`.

On Apple Silicon, the gateway gets the correct Netty native library through the `macos-arm64` Maven profile in `bookmall-gateway/pom.xml`.

When starting `bookmall-book`, if Sentinel cannot write its default log directory, point it into the workspace:

```bash
mkdir -p logs/sentinel
mvn -f BookMall/pom.xml -pl bookmall-book spring-boot:run "-Dspring-boot.run.jvmArguments=-Dcsp.sentinel.log.dir=${PWD}/logs/sentinel"
```

For frontend, run `cd front && npm install && npm run dev` to start Vue at `http://localhost:5173`, or `npm run build` to produce the build. Apply database changes by running `sql/sql.txt` and `sql/updates/*.sql` against MySQL. Publish config changes with `cd nacos-config && bash publish.sh`.

## Coding Style & Naming Conventions

Backend: use Java 17, UTF-8, and 4-space indentation. Follow existing packages: `controller`, `service`, `service.impl`, `mapper`, `entity`, `dto`, `vo`, `config`, `client`, `filter`, `util`. Name classes by role, like `BookCreateRequest` or `AuthGlobalFilter`. Keep controllers thin, validate DTOs, return `Result<T>` / `PageResult<T>`, and throw `BusinessException` with `ErrorCode`. No linter is configured; match the surrounding code.

Frontend: keep requests in `front/src/api/`, views in `front/src/views/`, and use the existing Vue 3 SFC style. New database changes go in `sql/updates/` and align with backend entities and mappers. For new environments, `sql/sql.txt` is the complete initialization script.

## Testing Guidelines

All backend modules have JUnit 5 + Mockito unit tests under `BookMall/<module>/src/test/java/com/bookmall/<module>`; they are plain unit tests (MockitoExtension, mocked mappers/Feign clients/MQ publishers) and do not start a Spring context or require infra. Add new tests there, name them `method_expectedBehavior_whenCondition`, e.g. `createOrder_snapshotPrice_whenBookExists`. Run targeted tests with `mvn -f BookMall/pom.xml -pl <module> -am test`. No frontend test runner is configured; verify with `npm run dev`.

## Commit & Pull Request Guidelines

Use scoped Conventional Commits like `feat(auth)`, `docs`, or `chore`. Keep subjects lowercase and focused. In PRs, describe changes, show verification steps, and reference related issues. Include related backend, frontend, SQL, and config changes for cross-layer features.

## Documentation Sync

Update `README.md`, `说明文档/`, `sql/sql.txt`, `nacos-config/*.yaml`, and this file in the same change as related code, schema, or config modifications. Keep implementation and documentation in one PR.

## Security & Configuration

Keep real credentials out of Git. Keep local ports and service names in `application.yml`; DB, Redis, and JWT values belong in `nacos-config/*.yaml`. Never let downstream services trust client-supplied `X-User-Id`; only the gateway filter should set it.

