import { StatusBadge } from "./status-badge"
import { AlertCircle, CheckCircle2, AlertTriangle } from "lucide-react"

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

  return (
    <div
      className="bg-card border border-border rounded-lg p-6 space-y-4 hover:border-primary/30 transition-all duration-300 hover:shadow-lg hover:shadow-primary/5 animate-scale-in"
      style={{ animationDelay: `${index * 100}ms` }}
    >
      <div className="flex items-start justify-between">
        <div className="space-y-1">
          <div className="flex items-center gap-2">
            <h3 className="font-semibold text-foreground">PR #{prNumber}</h3>
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

      <div className="grid grid-cols-4 gap-4 pt-4 border-t border-border">
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
        <div>
          <p className="text-xs text-muted-foreground">Files Changed</p>
          <p className="text-lg font-semibold text-foreground">{metrics.filesChanged}</p>
        </div>
      </div>

      <div className="space-y-2 pt-2">
        <p className="text-sm font-medium text-foreground">Findings</p>
        {findings.map((finding, idx) => {
          const { icon: Icon, color } = severityConfig[finding.severity]
          return (
            <div key={idx} className="flex items-start gap-2 text-sm">
              <Icon size={16} className={`mt-0.5 flex-shrink-0 ${color}`} />
              <div>
                <span className="font-medium text-foreground">{finding.category}:</span>{" "}
                <span className="text-muted-foreground">{finding.message}</span>
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
