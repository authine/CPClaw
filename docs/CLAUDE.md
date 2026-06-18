# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

CPClaw is an MVP assistant for operating CloudPivot through a chat UI. It has two main parts:

- `server/`: Java 21 Spring Boot 3.5 backend with REST APIs, JPA persistence, Flyway migrations, credential encryption, metadata sync/search, CloudPivot integration, conversation handling, agent orchestration, and audit/confirmation records.
- `web/`: Vue 3 + TypeScript + Vite frontend using Element Plus. The current ordinary-user UI is intentionally focused on chat and CloudPivot account settings.

Design docs live under `docs/`. Start from `docs/README.md` for the reading order. Documentation changes must be explicitly confirmed by the user before being committed or pushed.

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

There is no Maven wrapper in the repo, so use the installed `mvn` command.

### Frontend

Run from `web/`:

```bash
npm install
npm run dev
npm run build
npm run preview
```

`npm run build` runs `vue-tsc --noEmit` before `vite build`. There is no separate lint script currently defined.

### Local runtime defaults

- Backend default port: `8080` (`SERVER_PORT` can override).
- Frontend dev server port: `5173`; Vite proxies `/api` to `http://localhost:8080`.
- Backend default datasource is local MySQL: `jdbc:mysql://localhost:3306/CPClaw`; override with `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD`.
- Flyway runs migrations from `server/src/main/resources/db/migration/`.
- H2 is used by tests through test-specific Spring properties in `CpClawApiTests`.

Useful backend environment variables from `application.yml`:

```bash
DATABASE_URL=jdbc:mysql://localhost:3306/CPClaw?useUnicode=true&characterEncoding=utf8&connectionCollation=utf8mb4_unicode_ci&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
DATABASE_USERNAME=root
DATABASE_PASSWORD=
SERVER_PORT=8080
CPC_ENCRYPTION_KEY=
CPC_STORAGE_ROOT=./storage
CPC_CLOUDPIVOT_CORP_ID=dingbb40ac2a2529cb14
```

## Backend architecture

- `CpClawApplication` is the Spring Boot entry point.
- Controllers return the shared `ApiResponse<T>` envelope: `{ success, data, message }`. The frontend `requestJson` helper expects this shape for every `/api` response.
- Core API areas:
  - `settings`: user/admin CloudPivot settings, model config summaries, and connection tests.
  - `metadata`: CloudPivot metadata initialization, app listing, and metadata search.
  - `conversation`: conversation creation, message history, and message submission.
  - `audit`: agent run/tool-call inspection and confirmation actions.
  - `attachment`: file upload/storage metadata.
- `ConversationService.sendMessage` persists the user message, creates an assistant message placeholder, then delegates to `AgentOrchestrator` and saves the returned assistant content.
- `AgentOrchestrator` is the MVP agent path. It performs simple Chinese keyword intent detection, uses `MetadataSearchService.bestMatch`, creates audit records, and:
  - for query intent, calls `CloudPivotRuntimeService` to execute a CloudPivot runtime query and returns real query output;
  - for write-risk intents, creates a confirmation and does not execute the operation until confirmed.
- `CloudPivotConnector` abstracts CloudPivot access. `MvpCloudPivotConnector` handles connection testing, metadata discovery, runtime `/api/runtime/query/list` querying, password/RSA login variants, auth headers, and local fallback metadata for test URLs when fallback is enabled.
- `MetadataService.initializeCloudPivotMetadata` replaces local app/entity/search-document metadata from a CloudPivot snapshot. Search documents are what the agent uses for local metadata matching.
- `CredentialService` stores secrets in `encrypted_credentials`; settings/model responses only expose `hasPassword`/`hasApiKey` flags, not secret values.
- Schema is currently centralized in `V1__init_schema.sql`; JPA has `ddl-auto: none` for normal runtime.

## Frontend architecture

- `web/src/main.ts` mounts the Vue app; `App.vue` hosts the router.
- `web/src/router/index.ts` uses `MainLayout` with current routes:
  - `/` -> `ChatView`
  - `/settings` -> `SettingsView`
- `web/src/services/api.ts` is the shared fetch wrapper. Feature services under `web/src/services/*Api.ts` call it and should continue returning typed `data` payloads from the backend response envelope.
- `ChatView.vue` owns the current MVP chat state: local message list, conversation creation, message send, and confirmation of the last risky operation.
- `SettingsView.vue` is ordinary-user focused: CloudPivot base URL, username, password save/test. Admin/model setup APIs still exist in the backend, but the current UI hides them from ordinary users.
- Shared frontend DTOs live in `web/src/types/`; keep them aligned with Java record DTOs when changing API payloads.

## Product constraints to preserve

- Ordinary users should see only chat and CloudPivot account settings unless the user asks to change that scope.
- Chat query flow must execute metadata-to-runtime-query and should not regress to placeholder previews for query results.
- Write/update/delete-style CloudPivot operations require confirmation before execution and should produce audit records.
- Do not commit or push documentation changes until the user explicitly confirms the document content.
