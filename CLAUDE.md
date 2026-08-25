# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

CPClaw is a chat-driven assistant for operating CloudPivot. The product path is: synchronize CloudPivot design-time metadata into local storage, use that local metadata graph/index to understand user intent, execute confirmed runtime CloudPivot operations through the backend, and return auditable chat results.

Main code areas:

- `server/`: Java 21, Spring Boot 3.5, Spring Web MVC, Spring Data JPA, Flyway, MySQL primary persistence, optional PostgreSQL/pgvector metadata semantic recall, Playwright Java for CloudPivot fallback paths, and OpenAI-compatible model calls.
- `web/`: Vue 3 + TypeScript + Vite frontend with Element Plus, Pinia, Vue Router, markdown rendering, attachment upload, chat execution UI, metadata, audit, and settings pages.
- `docs/`: product, technical, test, and project-management source of truth. `docs/README.md` defines the reading order and requires design/test/process changes to be reflected in docs before delivery.

There is also a `docs/CLAUDE.md`; this root file is the repository-level guidance. If the two diverge, prefer this root file and update both only when the scoped docs guidance needs to change.

## Common commands

### Backend

Run from `server/`:

```bash
mvn spring-boot:run
mvn test
mvn test -Dtest=CpClawApiTests
mvn test -Dtest=CpClawApiTests#mvpApiFlowWorks
mvn package
```

There is no Maven wrapper in the repository; use the installed `mvn` command.

### Frontend

Run from `web/`:

```bash
npm install
npm run dev
npm run build
npm run preview
```

`npm run build` runs `vue-tsc --noEmit` before `vite build`. No separate lint script is currently defined.

### Local runtime defaults

- Backend port: `8080` (`SERVER_PORT` can override).
- Frontend dev server: `5173`; Vite proxies `/api` to `http://localhost:8080`.
- Normal backend datasource: MySQL `jdbc:mysql://localhost:3306/CPClaw`; override with `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`.
- Tests use H2 with per-test Spring properties and often mock `CloudPivotConnector`; Flyway is disabled in `CpClawApiTests`.
- Flyway migrations live in `server/src/main/resources/db/migration/`; JPA runtime uses `ddl-auto: none`.

Useful backend environment variables from `application.yml`:

```bash
DATABASE_URL=jdbc:mysql://localhost:3306/CPClaw?useUnicode=true&characterEncoding=utf8&connectionCollation=utf8mb4_unicode_ci&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
DATABASE_USERNAME=root
DATABASE_PASSWORD=
SERVER_PORT=8080
CPC_ENCRYPTION_KEY=
CPC_STORAGE_ROOT=./storage
CPC_CLOUDPIVOT_CORP_ID=dingbb40ac2a2529cb14
CPCLAW_VECTOR_ENABLED=false
CPCLAW_VECTOR_JDBC_URL=jdbc:postgresql://localhost:5432/CPClaw
CPCLAW_VECTOR_USERNAME=postgres
CPCLAW_VECTOR_PASSWORD=
CPCLAW_VECTOR_TOP_K=20
CPCLAW_VECTOR_MIN_SIMILARITY=0.62
CPCLAW_EMBEDDING_BASE_URL=
CPCLAW_EMBEDDING_API_KEY=
CPCLAW_EMBEDDING_MODEL=text-embedding-v4
```

## Backend architecture

- `CpClawApplication` starts the Spring Boot app. Controllers use the shared `ApiResponse<T>` envelope `{ success, data, message }`; the frontend `requestJson` helper expects every `/api` response to use this shape.
- Main API groups are:
  - `/api/settings`: user/admin CloudPivot settings, model config summaries, and connection tests.
  - `/api/metadata`: metadata sync, app listing, full metadata model, and search.
  - `/api/conversations`: conversation creation/history/deletion and message submission.
  - `/api/agent`: agent plan/preview endpoints.
  - `/api/audit`: agent runs, tool calls, confirmations, and confirmed-operation execution.
  - `/api/attachments`: file upload/storage metadata.
- `ConversationService.sendMessage` persists the user message, creates an assistant placeholder, delegates to `AgentOrchestrator`, then stores the returned assistant message and metadata.
- `AgentOrchestrator` is the current ReAct + Reflection path. It observes recent conversation context, detects intent, uses `MetadataSearchService.bestMatch`, builds a `MetadataExecutionPlanner` plan, optionally asks `ModelGateway.planIntent`, records `AgentRun`/tool-call audit entries, executes read intents through `CloudPivotRuntimeService`, creates confirmations for write-risk intents, and writes reflection metadata back to audit.
- `MetadataService.initializeCloudPivotMetadata` is the metadata ingestion boundary. It fetches a `CloudPivotMetadataSnapshot`, replaces local apps/entities/data items/relations/API endpoints/search documents, and indexes those documents into optional vector search. MySQL metadata/search documents remain the authoritative source; vector search is an enhancement and must not override exact schema/code/path matches.
- `MetadataSearchService` performs deterministic metadata recall/ranking using names, codes, aliases, graph paths, business terms, and optional `MetadataVectorSearch` candidates. Agent execution should use schema codes from this local Metadata Index, not invented or live-searched values.
- `CloudPivotConnector` abstracts CloudPivot access. `MvpCloudPivotConnector` handles connection tests, metadata discovery, runtime list queries, login/auth headers, fallback metadata for test URLs, and delete support. `CloudPivotRuntimeService` wraps runtime query/record-target behavior for the agent.
- `OpenAiCompatibleModelGateway` calls chat-completion-compatible APIs for intent planning and analysis; local/example URLs return deterministic local responses for tests/demo paths.
- `CredentialService` stores secrets in `encrypted_credentials`; settings/model DTOs expose flags such as `hasPassword`/`hasApiKey`, not secret values.

## Frontend architecture

- `web/src/main.ts` mounts the Vue app; `App.vue` hosts the router.
- `web/src/router/index.ts` uses `MainLayout` with routes for chat (`/`), metadata (`/metadata`), audit (`/audit`), and settings (`/settings`).
- `web/src/services/api.ts` is the shared fetch wrapper. Feature services under `web/src/services/*Api.ts` should return typed `data` payloads from the backend envelope rather than exposing the envelope to views.
- `ChatView.vue` owns the chat flow: conversation selection/creation, message sending, progress/execution cards, attachments, markdown answers, model/thinking controls, candidate/plan display, and risky-operation confirmation.
- `MetadataView.vue` and metadata services expose sync/search/model-inspection paths; `AuditView.vue` surfaces agent runs/tool calls/confirmation state; `SettingsView.vue` manages CloudPivot/model settings.
- Shared frontend DTOs live in `web/src/types/`; keep them aligned with Java record DTOs when changing API payloads.

## Product and safety constraints from docs

- CloudPivot runtime queries should follow the real chain: synchronized metadata -> local match/plan -> CloudPivot runtime API -> answer. Do not replace query results with placeholder/demo data when a real runtime call is expected.
- Create/update/delete, workflow/action, batch export, and attachment-to-CloudPivot operations require backend confirmation/audit before execution.
- The frontend must not call CloudPivot directly; CloudPivot access and credential handling stay in the backend.
- Documentation updates require user confirmation before committing to Git. Product flow, Agent behavior, CloudPivot integration, data model, security, tests, and progress/process changes should be synchronized to the relevant files under `docs/` as described in `docs/README.md`.
