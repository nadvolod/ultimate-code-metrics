# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Ultimate Test Metrics is an AI-powered PR review orchestration system using Temporal workflows to run parallel analysis across 5 agents: Code Quality, Test Quality, Security, Complexity, and Priority. It produces an overall recommendation (APPROVE, REQUEST_CHANGES, or BLOCK) based on aggregated findings.

## Common Commands

### Frontend (Next.js - root directory)
```bash
npm install              # Install dependencies
npm run dev              # Start dev server (localhost:3000)
npm run build            # Build for production
npm run lint             # Lint with ESLint
```

### Backend (Java/Maven - java/ directory)
```bash
cd java
mvn clean install                  # Build all modules with tests
mvn clean install -DskipTests      # Build without tests
mvn test                           # Run tests only

# Run PR review workflow (requires Temporal server running)
cd temporal-review
mvn exec:java -Dexec.args="../../sample-input.json ../../sample-output.json"
```

### Temporal Server (required for backend)
```bash
temporal server start-dev    # Start local server (port 7233, Web UI: localhost:8233)
```

## Architecture

### Backend Workflow Pattern
```
WorkerApp (CLI entry point)
  → Temporal Client
    → PRReviewWorkflow (orchestrator)
      → Activities (5 parallel-capable, currently sequential):
        - CodeQualityActivity → CodeQualityAgent → LLM
        - TestQualityActivity → TestQualityAgent → LLM
        - SecurityQualityActivity → SecurityAgent → LLM
        - ComplexityQualityActivity → ComplexityAgent → LLM
        - PriorityActivity → PriorityAgent → LLM
      → Aggregates results into ReviewResponse
```

**Key separation:**
- **Workflows** are deterministic orchestration logic (no external calls)
- **Activities** are thin wrappers that can fail and retry
- **Agents** contain business logic and make LLM calls

### Technology Stack
- **Backend**: Java 11+, Maven, Temporal SDK 1.24.1, OkHttp, Jackson
- **Frontend**: Next.js 16, React 19, TypeScript 5 (strict), Tailwind CSS 4, Radix UI, Recharts
- **LLM**: OpenAI API (gpt-4o-mini default)

## Key Files

- `java/temporal-review/src/main/java/com/utm/temporal/WorkerApp.java` - CLI entry point
- `java/temporal-review/src/main/java/com/utm/temporal/workflow/PRReviewWorkflowImpl.java` - Orchestration logic
- `java/temporal-review/src/main/java/com/utm/temporal/agent/` - 5 LLM agents
- `app/` - Next.js app router pages
- `components/` - React components (Radix UI primitives)
- `lib/types/review.ts` - TypeScript interfaces matching Java models
- `sample-input.json` / `sample-output.json` - Example workflow I/O

## Environment Variables

Required:
- `OPENAI_API_KEY` - OpenAI API key

Optional:
- `OPENAI_MODEL` - LLM model (default: gpt-4o-mini)
- `DUMMY_MODE=true` - Use canned responses without API calls

## Conventions

- TypeScript: Functional components with hooks, explicit types, PascalCase components, camelCase functions
- Java: PascalCase classes, camelCase methods, package `com.utm.temporal.*`
- Never modify Temporal workflow signatures without considering backward compatibility
- Always run tests and lint before committing

## Exit Codes (Java CLI)
- 0: Success
- 1: Invalid arguments
- 2: Workflow execution failed
- 3: File I/O error
