# Ultimate Code Metrics – MVP Consolidation & Migration Guide

This document defines **everything required to assemble the Ultimate Test Metrics MVP**  
by extracting the working Temporal Java implementation and wiring the remaining pieces
(UI, tests, CI) around it.

This file is designed to be **given to an LLM** and executed step‑by‑step:
- copy the correct code
- place it in the correct structure
- verify it builds and runs
- avoid accidental scope creep

Simplicity, correctness, and a working demo are the priority.

---

## 1. Goal of the Project

**Ultimate Code Metrics** is a minimal, real, end‑to‑end MVP that demonstrates:

- AI‑assisted pull request review
- Deterministic orchestration of AI using Temporal
- A small, high‑signal test suite
- CI‑driven governance with a single PR comment outcome

This is **not** a SaaS yet.
This is a **working demo + talk artifact**.

---

## 2. Source of Truth for Temporal Code

The Temporal Java implementation already works and lives here:

```
https://github.com/nadvolod/temporal-warmups/tree/901-java/exercise-901-githubpr/java/src/main/java/solution
```

This folder is considered the **golden reference** for:
- Workflow design
- Activity design
- LLM integration
- Agent orchestration logic

We will **extract the good code only** and move it into a new repository.

---

## 3. Target Repository

Create a new repository:

```
ultimate-test-metrics
```

### Target structure
```
ultimate-test-metrics/
  README.md
  ui/                         # Next.js UI (v0-generated)
  tests/                      # Playwright tests
  java/
    pom.xml                   # Maven parent
    temporal-review/          # Extracted Temporal Java module
  .github/
    workflows/
      pr-governance.yml
```

Only working, relevant code belongs here.

---

## 4. Temporal Java Module (CRITICAL)

### 4.1 Create the Maven module

Inside `java/`:

```
java/
  pom.xml
  temporal-review/
    pom.xml
    src/main/java/
    src/main/resources/
```

The module name is:
```
temporal-review
```

---

### 4.2 Copy code from the old repo

Copy **only** the contents of:

```
exercise-901-githubpr/java/src/main/java/solution
```

Into:

```
ultimate-test-metrics/java/temporal-review/src/main/java/com/utm/temporal
```

While copying:
- Preserve package structure where possible
- Rename packages if needed for clarity (e.g. `solution` → `com.utm.temporal`)
- Remove any exercise or warmup specific naming

---

### 4.3 What code MUST be included

From the old repo, ensure these concepts are present:

- Workflow interface and implementation
- Activities interface and implementation
- LLM client abstraction
- OpenAI implementation
- Agent logic (Code, Test, Security)
- Input and output models
- Prompt templates
- JSON parsing logic

---

### 4.4 What MUST be removed

Do **not** bring over:
- exercise scaffolding
- unused utilities
- commented demo code
- anything not used by the working workflow

If code does not serve the MVP, delete it.

---

## 5. Temporal Runtime Assumptions

### Local development
Temporal is run locally via:

```
temporal server start-dev
```

This provides:
- Temporal Server
- Temporal UI
- Default namespace
- SQLite persistence

No Docker Compose required for local dev.

---

## 6. Java CLI Contract (MANDATORY)

The Temporal module must expose a **single CLI entrypoint**.

### CLI behavior
- Accepts two arguments:
  1. path to input JSON
  2. path to output JSON
- Starts the Temporal workflow
- Waits for completion
- Writes the final aggregated result to the output file
- Exits with:
  - `0` on success
  - non‑zero on failure

### Example invocation
```
mvn -pl temporal-review -DskipTests exec:java \
  -Dexec.mainClass="com.utm.temporal.RunReview" \
  -Dexec.args="agent-input.json agent-output.json"
```

This contract is **non‑negotiable**.
CI and tooling depend on it.

---

## 7. UI (Non‑Blocking but Required)

The UI lives under:

```
ui/
```

It is:
- Next.js (App Router)
- Generated with v0
- Mostly static for MVP
- Reads data from mock or placeholder APIs

The UI does **not** call Temporal directly.

---

## 8. Tests (Small, High Signal)

### Required tests
- API CRUD tests (in memory)
- 1 security test (Bearer auth required)
- 1 E2E smoke test

Tests live under:
```
tests/
```

Playwright is used for both API and E2E tests.

---

## 9. GitHub Actions (PR Governance)

CI lives at:
```
.github/workflows/pr-governance.yml
```

### CI flow
1. Checkout repo
2. Setup Node + Java
3. Install dependencies
4. Run Playwright tests
5. Start Temporal server (Docker service container recommended)
6. Run Java CLI to perform PR review
7. Upsert a single PR comment with results

The PR comment must be **updated**, not duplicated.

---

## 10. PR Comment Contract

The PR comment must contain:
- Test results summary
- Code Quality findings
- Test Quality findings
- Security findings
- Overall recommendation

Use a unique marker:
```
<!-- UTM_AI_REVIEW -->
```

So the same comment can be updated each run.

---

## 11. Verification Checklist (MANDATORY)

After copying and wiring code, verify:

- [ ] `mvn -pl temporal-review compile` passes
- [ ] Local Temporal server starts
- [ ] Java worker connects successfully
- [ ] CLI runner produces valid JSON output
- [ ] UI builds and runs
- [ ] Playwright tests pass
- [ ] CI workflow runs end‑to‑end on a PR

If any item fails, fix before proceeding.

---

## 12. Guardrails

- No new features until MVP works end‑to‑end
- No refactors that don’t improve reliability
- No abstraction for abstraction’s sake
- Temporal code stays minimal and focused

---

## 13. Final Reminder

This project exists to:
- **Ship a working MVP**
- **Support a strong technical talk**
- **Demonstrate good engineering judgment**

Anything that doesn’t support those goals is noise.

## 14. Future metrics to track and capture
- PRs analyzed
- Engineering Hours Saved
- # of security issues found

