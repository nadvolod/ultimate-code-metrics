# Ultimate Test Metrics

AI-powered PR review orchestration system built on Temporal workflows. Uses LLM agents to analyze pull requests across three dimensions: code quality, security, and test coverage.

## Overview

This project uses Temporal to orchestrate parallel PR reviews by specialized AI agents. Each agent analyzes a pull request diff and provides specific, actionable feedback. The system aggregates results and provides an overall recommendation (APPROVE, REQUEST_CHANGES, or BLOCK).

## Architecture

The system follows the Temporal workflow pattern:

- **Workflow** (`PRReviewWorkflow`): Orchestrates three specialized agents sequentially
- **Activities**: Thin wrappers around agents, registered with Temporal workers
- **Agents**: Business logic for code quality, test coverage, and security analysis
- **LLM Client**: Abstraction for AI model calls (currently OpenAI)

### Data Flow

```
RunReview CLI → Temporal Client → PRReviewWorkflow
  → [CodeQualityActivity → TestQualityActivity → SecurityQualityActivity]
  → [CodeQualityAgent → TestQualityAgent → SecurityAgent]
  → LlmClient (OpenAI) → Review Results (JSON)
```

## Current Folder Structure

```
ultimate-test-metrics/
├── README.md              # This file
├── CLAUDE.md              # Internal development documentation
├── ui/                    # (Empty) Future Next.js application
├── sample-input.json      # Example input for testing
├── verify-temporal.sh     # Temporal verification script
└── java/
    ├── pom.xml            # Maven parent POM
    └── temporal-review/   # Temporal-based PR review orchestration
        ├── pom.xml
        └── src/main/java/com/utm/temporal/
            ├── RunReview.java      # CLI entry point
            ├── activity/           # 3 activity interfaces + implementations
            ├── agent/              # 3 LLM agents (Code, Test, Security)
            ├── llm/                # LLM client abstraction (OpenAI)
            ├── model/              # Data models (Request, Response, etc.)
            └── workflow/           # Workflow interface + implementation
```

## Planned Folder Structure (Not Yet Implemented)

The following components are planned but not yet implemented:

```
ultimate-test-metrics/
├── tests/                 # Playwright tests
│   ├── api/              # API integration tests
│   └── e2e/              # End-to-end workflow tests
├── java/
│   ├── github-pr-client/  # Fetch PR diff + metadata from GitHub API
│   └── pr-commenter/      # Upsert single PR comment via GitHub API
└── .github/
    └── workflows/
        └── pr.yml         # CI pipeline
```

## Prerequisites

1. **Java 11+** - For running the Temporal worker and activities
2. **Maven 3.6+** - For building the Java project
3. **Temporal Server** - Local development server (see installation below)
4. **OpenAI API Key** - For LLM agent calls

### Installing Temporal Server

**macOS (Homebrew)**:
```bash
brew install temporal
```

**Linux/Windows/Other**:
Follow the official guide: https://docs.temporal.io/cli#install

## Quick Start

### 1. Start Temporal Server

Start the local Temporal server with default settings:

```bash
temporal server start-dev
```

This will:
- Start Temporal server on `localhost:7233`
- Start the Web UI on `http://localhost:8233`
- Create a default namespace

**Verify the server is running**:
```bash
# In another terminal
temporal operator namespace list
```

You should see the `default` namespace listed.

### 2. Build the Java Project

```bash
cd java
mvn clean install
```

This compiles all modules and runs tests.

### 3. Set Environment Variables

```bash
export OPENAI_API_KEY="your-api-key-here"
export OPENAI_MODEL="gpt-4o-mini"  # Optional, defaults to gpt-4o-mini
```

### 4. Run a PR Review

You can use the provided sample input or create your own:

```bash
cd java/temporal-review
mvn exec:java -Dexec.args="../../sample-input.json ../../sample-output.json"
```

**What happens**:
1. Connects to Temporal server at `localhost:7233`
2. Starts a worker registered to the `pr-review` task queue
3. Executes the `PRReviewWorkflow` synchronously
4. Calls three agents in sequence (each blocks for ~2-3 seconds)
5. Aggregates results and writes to `sample-output.json`

### 5. View Results

**Check the output file**:
```bash
cat sample-output.json
```

**View in Temporal Web UI**:
1. Open http://localhost:8233
2. Go to "Workflows"
3. Find your workflow (ID: `pr-review-<uuid>`)
4. Inspect execution history, activity results, and timings

## Verifying Temporal is Working

### Method 1: CLI Health Check

```bash
temporal operator namespace list
```

Expected output: List of namespaces including `default`

### Method 2: Web UI

1. Open http://localhost:8233
2. Verify the dashboard loads
3. Check "Workflows" tab (should be empty initially)

### Method 3: Run the Verification Script

```bash
./verify-temporal.sh
```

This script checks:
- Temporal CLI installation
- Temporal server connection
- Web UI accessibility
- Java and Maven installation
- Environment variables

### Method 4: Run a Test Review

Follow steps 3-5 in Quick Start above. Successful execution means:
- Temporal server is running
- Worker can connect and register
- Activities can execute
- Workflow completes successfully

## Exit Codes

The `RunReview` CLI uses these exit codes:

- `0` - Success
- `1` - Invalid arguments
- `2` - Workflow execution failed
- `3` - File I/O error

## Temporal Configuration

- **Server**: `localhost:7233` (default)
- **Task Queue**: `pr-review`
- **Workflow ID Pattern**: `pr-review-<uuid>`
- **Activity Timeout**: 60 seconds
- **Retry Policy**: Exponential backoff (5s initial, 2x multiplier)

## Key Dependencies

- **Temporal SDK**: 1.24.1
- **OkHttp**: 4.12.0 (for OpenAI API calls)
- **Jackson**: 2.15.3 (for JSON serialization)
- **SLF4J**: 2.0.9 (for logging)

## Environment Variables

- `OPENAI_API_KEY` - **Required** - Your OpenAI API key
- `OPENAI_MODEL` - Optional - Model to use (default: `gpt-4o-mini`)
- `OPENAI_BASE_URL` - Optional - OpenAI API base URL (default: https://api.openai.com/v1/chat/completions)
- `DUMMY_MODE` - Optional - Set to `true` to use canned responses without API calls (default: `false`)

## Input/Output Format

### ReviewRequest (Input)

```json
{
  "prTitle": "Add user authentication feature",
  "prDescription": "This PR implements JWT-based authentication for user login and registration",
  "diff": "diff --git a/src/auth.js b/src/auth.js\n...",
  "testSummary": {
    "passed": 42,
    "failed": 0,
    "skipped": 1
  }
}
```

### ReviewResponse (Output)

```json
{
  "overallRecommendation": "APPROVE",
  "agents": [
    {
      "agentName": "CodeQuality",
      "riskLevel": "LOW",
      "recommendation": "APPROVE",
      "findings": ["Code follows naming conventions", "Functions are appropriately sized"]
    },
    {
      "agentName": "TestQuality",
      "riskLevel": "LOW",
      "recommendation": "APPROVE",
      "findings": ["Test coverage is adequate", "All tests passing"]
    },
    {
      "agentName": "Security",
      "riskLevel": "LOW",
      "recommendation": "APPROVE",
      "findings": ["No security vulnerabilities detected"]
    }
  ],
  "metadata": {
    "generatedAt": "2026-01-11T10:30:00Z",
    "tookMs": 8432,
    "model": "gpt-4o-mini"
  }
}
```

## Troubleshooting

### Temporal server not starting

```bash
# Check if port 7233 is in use
lsof -i :7233

# Kill existing process if needed
kill -9 <PID>
```

### Connection refused errors

Ensure Temporal server is running:
```bash
temporal server start-dev
```

### Agent failures

Check that `OPENAI_API_KEY` is set:
```bash
echo $OPENAI_API_KEY
```

If you don't have an OpenAI API key, you can use DUMMY_MODE for testing:
```bash
export DUMMY_MODE=true
```

### Maven build failures

Ensure Java 11+ is installed:
```bash
java -version
```

Clean and rebuild:
```bash
cd java
mvn clean install -U
```

## Development Documentation

For detailed development guidance, architectural decisions, and implementation patterns, see [CLAUDE.md](./CLAUDE.md).

## License

[To be determined]
