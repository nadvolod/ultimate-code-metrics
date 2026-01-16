"use client"

import { useEffect, useState } from "react"
import { ReportPanel } from "@/components/report-panel"
import { recentReports as mockReports } from "@/lib/mock-data"
import type { TestReport } from "@/lib/types/review"
import { Button } from "@/components/ui/button"
import { Filter, Download, Loader2 } from "lucide-react"

export default function ReportsPage() {
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
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold text-foreground">AI Analysis Reports</h2>
          <p className="text-muted-foreground mt-1">Review detailed findings and recommendations</p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" className="gap-2 bg-transparent">
            <Filter size={18} />
            Filter
          </Button>
          <Button variant="outline" className="gap-2 bg-transparent">
            <Download size={18} />
            Export
          </Button>
        </div>
      </div>

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

      {!loading && (
        <div className="space-y-4">
          {displayReports.map((report, idx) => (
            <ReportPanel key={report.id} {...report} index={idx} />
          ))}
        </div>
      )}
    </div>
  )
}
