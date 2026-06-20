# CPClaw Progress

This file is the live project progress board for CPClaw. Future task updates should be recorded here first, then committed and pushed to GitHub so the latest state is visible remotely.

## Current Snapshot

- Date: 2026-06-20
- Branch: `main`
- Remote: `origin` -> `ssh://git@github.com/authine/CPClaw.git`
- Project phase: MVP stabilization and conversation-workbench completion
- Primary goal: deliver a usable CloudPivot super-agent conversation UI with settings, metadata initialization, conversation history, attachments, confirmation, and audit visibility.

## Requirement Baseline

CPClaw is a conversational super-agent for the CloudPivot low-code platform. Users should be able to use natural language to access CloudPivot applications and operate data in a way that approximates a real employee using the browser or APIs.

Core MVP expectations:

- Conversation-first UI as the main entry.
- Multi-turn conversation support.
- New conversation creation.
- Historical conversations saved and viewable.
- System settings for CloudPivot login URL, account, password, and model configuration.
- Admin metadata initialization for CloudPivot app architecture.
- Local metadata index used for intent matching and capability discovery.
- ReAct + Reflection style execution pipeline, with the current implementation still in MVP/rule-based form.
- Query scenarios first.
- High-risk write/delete/workflow/action operations require confirmation before execution.
- Audit records and tool inputs/outputs must be masked.

## Latest Engineering Progress

### Frontend Workbench

- Status: in progress, local working tree only
- Added conversation-focused workbench structure.
- Added history conversation list and conversation switching.
- Added new conversation actions.
- Added model selector and thinking-mode switch on the conversation page.
- Added attachment upload entry and sends uploaded attachment IDs with the next message.
- Added Agent execution flow display, metadata match details, risk level, candidate objects, execution steps, and confirmation handling.

### Settings UI

- Status: in progress, local working tree only
- Expanded settings into user CloudPivot account, admin metadata environment, and model configuration sections.
- Added saved credential indicators.
- Added user/admin connection test actions.
- Added model list and model capability display.

### Metadata UI

- Status: in progress, local working tree only
- Restored metadata route and navigation entry.
- Added metadata sync action, search, loading state, error state, and sync summary.

### Audit UI

- Status: in progress, local working tree only
- Restored audit route and navigation entry.
- Added Agent Run lookup, empty-state handling, not-found state, loading state, and masked tool-call table.

## Verification Log

- `npm run build` in `web/`: passed on 2026-06-20.
- `git diff --check`: passed on 2026-06-20. Only Windows line-ending warnings were shown.
- Frontend dev server: running at `http://127.0.0.1:5173/` during local verification.
- Frontend route HTTP checks returned 200 for `/`, `/settings`, `/metadata`, and `/audit`.
- Sensitive diff scan: no original CloudPivot account password, obvious bearer token, or API key literal found in the current diff.
- Backend `mvn test`: attempted using local JDK/Maven paths, but Maven dependency resolution failed because network access to Maven Central was interrupted (`Connection reset` / `Network is unreachable`). This was not a test assertion failure.

## Current Blockers And Risks
