export interface MetricCard {
  label: string
  value: string
  change?: string
  trend?: "up" | "down"
}

export interface DataSource {
  id: string
  name: string
  type: string
  status: "connected" | "disconnected" | "error"
  lastSync?: string
  icon: string
}

export interface TestReport {
  id: string
  prNumber: number
  prTitle: string
  author: string
  recommendation: "APPROVE" | "BLOCK"
  confidence: number
  timestamp: string
  metrics: {
    coverage: number
    testsAdded: number
    testsModified: number
    filesChanged: number
  }
  findings: {
    category: string
    severity: "high" | "medium" | "low"
    message: string
  }[]
}

export const dashboardMetrics: MetricCard[] = [
  {
    label: "PRs Analyzed",
    value: "1,247",
    change: "+12%",
    trend: "up",
  },
  {
    label: "Avg Approval Time",
    value: "2.3h",
    change: "-18%",
    trend: "down",
  },
  {
    label: "Test Coverage",
    value: "87.5%",
    change: "+5.2%",
    trend: "up",
  },
  {
    label: "Auto-Approved",
    value: "64%",
    change: "+8%",
    trend: "up",
  },
]

export const dataSources: DataSource[] = [
  {
    id: "1",
    name: "acme-corp/web-app",
    type: "GitHub Repository",
    status: "connected",
    lastSync: "2 minutes ago",
    icon: "github",
  },
  {
    id: "2",
    name: "acme-corp/api-service",
    type: "GitHub Repository",
    status: "connected",
    lastSync: "5 minutes ago",
    icon: "github",
  },
  {
    id: "3",
    name: "acme-corp/mobile-app",
    type: "GitHub Repository",
    status: "disconnected",
    lastSync: "2 days ago",
    icon: "github",
  },
]

export const recentReports: TestReport[] = [
  {
    id: "1",
    prNumber: 1337,
    prTitle: "Add user authentication flow",
    author: "sarah.dev",
    recommendation: "APPROVE",
    confidence: 94,
    timestamp: "5 minutes ago",
    metrics: {
      coverage: 92,
      testsAdded: 12,
      testsModified: 3,
      filesChanged: 8,
    },
    findings: [
      {
        category: "Test Coverage",
        severity: "low",
        message: "Excellent coverage for new authentication logic (92%)",
      },
      {
        category: "Code Quality",
        severity: "low",
        message: "Well-structured unit tests with good edge case handling",
      },
    ],
  },
  {
    id: "2",
    prNumber: 1336,
    prTitle: "Refactor payment processing",
    author: "mike.eng",
    recommendation: "BLOCK",
    confidence: 88,
    timestamp: "1 hour ago",
    metrics: {
      coverage: 45,
      testsAdded: 2,
      testsModified: 0,
      filesChanged: 15,
    },
    findings: [
      {
        category: "Test Coverage",
        severity: "high",
        message: "Critical payment logic lacks adequate test coverage (45%)",
      },
      {
        category: "Risk Analysis",
        severity: "high",
        message: "High-risk code changes to payment processing without sufficient tests",
      },
      {
        category: "Code Quality",
        severity: "medium",
        message: "New error handling paths need additional test coverage",
      },
    ],
  },
  {
    id: "3",
    prNumber: 1335,
    prTitle: "Update dashboard styling",
    author: "alex.design",
    recommendation: "APPROVE",
    confidence: 76,
    timestamp: "3 hours ago",
    metrics: {
      coverage: 88,
      testsAdded: 0,
      testsModified: 2,
      filesChanged: 4,
    },
    findings: [
      {
        category: "Test Coverage",
        severity: "low",
        message: "UI changes have appropriate snapshot tests",
      },
      {
        category: "Risk Analysis",
        severity: "low",
        message: "Low-risk styling changes, no breaking changes detected",
      },
    ],
  },
]

export const settingsConfig = {
  autoApproval: {
    enabled: true,
    minCoverage: 80,
    minConfidence: 85,
  },
  notifications: {
    email: true,
    slack: true,
    webhooks: false,
  },
  aiModel: {
    provider: "OpenAI",
    model: "gpt-4",
    temperature: 0.3,
  },
}
