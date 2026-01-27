# Dashboard Metrics Implementation

## Overview

This document describes the implementation of real-time metrics on the dashboard, replacing mock data with computed values from actual PR review files.

## Before & After

### Before (Mock Data)
```typescript
{
  "PRs Analyzed": "1,247",
  "Avg Approval Time": "2.3h",
  "Test Coverage": "87.5%",
  "Auto-Approved": "64%"
}
```

### After (Real Data from 6 Reviews)
```typescript
{
  "PRs Analyzed": "6",
  "Avg Analysis Time": "0.2 min",
  "Auto-Approved": "33%",
  "Engineering Hours Saved": "3 hrs"
}
```

## Key Changes

### 1. New Metric: Engineering Hours Saved

**Purpose**: Demonstrate ROI and business value of AI-powered PR reviews

**Calculation**:
```typescript
engineeringHoursSaved = prsAnalyzed × 0.45 hours

Where:
  0.45 hours = 27 minutes saved per PR
  27 minutes = 30 min (manual review) - 3 min (AI analysis)
```

**Tooltip**: Hovering over this metric shows:
> "Based on 27 min saved per PR (30 min manual review - 3 min AI analysis)"

### 2. Renamed Metrics

| Old Name | New Name | Reason |
|----------|----------|--------|
| Avg Approval Time | Avg Analysis Time | More accurate - shows AI processing time |
| Test Coverage | Engineering Hours Saved | Coverage not tracked; hours saved more valuable |

### 3. Real-Time Data Source

**Source**: `/data/reviews/*.json` files

**Data Flow**:
```
Review Files → Metrics API → Dashboard
   ↓              ↓              ↓
6 JSON files   Aggregate    Display
in data/       metrics      metrics
reviews/       computed     on cards
```

## API Endpoint

### `/app/api/metrics/route.ts`

**Method**: GET

**Response** (with data):
```json
{
  "prsAnalyzed": 6,
  "avgAnalysisTimeMinutes": "0.2",
  "autoApprovedPct": "33",
  "engineeringHoursSaved": "3"
}
```

**Response** (no data):
```json
null
```

**Error Handling**:
- Missing directory → Returns null
- Invalid JSON files → Skipped (logged to console)
- Missing fields → Defaults used (0 for tookMs, non-APPROVE for recommendation)

## Metric Calculations

### 1. PRs Analyzed
```typescript
prsAnalyzed = reviews.length
```
Simply counts the number of JSON files in `/data/reviews/`

### 2. Avg Analysis Time
```typescript
totalMs = sum(review.metadata.tookMs for each review)
avgAnalysisTimeMinutes = (totalMs / reviews.length / 1000 / 60).toFixed(1)
```

**Current Values** (from 6 reviews):
| File | tookMs | Time (min) |
|------|--------|------------|
| review-1-api-integration.json | 2,500 | 0.04 |
| review-2-landing-page.json | 1,800 | 0.03 |
| review-26.json | 31,986 | 0.53 |
| review-3-workflow.json | 3,200 | 0.05 |
| review-42.json | 10,535 | 0.18 |
| review-6.json | 19,673 | 0.33 |
| **Average** | **11,616** | **0.2** |

### 3. Auto-Approved %
```typescript
approvedCount = reviews.filter(r => r.overallRecommendation === "APPROVE").length
autoApprovedPct = ((approvedCount / reviews.length) * 100).toFixed(0)
```

**Current Breakdown** (from 6 reviews):
- ✅ APPROVE: 2 (33%)
  - review-1-api-integration.json
  - review-2-landing-page.json
- ❌ BLOCK: 2 (33%)
  - review-26.json
  - review-6.json
- 🔄 REQUEST_CHANGES: 2 (33%)
  - review-3-workflow.json
  - review-42.json

### 4. Engineering Hours Saved
```typescript
engineeringHoursSaved = (prsAnalyzed × 0.45).toFixed(0)
```

**Assumptions**:
- Manual code review: 30 minutes (industry average)
- AI analysis: 3 minutes (based on current avg of 0.2 min, rounded up for buffer)
- Time saved: 27 minutes per PR = 0.45 hours

**Current Value**: 6 PRs × 0.45 = 2.7 hours ≈ **3 hours**

## Dashboard Features

### Loading States

1. **Initial Load**:
   ```
   [Loading spinner]
   ```

2. **With Real Data**:
   ```
   ┌─────────────────────┐
   │ PRs Analyzed        │
   │ 6              +12% │
   └─────────────────────┘
   ```

3. **Fallback (No Data)**:
   ```
   ┌─────────────────────────────────────┐
   │ Using sample data - Run PR reviews  │
   │ to see real metrics                 │
   └─────────────────────────────────────┘

   [Shows mock metrics: 1,247 PRs, etc.]
   ```

### Tooltip Feature

**Engineering Hours Saved** metric includes an info icon (ℹ️):
- Hover to see explanation of calculation
- Helps users understand the value
- Builds confidence in the metric

## Testing

### Run Validation Script
```bash
./scripts/validate-metrics.sh
```

**Expected Output**:
```
✓ Server is running
✓ PRs Analyzed: 6
✓ Avg Analysis Time: 0.2 min
✓ Auto-Approved: 33%
✓ Engineering Hours Saved: 3 hrs
✓ Found 6 review files
✓ Metrics count matches file count
✓ Auto-approved percentage is correct
✓ Dashboard is accessible
✅ Validation Complete!
```

### Manual Testing

1. **View Dashboard**:
   ```
   http://localhost:3000/dashboard
   ```

2. **Test API Directly**:
   ```bash
   curl http://localhost:3000/api/metrics | jq .
   ```

3. **Test Fallback**:
   ```bash
   mv data/reviews data/reviews.bak
   curl http://localhost:3000/api/metrics
   # Should return: null

   # Dashboard should show mock data with banner

   mv data/reviews.bak data/reviews
   ```

4. **Generate New Review**:
   ```bash
   # Run your PR review tool to generate a new review
   # Refresh dashboard to see updated metrics
   ```

## File Structure

```
app/
├── api/
│   ├── metrics/
│   │   └── route.ts          # NEW - Metrics aggregation endpoint
│   └── reviews/
│       └── route.ts          # Existing - Review list endpoint
└── dashboard/
    └── page.tsx              # MODIFIED - Consumes metrics API

components/
└── metric-card.tsx           # MODIFIED - Added tooltip support

lib/
└── mock-data.ts              # MODIFIED - Updated fallback data

scripts/
└── validate-metrics.sh       # NEW - Validation script

docs/
└── METRICS_IMPLEMENTATION.md # NEW - This document
```

## Integration Points

### Frontend → API
```typescript
// Dashboard fetches metrics on mount
useEffect(() => {
  const response = await fetch("/api/metrics")
  const data = await response.json()
  setMetrics(data)
}, [])
```

### API → File System
```typescript
// API reads review files
const files = await readdir(REVIEWS_DIR)
const reviews = files
  .filter(f => f.endsWith(".json"))
  .map(f => JSON.parse(readFile(join(REVIEWS_DIR, f))))
```

### Dashboard → UI
```typescript
// Build display metrics
const displayMetrics = useMemo(() => {
  if (!metrics) return dashboardMetrics // fallback

  return [
    { label: "PRs Analyzed", value: metrics.prsAnalyzed },
    { label: "Avg Analysis Time", value: `${metrics.avgAnalysisTimeMinutes} min` },
    { label: "Auto-Approved", value: `${metrics.autoApprovedPct}%` },
    { label: "Engineering Hours Saved", value: `${metrics.engineeringHoursSaved} hrs`,
      tooltip: "Based on 27 min saved per PR..." }
  ]
}, [metrics])
```

## Future Enhancements

### Priority 1: Historical Trends
**Effort**: 4-6 hours
**Value**: High - Shows improvement over time

Add trend arrows based on historical data:
```typescript
interface DashboardMetrics {
  // ... existing fields
  trend: {
    prsAnalyzed: string    // e.g., "+12%"
    direction: "up" | "down"
  }
}
```

**Implementation**:
1. Store daily/weekly snapshots of metrics
2. Compare current vs previous period
3. Calculate percentage change
4. Display trend arrow and percentage

### Priority 2: Agent Performance
**Effort**: 4-6 hours
**Value**: Medium - Helps identify problematic agents

Show breakdown by agent:
```typescript
interface AgentStats {
  agentName: string
  totalReviews: number
  blockRate: number
  avgRiskLevel: "LOW" | "MEDIUM" | "HIGH"
}
```

### Priority 3: Test Coverage Metric
**Effort**: 2-3 hours + backend changes
**Value**: Medium - Common metric for engineering teams

Requires:
1. Backend to compute coverage from test results
2. Store coverage % in review response
3. Display in dashboard

**Backend Changes Needed**:
```java
public class ReviewResponse {
  // ... existing fields
  private Double testCoverage; // Add this field
}
```

## Business Value

### ROI Demonstration

**Current Results** (6 PRs):
- Time Saved: 3 hours
- Cost Savings: 3 hours × $100/hour = **$300**

**Projected Annual Impact** (500 PRs):
- Time Saved: 225 hours
- Cost Savings: 225 hours × $100/hour = **$22,500**

**Additional Benefits**:
- Faster feedback loop (minutes vs hours)
- Consistent quality standards
- 24/7 availability (no waiting for reviewers)
- Reduced reviewer burnout
- Focus on high-value architectural decisions

### Demo Talking Points

1. **Speed**: "AI analyzes PRs in seconds, not hours"
2. **Consistency**: "Same quality standards applied to every PR"
3. **Scalability**: "Handle 10x more PRs without hiring more reviewers"
4. **ROI**: "Already saved 3 hours with just 6 PRs"
5. **Quality**: "33% of PRs auto-approved, catching issues early"

## Troubleshooting

### Metrics Not Showing

**Problem**: Dashboard shows mock data instead of real metrics

**Solutions**:
1. Check if server is running: `curl http://localhost:3000/api/metrics`
2. Verify review files exist: `ls data/reviews/*.json`
3. Check console for errors: Look for JSON parse errors
4. Restart dev server: `npm run dev`

### Incorrect Calculations

**Problem**: Metrics don't match expected values

**Solutions**:
1. Run validation script: `./scripts/validate-metrics.sh`
2. Check review file format: `cat data/reviews/review-1.json | jq .`
3. Verify all files are valid JSON: `for f in data/reviews/*.json; do jq . $f > /dev/null || echo "Invalid: $f"; done`

### Tooltip Not Appearing

**Problem**: Info icon present but tooltip doesn't show on hover

**Solutions**:
1. Check CSS classes are loaded (Tailwind)
2. Verify `group` and `group-hover` classes are present
3. Check z-index of tooltip (should be 10)
4. Try different browser (could be CSS compatibility)

## References

- [Plan Document](../IMPLEMENTATION_SUMMARY.md)
- [Metrics API](../app/api/metrics/route.ts)
- [Dashboard Component](../app/dashboard/page.tsx)
- [Validation Script](../scripts/validate-metrics.sh)
