# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Ultimate Test Metrics is a PR review orchestration system built on Temporal workflows. It uses LLM-powered agents to analyze pull requests across three dimensions: code quality, test quality, and security. The system runs agents in parallel via Temporal activities and aggregates their recommendations.

## Build and Development Commands

**Build the project:**
```bash
cd java
mvn clean install
```

**Run a PR review:**
```bash
cd java/temporal-review
mvn exec:java -Dexec.args="<input-json-path> <output-json-path>"
```

**Compile only:**
```bash
cd java
mvn compile
```

## Architecture

### Temporal Workflow Pattern

The codebase follows the standard Temporal workflow pattern with clear separation of concerns:

- **Workflow** (`PRReviewWorkflow`/`PRReviewWorkflowImpl`): Orchestrates the review process. Calls three activities sequentially and aggregates results. Uses `Workflow.currentTimeMillis()` instead of `System.currentTimeMillis()` for deterministic execution.

- **Activities** (`CodeQualityActivity`, `TestQualityActivity`, `SecurityQualityActivity`): Each activity is a thin wrapper around an Agent. Activities are registered with the worker and invoked by the workflow via activity stubs.

- **Agents** (`CodeQualityAgent`, `TestQualityAgent`, `SecurityAgent`): Contain the actual business logic for analyzing PRs. Each agent uses the `LlmClient` abstraction to call an LLM with specific prompts and criteria.

### Key Architectural Decisions

1. **Activity Configuration**: Activities use a 60-second timeout with exponential backoff (2x multiplier, 5s initial retry). See `PRReviewWorkflowImpl:20-26`.

2. **Fail Fast**: No graceful degradation. If any agent fails, the entire workflow fails. See `PRReviewWorkflowImpl:96-100` and agent implementations.

3. **Sequential Execution**: Agents run sequentially (not in parallel) to simplify the initial implementation. Each blocks for 2-3 seconds during LLM calls.

4. **Aggregation Logic**: The workflow aggregates agent results with priority: BLOCK > REQUEST_CHANGES > APPROVE. See `PRReviewWorkflowImpl:103-120`.

5. **LLM Client Abstraction**: The `LlmClient` interface allows swapping LLM providers. Currently only `OpenAiLlmClient` is implemented, which requires the `OPENAI_API_KEY` environment variable.

## Data Flow

```
RunReview CLI
  ↓ (reads ReviewRequest JSON)
Temporal Client
  ↓ (starts workflow)
PRReviewWorkflow
  ↓ (calls activities sequentially)
[CodeQualityActivity → TestQualityActivity → SecurityQualityActivity]
  ↓ (each activity delegates to agent)
[CodeQualityAgent → TestQualityAgent → SecurityAgent]
  ↓ (agents call LLM)
LlmClient (OpenAI)
  ↓ (returns AgentResult as JSON)
PRReviewWorkflow
  ↓ (aggregates results)
RunReview CLI
  ↓ (writes ReviewResponse JSON)
```

## Key Components

### Models (`com.utm.temporal.model`)
- `ReviewRequest`: Input containing PR title, description, diff, and optional test summary
- `ReviewResponse`: Output containing overall recommendation, list of agent results, and metadata
- `AgentResult`: Individual agent result with agentName, riskLevel, recommendation, and findings
- `TestSummary`: Optional test metrics (passed, failed, skipped)

### Entry Point
`RunReview.java` is the CLI entry point. It:
1. Reads input JSON (`ReviewRequest`)
2. Connects to local Temporal server
3. Starts a worker and registers workflow + activities
4. Executes the workflow synchronously
5. Writes output JSON (`ReviewResponse`)
6. Exits with code 0 (success), 1 (invalid args), 2 (workflow failed), or 3 (file I/O error)

### Environment Variables
- `OPENAI_API_KEY`: Required for LLM calls
- `OPENAI_MODEL`: Optional, defaults to "gpt-4o-mini"

## Important Patterns

### Temporal Determinism
The workflow uses `Workflow.currentTimeMillis()` instead of `System.currentTimeMillis()` to ensure deterministic replay. See `PRReviewWorkflowImpl:42,79`.

### Agent Prompt Structure
All agents follow the same pattern:
1. Build system prompt with criteria and output format
2. Build user prompt with PR details (title, description, diff)
3. Call LLM with temperature 0.2 and JSON response format
4. Parse response into `AgentResult`

See `CodeQualityAgent:32-66` for the canonical implementation.

### Dependency Injection
Agents are instantiated in `RunReview.main()` and injected into Activity implementations. This allows for testing with mock LLM clients. See `RunReview:77-86`.

## Maven Project Structure
- Parent POM: `java/pom.xml` (defines Java 11, UTF-8 encoding)
- Module: `java/temporal-review/pom.xml` (Temporal SDK 1.24.1, OkHttp 4.12.0, Jackson 2.15.3)
