# Building an AI-Powered PR Review System with Temporal and OpenAI

What if you could get consistent, expert-level PR reviews in under 10 seconds?

If you've worked on any team larger than two people, you know the PR review queue is the code equivalent of waiting at the DMV. Your brilliant feature sits there, aging like fine wine you're not allowed to drink, while reviewers context-switch between meetings, Slack messages, and that one "quick question" that takes an hour.

Manual reviews take 30+ minutes on average. Quality varies wildly—from the reviewer who rubber-stamps everything with "LGTM" to the one who's "thorough" (read: will find issues with your variable naming choices until the heat death of the universe). We've all been there: your PR sitting in review purgatory while you context-switch to three other things, desperately trying to remember what that code even does when the feedback finally arrives.

I built a system that systematically analyzes PRs across multiple dimensions using AI agents orchestrated by Temporal. Here's why I made the architectural decisions I did, and what I learned along the way.

## Why Multi-Agent Architecture?

The naive approach to AI code review is straightforward: throw the entire diff at GPT-4 with a prompt like "review this code." It works, sort of. But the results are inconsistent, generic, and miss domain-specific concerns.

Instead, I opted for specialized agents—it's like hiring five specialists instead of one overworked generalist who claims to know everything about code quality, testing, security, complexity, AND project prioritization. You wouldn't ask your dentist to do your taxes. Same principle.

Each agent is an expert in one domain, like a code review Avengers team:

- **Code Quality Agent** ("the naming police") - evaluates naming conventions, function size, and single responsibility
- **Test Quality Agent** ("the coverage zealot") - examines test cases, assertions, and edge case coverage
- **Security Agent** ("the paranoid friend") - hunts for hardcoded secrets, injection vulnerabilities, and auth issues
- **Complexity Agent** ("the math nerd") - calculates cyclomatic and cognitive complexity
- **Priority Agent** ("the project manager") - aggregates findings and identifies what actually matters

The key to making agents useful is being *very* specific about what you want. Here's the prompt design for the Code Quality Agent:

```java
private String buildSystemPrompt() {
    return "You are a Code Quality Reviewer analyzing pull request diffs.\n\n" +
           "Your task is to evaluate the code against these criteria:\n" +
           "1. **Naming**: Are variables, functions, and classes clearly named?\n" +
           "2. **Function Size**: Are functions reasonably sized and focused?\n" +
           "3. **Responsibilities**: Does each component have a single, clear responsibility?\n" +
           "4. **Boundaries**: Are module boundaries and abstractions clear?\n" +
           "5. **Error Handling**: Is error handling present and appropriate?\n" +
           "6. **Refactoring Opportunities**: Are there obvious improvements?\n\n" +
           "CRITICAL REQUIREMENTS:\n" +
           "- Your findings MUST reference concrete issues found in the diff\n" +
           "- NO generic fluff like \"code looks good\" or \"nice work\"\n" +
           "- Be specific: quote variable names, line numbers, function names\n" +
           "- If you can't find specific issues, say so explicitly\n\n" +
           "Risk Level Guidelines:\n" +
           "- LOW: Minor style issues, suggestions for improvement\n" +
           "- MEDIUM: Unclear naming, functions that could be refactored\n" +
           "- HIGH: Missing error handling, unclear responsibilities\n\n" +
           "Respond ONLY with valid JSON matching this exact structure:\n" +
           "{\n" +
           "  \"agentName\": \"Code Quality\",\n" +
           "  \"riskLevel\": \"LOW|MEDIUM|HIGH\",\n" +
           "  \"recommendation\": \"APPROVE|REQUEST_CHANGES|BLOCK\",\n" +
           "  \"findings\": [\"specific finding 1\", \"specific finding 2\", ...]\n" +
           "}";
}
```

Notice the "NO generic fluff" instruction. Without it, LLMs love to say things like "overall the code looks well-structured." That's useless. We want specific, actionable findings or explicit acknowledgment that nothing was found.

## Why Temporal for Orchestration?

I could have built this with a simple Python script. Or a message queue. Or a cron job that runs every few minutes. I've tried all of those approaches before, and they all end the same way: debugging production issues at 3 AM while wondering why I didn't just become a goat farmer.

Temporal is the adult supervision your distributed system desperately needs.

The problem with simple scripts is the "works on my machine" syndrome. They die silently at 3 AM, leave no trace of what happened, and good luck figuring out which PR was half-processed when the server ran out of memory. Message queues solve some of this, but now you wanted to deploy a feature and instead you're debugging RabbitMQ connection pools.

I've written enough cron jobs that fail silently to know better.

What Temporal provides:

- **Durable execution** - Workflows survive crashes like a cockroach. If the server dies mid-review, Temporal picks up exactly where it left off.
- **Built-in retry with exponential backoff** - So your system doesn't DDoS itself (or your OpenAI bill) when things go wrong.
- **Workflow history & visibility** - It's like CSI for your workflows. You can see exactly what happened, when, and why.
- **Activity timeouts** - Because LLMs sometimes take naps, and you need to know when to give up and retry.

Here's the workflow implementation:

```java
public class PRReviewWorkflowImpl implements PRReviewWorkflow {

    private static final ActivityOptions ACTIVITY_OPTIONS = ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(60))
            .setRetryOptions(RetryOptions.newBuilder()
                    .setInitialInterval(Duration.ofSeconds(5))
                    .setBackoffCoefficient(2)
                    .build())
            .build();

    // Create activity stubs for each agent
    private final CodeQualityActivity codeQualityActivity =
        Workflow.newActivityStub(CodeQualityActivity.class, ACTIVITY_OPTIONS);
    private final TestQualityActivity testQualityActivity =
        Workflow.newActivityStub(TestQualityActivity.class, ACTIVITY_OPTIONS);
    private final SecurityQualityActivity securityQualityActivity =
        Workflow.newActivityStub(SecurityQualityActivity.class, ACTIVITY_OPTIONS);
    private final ComplexityQualityActivity complexityQualityActivity =
        Workflow.newActivityStub(ComplexityQualityActivity.class, ACTIVITY_OPTIONS);
    private final PriorityActivity priorityActivity =
        Workflow.newActivityStub(PriorityActivity.class, ACTIVITY_OPTIONS);

    @Override
    public ReviewResponse review(ReviewRequest request) {
        List<AgentResult> results = new ArrayList<>();

        // Sequential execution - each agent runs after the previous completes
        AgentResult codeQuality = codeQualityActivity.analyze(request);
        results.add(codeQuality);

        AgentResult testQuality = testQualityActivity.analyze(request);
        results.add(testQuality);

        AgentResult security = securityQualityActivity.analyze(request);
        results.add(security);

        AgentResult complexity = complexityQualityActivity.analyze(request);
        results.add(complexity);

        // Priority Agent sees ALL previous findings
        AgentResult priority = priorityActivity.prioritizeIssues(request, results);
        results.add(priority);

        String overall = aggregate(results);
        return new ReviewResponse(overall, results, metadata, ...);
    }
}
```

Notice the sequential execution pattern. I *could* run agents in parallel, but there's a reason I don't: the Priority Agent needs to see findings from all other agents to make sensible prioritization decisions. It's the manager who actually reads the reports before the meeting.

## The Aggregation Logic

How do you combine recommendations from five different agents into a single verdict? I call it the "most paranoid friend wins" approach.

```java
private String aggregate(List<AgentResult> results) {
    // Check for BLOCK
    for (AgentResult result : results) {
        if ("BLOCK".equals(result.recommendation)) {
            return "BLOCK";
        }
    }

    // Check for REQUEST_CHANGES
    for (AgentResult result : results) {
        if ("REQUEST_CHANGES".equals(result.recommendation)) {
            return "REQUEST_CHANGES";
        }
    }

    // All agents approved
    return "APPROVE";
}
```

It's elegantly simple (unlike my first three attempts). If any agent says BLOCK, the overall recommendation is BLOCK. Security Agent found a hardcoded API key? Full stop. Else if any says REQUEST_CHANGES, that's the overall result. Only if everyone approves do we give the green light.

It's like deciding on a restaurant with five friends—one veto and you're ordering pizza.

Why this conservative approach? Better to have false positives than let vulnerabilities through. A developer spending five extra minutes addressing a non-issue is annoying. A hardcoded secret making it to production is a resume-generating event.

## Measuring ROI: The Dashboard

Here's a controversial opinion: if you can't measure it, you shouldn't build it. Especially with AI tools, where the hype-to-value ratio can be... unfavorable.

I built metrics tracking from day one. Not because dashboards are fun (they are), but because I needed to prove this thing actually saves time.

```typescript
// Engineering hours saved calculation
// Assumption: Manual review = 30 min, AI analysis = 3 min
// Saved per PR = 27 min = 0.45 hours
const engineeringHoursSaved = (prsAnalyzed * 0.45).toFixed(0)

// Auto-approved percentage
const approvedCount = reviews.filter(
  (r) => r.overallRecommendation === "APPROVE"
).length
const autoApprovedPct = ((approvedCount / reviews.length) * 100).toFixed(0)

// Average analysis time
const totalMs = reviews.reduce((sum, r) => sum + (r.metadata?.tookMs || 0), 0)
const avgAnalysisTimeMinutes = (totalMs / reviews.length / 1000 / 60).toFixed(1)
```

The four key metrics:

1. **PRs Analyzed** - Volume. Is this thing actually being used?
2. **Avg Analysis Time** - Speed. We're hitting under 10 seconds typically.
3. **Auto-Approval Rate** - Efficiency. What percentage of PRs pass without human intervention?
4. **Engineering Hours Saved** - Business value. The number that makes managers smile.

The math is simple: if a manual review takes 30 minutes and AI analysis takes 3 minutes, that's 27 minutes saved per PR. Multiply by 0.45 hours and you've got your ROI story.

## Lessons Learned

**Prompt engineering is the hard part.** Writing prompts is like explaining things to a very smart alien who takes everything literally. "Review this code" gets you generic fluff. "Find specific issues, quote line numbers, no generic praise" gets you useful output.

**Be specific about what you DON'T want.** The "NO generic fluff" instruction in my prompts exists because without it, every review included phrases like "the code demonstrates good practices." Useful for ego; useless for quality.

**Dummy mode is essential.** Early in development, I burned through $50 in OpenAI credits in a single afternoon of testing. Now I have a `DUMMY_MODE` environment variable that returns canned responses. Your OpenAI bill will thank you.

**File-based storage is surprisingly practical.** I started with JSON files in a `data/reviews` directory instead of a database. It's easy to debug (just `cat` the file), easy to backup (it's just files), and easy to understand. Sometimes the boring solution is the right solution.

**Sequential agents enable context sharing.** The Priority Agent running last isn't just about ordering—it means the agent responsible for identifying what matters most has access to everything everyone else found. It's architecture serving a purpose, not just accidental complexity.

90% of AI development is prompt tweaking and existential questioning of your JSON schemas. The other 10% is explaining to people that no, the AI won't replace developers, it just makes the annoying parts less annoying.

## What's Next

The system works, but there's always more to build:

- **New agents** - Documentation quality checker, code duplication detector
- **GitHub Actions integration** - Already working, runs on every PR automatically
- **Open source** - Considering releasing this for others to use and improve

If you're drowning in PR reviews or just curious about AI-powered development tools, I'd love to hear about your experiences. The code review problem isn't going away—but maybe we can make the wait a little less like the DMV.

---

*Built with Temporal, OpenAI, and an unhealthy obsession with automation.*
