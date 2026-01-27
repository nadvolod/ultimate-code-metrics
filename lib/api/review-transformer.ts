import type {
  BackendReviewResponse,
  BackendRiskLevel,
  TestReport,
  Finding,
  FindingSeverity,
  FrontendRecommendation,
} from "@/lib/types/review"

// Risk level to confidence mapping
const RISK_TO_CONFIDENCE: Record<BackendRiskLevel, number> = {
  LOW: 95,
  MEDIUM: 75,
  HIGH: 50,
}

// Risk level to severity mapping
const RISK_TO_SEVERITY: Record<BackendRiskLevel, FindingSeverity> = {
  LOW: "low",
  MEDIUM: "medium",
  HIGH: "high",
}

/**
 * Maps backend recommendation to frontend format
 * REQUEST_CHANGES and BLOCK both map to BLOCK
 */
function mapRecommendation(backendRec: string): FrontendRecommendation {
  return backendRec === "APPROVE" ? "APPROVE" : "BLOCK"
}

/**
 * Calculates overall confidence based on agent risk levels
 * Uses the worst (highest risk) level to determine confidence
 */
function calculateConfidence(response: BackendReviewResponse): number {
  if (!response.agents || response.agents.length === 0) {
    return 75 // Default medium confidence
  }

  // Get all risk levels and find the worst one
  const riskLevels = response.agents.map((a) => a.riskLevel)
  const hasHigh = riskLevels.includes("HIGH")
  const hasMedium = riskLevels.includes("MEDIUM")

  if (hasHigh) return RISK_TO_CONFIDENCE.HIGH
  if (hasMedium) return RISK_TO_CONFIDENCE.MEDIUM
  return RISK_TO_CONFIDENCE.LOW
}

/**
 * Transforms agent findings into frontend Finding format
 */
function transformFindings(response: BackendReviewResponse): Finding[] {
  const findings: Finding[] = []

  for (const agent of response.agents || []) {
    const severity = RISK_TO_SEVERITY[agent.riskLevel]
    const category = agent.agentName

    for (const message of agent.findings || []) {
      findings.push({
        category,
        severity,
        message,
      })
    }
  }

  return findings
}

/**
 * Formats a timestamp for display
 */
function formatTimestamp(isoString: string): string {
  const date = new Date(isoString)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffMins = Math.floor(diffMs / (1000 * 60))
  const diffHours = Math.floor(diffMs / (1000 * 60 * 60))
  const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24))

  if (diffMins < 1) return "just now"
  if (diffMins < 60) return `${diffMins} minute${diffMins === 1 ? "" : "s"} ago`
  if (diffHours < 24) return `${diffHours} hour${diffHours === 1 ? "" : "s"} ago`
  if (diffDays < 7) return `${diffDays} day${diffDays === 1 ? "" : "s"} ago`
  return date.toLocaleDateString()
}

/**
 * Generates a unique ID from filename and timestamp
 */
function generateId(filename: string, response: BackendReviewResponse): string {
  const base = filename.replace(/\.json$/, "")
  const timestamp = response.metadata?.generatedAt || Date.now().toString()
  return `${base}-${new Date(timestamp).getTime()}`
}

/**
 * Extracts PR metadata from the response or filename
 * Falls back to sensible defaults if not present
 */
function extractPrMetadata(
  response: BackendReviewResponse,
  filename: string
): { prNumber: number; prTitle: string; author: string } {
  // Try to use data from the response first
  if (response.prNumber && response.prTitle) {
    return {
      prNumber: response.prNumber,
      prTitle: response.prTitle,
      author: response.author || "unknown",
    }
  }

  // Try to extract PR number from filename (e.g., "review-123.json")
  const prMatch = filename.match(/(\d+)/)
  const prNumber = prMatch ? parseInt(prMatch[1], 10) : 0

  return {
    prNumber,
    prTitle: `PR #${prNumber || "Review"}`,
    author: response.author || "unknown",
  }
}

/**
 * Transforms a backend ReviewResponse to frontend TestReport format
 */
export function transformReviewResponse(
  response: BackendReviewResponse,
  filename: string
): TestReport {
  const { prNumber, prTitle, author } = extractPrMetadata(response, filename)

  return {
    id: generateId(filename, response),
    prNumber,
    prTitle,
    author,
    recommendation: mapRecommendation(response.overallRecommendation),
    confidence: calculateConfidence(response),
    timestamp: formatTimestamp(response.metadata?.generatedAt || new Date().toISOString()),
    metrics: {
      // Default metrics - backend doesn't provide these yet
      coverage: 0,
      testsAdded: 0,
      testsModified: 0,
      filesChanged: 0,
    },
    findings: transformFindings(response),
  }
}

/**
 * Transforms multiple review responses
 */
export function transformReviewResponses(
  reviews: Array<{ response: BackendReviewResponse; filename: string }>
): TestReport[] {
  // Transform reviews and pair with raw timestamps for sorting
  const reportsWithTimestamps = reviews.map(({ response, filename }) => ({
    report: transformReviewResponse(response, filename),
    timestamp: response.metadata?.generatedAt || new Date(0).toISOString(),
  }))

  // Sort by actual timestamp (most recent first)
  return reportsWithTimestamps
    .sort((a, b) => {
      const timeA = new Date(a.timestamp).getTime()
      const timeB = new Date(b.timestamp).getTime()
      return timeB - timeA
    })
    .map(({ report }) => report)
}
