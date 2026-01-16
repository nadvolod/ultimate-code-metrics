"use client"

import { useEffect, useState } from "react"
import { MetricCard } from "@/components/metric-card"
import { ReportPanel } from "@/components/report-panel"
import { dashboardMetrics, recentReports as mockReports } from "@/lib/mock-data"
import type { TestReport } from "@/lib/types/review"
import { TrendingUp, Loader2 } from "lucide-react"

export default function DashboardPage() {
  const [reports, setReports] = useState<TestReport[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    async function fetchReports() {
      try {
        const response = await fetch("/api/reviews")
        if (!response.ok) {
          throw new Error("Failed to fetch reviews")
        }
        const data = await response.json()
        setReports(data)
      } catch (err) {
        console.error("Error fetching reviews:", err)
        setError(err instanceof Error ? err.message : "Failed to load reviews")
      } finally {
        setLoading(false)
      }
    }

    fetchReports()
  }, [])

  // Use API data if available, otherwise fall back to mock data
  const displayReports = reports.length > 0 ? reports : mockReports

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
        {loading ? (
          <div className="flex items-center justify-center py-12">
            <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
          </div>
        ) : error && reports.length === 0 ? (
          <div className="text-center py-8">
            <p className="text-muted-foreground mb-2">Using sample data</p>
            <p className="text-xs text-muted-foreground">Run a PR review to see real data</p>
          </div>
        ) : null}
        <div className="space-y-4">
          {!loading && displayReports.map((report, idx) => (
            <ReportPanel key={report.id} {...report} index={idx} />
          ))}
        </div>
      </div>
    </div>
  )
}
