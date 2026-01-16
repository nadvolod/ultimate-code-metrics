// Backend types (matches Java ReviewResponse)
export type BackendRecommendation = "APPROVE" | "REQUEST_CHANGES" | "BLOCK"
export type BackendRiskLevel = "LOW" | "MEDIUM" | "HIGH"

export interface BackendAgent {
  agentName: string
  riskLevel: BackendRiskLevel
  recommendation: BackendRecommendation
  findings: string[]
}

export interface BackendMetadata {
  generatedAt: string
  tookMs: number
  model: string
}

export interface BackendReviewResponse {
  overallRecommendation: BackendRecommendation
  agents: BackendAgent[]
  metadata: BackendMetadata
  // Optional PR metadata (to be added to backend)
  prNumber?: number
  prTitle?: string
  author?: string
}

// Frontend types (used by dashboard)
export type FrontendRecommendation = "APPROVE" | "BLOCK"
export type FindingSeverity = "high" | "medium" | "low"

export interface Finding {
  category: string
  severity: FindingSeverity
  message: string
}

export interface TestReportMetrics {
  coverage: number
  testsAdded: number
  testsModified: number
  filesChanged: number
}

export interface TestReport {
  id: string
  prNumber: number
  prTitle: string
  author: string
  recommendation: FrontendRecommendation
  confidence: number
  timestamp: string
  metrics: TestReportMetrics
  findings: Finding[]
}
