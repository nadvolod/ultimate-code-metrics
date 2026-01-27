# Agent Prompts

This directory contains the system prompts for all review agents. Each prompt is stored as a markdown file for better readability and easier iteration.

## Available Prompts

- **`code-quality.md`** - Code Quality Agent prompt
  - Evaluates naming, function size, responsibilities, boundaries, error handling
  - Used by: `CodeQualityAgent.java`

- **`security.md`** - Security Agent prompt
  - Focuses on secrets, authentication/authorization, and injection vulnerabilities
  - Used by: `SecurityAgent.java`

- **`test-quality.md`** - Test Quality Agent prompt
  - Assesses test coverage and suggests high-value tests
  - Used by: `TestQualityAgent.java`

- **`complexity.md`** - Complexity Agent prompt
  - Estimates cyclomatic and cognitive complexity
  - Used by: `ComplexityAgent.java`

- **`priority.md`** - Priority Agent prompt
  - Consolidates and ranks findings from other agents
  - Used by: `PriorityAgent.java`

## How Prompts Are Loaded

Prompts are loaded at runtime using the `PromptLoader` utility class:

```java
String prompt = PromptLoader.loadPrompt("code-quality");
```

This reads from `src/main/resources/prompts/code-quality.md` and returns the content as a string.

## Benefits of Markdown Prompts

1. **Readability**: Proper markdown formatting vs Java string concatenation
2. **Iteration Speed**: Edit prompts without recompiling Java code
3. **Version Control**: Clear diffs in git when prompts change
4. **Collaboration**: Non-developers can review and suggest prompt improvements
5. **Documentation**: Prompts serve as documentation for agent behavior
6. **Testing**: Can swap prompts for testing different approaches

## Editing Prompts

### Format Guidelines

- Use markdown headers (`#`, `##`, `###`) for structure
- Use bold (`**text**`) for emphasis
- Use code blocks (\`\`\`json\`\`\`) for examples
- Keep JSON response format at the end

### Testing Changes

After editing a prompt:

1. **No recompilation needed** - Changes are picked up immediately
2. **Run tests** to verify the agent still works:
   ```bash
   cd java
   mvn -pl temporal-review test
   ```

3. **Test with a real PR** to see the updated output:
   ```bash
   # Trigger the PR review workflow or run locally
   ```

### Prompt Engineering Tips

- **Be specific**: Include concrete examples of what to look for
- **Set clear rules**: Use numbered lists for strict guidelines
- **Define risk levels**: Clear criteria for LOW/MEDIUM/HIGH
- **Request structure**: Always specify exact JSON format
- **Handle edge cases**: Tell the agent what to do when no issues found

## Adding New Agents

To add a new agent:

1. Create a new prompt file: `src/main/resources/prompts/new-agent.md`
2. Create the agent class: `src/main/java/com/utm/temporal/agent/NewAgent.java`
3. Load the prompt in the agent:
   ```java
   import com.utm.temporal.util.PromptLoader;

   private String buildSystemPrompt() {
       return PromptLoader.loadPrompt("new-agent");
   }
   ```

## Prompt Structure Template

Use this template for new prompts:

```markdown
# Agent Name

Brief description of the agent's purpose.

## Evaluation Criteria

What the agent looks for:
1. Criterion 1
2. Criterion 2
3. Criterion 3

## Risk Level Guidelines

- **LOW**: Description
- **MEDIUM**: Description
- **HIGH**: Description

## Recommendation Guidelines

- **APPROVE**: When to approve
- **REQUEST_CHANGES**: When to request changes
- **BLOCK**: When to block

## IMPORTANT

- Specific requirements
- Output format requirements

## Response Format

\`\`\`json
{
  "agentName": "Agent Name",
  "riskLevel": "LOW|MEDIUM|HIGH",
  "recommendation": "APPROVE|REQUEST_CHANGES|BLOCK",
  "findings": ["finding 1", "finding 2", ...]
}
\`\`\`
```

## Version Control Best Practices

- **Commit prompts with agent changes**: When adding new agents or changing behavior
- **Write clear commit messages**: Explain why the prompt was changed
- **Review prompt changes carefully**: Small changes can significantly affect agent behavior
- **Test before merging**: Run full test suite after prompt changes

## Troubleshooting

### Prompt file not found

If you see: `RuntimeException: Prompt file not found: /prompts/xyz.md`

**Causes:**
- Typo in the agent name passed to `PromptLoader.loadPrompt()`
- Prompt file not in `src/main/resources/prompts/`
- File not included in JAR (check `target/classes/prompts/`)

**Solution:**
- Verify file exists: `ls src/main/resources/prompts/`
- Check spelling matches exactly (case-sensitive)
- Rebuild: `mvn clean compile`

### Prompt changes not taking effect

**Cause:** Old compiled classes in target directory

**Solution:**
```bash
cd java
mvn clean compile
```

### Testing specific prompts

To test a single agent's prompt:

```java
// In a test file
String prompt = PromptLoader.loadPrompt("code-quality");
System.out.println(prompt);
// Verify the content matches expectations
```
