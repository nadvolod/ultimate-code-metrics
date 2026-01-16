import { MetricCard } from "@/components/metric-card"
import { ReportPanel } from "@/components/report-panel"
import { dashboardMetrics, recentReports } from "@/lib/mock-data"
import { TrendingUp } from "lucide-react"

export default function DashboardPage() {
  return (
    <div className="space-y-8">
      {/* Metrics Grid */}
      <div>
        <h2 className="text-lg font-semibold text-foreground mb-4">Overview Metrics</h2>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
          {dashboardMetrics.map((metric, idx) => (
            <MetricCard key={idx} {...metric} index={idx} />
          ))}
        </div>
      </div>

      {/* Chart Placeholder */}
      <div className="bg-card border border-border rounded-lg p-6 animate-fade-in">
        <div className="flex items-center justify-between mb-6">
          <h2 className="text-lg font-semibold text-foreground">PR Analysis Trend</h2>
          <div className="flex items-center gap-2 text-sm text-success">
            <TrendingUp size={16} />
            <span>+12% this week</span>
          </div>
        </div>
        <div className="h-64 flex items-center justify-center bg-muted/30 rounded-lg border border-border/50">
          <p className="text-muted-foreground text-sm">Chart visualization would go here</p>
        </div>
      </div>

      {/* Recent Reports */}
      <div>
        <h2 className="text-lg font-semibold text-foreground mb-4">Recent PR Analysis</h2>
        <div className="space-y-4">
          {recentReports.map((report, idx) => (
            <ReportPanel key={report.id} {...report} index={idx} />
          ))}
        </div>
      </div>
    </div>
  )
}
