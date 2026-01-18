# GitHub Copilot Instructions

This file provides guidance for GitHub Copilot when working with the Ultimate Test Metrics repository.

## Project Overview

Ultimate Test Metrics is an AI-powered PR review orchestration system built on Temporal workflows. It uses LLM agents to analyze pull requests across three dimensions: code quality, test quality, and security.

## Tech Stack

### Frontend
- **Next.js 16** with React 19
- **TypeScript** (strict mode)
- **Tailwind CSS 4** for styling
- **Radix UI** for component primitives
- **pnpm** for package management
- **ESLint** for linting

### Backend
- **Java 11+** with Maven 3.6+
- **Temporal SDK 1.24.1** for workflow orchestration
- **OkHttp 4.12.0** for HTTP client
- **Jackson 2.15.3** for JSON serialization
- **SLF4J 2.0.9** for logging

### Testing
- Frontend: (To be implemented with Playwright)
- Backend: JUnit (standard Java testing)

## Build & Test Commands

### Frontend (Next.js)
```bash
# Install dependencies
pnpm install

# Development server
pnpm dev

# Build
pnpm build

# Lint
pnpm lint

# Production server
pnpm start
```

### Backend (Java)
```bash
# Navigate to Java directory
cd java

# Build all modules
mvn clean install

# Build without tests
mvn clean install -DskipTests

# Run tests only
mvn test

# Run specific module
cd java/temporal-review
mvn clean install
```

### Temporal Server (Required for Backend)
```bash
# Start local Temporal server
temporal server start-dev

# Verify connection
temporal operator namespace list

# Web UI: http://localhost:8233
```

## Code Style & Conventions

### General
- Write clear, self-documenting code
- Add comments only when necessary to explain complex logic
- Follow existing patterns in the codebase
- Keep functions small and focused

### TypeScript/JavaScript
- Use TypeScript for all new files
- Use functional components with hooks
- Prefer `const` over `let`, avoid `var`
- Use explicit types rather than `any`
- Follow existing naming conventions:
  - Components: PascalCase (e.g., `ReviewCard`)
  - Functions/variables: camelCase (e.g., `fetchReviewData`)
  - Constants: UPPER_SNAKE_CASE (e.g., `API_ENDPOINT`)

### Java
- Follow Java naming conventions:
  - Classes: PascalCase (e.g., `PRReviewWorkflow`)
  - Methods/variables: camelCase (e.g., `executeReview`)
  - Constants: UPPER_SNAKE_CASE (e.g., `TASK_QUEUE_NAME`)
- Package structure follows `com.utm.temporal.<subpackage>` pattern
- Use dependency injection where appropriate
- Handle exceptions appropriately with meaningful messages

### File Organization
- Frontend: Components in `/components`, pages in `/app`, utilities in `/lib`
- Backend: Follow Maven standard directory structure
- Tests: Co-locate with source code following language conventions

## Architecture Patterns

### Temporal Workflows
- **Workflows**: Orchestration logic, must be deterministic
- **Activities**: Business logic, can make external calls
- **Agents**: Specialized LLM-based reviewers (Code, Test, Security)
- **Task Queue**: Use `pr-review` for all PR review workflows

### Data Flow
```
CLI → Temporal Client → Workflow → Activities → Agents → LLM → Results
```

### Key Models
- `ReviewRequest`: Input (PR title, description, diff, test summary)
- `ReviewResponse`: Output (recommendation, agent findings, metadata)
- `AgentResponse`: Individual agent review results

## Environment Variables

### Required
- `OPENAI_API_KEY`: OpenAI API key for LLM calls

### Optional
- `OPENAI_MODEL`: Model to use (default: `gpt-4o-mini`)
- `OPENAI_BASE_URL`: OpenAI API base URL
- `DUMMY_MODE`: Set to `true` to use canned responses for testing

## Restrictions

### Do NOT:
- Commit secrets, API keys, or sensitive data to the repository
- Modify files under `.git/` directory
- Remove or modify working tests unless fixing bugs in those tests
- Change core Temporal workflow signatures without considering backward compatibility
- Add dependencies without checking for security vulnerabilities
- Modify Maven POMs without testing the build
- Change Node.js version without updating documentation

### Always:
- Run tests before committing code changes
- Lint code before committing
- Update documentation when changing APIs or configurations
- Handle errors gracefully with meaningful messages
- Validate environment variables before use
- Use `.gitignore` to exclude build artifacts and dependencies

## Testing Guidelines

### Before Committing
1. Run relevant tests for your changes:
   - Frontend: `pnpm lint` and any test commands
   - Backend: `mvn test` in affected modules
2. Ensure build succeeds:
   - Frontend: `pnpm build`
   - Backend: `mvn clean install`
3. Test manually if adding new features:
   - Start Temporal server
   - Run the application
   - Verify expected behavior

### Test Structure
- Write unit tests for business logic
- Write integration tests for Temporal workflows
- Test error handling and edge cases
- Use descriptive test names

## Common Tasks

### Adding a New Agent
1. Create agent class in `java/temporal-review/src/main/java/com/utm/temporal/agent/`
2. Implement agent interface with LLM call
3. Create activity interface and implementation
4. Register activity in workflow
5. Update `PRReviewWorkflow` to orchestrate new agent
6. Add tests for the new agent

### Adding a Frontend Component
1. Create component in `/components` directory
2. Use TypeScript with proper types
3. Style with Tailwind CSS classes
4. Export and import in relevant pages
5. Add to components manifest if needed

### Updating Dependencies
1. Frontend: Use `pnpm add <package>` or `pnpm add -D <package>` for dev dependencies
2. Backend: Update `pom.xml` with appropriate version
3. Run security checks
4. Test thoroughly before committing

## Troubleshooting

### Common Issues
- **Temporal connection refused**: Ensure `temporal server start-dev` is running
- **Maven build failures**: Check Java version (`java -version`), clean build (`mvn clean install -U`)
- **Frontend build errors**: Clear `.next` cache, reinstall dependencies (`rm -rf .next && pnpm install`)
- **Missing environment variables**: Check `.env.local` or export variables in shell

## Resources

- [Temporal Documentation](https://docs.temporal.io)
- [Next.js Documentation](https://nextjs.org/docs)
- [Tailwind CSS Documentation](https://tailwindcss.com/docs)
- [Maven Documentation](https://maven.apache.org/guides/)

## Exit Codes (Java CLI)
- `0`: Success
- `1`: Invalid arguments
- `2`: Workflow execution failed
- `3`: File I/O error

## Notes for AI Agents

When making changes:
1. Understand the full context before modifying code
2. Make minimal, surgical changes
3. Test changes thoroughly
4. Update documentation if APIs change
5. Follow existing patterns and conventions
6. Consider backward compatibility
7. Handle errors gracefully
