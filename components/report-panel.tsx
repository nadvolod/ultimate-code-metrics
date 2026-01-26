import { StatusBadge } from "./status-badge"
import { AlertCircle, CheckCircle2, AlertTriangle, ExternalLink } from "lucide-react"

interface ReportPanelProps {
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
  index?: number
}

export function ReportPanel({
  prNumber,
  prTitle,
  author,
  recommendation,
  confidence,
  timestamp,
  metrics,
  findings,
  index = 0,
}: ReportPanelProps) {
  const severityConfig = {
    high: { icon: AlertCircle, color: "text-destructive" },
    medium: { icon: AlertTriangle, color: "text-warning" },
    low: { icon: CheckCircle2, color: "text-success" },
  }

  // Build GitHub PR URL if repo config is available
  const githubUrl =
    process.env.NEXT_PUBLIC_GITHUB_REPO_OWNER && process.env.NEXT_PUBLIC_GITHUB_REPO_NAME
      ? `https://github.com/${process.env.NEXT_PUBLIC_GITHUB_REPO_OWNER}/${process.env.NEXT_PUBLIC_GITHUB_REPO_NAME}/pull/${prNumber}`
      : null

  return (
    <div
      className="bg-card border border-border rounded-lg p-6 space-y-4 hover:border-primary/30 transition-all duration-300 hover:shadow-lg hover:shadow-primary/5 animate-scale-in"
      style={{ animationDelay: `${index * 100}ms` }}
    >
      <div className="flex items-start justify-between">
        <div className="space-y-1">
          <div className="flex items-center gap-2">
            {githubUrl ? (
              <a
                href={githubUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="font-semibold text-foreground hover:text-primary transition-colors flex items-center gap-1"
              >
                PR #{prNumber}
                <ExternalLink size={14} className="opacity-70" />
              </a>
            ) : (
              <h3 className="font-semibold text-foreground">PR #{prNumber}</h3>
            )}
            <StatusBadge status={recommendation} size="sm" />
          </div>
          <p className="text-sm text-muted-foreground">{prTitle}</p>
          <p className="text-xs text-muted-foreground">
            by {author} • {timestamp}
          </p>
        </div>
        <div className="text-right">
          <p className="text-2xl font-semibold text-foreground">{confidence}%</p>
          <p className="text-xs text-muted-foreground">Confidence</p>
        </div>
      </div>

      <div className="grid grid-cols-3 gap-4 pt-4 border-t border-border">
        <div>
          <p className="text-xs text-muted-foreground">Coverage</p>
          <p className="text-lg font-semibold text-foreground">{metrics.coverage}%</p>
        </div>
        <div>
          <p className="text-xs text-muted-foreground">Tests Added</p>
          <p className="text-lg font-semibold text-foreground">{metrics.testsAdded}</p>
        </div>
        <div>
          <p className="text-xs text-muted-foreground">Tests Modified</p>
          <p className="text-lg font-semibold text-foreground">{metrics.testsModified}</p>
        </div>
      </div>

      <div className="space-y-2 pt-2">
        <p className="text-sm font-medium text-foreground">Issues by Agent</p>
        {findings.length > 0 ? (
          <div className="grid grid-cols-2 gap-2">
            {Object.entries(
              findings.reduce(
                (acc, finding) => {
                  if (!acc[finding.category]) {
                    acc[finding.category] = {
                      count: 0,
                      severity: finding.severity,
                    }
                  }
                  acc[finding.category].count++

                  // Track highest severity
                  if (
                    finding.severity === "high" ||
                    (finding.severity === "medium" && acc[finding.category].severity === "low")
                  ) {
                    acc[finding.category].severity = finding.severity
                  }

                  return acc
                },
                {} as Record<string, { count: number; severity: "high" | "medium" | "low" }>
              )
            ).map(([agentName, summary]) => {
              const { icon: Icon, color } = severityConfig[summary.severity]
              return (
                <div key={agentName} className="flex items-center gap-2 text-sm">
                  <Icon size={16} className={`flex-shrink-0 ${color}`} />
                  <span className="font-medium text-foreground">{agentName}:</span>
                  <span className="text-muted-foreground">{summary.count} issue{summary.count !== 1 ? 's' : ''}</span>
                </div>
              )
            })}
          </div>
        ) : (
          <p className="text-sm text-muted-foreground">No issues found</p>
        )}
      </div>
    </div>
  )
}
