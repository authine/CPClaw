# Repository Guidelines

## Project Structure & Module Organization

CPClaw has two deployable modules:

- `server/` — Java 21/Spring Boot backend. Code is under `src/main/java/com/cpclaw/`, tests under `src/test/java/`, configuration under `src/main/resources/`, and Flyway migrations under `src/main/resources/db/migration/`.
- `web/` — Vue 3 + TypeScript + Vite frontend. Views, components, API clients, state, and DTOs live in `src/views/`, `src/components/`, `src/services/`, `src/stores/`, and `src/types/`.
- `docs/` — product, technical, test, and project-management source of truth; synchronize design and acceptance changes here.

## Build, Test, and Development Commands

Run backend commands from `server/`:

```bash
mvn spring-boot:run        # start API on port 8080
mvn test                   # run all Java tests
mvn test -Dtest=CpClawApiTests
mvn package                # build the Spring Boot artifact
```

Run frontend commands from `web/`:

```bash
npm install                # install dependencies
npm run dev                # start Vite on port 5173
npm run build              # type-check, then create production bundle
npm run preview            # serve the production bundle locally
```

Vite proxies `/api` to `http://localhost:8080`; no frontend lint script is configured.

## Coding Style & Naming Conventions

Use four-space indentation in Java and two spaces in Vue/TypeScript. Use `PascalCase` types, `camelCase` methods/variables, and `UPPER_SNAKE_CASE` constants. Name components `PascalCase.vue`, services `*Api.ts`, and tests `*Tests.java`. Keep the `{ success, data, message }` API envelope and align frontend types with Java DTOs.

## Testing Guidelines

Backend tests use Spring Boot Test/JUnit and cover API, service, model, vector, and conversation behavior. Add focused regression tests beside the affected package; run `mvn test` before submitting. For UI or integration changes, exercise `docs/test-cases/` and record validation results in the relevant docs.

## Commit & Pull Request Guidelines

History uses short imperative subjects, often prefixed `feat:`, `docs:`, or `fix:` (for example, `docs: 记录最新环境启动验证`). Keep commits scoped. Pull requests should explain behavior changes, list verification commands, link an issue or design document, and include screenshots for UI changes. Never commit credentials, tokens, cookies, `.env` files, browser state, or logs.

## Architecture & Safety Notes

The backend owns CloudPivot access, credentials, runtime execution, and audit/confirmation checks; the frontend calls backend APIs only. Agent execution uses synchronized local metadata and schema codes, not invented identifiers. Write-risk operations require confirmation and audit entries. Update applicable `docs/` files before delivery, and obtain user confirmation before committing documentation changes.
